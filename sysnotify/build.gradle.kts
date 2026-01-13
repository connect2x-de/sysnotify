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

import de.connect2x.conventions.GitLabPackage
import de.connect2x.conventions.authenticatedPackageRegistry
import de.connect2x.conventions.c2xOrganization
import de.connect2x.conventions.configureJava
import de.connect2x.conventions.enableAbiChecker
import de.connect2x.conventions.gitlab
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.abiValidation
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultCInteropSettings
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import kotlin.io.path.Path
import kotlin.io.path.div

plugins {
    alias(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.android.library)
    `maven-publish`
}

configureJava(sharedLibs.versions.targetJvm)

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
    enableAbiChecker("SysnotifyPrivateApi")
    withSourcesJar(true)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
    androidTarget {
        publishLibraryVariants("release")
    }
    jvm()
    js {
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
                sharedLib {
                    // Fix for libcrypt.so.1 not-found error on distroless
                    // See https://youtrack.jetbrains.com/projects/KT/issues/KT-55643
                    linkerOpts("--as-needed")
                    freeCompilerArgs += "-Xoverride-konan-properties=linkerGccFlags.linux=-lgcc -lgcc_eh -lc -ldl"
                }
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
        val commonMain by getting {
            dependencies {
                api(sharedLibs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(sharedLibs.kotlin.test)
            }
        }
        androidMain {
            dependencies {
                implementation(sharedLibs.androidx.core)
                implementation(sharedLibs.androidx.activity.ktx)
            }
        }
        jvmMain {
            // Include JVM natives for desktop targets
            resources.srcDir("build/generatedResources")
        }
        val nativeMain by getting
        val desktopMain by creating {
            dependsOn(nativeMain)
            dependencies {
                implementation(libs.kmpJni)
            }
        }
        macosMain { dependsOn(desktopMain) }
        linuxMain { dependsOn(desktopMain) }
        mingwMain { dependsOn(desktopMain) }

        val sharedTest by creating {
            dependsOn(commonTest)
        }
        appleTest { dependsOn(sharedTest) }
        linuxTest { dependsOn(sharedTest) }
        mingwTest { dependsOn(sharedTest) }
        jvmTest { dependsOn(sharedTest) }
        jsTest { dependsOn(sharedTest) }

        val activatableMain by creating {
            dependsOn(commonMain)
        }
        linuxMain { dependsOn(activatableMain) }
        mingwMain { dependsOn(activatableMain) }
        appleMain { dependsOn(activatableMain) }
        jvmMain { dependsOn(activatableMain) }
        jsMain { dependsOn(activatableMain) }
    }
}

android {
    namespace = "${rootProject.group}.${rootProject.name}"
    compileSdk = sharedLibs.versions.androidCompileSDK.get().toInt()
    defaultConfig {
        minSdk = sharedLibs.versions.androidMinimalSDK.get().toInt()
    }
    buildTypes {
        debug {
            isDefault = true
        }
        release {
            isMinifyEnabled = false
        }
    }
}

tasks {
    val linkReleaseSharedMacosArm64 by getting
    val linkReleaseSharedMacosX64 by getting
    val linkReleaseSharedLinuxArm64 by getting
    val linkReleaseSharedLinuxX64 by getting

    // Native - TODO: implement bundling for Kotlin/Native using kMBed once it's on Maven Central
    val `extractSysnotify-platform-binariesWindowsX64` by getting
    val copyDebugTestMingwX64SharedLibraries by registering(Copy::class) {
        dependsOn(`extractSysnotify-platform-binariesWindowsX64`)
        from("build/sysnotify-platform/windowsX64/lib/sysnotify-com.dll")
        into("build/bin/mingwX64/debugTest")
    }
    val linkDebugTestMingwX64 by getting { dependsOn(copyDebugTestMingwX64SharedLibraries) }
    val copyDebugSharedMingwX64SharedLibraries by registering(Copy::class) {
        dependsOn(`extractSysnotify-platform-binariesWindowsX64`)
        from("build/sysnotify-platform/windowsX64/lib/sysnotify-com.dll")
        into("build/bin/mingwX64/debugShared")
    }
    val linkDebugSharedMingwX64 by getting { dependsOn(copyDebugSharedMingwX64SharedLibraries) }
    val copyReleaseSharedMingwX64SharedLibraries by registering(Copy::class) {
        dependsOn(`extractSysnotify-platform-binariesWindowsX64`)
        from("build/sysnotify-platform/windowsX64/lib/sysnotify-com.dll")
        into("build/bin/mingwX64/releaseShared")
    }
    val linkReleaseSharedMingwX64 by getting { dependsOn(copyReleaseSharedMingwX64SharedLibraries) }

    // JVM
    val copyNativesForJvm by registering(Copy::class) {
        dependsOn(
            linkReleaseSharedMacosArm64,
            linkReleaseSharedMacosX64,
            linkReleaseSharedLinuxArm64,
            linkReleaseSharedLinuxX64,
            linkReleaseSharedMingwX64
        )
        from("build/bin/linuxX64/releaseShared/libsysnotify.so") { into("linux-x64/") }
        from("build/bin/linuxArm64/releaseShared/libsysnotify.so") { into("linux-arm64/") }
        from("build/bin/macosX64/releaseShared/libsysnotify.dylib") { into("macos-x64/") }
        from("build/bin/macosArm64/releaseShared/libsysnotify.dylib") { into("macos-arm64/") }
        from("build/bin/mingwX64/releaseShared/sysnotify.dll") { into("windows-x64/") }
        from("build/sysnotify-platform/windowsX64/lib/sysnotify-com.dll") { into("windows-x64/") }
        destinationDir = (Path("build") / "generatedResources" / "natives").toFile()
    }
    val jvmProcessResources by getting {
        dependsOn(copyNativesForJvm)
    }
}

publishing {
    repositories {
        authenticatedPackageRegistry()
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name = project.name
            description = "Cross-platform system notifications for Kotlin Multiplatform"
            url = "https://gitlab.com/connect2x/sysnotify"
            c2xOrganization()
            licenses {
                license {
                    name = "Apache 2.0"
                    url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                }
            }
            scm {
                url = this@pom.url
            }
        }
    }
}
