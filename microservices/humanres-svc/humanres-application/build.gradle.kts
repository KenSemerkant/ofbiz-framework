plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:humanres-svc:humanres-domain"))
    testImplementation(project(":platform:arch-rules"))
}
