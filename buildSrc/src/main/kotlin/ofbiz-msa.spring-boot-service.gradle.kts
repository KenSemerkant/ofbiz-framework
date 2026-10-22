// ofbiz-msa.spring-boot-service — runnable Spring Boot 3.3 service module
// (adapters/infrastructure). Applies dependency management + common service
// dependencies (OTel, actuator). Virtual threads on by default in
// application.yaml (FUTURESTATE-LOWLEVEL §1).
plugins {
    java
    jacoco
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(platform("io.opentelemetry:opentelemetry-bom:1.67.0"))
    implementation("io.opentelemetry:opentelemetry-api")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

}

// Security overrides one patch ahead of the Boot 4.0.6 BOM (trivy gate,
// 2026-10). Drop entries as the BOM catches up.
configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
    dependencies {
        dependency("org.apache.tomcat.embed:tomcat-embed-core:11.0.25")
        dependency("org.apache.tomcat.embed:tomcat-embed-el:11.0.25")
        dependency("org.apache.tomcat.embed:tomcat-embed-websocket:11.0.25")
        dependency("org.springframework:spring-expression:7.0.9")
        dependency("org.springframework:spring-webmvc:7.0.9")
        dependency("tools.jackson.core:jackson-core:3.1.7")
        dependency("tools.jackson.core:jackson-databind:3.1.7")
        dependency("com.fasterxml.jackson.core:jackson-core:2.22.3")
        dependency("com.fasterxml.jackson.core:jackson-databind:2.22.3")
        dependency("io.micrometer:micrometer-core:1.16.6")
        // opensearch-java 2.22 pulls httpcore5 5.3.x (CVE-2026-54399/-54428)
        dependency("org.apache.httpcomponents.core5:httpcore5:5.4.3")
        dependency("org.apache.httpcomponents.core5:httpcore5-h2:5.4.3")
        dependency("org.postgresql:postgresql:42.7.12")
        dependency("org.springframework.data:spring-data-commons:4.0.6")
        dependency("org.springframework.kafka:spring-kafka:4.0.6")
        dependency("at.yawk.lz4:lz4-java:1.11.4")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports { xml.required = true }
    // match the gate: Application mains are not product code under test
    classDirectories.setFrom(files(
        sourceSets.main.get().output.asFileTree.matching {
            exclude("**/*Application*")
        }
    ))
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(files(
        sourceSets.main.get().output.asFileTree.matching {
            exclude("**/*Application*")
        }
    ))
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.95".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Security overrides that the Boot 4.0.6 BOM does not carry yet (trivy gate).
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.netty") {
            useVersion("4.2.17.Final")
        }
        if (requested.group == "org.springframework" && requested.version?.startsWith("7.0.") == true) {
            useVersion("7.0.9")
        }
    }
}

springBoot {
    buildInfo()
}
