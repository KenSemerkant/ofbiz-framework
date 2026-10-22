plugins {
    id("ofbiz-msa.spring-boot-service")
}

dependencies {
    implementation(project(":microservices:inventory-svc:inventory-application"))
    implementation(project(":microservices:inventory-svc:inventory-domain"))
    // P3-GATE: reusable CDC projection core (IdempotentProjector)
    implementation(project(":platform:projection"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation(project(":platform:arch-rules"))
}
