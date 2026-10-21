plugins {
    id("ofbiz-msa.spring-boot-service")
}

dependencies {
    implementation(project(":microservices:party-svc:party-application"))
    implementation(project(":microservices:party-svc:party-domain"))
    implementation(project(":microservices:party-svc:party-adapters:adapters-grpc"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation(platform("io.grpc:grpc-bom:1.75.0"))
    implementation("io.grpc:grpc-netty-shaded")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(project(":platform:arch-rules"))
}
