// gradle.properties
val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "1.8.10"
    id("io.ktor.plugin") version "2.2.4"
}

group = "future"
version = "0.0.1"
application {
    //app의 main() 메소드 설정.
    mainClass.set("future.ApplicationKt")

    //개발모드인지 설정
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version") //netty engin 의존성 추가
    implementation("io.ktor:ktor-server-websockets-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktor_version") //request에 대한 로깅 플러그인용
    implementation("ch.qos.logback:logback-classic:$logback_version") //logback 로거: resource/logback.xml
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")

    implementation ("io.ktor:ktor-client-core:$ktor_version")
    implementation ("io.ktor:ktor-client-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation ("io.ktor:ktor-client-websockets:$ktor_version")
    implementation ("io.ktor:ktor-client-logging-jvm:$ktor_version")

    implementation("com.google.code.gson:gson:2.8.9")
}