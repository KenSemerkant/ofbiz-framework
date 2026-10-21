plugins {
    id("ofbiz-msa.spring-boot-service")
}

// Edge service is WebFlux (SCG); drop the servlet stack the convention
// plugin adds so Boot starts Netty, not Tomcat.
configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    exclude(group = "org.springframework", module = "spring-webmvc")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    // ADR-011: traces -> OTLP. Boot 4 splits tracing into micrometer modules;
    // -opentelemetry bridges micrometer observations into OTel spans, the
    // exporter ships them to the collector/Jaeger.
    implementation("org.springframework.boot:spring-boot-micrometer-tracing")
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-opentelemetry")
    // the Boot module deliberately leaves the bridge choice to the app
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    runtimeOnly("io.opentelemetry:opentelemetry-exporter-otlp")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}

configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.3")
    }
}
