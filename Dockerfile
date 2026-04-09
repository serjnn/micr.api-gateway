# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and resolve dependencies
COPY pom.xml .
RUN mvn dependency:resolve-plugins -B && \
    mvn dependency:resolve -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy built jar from build stage
COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8989

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
