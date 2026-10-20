plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:identity-svc:identity-domain"))
    testImplementation(project(":platform:arch-rules"))
}
