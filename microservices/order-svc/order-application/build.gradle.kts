plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:order-svc:order-domain"))
    testImplementation(project(":platform:arch-rules"))
}
