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

import kotlin.jvm.JvmInline

/**
 * A value class describing the capabilities of a given [NotificationHandler] instance.
 * Each flag indicates whether a feature is supported.
 * The [isSupported] flag indicates if notifications are supported on the current platform at all.
 */
@JvmInline
value class NotificationCapabilities(val mask: UInt) {
    companion object {
        // @formatter:off
        const val IS_TITLE_SUPPORTED: UInt          = 0b0000_0001U
        const val IS_DESCRIPTION_SUPPORTED: UInt    = 0b0000_0010U
        const val IS_ICON_SUPPORTED: UInt           = 0b0000_0100U
        const val IS_ACTION_SUPPORTED: UInt         = 0b0000_1000U
        const val IS_POP_SUPPORTED: UInt            = 0b0001_0000U
        const val IS_SOUND_SUPPORTED: UInt          = 0b0010_0000U
        const val IS_CUSTOM_SOUND_SUPPORTED: UInt   = 0b0100_0000U
        const val IS_GROUPING_SUPPORTED: UInt       = 0b1000_0000U
        // @formatter:on

        val ALL: UInt = IS_TITLE_SUPPORTED or
                IS_DESCRIPTION_SUPPORTED or
                IS_ICON_SUPPORTED or
                IS_ACTION_SUPPORTED or
                IS_POP_SUPPORTED or
                IS_SOUND_SUPPORTED or
                IS_CUSTOM_SOUND_SUPPORTED or
                IS_GROUPING_SUPPORTED
    }

    constructor(
        isTitleSupported: Boolean,
        isDescriptionSupported: Boolean,
        isIconSupported: Boolean,
        isActionSupported: Boolean,
        isPopSupported: Boolean,
        isSoundSupported: Boolean,
        isCustomSoundSupported: Boolean,
        isGroupingSupported: Boolean
    ) : this(
        if (isTitleSupported) IS_TITLE_SUPPORTED else 0U
                or if (isDescriptionSupported) IS_DESCRIPTION_SUPPORTED else 0U
                or if (isIconSupported) IS_ICON_SUPPORTED else 0U
                or if (isActionSupported) IS_ACTION_SUPPORTED else 0U
                or if (isPopSupported) IS_POP_SUPPORTED else 0U
                or if (isSoundSupported) IS_SOUND_SUPPORTED else 0U
                or if (isCustomSoundSupported) IS_CUSTOM_SOUND_SUPPORTED else 0U
                or if (isGroupingSupported) IS_GROUPING_SUPPORTED else 0U
    )

    inline val isTitleSupported: Boolean
        get() = (mask and IS_TITLE_SUPPORTED) == IS_TITLE_SUPPORTED

    inline val isDescriptionSupported: Boolean
        get() = (mask and IS_DESCRIPTION_SUPPORTED) == IS_DESCRIPTION_SUPPORTED

    inline val isIconSupported: Boolean
        get() = (mask and IS_ICON_SUPPORTED) == IS_ICON_SUPPORTED

    inline val isActionSupported: Boolean
        get() = (mask and IS_ACTION_SUPPORTED) == IS_ACTION_SUPPORTED

    inline val isPopSupported: Boolean
        get() = (mask and IS_POP_SUPPORTED) == IS_POP_SUPPORTED

    inline val isSoundSupported: Boolean
        get() = (mask and IS_SOUND_SUPPORTED) == IS_SOUND_SUPPORTED

    inline val isCustomSoundSupported: Boolean
        get() = (mask and IS_CUSTOM_SOUND_SUPPORTED) == IS_CUSTOM_SOUND_SUPPORTED

    inline val isGroupingSupported: Boolean
        get() = (mask and IS_GROUPING_SUPPORTED) == IS_GROUPING_SUPPORTED

    /**
     * If true, the underlying implementation supports at least
     * one of three ways to display a basic notification. That is,
     * either using a title, description or icon, or any combination
     * of the aforementioned components.
     */
    inline val isSupported: Boolean
        get() = isTitleSupported || isDescriptionSupported || isIconSupported || isSoundSupported
}
