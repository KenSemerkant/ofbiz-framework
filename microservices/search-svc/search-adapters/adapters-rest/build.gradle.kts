plugins {
    id("ofbiz-msa.spring-boot-service")
}

dependencies {
    implementation(project(":microservices:search-svc:search-application"))
    implementation(project(":microservices:search-svc:search-domain"))
    implementation(project(":microservices:search-svc:search-adapters:adapters-grpc"))
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.opensearch.client:opensearch-java:2.22.0")
    implementation("org.opensearch.client:opensearch-rest-client:2.11.1")
    // opensearch-java still speaks Jackson 2 (Boot 4 moved to Jackson 3)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation(platform("io.grpc:grpc-bom:1.75.0"))
    implementation("io.grpc:grpc-netty-shaded")

    testImplementation("org.testcontainers:testcontainers-kafka")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    // adapters-grpc keeps gRPC deps as implementation — the test drives the
    // generated stubs directly, so it needs them on its own classpath
    testImplementation(platform("io.grpc:grpc-bom:1.75.0"))
    testImplementation("io.grpc:grpc-protobuf")
    testImplementation("io.grpc:grpc-stub")
    testImplementation("com.google.protobuf:protobuf-java:3.25.5")
    testImplementation(project(":platform:arch-rules"))
}
