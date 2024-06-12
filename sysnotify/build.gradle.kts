/*
 * Copyright 2024 connect2x
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

import kotlin.io.path.Path
import kotlin.io.path.div

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

val hostOs = System.getProperty("os.name")
val isArm64 = System.getProperty("os.arch") == "aarch64"
val javaHome = Path(System.getProperty("java.home")) // For JNI interop
val jniHeaders = javaHome / "include"
val jniLibraries = javaHome / "lib"

kotlin {
    targetHierarchy.default()

    val commonMain by sourceSets.getting {
        dependencies {
            implementation("com.benasher44:uuid:0.8.4")
        }
    }
    val commonTest by sourceSets.getting {
        dependencies {
            implementation(libs.kotlin.test)
        }
    }
    val appleMain by sourceSets.creating {
        dependsOn(commonMain)
    }

    // Plain ol' Java
    jvm()
    // Android
    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    // macOS/iOS
    if (hostOs.contains("mac", true)) {
        listOf(
            macosX64(),
            macosArm64()
        ).forEach {
            it.apply {
                sourceSets { add(appleMain) }
                compilations["main"].cinterops {
                    val jni by creating {
                        compilerOpts("-I${jniHeaders / "darwin"}")
                        headers("${jniHeaders / "jni.h"}")
                    }
                }
                binaries {
                    framework {
                        baseName = "shared"
                    }
                    sharedLib {}
                }
            }
        }
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64(),
        ).forEach {
            it.apply {
                sourceSets { add(appleMain) }
                binaries {
                    framework {
                        baseName = "shared"
                    }
                }
            }
        }
    }
    // Linux
    if (hostOs.contains("linux", true)) {
        linuxX64 {
            compilations["main"].cinterops {
                val libnotify by creating
                val jni by creating {
                    compilerOpts("-I${jniHeaders / "linux"}")
                    headers("${jniHeaders / "jni.h"}")
                }
            }
        }
    }
    // Windows - a horrible mess as always
    if (hostOs.contains("windows", true)) {
        val comPath = project(":sysnotify-com").projectDir.toPath()
        mingwX64 {
            compilations["main"].cinterops {
                val sysnotifycom by creating {
                    headers("${comPath / "include" / "sysnotify-com.h"}")
                }
                val jni by creating {
                    compilerOpts("-I${jniHeaders / "win32"}")
                    headers("${jniHeaders / "jni.h"}")
                }
            }
            binaries {
                sharedLib {
                    linkerOpts(
                        "-L${comPath / "build" / "cmake" / "RelWithDebInfo"}",
                        "-L$jniLibraries",
                        "-lsysnotify-com",
                        "-ljawt",
                        "-ljvm"
                    )
                }
            }
        }
    }
    // Source sets
    sourceSets {
        jvmMain { dependsOn(commonMain) }
        androidMain { dependsOn(commonMain) }
        linuxMain { dependsOn(commonMain) }
        iosMain { dependsOn(appleMain) }
        macosMain { dependsOn(appleMain) }
        mingwMain { dependsOn(commonMain) }
    }
}

android {
    namespace = "de.connect2x.sysnotify"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
dependencies {
    implementation(libs.androidx.core)
}

// If we are on Windows, inject the CMake submodule as a dependency to assemble
if (hostOs.contains("windows", true)) {
    tasks.findByName("assemble")!!.apply {
        val dependencies = arrayOf(
            ":sysnotify-com:cmakeConfigure",
            ":sysnotify-com:cmakeBuild"
        )
        dependsOn(*dependencies)
        mustRunAfter(*dependencies)
    }
}
