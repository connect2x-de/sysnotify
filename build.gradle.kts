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

import de.connect2x.conventions.authenticatedPackageRegistry
import de.connect2x.conventions.c2xOrganization
import de.connect2x.conventions.configureJava
import de.connect2x.conventions.withVersionSuffix

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.c2xConventions)
    `maven-publish`
}

allprojects {
    group = "de.connect2x"
    version = withVersionSuffix(rootProject.libs.versions.sysnotify)
    configureJava(rootProject.libs.versions.jvmTarget)

    if (System.getenv("WITH_LOCK")?.toBoolean() == true) {
        dependencyLocking {
            lockAllConfigurations()
        }
        val dependenciesForAll by tasks.registering(DependencyReportTask::class)
    }
}

subprojects {
    apply(plugin = "maven-publish")

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
}
