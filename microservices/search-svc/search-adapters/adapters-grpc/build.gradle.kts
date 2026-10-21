plugins {
    id("ofbiz-msa.java-library")
    id("ofbiz-msa.grpc")
}

dependencies {
    implementation(project(":microservices:search-svc:search-application"))
    implementation(project(":microservices:search-svc:search-domain"))
}
