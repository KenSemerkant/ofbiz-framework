// ofbiz-msa.java-library — pure Java library module (domain, application).
// Java 21, JUnit 5, no frameworks (enforced by OfbizArchitectureRules).
plugins {
    `java-library`
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Every module carries the ArchUnit layering test (repo rule: enforced per module).
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
val coverageClasses = files(
    sourceSets.main.get().output.asFileTree.matching {
        // generated protobuf/grpc stubs (package *.v1) and Application mains
        // are not product code under test
        exclude("**/v1/**", "**/*Application*")
    }
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports { xml.required = true }
    classDirectories.setFrom(coverageClasses)
}

// Application mains are untestable boilerplate; generated protobuf/grpc code
// is not ours to cover (added by the grpc convention).
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(coverageClasses)
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
