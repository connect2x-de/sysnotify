/*
 * Copyright 2026 connect2x GmbH
 *
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

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import de.connect2x.conventions.GitLabPackage
import de.connect2x.conventions.apache2
import de.connect2x.conventions.c2xOrganization
import de.connect2x.conventions.configureJava
import de.connect2x.conventions.defaultCompilerOptions
import de.connect2x.conventions.defaultPublishing
import de.connect2x.conventions.enableAbiChecker
import de.connect2x.conventions.gitlab
import de.connect2x.conventions.setProjectInfo
import de.connect2x.conventions.withAndroidLibrary
import de.connect2x.conventions.withBrowser
import de.connect2x.conventions.withIos
import de.connect2x.conventions.withJvm
import de.connect2x.conventions.withLinux
import de.connect2x.conventions.withMacos
import de.connect2x.conventions.withMingw
import de.connect2x.conventions.withWeb
import org.gradle.internal.extensions.stdlib.capitalized
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.DefaultCInteropSettings
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.time.ZonedDateTime
import kotlin.io.path.Path
import kotlin.io.path.div

plugins {
    alias(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.android.library)
    alias(sharedLibs.plugins.mavenPublish)
    alias(sharedLibs.plugins.dokka)
    `maven-publish`
}

configureJava(sharedLibs.versions.targetJvm)
defaultPublishing()

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
    defaultCompilerOptions()
    enableAbiChecker("InternalNotificationApi")
    withSourcesJar(true)
    withAndroidLibrary()
    withJvm()
    withWeb {
        withBrowser {
            testTask {
                enabled = false
            }
        }
        generateTypeScriptDefinitions()
        binaries.library()
    }
    withMacos {
        compilations.getByName("main") {
            cinterops {
                create("notificationdata")
            }
        }
        binaries {
            sharedLib()
            framework()
        }
    }
    withIos {
        binaries {
            sharedLib()
            framework()
        }
    }
    withLinux {
        compilations.getByName("main") {
            cinterops {
                create("libnotify") {
                    includePlatformBinaries(target)
                }
                create("notificationdata")
            }
        }
        binaries {
            sharedLib {
                // Fix for libcrypt.so.1 not-found error on distroless
                // See https://youtrack.jetbrains.com/projects/KT/issues/KT-55643
                // TODO: Integrate into conventions plugin
                linkerOpts("--as-needed")
                freeCompilerArgs += "-Xoverride-konan-properties=linkerGccFlags.linux=-lgcc -lgcc_eh -lc -ldl"
            }
        }
    }
    withMingw {
        compilations.getByName("main") {
            cinterops {
                create("sysnotifycom") {
                    includePlatformBinaries(target)
                }
                create("notificationdata")
            }
        }
        binaries {
            sharedLib()
        }
    }
    applyDefaultHierarchyTemplate {
        common {
            group("ios") { withIos() }
            group("macos") { withMacos() }
            group("native") {
                group("desktop") {
                    group("macos")
                    group("linux")
                    group("mingw")
                }
                group("ios")
            }
            group("shared") { // For tests
                group("native")
                group("web")
                withJvm()
            }
            group("activatable") {
                group("native")
                group("web")
                withJvm()
            }
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                api(sharedLibs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(sharedLibs.kotlin.test)
            }
        }
        androidMain {
            dependencies {
                implementation(sharedLibs.androidx.core)
                implementation(sharedLibs.androidx.activity)
            }
        }
        jvmMain {
            // Include JVM natives for desktop targets
            resources.srcDir("build/generatedResources")
        }
        named("desktopMain") {
            dependencies {
                implementation(libs.kmpJni)
            }
        }
        webMain {
            dependencies {
                implementation(sharedLibs.kotlin.browser)
            }
        }
    }
}

android {
    buildTypes {
        debug {
            isDefault = true
        }
        release {
            isMinifyEnabled = false
        }
    }
}

dokka {
    moduleName = project.name
    pluginsConfiguration {
        html {
            homepageLink = "https://gitlab.com/connect2x/lognity"
            footerMessage = "&copy; ${ZonedDateTime.now().year} connect2x GmbH"
        }
    }
}

tasks {
    val linkReleaseSharedMacosArm64 by getting
    val linkReleaseSharedLinuxArm64 by getting
    val linkReleaseSharedLinuxX64 by getting

    val `extractSysnotify-platform-binariesWindowsX64` by getting
    val copyDebugTestMingwX64SharedLibraries by registering(Copy::class) {
        group = "jvmInterop"
        description = "Copies all prebuilt Windows x64 natives into the JVM test resource root"
        dependsOn(`extractSysnotify-platform-binariesWindowsX64`)
        from("build/sysnotify-platform/windowsX64/lib/sysnotify-com.dll")
        into("build/bin/mingwX64/debugTest")
    }
    named("linkDebugTestMingwX64") { dependsOn(copyDebugTestMingwX64SharedLibraries) }
    val copyDebugSharedMingwX64SharedLibraries by registering(Copy::class) {
        group = "jvmInterop"
        description = "Copies all prebuilt Windows x64 Debug natives into the JVM resource root"
        dependsOn(`extractSysnotify-platform-binariesWindowsX64`)
        from("build/sysnotify-platform/windowsX64/lib/sysnotify-com.dll")
        into("build/bin/mingwX64/debugShared")
    }
    named("linkDebugSharedMingwX64") { dependsOn(copyDebugSharedMingwX64SharedLibraries) }
    val copyReleaseSharedMingwX64SharedLibraries by registering(Copy::class) {
        group = "jvmInterop"
        description = "Copies all prebuilt Windows x64 Release atives into the JVM resource root"
        dependsOn(`extractSysnotify-platform-binariesWindowsX64`)
        from("build/sysnotify-platform/windowsX64/lib/sysnotify-com.dll")
        into("build/bin/mingwX64/releaseShared")
    }
    val linkReleaseSharedMingwX64 by getting { dependsOn(copyReleaseSharedMingwX64SharedLibraries) }

    // JVM
    val copyNativesForJvm by registering(Copy::class) {
        group = "jvmInterop"
        description = "Copies all prebuilt natives into the JVM resource root"
        dependsOn(
            linkReleaseSharedMacosArm64,
            linkReleaseSharedLinuxArm64,
            linkReleaseSharedLinuxX64,
            linkReleaseSharedMingwX64
        )
        from("build/bin/linuxX64/releaseShared/libsysnotify_core.so") { into("linux-x64/") }
        from("build/bin/linuxArm64/releaseShared/libsysnotify_core.so") { into("linux-arm64/") }
        from("build/bin/macosArm64/releaseShared/libsysnotify_core.dylib") { into("macos-arm64/") }
        from("build/bin/mingwX64/releaseShared/sysnotify_core.dll") { into("windows-x64/") }
        from("build/sysnotify-platform/windowsX64/lib/sysnotify-com.dll") { into("windows-x64/") }
        destinationDir = (Path("build") / "generatedResources" / "natives").toFile()
    }
    named("jvmProcessResources") {
        dependsOn(copyNativesForJvm)
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            apache2()
            c2xOrganization()
            setProjectInfo(
                name = project.name,
                description = "Cross-platform system notifications for Kotlin Multiplatform",
                repository = "connect2x/sysnotify",
            )
        }
    }
}
