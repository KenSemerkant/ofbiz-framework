# Distroless JRE 21 image (FUTURESTATE-LOWLEVEL §1). Jar built by Gradle bootJar.
FROM gcr.io/distroless/java21-debian13:nonroot
COPY finance-adapters/adapters-rest/build/libs/adapters-rest.jar /app/finance-svc.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/finance-svc.jar"]
