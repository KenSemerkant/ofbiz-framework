plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:billing-svc:billing-domain"))
    testImplementation(project(":platform:arch-rules"))
}
