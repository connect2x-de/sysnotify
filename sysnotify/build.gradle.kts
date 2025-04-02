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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultCInteropSettings
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import kotlin.io.path.Path
import kotlin.io.path.div

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
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
    withSourcesJar(true)
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
            compilations.getByName("main") {
                cinterops {
                    val notificationdata by creating
                }
            }
            binaries {
                sharedLib()
                framework()
            }
        }
    }
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.apply {
            binaries {
                sharedLib()
                framework()
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
                    val notificationdata by creating
                }
            }
            binaries {
                sharedLib()
            }
        }
    }
    mingwX64 {
        compilations.getByName("main") {
            cinterops {
                val sysnotifycom by creating {
                    includePlatformBinaries(target)
                }
                val notificationdata by creating
            }
        }
        binaries {
            sharedLib()
        }
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.coroutines)
                implementation(libs.stately.common)
                implementation(libs.stately.collections)
                implementation(libs.kotlinx.atomicfu)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        androidMain {
            dependencies {
                implementation(libs.androidx.core)
                implementation(libs.androidx.activity.ktx)
            }
        }
        jvmMain {
            // Include JVM natives for desktop targets
            resources.srcDir("build/generatedResources")
        }

        val desktopMain by creating {
            dependsOn(nativeMain.get())
            dependencies {
                implementation(libs.kmpJni)
            }
        }
        macosMain { dependsOn(desktopMain) }
        linuxMain { dependsOn(desktopMain) }
        mingwMain { dependsOn(desktopMain) }

        val sharedTest by creating {
            dependsOn(commonTest.get())
        }
        appleTest { dependsOn(sharedTest) }
        linuxTest { dependsOn(sharedTest) }
        mingwTest { dependsOn(sharedTest) }
        jvmTest { dependsOn(sharedTest) }
        jsTest { dependsOn(sharedTest) }

        val activatableMain by creating {
            dependsOn(commonMain.get())
        }
        linuxMain { dependsOn(activatableMain) }
        mingwMain { dependsOn(activatableMain) }
        appleMain { dependsOn(activatableMain) }
        jvmMain { dependsOn(activatableMain) }
    }
}

dependencies {
    androidTestImplementation(libs.androidx.test)
}

android {
    namespace = "${rootProject.group}.${rootProject.name}"
    compileSdk = libs.versions.androidCompileSDK.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinimalSDK.get().toInt()
    }
    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
    }
}

val copyNativesForJvm: Copy by tasks.register<Copy>("copyNativesForJvm") {
    dependsOn(
        tasks.getByName("linkReleaseSharedMacosArm64"),
        tasks.getByName("linkReleaseSharedMacosX64"),
        tasks.getByName("linkReleaseSharedLinuxArm64"),
        tasks.getByName("linkReleaseSharedLinuxX64"),
        tasks.getByName("linkReleaseSharedMingwX64")
    )
    from("build/bin/linuxX64/releaseShared/libsysnotify.so") { into("linux-x64/") }
    from("build/bin/linuxArm64/releaseShared/libsysnotify.so") { into("linux-arm64/") }
    from("build/bin/macosX64/releaseShared/libsysnotify.dylib") { into("macos-x64/") }
    from("build/bin/macosArm64/releaseShared/libsysnotify.dylib") { into("macos-arm64/") }
    from("build/bin/mingwX64/releaseShared/sysnotify.dll") { into("windows-x64/") }
    from("build/sysnotify-platform/windowsX64/lib/sysnotify-com.dll") { into("windows-x64/") }
    destinationDir = (Path("build") / "generatedResources" / "natives").toFile()
}

tasks.getByName("jvmProcessResources") {
    dependsOn(copyNativesForJvm)
}

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
