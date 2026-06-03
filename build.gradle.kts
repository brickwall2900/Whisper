plugins {
    id("java")
    id("java-library")
    id("maven-publish")
    id("net.thebugmc.gradle.sonatype-central-portal-publisher").version("1.2.4")
}

group = "io.github.brickwall2900"
version = "1.0.0"
description = "yet another interprocess communication library in Java using TCP + TLS"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    withSourcesJar()
    withJavadocJar()
}

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