plugins {
    id("ofbiz-msa.spring-boot-service")
}

dependencies {
    implementation(project(":microservices:notification-svc:notification-application"))
    implementation(project(":microservices:notification-svc:notification-domain"))
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("com.icegreen:greenmail:2.0.1")
    testImplementation(project(":platform:arch-rules"))
}
