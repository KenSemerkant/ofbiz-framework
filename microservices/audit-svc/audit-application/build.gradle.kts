plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:audit-svc:audit-domain"))
    testImplementation(project(":platform:arch-rules"))
}
