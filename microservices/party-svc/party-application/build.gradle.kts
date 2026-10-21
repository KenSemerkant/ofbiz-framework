plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:party-svc:party-domain"))
    testImplementation(project(":platform:arch-rules"))
}
