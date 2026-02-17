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

rootProject.name = "sysnotify"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("https://gitlab.com/api/v4/projects/68438621/packages/maven") // c2x Conventions
        maven("https://gitlab.com/api/v4/projects/75787729/packages/maven") // compose multiplatform a11y
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        maven("https://gitlab.com/api/v4/projects/68438621/packages/maven") // c2x Conventions
        maven("https://gitlab.com/api/v4/projects/75787860/packages/maven") // compose multiplatform core a11y
        maven("https://gitlab.com/api/v4/projects/75787729/packages/maven") // compose multiplatform a11y
    }
}

plugins {
    id("de.connect2x.conventions.c2x-settings-plugin") version "20260217.173858"
}

include(":sysnotify")
include(":sysnotify-sample")
