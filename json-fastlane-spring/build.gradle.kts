plugins {
    `java-library`
}

dependencies {
    api(project(":json-fastlane-core"))
    api("org.springframework:spring-web:6.2.17")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
}
