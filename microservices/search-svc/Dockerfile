# Distroless JRE 21 image (FUTURESTATE-LOWLEVEL §1). Jar built by Gradle bootJar.
FROM gcr.io/distroless/java21-debian13:nonroot
COPY search-adapters/adapters-rest/build/libs/adapters-rest.jar /app/search-svc.jar
EXPOSE 8080 9090
ENTRYPOINT ["java", "-jar", "/app/search-svc.jar"]
