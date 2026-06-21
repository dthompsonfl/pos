plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    application
}

group = "com.enterprise.pos"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("io.ktor:ktor-server-auth:2.3.12")
    implementation("io.ktor:ktor-server-auth-jwt:2.3.12")
    implementation("io.ktor:ktor-server-default-headers:2.3.12")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("com.stripe:stripe-java:28.0.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.slf4j:slf4j-simple:2.0.13")
}

application {
    mainClass.set("com.enterprise.pos.backend.ApplicationKt")
}
