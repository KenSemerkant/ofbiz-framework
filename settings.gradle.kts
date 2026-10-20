// Modernization program build. The monolith keeps its own build at monolith/
// (run via `cd monolith && ./gradlew`); it is deliberately NOT included here.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ofbiz-modernization"

include(
    ":platform:arch-rules",
    ":platform:demo-svc:demo-domain",
    ":platform:demo-svc:demo-application",
    ":platform:demo-svc:demo-adapters:adapters-rest",
    ":platform:demo-svc:demo-adapters:adapters-grpc",
    ":microservices:identity-svc:identity-domain",
    ":microservices:identity-svc:identity-application",
    ":microservices:identity-svc:identity-adapters:adapters-rest",
)
