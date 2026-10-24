plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:workeffort-svc:workeffort-domain"))
    testImplementation(project(":platform:arch-rules"))
}
