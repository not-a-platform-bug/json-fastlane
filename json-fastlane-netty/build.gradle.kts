plugins {
    `java-library`
}

dependencies {
    api(project(":json-fastlane-core"))
    api("io.netty:netty-buffer:4.2.10.Final")
}
