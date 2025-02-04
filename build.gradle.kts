import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties


plugins {
    id("org.springframework.boot") version "3.1.7"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.flywaydb.flyway") version "10.6.0"
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

group = "com.mrkirby153"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        url = uri("https://repo.mrkirby153.com/repository/maven-public/")
        name = "mrkirby153"
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
    implementation("me.mrkirby153:KirbyUtils-Common:7.0-SNAPSHOT")
    implementation("me.mrkirby153:KirbyUtils-Spring:7.0-SNAPSHOT")
    implementation("com.mrkirby153:bot-core:8.0-SNAPSHOT")
    implementation("com.mrkirby153:interaction-menus:3.0-SNAPSHOT")
    implementation("net.dv8tion:JDA:5.3.0")
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-resources:2.3.12")

    implementation("com.google.api-client:google-api-client:1.35.2")
    implementation("com.google.oauth-client:google-oauth-client:1.36.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev411-1.25.0")


    implementation("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xjsr305=strict", "-Xcontext-receivers"))
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

flyway {
    var username = ""
    var url = ""
    var password = ""
    val bundledFile = file("src/main/resources/application.properties")
    val userFile = file("config/application.properties")
    if (bundledFile.exists()) {
        val props = Properties().apply { load(bundledFile.inputStream()) }
        url = props["spring.datasource.url"] as? String ?: ""
        username = props["spring.datasource.username"] as? String ?: ""
        password = props["spring.datasource.password"] as? String ?: ""
    }

    if (userFile.exists()) {
        val props = Properties().apply { load(userFile.inputStream()) }
        url = props["spring.datasource.url"] as? String ?: ""
        username = props["spring.datasource.username"] as? String ?: ""
        password = props["spring.datasource.password"] as? String ?: ""
    }

    this.url = url
    this.user = username
    this.password = password
}