# Distroless JRE 21 image (FUTURESTATE-LOWLEVEL §1). Jar built by Gradle bootJar.
FROM gcr.io/distroless/java21-debian13:nonroot
COPY party-adapters/adapters-rest/build/libs/adapters-rest.jar /app/party-svc.jar
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "/app/party-svc.jar"]
