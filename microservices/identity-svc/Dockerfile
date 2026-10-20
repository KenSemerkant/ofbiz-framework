# Distroless JRE 21 image (FUTURESTATE-LOWLEVEL §1). Jar built by Gradle bootJar.
FROM gcr.io/distroless/java21-debian13:nonroot
COPY identity-adapters/adapters-rest/build/libs/adapters-rest.jar /app/identity-svc.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/identity-svc.jar"]
