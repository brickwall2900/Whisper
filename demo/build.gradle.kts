plugins {
    id("java")
    id("application")
    id("distribution")
}

group = "io.github.brickwall2900.processing"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(project(":"))
    implementation(libs.slf4j.api)
    implementation(libs.bcprov.jdk18on)
    implementation(libs.bcpkix.jdk18on)
    implementation(libs.bundles.netty)
    implementation(libs.annotations)
}

tasks.test {
    useJUnitPlatform()
}