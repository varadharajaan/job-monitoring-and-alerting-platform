# ═══════════════════════════════════════════════════════════════
# Multi-stage Dockerfile — notification-worker (headless Kafka consumer)
# ═══════════════════════════════════════════════════════════════

FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
COPY platform/ platform/
COPY workers/notification-worker/ workers/notification-worker/

RUN chmod +x gradlew && \
    ./gradlew :workers:notification-worker:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy

RUN groupadd -r appuser && useradd -r -g appuser -d /app appuser
WORKDIR /app

COPY --from=builder /workspace/workers/notification-worker/build/libs/*.jar app.jar

RUN mkdir -p /var/log/jobmonitor && chown -R appuser:appuser /app /var/log/jobmonitor

USER appuser

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# No EXPOSE — headless Kafka consumer (web-application-type: none)

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
