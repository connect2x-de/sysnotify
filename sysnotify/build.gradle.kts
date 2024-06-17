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

import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import kotlin.io.path.Path
import kotlin.io.path.div

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

val os: OperatingSystem = OperatingSystem.current()
val javaHome = Path(System.getProperty("java.home")) // For JNI interop
val jniHeaders = javaHome / "include"
val jniLibraries = javaHome / "lib"

kotlin {
    jvm()
    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    js(IR) {
        browser {
            testTask {
                enabled = false
            }
        }
        binaries.library()
        generateTypeScriptDefinitions()
    }
    listOf(macosX64(), macosArm64()).forEach { target ->
        target.apply {
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
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.apply {
            binaries {
                framework {
                    baseName = "shared"
                }
            }
        }
    }
    linuxX64 {
        compilations["main"].cinterops {
            val libnotify by creating {}
            val jni by creating {
                compilerOpts("-I${jniHeaders / "linux"}")
                headers("${jniHeaders / "jni.h"}")
            }
        }
    }
    mingwX64 {
        val comPath = project(":sysnotify-com").projectDir.toPath()
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
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines)
                implementation(libs.uuid)
            }
        }
        val desktopMain by creating {
            dependsOn(nativeMain.get())
        }
        macosMain { dependsOn(desktopMain) }
        linuxMain { dependsOn(desktopMain) }
        mingwMain { dependsOn(desktopMain) }
    }
    applyCrossCompilationHack()
}

fun applyCrossCompilationHack() {
    // Disable cross-compilation - https://youtrack.jetbrains.com/issue/KT-30498/Option-to-disable-kotlin-native-cross-compilation
    when {
        os.isLinux -> kotlin.targets.filter { "linux" !in it.name }
        os.isWindows -> kotlin.targets.filter { "mingw" !in it.name }
        os.isMacOsX -> kotlin.targets.filter { "linux" in it.name || "mingw" in it.name }
        else -> emptyList()
    }.mapNotNull { it as? KotlinNativeTarget }.forEach { target ->
        target.apply {
            compilations.all {
                cinterops.all { tasks[interopProcessingTaskName].enabled = false }
                compileKotlinTask.enabled = false
                tasks[processResourcesTaskName].enabled = false
            }
            binaries.all { linkTask.enabled = false }
            mavenPublication {
                val publicationToDisable = this
                tasks.withType<AbstractPublishToMaven>()
                    .all { onlyIf { publication != publicationToDisable } }
                tasks.withType<GenerateModuleMetadata>()
                    .all { onlyIf { publication.get() != publicationToDisable } }
            }
        }
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

// If we are on Windows, inject the CMake submodule as a dependency to all 'mingw' tasks
if (os.isWindows) {
    tasks.filter { it.name.contains("mingw", true) }.forEach { task ->
        task.apply {
            val dependencies = arrayOf(
                ":sysnotify-com:cmakeConfigure",
                ":sysnotify-com:cmakeBuild"
            )
            dependsOn(*dependencies)
            mustRunAfter(*dependencies)
        }
    }
}
