plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:notification-svc:notification-domain"))
    testImplementation(project(":platform:arch-rules"))
}
