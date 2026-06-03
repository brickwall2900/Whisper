plugins {
    id("java")
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

    compileOnly(libs.slf4j.api)

    compileOnly(libs.bcprov.jdk18on)
    compileOnly(libs.bcpkix.jdk18on)

    // how many damn modules are there?
    compileOnly(libs.bundles.netty)

    compileOnly(libs.annotations)
}

tasks.test {
    useJUnitPlatform()
}