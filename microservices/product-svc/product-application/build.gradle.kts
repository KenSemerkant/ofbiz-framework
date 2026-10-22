plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:product-svc:product-domain"))
    api(project(":platform:projection"))
    testImplementation(project(":platform:arch-rules"))
}
