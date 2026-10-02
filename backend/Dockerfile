# Stage 1: Build with Maven
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw

COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Minimal, hardened production runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health check and create unprivileged application user with storage permissions
RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p /app/storage/documents && \
    chown -R spring:spring /app

USER spring:spring

# Copy compiled artifact from builder stage
COPY --from=builder --chown=spring:spring /app/target/aml-assistant-backend-*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=25s --retries=5 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
