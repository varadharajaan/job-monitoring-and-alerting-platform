# ═══════════════════════════════════════════════════════════════
# Multi-stage Dockerfile — log-ingestion-service
# ═══════════════════════════════════════════════════════════════

FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
COPY platform/ platform/
COPY buildSrc/ buildSrc/
COPY services/log-ingestion-service/ services/log-ingestion-service/

RUN chmod +x gradlew && \
    ./gradlew :services:log-ingestion-service:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre-jammy

RUN groupadd -r appuser && useradd -r -g appuser -d /app appuser
WORKDIR /app

COPY --from=builder /workspace/services/log-ingestion-service/build/libs/*.jar app.jar

RUN mkdir -p /var/log/jobmonitor && chown -R appuser:appuser /app /var/log/jobmonitor

USER appuser

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8086

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
