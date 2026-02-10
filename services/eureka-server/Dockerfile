FROM eclipse-temurin:17-jdk AS builder
WORKDIR /build
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
COPY platform/ platform/
COPY buildSrc/ buildSrc/
COPY services/eureka-server/ services/eureka-server/
RUN chmod +x gradlew && ./gradlew :services:eureka-server:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
COPY --from=builder /build/services/eureka-server/build/libs/*.jar app.jar
USER appuser
EXPOSE 8761
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -sf http://localhost:8761/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
