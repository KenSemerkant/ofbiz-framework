plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:inventory-svc:inventory-domain"))
    testImplementation(project(":platform:arch-rules"))
}
