plugins {
    id("ofbiz-msa.java-library")
    id("ofbiz-msa.grpc")
}

dependencies {
    implementation(project(":microservices:party-svc:party-application"))
    implementation(project(":microservices:party-svc:party-domain"))

    testImplementation(platform("io.grpc:grpc-bom:1.75.0"))
    testImplementation("io.grpc:grpc-inprocess")
}
