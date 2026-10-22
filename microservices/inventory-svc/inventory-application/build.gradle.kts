plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:inventory-svc:inventory-domain"))
    api(project(":platform:projection"))
    testImplementation(project(":platform:arch-rules"))
}
