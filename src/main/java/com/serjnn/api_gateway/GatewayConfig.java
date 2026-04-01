package com.serjnn.api_gateway;

import org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.dedupeResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.rewritePath;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration
public class GatewayConfig {

    private final JwtValidationFilterFactory jwtValidationFilterFactory;

    public GatewayConfig(JwtValidationFilterFactory jwtValidationFilterFactory) {
        this.jwtValidationFilterFactory = jwtValidationFilterFactory;
    }

    @Bean
    public RouterFunction<ServerResponse> gatewayRoutes() {
        return route("product")
                .route(path("/product/**"), http("lb://product"))
                .filter(rewritePath("/product(?<segment>/?.*)", "${segment}"))
                .build()
                .and(baseRoute("order", "lb://order", true))
                .and(baseRoute("client", "lb://client", false))
                .and(baseRoute("bucket", "lb://bucket", true))
                .and(baseRoute("orchestrator", "lb://orchestrator", true));
    }

    private RouterFunction<ServerResponse> baseRoute(String id, String uri, boolean secured) {
        var builder = route(id)
                .route(path("/" + id + "/**"), http(uri))
                .filter(rewritePath("/" + id + "(?<segment>/?.*)", "${segment}"))
                .after(dedupeResponseHeader("Access-Control-Allow-Credentials " +
                        "Access-Control-Allow-Origin", AfterFilterFunctions.DedupeStrategy.RETAIN_UNIQUE));

        if (secured) {
            builder.filter(jwtValidationFilterFactory.validateJwt());
        }

        return builder.build();
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:3000")
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
