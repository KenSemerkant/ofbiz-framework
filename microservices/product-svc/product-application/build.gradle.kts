plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:product-svc:product-domain"))
    testImplementation(project(":platform:arch-rules"))
}
