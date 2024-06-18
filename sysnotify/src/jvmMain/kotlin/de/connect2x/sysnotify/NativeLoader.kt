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

package de.connect2x.sysnotify

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.pathString

/**
 * A simple runtime loader for shared libraries embedded as JAR resources.
 * Since the JVM does not support linking from a block of memory, but rather a file
 * in the filesystem of the host, we extract the required natives from within the JAR
 * and place it into a temporary directory in the user's application data directory.
 */
internal object NativeLoader {
    private const val PLATFORM_WINDOWS: String = "windows"
    private const val PLATFORM_LINUX: String = "linux"
    private const val PLATFORM_MACOS: String = "macos" // "Stop calling it OSX!"

    @Suppress("UnsafeDynamicallyLoadedCode")
    fun load() {
        try {
            // If you change this, make sure to update the same code in build.gradle.kts too
            val architecture = when (System.getProperty("os.arch")) {
                "i386", "i486", "i586", "i686", "x86", "x32" -> "x86"
                "x64", "amd64", "x86_64", "x86-64" -> "x64"
                "aarch64", "arm64" -> "arm64"
                "arm", "armv7", "aarch32" -> "arm32"
                else -> throw IllegalStateException("Could not determine host architecture")
            }
            val os = System.getProperty("os.name").let {
                when {
                    it.contains("windows", true) -> PLATFORM_WINDOWS
                    it.contains("linux", true) -> PLATFORM_LINUX
                    it.contains("osx", true) || it.contains("mac", true) -> PLATFORM_MACOS
                    else -> throw IllegalStateException("Unsupported target platform")
                }
            }
            val libPrefix = if (os == PLATFORM_WINDOWS) "" else "lib"
            val extension = when (os) {
                PLATFORM_WINDOWS -> "dll"
                PLATFORM_MACOS -> "dylib"
                else -> "so"
            }
            val filePath = Path("natives") / "$os-$architecture" / "${libPrefix}sysnotify.$extension"
            val targetDirectory = Path(System.getProperty("user.home")) / ".sysnotify"
            val targetPath = targetDirectory / filePath
            Files.createDirectories(targetPath.parent)
            println("Copying natives from $filePath to $targetPath") // TODO: debug
            this::class.java.getResourceAsStream("/$filePath").use {
                Files.copy(
                    requireNotNull(it) { "Could not read JAR resource" },
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            if (os == PLATFORM_LINUX) System.loadLibrary("notify")
            System.load(targetPath.pathString)
            // Make sure we delete the file when the process terminates
            Runtime.getRuntime().addShutdownHook(Thread {
                Files.deleteIfExists(targetPath)
            })
        } catch (error: Throwable) { // Catch any type of error we might encounter and rethrow
            throw NativeLoaderException(error)
        }
    }
}

// This type allows the API consumer to catch the error on the JVM if required
class NativeLoaderException(error: Throwable) : RuntimeException("Could not load natives", error)
