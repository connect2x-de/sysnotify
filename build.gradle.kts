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

import de.connect2x.conventions.CI
import de.connect2x.conventions.withVersionSuffix
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.engine.plugins.DokkaHtmlPluginParameters
import java.time.ZonedDateTime

plugins {
    alias(sharedLibs.plugins.android.library) apply false
    alias(sharedLibs.plugins.android.application) apply false
    alias(sharedLibs.plugins.kotlin.multiplatform) apply false
    alias(sharedLibs.plugins.compose.multiplatform) apply false
    alias(sharedLibs.plugins.compose.compiler) apply false
    alias(sharedLibs.plugins.mavenPublish) apply false
    alias(sharedLibs.plugins.dokka) apply false
    alias(sharedLibs.plugins.c2xConventions)
}

allprojects {
    group = "de.connect2x.sysnotify"
    version = withVersionSuffix(rootProject.libs.versions.sysnotify)
}

subprojects {
    if ("sample" in project.name) return@subprojects
    if (CI.isCI) {
        apply<DokkaPlugin>()
        configure<DokkaExtension> {
            moduleName = project.name
            pluginsConfiguration {
                named<DokkaHtmlPluginParameters>("html") {
                    homepageLink = "https://gitlab.com/connect2x/sysnotify"
                    footerMessage = "&copy; ${ZonedDateTime.now().year} connect2x GmbH"
                }
            }
        }
    }
}
