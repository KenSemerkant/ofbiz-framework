plugins {
    id("ofbiz-msa.spring-boot-service")
}

dependencies {
    implementation(project(":microservices:identity-svc:identity-application"))
    implementation(project(":microservices:identity-svc:identity-domain"))
    implementation("org.springframework.boot:spring-boot-starter-oauth2-authorization-server")
}
