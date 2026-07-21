plugins {
    id("java")
    id("java-library")
    id("maven-publish")
    id("net.thebugmc.gradle.sonatype-central-portal-publisher").version("1.2.4")
}

group = "io.github.brickwall2900"
version = "1.0.1"
description = "yet another interprocess communication library in Java using TCP + TLS"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    withSourcesJar()
    withJavadocJar()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).tags = listOf(
        "apiNote:a:API Note:",
        "implSpec:a:Implementation Requirements:",
        "implNote:a:Implementation Note:")
}

signing {
    useGpgCmd()
}

centralPortal {
    pom {
        name = project.group.toString()
        description = project.description
        inceptionYear = "2026"
        url = "https://github.com/brickwall2900/Whisper"

        licenses {
            license {
                name = "MIT License"
                url = "https://mit-license.org/"
            }
        }

        developers {
            developer {
                id = "brickwall2900"
                name = "Marsh"
                email = "brickwall2900@gmail.com"
            }
        }

        scm {
            connection = "scm:https://github.com/brickwall2900/Whisper.git"
            developerConnection = "scm:git:ssh://git@github.com:brickwall2900/Whisper.git"
            url = "https://github.com/brickwall2900/Whisper"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation(libs.slf4j.api)

    implementation(libs.bcprov.jdk18on)
    implementation(libs.bcpkix.jdk18on)

    // how many damn modules are there?
    implementation(libs.bundles.netty)

    implementation(libs.annotations)
}

tasks.test {
    useJUnitPlatform()
}