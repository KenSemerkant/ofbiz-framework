# Distroless JRE 21 image (FUTURESTATE-LOWLEVEL §1). Jar built by Gradle bootJar.
FROM gcr.io/distroless/java21-debian13:nonroot
COPY build/libs/gateway.jar /app/gateway.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/gateway.jar"]
