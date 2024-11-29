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

import de.undercouch.gradle.tasks.download.Download
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.notExists

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.downloadTask)
    `maven-publish`
}

fun OperatingSystem.getFriendlyName(): String = familyName.replace("os x", "macos")
fun OperatingSystem.getPlatformPair(arch: String): String = "${getFriendlyName()}-$arch"
val hostOs: OperatingSystem = OperatingSystem.current()

operator fun DirectoryProperty.div(name: String): Path = get().asFile.toPath() / name

val ensureBuildDirectory: Task = tasks.create("ensureBuildDirectory") {
    val path = layout.buildDirectory.get().asFile.toPath()
    doLast { path.createDirectories() }
    onlyIf { path.notExists() }
}

fun downloadSysnotifyBinariesTask(platform: String, arch: String): Download =
    tasks.create<Download>("downloadSysnotifyBinaries${platform.capitalized()}${arch.capitalized()}") {
        group = "sysnotifyBinaries"
        dependsOn(ensureBuildDirectory)
        val fileName = "build-$platform-$arch.zip"
        src("https://gitlab.com/api/v4/projects/64488791/packages/generic/build/v${libs.versions.sysnotifyBinaries.get()}/$fileName")
        val destPath = layout.buildDirectory / "sysnotify-platform" / fileName
        dest(destPath.toFile())
        overwrite(true) // Always overwrite when downloading binaries
        onlyIf { destPath.notExists() }
    }

val downloadSdlBinariesWindowsX64: Download = downloadSysnotifyBinariesTask("windows", "x64")

fun extractSysnotifyBinariesTask(platform: String, arch: String): Copy =
    tasks.create<Copy>("extractSysnotifyBinaries${platform.capitalized()}${arch.capitalized()}") {
        group = "sysnotifyBinaries"
        val downloadTaskName = "downloadSysnotifyBinaries${platform.capitalized()}${arch.capitalized()}"
        dependsOn(downloadTaskName)
        val platformPair = "$platform-$arch"
        from(zipTree((layout.buildDirectory / "sysnotify-platform" / "build-$platformPair.zip").toFile()))
        val destPath = layout.buildDirectory / "sysnotify-platform" / platformPair
        into(destPath.toFile())
        onlyIf { destPath.notExists() }
    }

val extractSysnotifyBinariesWindowsX64: Copy = extractSysnotifyBinariesTask("windows", "x64")

val extractSysnotifyBinaries: Task = tasks.create("extractSysnotifyBinaries") {
    group = "sysnotifyBinaries"
    dependsOn(extractSysnotifyBinariesWindowsX64)
}

fun headerDownloadTask(name: String, url: String, version: Provider<String>): Exec = tasks.create<Exec>("download${name.capitalized()}Headers") {
    group = "sysnotifyHeaders"
    dependsOn(ensureBuildDirectory)
    workingDir = layout.buildDirectory.get().asFile
    commandLine("git", "clone", "--branch", version.get(), "--single-branch", url, name)
    onlyIf { (layout.buildDirectory / name).notExists() }
}

val downloadGlibHeaders: Exec = headerDownloadTask("glib", "https://github.com/GNOME/glib", libs.versions.glib)
val downloadGdkPixbufHeaders: Exec = headerDownloadTask("gdkPixbuf", "https://github.com/GNOME/gdk-pixbuf", libs.versions.gdkPixbuf)
val downloadLibnotifyHeaders: Exec = headerDownloadTask("libnotify", "https://github.com/GNOME/libnotify", libs.versions.libnotify)

fun headerUpdateTask(name: String): Exec = tasks.create<Exec>("update${name.capitalized()}Headers") {
    group = "sysnotifyHeaders"
    dependsOn("download${name.capitalized()}Headers")
    workingDir = (layout.buildDirectory / name).toFile()
    commandLine("git", "pull", "--force")
    onlyIf { (layout.buildDirectory / name).exists() }
}

val updateGlibHeaders: Exec = headerUpdateTask("glib")
val updateGdkPixbufHeaders: Exec = headerUpdateTask("gdkPixbuf")
val updateLibnotifyHeaders: Exec = headerUpdateTask("libnotify")

fun headerConfigureTask(name: String): Exec = tasks.create<Exec>("configure${name.capitalized()}Headers") {
    group = "sysnotifyHeaders"
    dependsOn("update${name.capitalized()}Headers")
    workingDir = (layout.buildDirectory / name).toFile()
    commandLine("meson", "setup", "build")
}

val configureGlibHeaders: Exec = headerConfigureTask("glib")
val configureGdkPixbufHeaders: Exec = headerConfigureTask("gdkPixbuf")
val configureLibnotifyHeaders: Exec = headerConfigureTask("libnotify")

fun headerBuildTask(name: String): Exec = tasks.create<Exec>("build${name.capitalized()}Headers") {
    group = "sysnotifyHeaders"
    dependsOn("configure${name.capitalized()}Headers")
    workingDir = (layout.buildDirectory / name).toFile()
    commandLine("meson", "compile", "-C", "build")
}

val buildGlibHeaders: Exec = headerBuildTask("glib")
val buildGdkPixbufHeaders: Exec = headerBuildTask("gdkPixbuf")
val buildLibnotifyHeaders: Exec = headerBuildTask("libnotify")

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
                        tasks.getByName(interopProcessingTaskName) {
                            dependsOn(buildGlibHeaders)
                            dependsOn(buildGdkPixbufHeaders)
                            dependsOn(buildLibnotifyHeaders)
                        }
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
                    tasks.getByName(interopProcessingTaskName) {
                        dependsOn(extractSysnotifyBinaries)
                    }
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
                implementation(libs.multiplatformJni)
                implementation(libs.multiplatformDlfcn)
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
        dependsOn(extractSysnotifyBinaries)
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
