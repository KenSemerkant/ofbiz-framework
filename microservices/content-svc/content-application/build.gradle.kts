plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:content-svc:content-domain"))
    testImplementation(project(":platform:arch-rules"))
}
