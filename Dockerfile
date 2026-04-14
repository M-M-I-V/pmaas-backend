# Multi-stage Dockerfile for pmaas
#
# SECURITY HARDENING:
#   - Pinned exact image tag — never use :latest (prevents silent base image changes)
#   - Non-root runtime user 'pmaas' (principle of least privilege)
#   - Separate build stage: Maven, JDK, and source code are NOT in the final image
#   - No secrets in ENV or ARG — all secrets injected at runtime via environment
#
# JVM FLAGS (Render free tier — 512 MB RAM):
#   -Xmx384m     Max heap: 384 MB (leaves ~128 MB for OS + non-heap)
#   -Xss512k     Stack size per thread: 512 KB (reduced from default 1 MB)
#   -XX:+UseG1GC G1 garbage collector — better pause times than default
#
# HEALTHCHECK:
#   Requires spring-boot-starter-actuator in pom.xml (add when ready).
#   Render uses this to determine when the container is ready to serve traffic.
#   Until actuator is added, Render will use TCP port check instead.

# Stage 1: Build
FROM eclipse-temurin:21.0.5_11-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn -q -B dependency:go-offline --no-transfer-progress
COPY src ./src
RUN mvn -q -B clean package -DskipTests --no-transfer-progress

# Stage 2: Runtime
FROM eclipse-temurin:21.0.5_11-jre-alpine
RUN addgroup -S pmaas && adduser -S pmaas -G pmaas
WORKDIR /app
COPY --from=build /app/target/pmaas-*.jar app.jar
RUN mkdir -p /app/uploads && chown -R pmaas:pmaas /app
USER pmaas
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep UP || exit 1
ENTRYPOINT ["java", \
    "-Xmx384m", \
    "-Xms128m", \
    "-Xss512k", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
