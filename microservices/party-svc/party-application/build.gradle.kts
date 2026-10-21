plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:party-svc:party-domain"))
    api(project(":platform:projection"))
    testImplementation(project(":platform:arch-rules"))
}
