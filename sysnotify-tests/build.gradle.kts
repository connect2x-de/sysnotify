plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    mingwX64()
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotest.engine)
                implementation(project(":sysnotify"))
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.kotest.runner.jvm)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
