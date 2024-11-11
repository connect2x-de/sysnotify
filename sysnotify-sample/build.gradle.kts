/*
 * Copyright 2024 connect2x GmbH
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    val kotlinJvmVersion = libs.versions.jvmTarget.get()
    val kotlinJvmTarget = JvmTarget.fromTarget(kotlinJvmVersion)
    jvmToolchain(JavaLanguageVersion.of(kotlinJvmVersion).asInt())
    jvm {
        compilations.all {
            compileTaskProvider {
                compilerOptions {
                    jvmTarget = kotlinJvmTarget
                }
            }
        }
    }

    js(IR) {
        browser {
            runTask {
                mainOutputFileName = "Sample.js"
            }
            webpackTask {
                mainOutputFileName = "Sample.js"
            }
        }
        binaries.executable()
    }

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(kotlinJvmTarget)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Sysnotify"
            isStatic = true
        }
    }

    sourceSets {
        androidMain {
            dependencies {
                implementation(libs.androidx.compose.ui.tooling.preview)
                implementation(libs.androidx.activity.compose)
            }
        }
        commonMain {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation(project(":sysnotify"))
            }
        }
        jvmMain {
            dependencies {
                implementation(project(path = ":sysnotify", configuration = "jvmNativesJarLinuxX64"))
                implementation(project(path = ":sysnotify", configuration = "jvmNativesJarLinuxArm64"))
                implementation(project(path = ":sysnotify", configuration = "jvmNativesJarMacosX64"))
                implementation(project(path = ":sysnotify", configuration = "jvmNativesJarMacosArm64"))
                implementation(project(path = ":sysnotify", configuration = "jvmNativesJarWindowsX64"))
                // this is needed to create lock files working on all machines
                if (System.getProperty("bundleAll") == "true") {
                    implementation(compose.desktop.linux_x64)
                    implementation(compose.desktop.linux_arm64)
                    implementation(compose.desktop.windows_x64)
                    implementation(compose.desktop.macos_x64)
                    implementation(compose.desktop.macos_arm64)
                } else {
                    implementation(compose.desktop.currentOs)
                }
            }
        }

    }
}

android {
    namespace = "${rootProject.group}.${rootProject.name}.sample"
    compileSdk = 34

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "${rootProject.group}.${rootProject.name}.sample"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
    }
    buildFeatures {
        compose = true
    }
    dependencies {
        debugImplementation(libs.androidx.compose.ui.tooling)
    }
}

compose {
    desktop {
        application {
            mainClass = "${rootProject.group}.${rootProject.name}.sample.MainKt"
            buildTypes.release.proguard { isEnabled = false }
            nativeDistributions {
                modules("java.net.http", "java.sql", "java.naming")
                targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                appResourcesRootDir.set(layout.buildDirectory) // @see https://github.com/JetBrains/compose-jb/tree/master/tutorials/Native_distributions_and_local_execution#jvm-resource-loading
                packageName = "Sysnotify Sample"
                packageVersion = "1.1.0"

                windows {
                    menu = true
                    // see https://wixtoolset.org/documentation/manual/v3/howtos/general/generate_guids.html
                    upgradeUuid = "3e1d7fa0-4c3f-4c3d-805f-d28bd21d6019"
                }
                macOS {
                    dockName = "Sysnotify Sample"
                }
            }
        }
    }
}
