plugins {
    id("ofbiz-msa.java-library")
}

dependencies {
    api(project(":microservices:search-svc:search-domain"))
    testImplementation(project(":platform:arch-rules"))
}
