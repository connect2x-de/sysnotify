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

import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultCInteropSettings
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import kotlin.io.path.div

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

fun OperatingSystem.getFriendlyName(): String = familyName.replace("os x", "macos")
fun OperatingSystem.getPlatformPair(arch: String): String = "${getFriendlyName()}-$arch"
val hostOs: OperatingSystem = OperatingSystem.current()

val KonanTarget.familyName: String
    get() = when (family) {
        Family.ANDROID -> "android"
        Family.IOS -> "ios"
        Family.OSX -> "macos"
        Family.LINUX -> "linux"
        Family.MINGW -> "windows"
        Family.TVOS -> "tvos"
        Family.WATCHOS -> "watchos"
    }

val KonanTarget.architectureName: String
    get() = architecture.name.lowercase()

val binaryPackage: GitLabPackage = gitlab().project(
    "connect2x/sysnotify-platform-binaries"
).packageRegistry["generic/build", libs.versions.sysnotifyBinaries]

fun DefaultCInteropSettings.includePlatformBinaries(target: KotlinNativeTarget) {
    tasks.getByName(interopProcessingTaskName) {
        val konanTarget = target.konanTarget
        val fileName = "build-${konanTarget.familyName}-${konanTarget.architectureName}.zip"
        val suffix = "${konanTarget.familyName}${konanTarget.architectureName.capitalized()}"
        dependsOn(binaryPackage[fileName, suffix, "sysnotify-platform"].extractTask)
    }
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
            binaries {
                framework {
                    baseName = rootProject.name
                }
                sharedLib()
            }
        }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.apply {
            binaries {
                framework {
                    baseName = rootProject.name
                }
                sharedLib()
            }
        }
    }
    listOf(linuxX64(), linuxArm64()).forEach {
        it.apply {
            compilations.getByName("main") {
                cinterops {
                    val libnotify by creating {
                        includePlatformBinaries(target)
                    }
                }
            }
            binaries.sharedLib()
        }
    }
    mingwX64 {
        compilations.getByName("main") {
            cinterops {
                val sysnotifycom by creating {
                    includePlatformBinaries(target)
                }
            }
        }
        binaries.sharedLib()
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        val desktopMain by creating {
            dependsOn(nativeMain.get())
            dependencies {
                implementation(libs.kmpJni)
            }
        }
        macosMain { dependsOn(desktopMain) }
        linuxMain { dependsOn(desktopMain) }
        mingwMain { dependsOn(desktopMain) }
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines)
            }
        }
    }
}

android {
    namespace = "${rootProject.group}.${rootProject.name}"
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

fun jvmNativesJar(os: OperatingSystem, arch: String): Jar {
    // Create custom JAR task(s) for packaging shared libraries produced by Kotlin/Native targets
    val linkTask = tasks.let { taskList ->
        when {
            os.isLinux -> taskList.findByName("linkReleaseSharedLinux${arch.capitalized()}")
            os.isWindows -> taskList.findByName("linkReleaseSharedMingw${arch.capitalized()}")
            os.isMacOsX -> taskList.findByName("linkReleaseSharedMacos${arch.capitalized()}")
            else -> null
        }
    } ?: throw IllegalStateException("Could not find matching Kotlin/Native link task for building JVM bindings")
    println("Using '${linkTask.name}' as JVM bindings link task")
    val platformPair = os.getPlatformPair(arch)
    val taskName = "jvmNativesJar${os.getFriendlyName().capitalized()}${arch.capitalized()}"
    configurations.create(taskName)
    val classifier = "jvm-natives-$platformPair"

    return task(taskName, Jar::class) {
        dependsOn(linkTask)
        archiveClassifier = classifier
        from(linkTask.outputs) {
            exclude("*.h", "*.def")
        }
        val platformDir = layout.buildDirectory / "sysnotify-platform"
        if (os.isWindows) {
            from(platformDir / "windows-$arch" / "lib") {
                include("*.dll")
            }
        }
        into("natives/$platformPair")
        outputs.upToDateWhen { false } // These should always be generated fresh as it doesn't take any time
    }.apply {
        tasks {
            assemble { dependsOn(this@apply) }
        }
        artifacts {
            add(taskName, this@apply) // Make sure our natives JAR is added as an artifact
        }
        publishing {
            publications {
                create<MavenPublication>(taskName) {
                    artifact(this@apply) {
                        this.classifier = ""
                    }
                    artifactId = "${rootProject.name}-$classifier" // These get their own unique artifact IDs
                }
            }
        }
    }
}

val jvmNativesMacosX64: Jar = jvmNativesJar(OperatingSystem.forName("osx"), "x64")
val jvmNativesMacosArm64: Jar = jvmNativesJar(OperatingSystem.forName("osx"), "arm64")
val jvmNativesLinunxX64: Jar = jvmNativesJar(OperatingSystem.forName("linux"), "x64")
val jvmNativesLinuxArm64: Jar = jvmNativesJar(OperatingSystem.forName("linux"), "arm64")
val jvmNativesMingwX64: Jar = jvmNativesJar(OperatingSystem.forName("windows"), "x64")

publishing {
    repositories {
        repositories {
            System.getenv("CI_API_V4_URL")?.let { apiUrl ->
                maven {
                    url = uri("$apiUrl/projects/${System.getenv("CI_PROJECT_ID")}/packages/maven")
                    name = "GitLab"
                    credentials(HttpHeaderCredentials::class) {
                        name = "Job-Token"
                        value = System.getenv("CI_JOB_TOKEN")
                    }
                    authentication {
                        create("header", HttpHeaderAuthentication::class)
                    }
                }
            }
        }
    }
    publications {
        configureEach {
            if (this is MavenPublication) {
                pom {
                    name.set(project.name)
                    description.set("Cross-platform system notifications for Kotlin Multiplatform")
                    url.set(System.getenv("CI_PROJECT_URL"))
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
                        url.set(this@pom.url)
                    }
                }
            }
        }
    }
}
