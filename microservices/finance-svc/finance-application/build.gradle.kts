plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:finance-svc:finance-domain"))
    testImplementation(project(":platform:arch-rules"))
}
