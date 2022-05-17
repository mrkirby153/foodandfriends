import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    id("org.springframework.boot") version "2.6.6"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    id("org.flywaydb.flyway") version "6.0.6"
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.spring") version "1.6.10"
}

group = "com.mrkirby153"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

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
    implementation("com.mrkirby153:bot-core:4.0-SNAPSHOT")
    implementation("com.mrkirby153:interaction-menus:1.0-SNAPSHOT")
    implementation("net.dv8tion:JDA:5.0.0-alpha.11")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
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