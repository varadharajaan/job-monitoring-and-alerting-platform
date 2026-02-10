# ═══════════════════════════════════════════════════════════════
# Multi-stage Dockerfile — job-monitoring-service
# ═══════════════════════════════════════════════════════════════

FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
COPY platform/ platform/
COPY services/job-monitoring-service/ services/job-monitoring-service/

RUN chmod +x gradlew && \
    ./gradlew :services:job-monitoring-service:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy

RUN groupadd -r appuser && useradd -r -g appuser -d /app appuser
WORKDIR /app

COPY --from=builder /workspace/services/job-monitoring-service/build/libs/*.jar app.jar

RUN mkdir -p /var/log/jobmonitor && chown -R appuser:appuser /app /var/log/jobmonitor

USER appuser

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=40s \
    CMD curl -f http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar app.jar"]
