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
    ":microservices:inventory-svc:inventory-domain",
    ":microservices:inventory-svc:inventory-application",
    ":microservices:inventory-svc:inventory-adapters:adapters-rest",
    ":microservices:order-svc:order-domain",
    ":microservices:order-svc:order-application",
    ":microservices:order-svc:order-adapters:adapters-rest",
    ":microservices:billing-svc:billing-domain",
    ":microservices:billing-svc:billing-application",
    ":microservices:billing-svc:billing-adapters:adapters-rest",
    ":microservices:finance-svc:finance-domain",
    ":microservices:finance-svc:finance-application",
    ":microservices:finance-svc:finance-adapters:adapters-rest",
    ":microservices:manufacturing-svc:manufacturing-domain",
    ":microservices:manufacturing-svc:manufacturing-application",
    ":microservices:manufacturing-svc:manufacturing-adapters:adapters-rest",
    ":microservices:workeffort-svc:workeffort-domain",
    ":microservices:workeffort-svc:workeffort-application",
    ":microservices:workeffort-svc:workeffort-adapters:adapters-rest",
    ":microservices:humanres-svc:humanres-domain",
    ":microservices:humanres-svc:humanres-application",
    ":microservices:humanres-svc:humanres-adapters:adapters-rest",
    ":microservices:content-svc:content-domain",
    ":microservices:content-svc:content-application",
    ":microservices:content-svc:content-adapters:adapters-rest",
    ":microservices:marketing-svc:marketing-domain",
    ":microservices:marketing-svc:marketing-application",
    ":microservices:marketing-svc:marketing-adapters:adapters-rest",
)
