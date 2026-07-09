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

import de.connect2x.conventions.configureJava
import de.connect2x.conventions.defaultCompilerOptions
import de.connect2x.conventions.defaultComposeDesktopApplication
import de.connect2x.conventions.withAndroid
import de.connect2x.conventions.withBrowser
import de.connect2x.conventions.withIos
import de.connect2x.conventions.withJvm
import de.connect2x.conventions.withMacos
import de.connect2x.conventions.withWeb

plugins {
    alias(sharedLibs.plugins.kotlin.multiplatform)
    alias(sharedLibs.plugins.android.application)
    alias(sharedLibs.plugins.compose.multiplatform)
    alias(sharedLibs.plugins.compose.compiler)
}

configureJava(sharedLibs.versions.targetJvm)

kotlin {
    defaultCompilerOptions()
    withAndroid("$group.sample")
    withJvm()
    withWeb {
        withBrowser {
            runTask {
                mainOutputFileName = "sample.js"
            }
            webpackTask {
                mainOutputFileName = "sample.js"
            }
        }
        binaries.executable()
    }
    withMacos {
        binaries {
            executable {
                entryPoint = "${rootProject.group}.sample.main"
            }
        }
    }
    withIos {
        binaries {
            framework {
                baseName = "Sysnotify"
                isStatic = true
            }
        }
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        androidMain {
            dependencies {
                implementation(sharedLibs.androidx.activityCompose)
            }
        }
        commonMain {
            dependencies {
                implementation(sharedLibs.compose.runtime)
                implementation(sharedLibs.compose.foundation)
                implementation(sharedLibs.compose.ui)
                implementation(sharedLibs.compose.material3)
                implementation(sharedLibs.compose.resources)
                implementation(projects.sysnotifyCore)
            }
        }
        jvmMain {
            dependencies {
                runtimeOnly(compose.desktop.currentOs)
                runtimeOnly(sharedLibs.kotlinx.coroutines.swing)
            }
        }
    }
}

android {
    sourceSets {
        named("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            res.srcDirs("src/androidMain/res")
            resources.srcDirs("src/commonMain/resources")
        }
    }
    defaultConfig {
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
}

val jdkLauncher: Provider<JavaLauncher> = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(sharedLibs.versions.distributionJvm.get().toInt())
    vendor = JvmVendorSpec.ADOPTIUM
}

defaultComposeDesktopApplication(
    appName = "Sysnotify Sample",
    appId = "${rootProject.group}.sample",
    appVersion = libs.versions.sysnotify.get(),
    mainClassName = "${rootProject.group}.sample.Main"
) {
    buildTypes.release.proguard { isEnabled = false }
    javaHome = jdkLauncher.get().metadata.installationPath.asFile.absolutePath
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    nativeDistributions {
        modules("java.net.http", "java.sql", "java.naming", "jdk.unsupported")
        appResourcesRootDir.set(layout.buildDirectory)
        windows {
            upgradeUuid = "3e1d7fa0-4c3f-4c3d-805f-d28bd21d6019"
        }
    }
}
