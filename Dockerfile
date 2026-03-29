# Multi-stage Dockerfile for eClinic / pmaas
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

# Copy dependency descriptors first — layer cached until pom.xml changes
COPY pom.xml .

# Download dependencies into the local Maven cache (separate layer for caching)
# -q: quiet output  -B: batch (non-interactive)  --no-transfer-progress: cleaner logs
RUN apk add --no-cache maven && \
    mvn -q -B dependency:go-offline --no-transfer-progress

# Copy source code (this layer invalidates when any source file changes)
COPY src ./src

# Build the fat JAR, skipping tests (tests run in CI, not in Docker build)
RUN mvn -q -B clean package -DskipTests --no-transfer-progress

# Stage 2: Runtime
FROM eclipse-temurin:21.0.5_11-jre-alpine

# Create a non-root group and user for the application process.
# -S: system account (no home dir, no login shell)
# -G: assign to group
RUN addgroup -S pmaas && adduser -S pmaas -G pmaas

WORKDIR /app

# Copy only the compiled fat JAR from the build stage.
# Source code, JDK, Maven, and build artifacts are left behind.
COPY --from=build /app/target/pmaas-*.jar app.jar

# Create the uploads directory owned by the app user.
# NOTE: On Render free tier the filesystem is ephemeral — files here are wiped
# on every deployment. Use Cloudinary or Supabase Storage for persistent files.
RUN mkdir -p /app/uploads && chown -R pmaas:pmaas /app

# Switch to the non-root user for all subsequent commands and the runtime process
USER pmaas

# Document the port the application listens on (informational — does not publish it)
EXPOSE 8080

# Health check via Spring Boot Actuator.
# Requires spring-boot-starter-actuator + management.endpoints.web.exposure.include=health
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep UP || exit 1

ENTRYPOINT ["java", \
    "-Xmx384m", \
    "-Xss512k", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]