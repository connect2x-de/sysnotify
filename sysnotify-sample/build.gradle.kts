/*
 * Copyright 2025 connect2x GmbH
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

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget()
    jvm()
    js {
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
    listOf(macosX64(), macosArm64()).forEach {
        it.apply {
            binaries {
                executable {
                    entryPoint = "${rootProject.group}.${rootProject.name}.sample.main"
                }
            }
        }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.apply {
            binaries {
                framework {
                    baseName = "Sysnotify"
                    isStatic = true
                }
            }
        }
    }
    sourceSets {
        androidMain {
            dependencies {
                implementation(libs.androidx.compose.ui.tooling.preview)
                implementation(libs.androidx.activity.compose)
            }
        }
        val commonMain by getting {
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
                // this is needed to create lock files working on all machines
                if (System.getProperty("bundleAll") == "true") {
                    implementation(compose.desktop.linux_x64)
                    implementation(compose.desktop.linux_arm64)
                    implementation(compose.desktop.windows_x64)
                    implementation(compose.desktop.macos_x64)
                    implementation(compose.desktop.macos_arm64)
                } else implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "${rootProject.group}.${rootProject.name}.sample"
    compileSdk = libs.versions.androidCompileSDK.get().toInt()

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        applicationId = "${rootProject.group}.${rootProject.name}.sample"
        minSdk = libs.versions.androidMinimalSDK.get().toInt()
        versionCode = 1
        versionName = libs.versions.sysnotify.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            isDefault = true
            isMinifyEnabled = false
        }
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
                appResourcesRootDir.set(layout.buildDirectory)
                packageName = "Sysnotify Sample"
                packageVersion = libs.versions.sysnotify.get()
                windows {
                    menu = true
                    upgradeUuid = "3e1d7fa0-4c3f-4c3d-805f-d28bd21d6019"
                }
                macOS {
                    bundleID = "${rootProject.group}.${rootProject.name}.sample"
                    dockName = "Sysnotify Sample"
                }
            }
        }
    }
}
