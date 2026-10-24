plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:manufacturing-svc:manufacturing-domain"))
    testImplementation(project(":platform:arch-rules"))
}
