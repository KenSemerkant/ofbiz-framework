plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:marketing-svc:marketing-domain"))
    testImplementation(project(":platform:arch-rules"))
}
