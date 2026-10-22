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
    ":platform:projection",
    ":platform:demo-svc:demo-domain",
    ":platform:demo-svc:demo-application",
    ":platform:demo-svc:demo-adapters:adapters-rest",
    ":platform:demo-svc:demo-adapters:adapters-grpc",
    ":microservices:identity-svc:identity-domain",
    ":microservices:identity-svc:identity-application",
    ":microservices:identity-svc:identity-adapters:adapters-rest",
    ":microservices:gateway",
    ":microservices:audit-svc:audit-domain",
    ":microservices:audit-svc:audit-application",
    ":microservices:audit-svc:audit-adapters:adapters-rest",
    ":microservices:notification-svc:notification-domain",
    ":microservices:notification-svc:notification-application",
    ":microservices:notification-svc:notification-adapters:adapters-rest",
    ":microservices:search-svc:search-domain",
    ":microservices:search-svc:search-application",
    ":microservices:search-svc:search-adapters:adapters-rest",
    ":microservices:search-svc:search-adapters:adapters-grpc",
    ":microservices:party-svc:party-domain",
    ":microservices:party-svc:party-application",
    ":microservices:party-svc:party-adapters:adapters-grpc",
    ":microservices:party-svc:party-adapters:adapters-rest",
    ":microservices:product-svc:product-domain",
    ":microservices:product-svc:product-application",
    ":microservices:product-svc:product-adapters:adapters-rest",
)
