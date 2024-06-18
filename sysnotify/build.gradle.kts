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

import org.gradle.configurationcache.extensions.capitalized
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import kotlin.io.path.Path
import kotlin.io.path.div

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    id("maven-publish")
}

// Remap host architecture to some consistent aliases (mostly used for native packaging)
val architecture: String = when (System.getProperty("os.arch")) {
    "i386", "i486", "i586", "i686", "x86", "x32" -> "x86"
    "x64", "amd64", "x86_64", "x86-64" -> "x64"
    "aarch64", "arm64" -> "arm64"
    "arm", "armv7", "aarch32" -> "arm32"
    else -> throw IllegalStateException("Could not determine host architecture")
}
println("Detected host architecture $architecture")

val os: OperatingSystem = OperatingSystem.current()
val platformPair = "${os.familyName}-${architecture}"
println("Detected host OS ${os.familyName}")
val javaHome = Path(System.getProperty("java.home")) // For JNI interop
println("Found java home at $javaHome")
val jniHeaders = javaHome / "include"
val jniLibraries = javaHome / "lib"

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
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget = kotlinJvmTarget
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
                    baseName = "sysnotify"
                }
                sharedLib {}
            }
        }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.apply {
            binaries {
                framework {
                    baseName = "sysnotify"
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
        binaries.sharedLib {}
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
                compileTaskProvider { enabled = false }
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
    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
    }
}

dependencies {
    implementation(libs.androidx.core)
}

// If we are on Windows, inject the CMake submodule as a dependency to all 'mingw' tasks
if (os.isWindows) {
    tasks {
        filter { it.name.contains("mingw", true) }.forEach { task ->
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
}

// Create custom JAR task(s) for packaging shared libraries produced by Kotlin/Native targets
val linkTask = tasks.let { taskList ->
    when {
        os.isLinux -> taskList.find { it.name == "linkReleaseSharedLinux${architecture.capitalized()}" }
        os.isWindows -> taskList.find { it.name == "linkReleaseSharedMingw${architecture.capitalized()}" }
        os.isMacOsX -> taskList.find { it.name == "linkReleaseSharedMacos${architecture.capitalized()}" }
        else -> throw IllegalStateException("JVM bindings are not supported on the current platform")
    } ?: throw IllegalStateException("Could not find matching Kotlin/Native link task for building JVM bindings")
}
println("Using '${linkTask.name}' as JVM bindings link task")

val nativesJar = task("nativesJar", Jar::class) {
    dependsOn(linkTask)
    archiveClassifier = "jvm-natives-$platformPair"
    from(linkTask.outputs) {
        exclude("*.h")
    }
    into("natives/$platformPair")
    println("Configured JNI bindings JAR '${archiveBaseName.get()}'")
}

tasks {
    assemble {
        dependsOn(nativesJar)
        mustRunAfter(nativesJar)
    }
}

configurations {
    val nativesJar by creating
}

artifacts {
    add("nativesJar", nativesJar) // Make sure our natives JAR is added as an artifact
}

publishing {
    publications.configureEach {
        if (this is MavenPublication) {
            artifact(nativesJar) {
                classifier = "jvm-natives-$platformPair"
            }
            pom {
                name.set(project.name)
                description.set("Cross-platform system notifications for Kotlin Multiplatform")
                url.set("https://gitlab.com/connect2x/sysnotify")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("michael.thiele")
                        id.set("benkuly")
                        id.set("KitsuneAlex")
                    }
                }
                scm {
                    url.set("https://gitlab.com/connect2x/sysnotify")
                }
            }
        }
    }
}
