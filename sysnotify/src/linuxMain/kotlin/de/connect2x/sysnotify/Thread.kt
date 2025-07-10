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

package de.connect2x.sysnotify

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import platform.linux.SYS_gettid
import platform.posix.nanosleep
import platform.posix.pthread_create
import platform.posix.pthread_join
import platform.posix.pthread_t
import platform.posix.pthread_tVar
import platform.posix.syscall
import platform.posix.timespec
import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private var threadName: String? = null

// Implementation based on https://git.karmakrafts.dev/kk/filament/-/blob/master/filament-core/src/linuxMain/kotlin/dev/karmakrafts/filament/Thread.kt
@OptIn(ExperimentalForeignApi::class)
internal class Thread private constructor(private val handle: pthread_t) {
    companion object {
        init {
            // Workaround for X11 applications to allow thread creation through pthread
            LibX11.XInitThreads()
        }

        inline val id: ULong
            get() = syscall(SYS_gettid.convert()).toULong()

        var name: String
            get() = threadName ?: "Thread $id"
            set(value) {
                threadName = value
            }

        fun sleep(millis: Long) = memScoped {
            val spec = alloc<timespec> {
                tv_sec = millis / 1000
                tv_nsec = millis % 1000000
            }
            nanosleep(spec.ptr, spec.ptr)
            (spec.tv_sec * 1000) + (spec.tv_nsec / 1000000)
        }

        operator fun invoke(block: () -> Unit): Thread = memScoped {
            val blockPtr = StableRef.create(block).asCPointer()
            val handle = alloc<pthread_tVar>()
            pthread_create(handle.ptr, null, staticCFunction { userData ->
                userData?.asStableRef<() -> Unit>()?.apply {
                    get()()
                    dispose()
                }
                null
            }, blockPtr)
            Thread(handle.value)
        }
    }

    fun join() = pthread_join(handle, null)
}
