package com.serjnn.api_gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Component
public class JwtValidationFilterFactory {
    private static final Logger log = LoggerFactory.getLogger(JwtValidationFilterFactory.class);
    private final RestClient restClient;

    public JwtValidationFilterFactory(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public HandlerFilterFunction<ServerResponse, ServerResponse> validateJwt() {
        return (request, next) -> {
            log.info("Validating JWT token for request: {}", request.uri());
            String authHeader = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Unauthorized request: missing or invalid Authorization header");
                return ServerResponse.status(HttpStatus.UNAUTHORIZED).build();
            }

            String jwtToken = authHeader.substring(7);
            String validationUrl = "http://client/api/v1/auth/validate";

            try {
                var response = restClient.post()
                        .uri(validationUrl)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                        .retrieve()
                        .toBodilessEntity();

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("JWT token validated successfully");
                    return next.handle(request);
                } else {
                    log.warn("Forbidden request: JWT validation failed with status: {}", response.getStatusCode());
                    return ServerResponse.status(HttpStatus.FORBIDDEN).build();
                }
            } catch (Exception e) {
                log.error("Error validating JWT token at {}: {}", validationUrl, e.getMessage(), e);
                return ServerResponse.status(HttpStatus.BAD_GATEWAY).build();
            }
        };
    }
}
