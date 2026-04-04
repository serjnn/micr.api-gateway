FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/api-gateway-0.0.1-SNAPSHOT.jar /app/api-gateway.jar
EXPOSE 8989
ENTRYPOINT ["java", "-jar", "/app/api-gateway.jar"]
