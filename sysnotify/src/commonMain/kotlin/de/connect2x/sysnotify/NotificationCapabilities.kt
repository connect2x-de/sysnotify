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

package de.connect2x.sysnotify

import kotlin.jvm.JvmInline

/**
 * Represents the capabilities of a notification system on a specific platform.
 *
 * This class uses a bit mask to efficiently store which notification features
 * are supported by the underlying platform implementation. Each bit in the mask
 * corresponds to a specific capability such as title support, icon support, etc.
 *
 * @property mask The bit mask representing the supported capabilities.
 */
@JvmInline
value class NotificationCapabilities(val mask: UInt) {
    /**
     * Contains constants for all supported notification capabilities.
     * Each constant represents a specific bit in the capabilities mask.
     */
    companion object {
        // @formatter:off
        const val IS_TITLE_SUPPORTED: UInt          = 0b0000_0000_0001U
        const val IS_DESCRIPTION_SUPPORTED: UInt    = 0b0000_0000_0010U
        const val IS_ICON_SUPPORTED: UInt           = 0b0000_0000_0100U
        const val IS_ACTION_SUPPORTED: UInt         = 0b0000_0000_1000U
        const val IS_POP_SUPPORTED: UInt            = 0b0000_0001_0000U
        const val IS_SOUND_SUPPORTED: UInt          = 0b0000_0010_0000U
        const val IS_CUSTOM_SOUND_SUPPORTED: UInt   = 0b0000_0100_0000U
        const val IS_GROUPING_SUPPORTED: UInt       = 0b0000_1000_0000U
        const val IS_STATUS_ICON_SUPPORTED: UInt    = 0b0001_0000_0000U
        // @formatter:on

        /**
         * A mask with all capabilities enabled.
         * This represents a notification system that supports all available features.
         */
        val ALL: UInt = IS_TITLE_SUPPORTED or
                IS_DESCRIPTION_SUPPORTED or
                IS_ICON_SUPPORTED or
                IS_ACTION_SUPPORTED or
                IS_POP_SUPPORTED or
                IS_SOUND_SUPPORTED or
                IS_CUSTOM_SOUND_SUPPORTED or
                IS_GROUPING_SUPPORTED or
                IS_STATUS_ICON_SUPPORTED
    }

    /**
     * Constructs a NotificationCapabilities instance from individual boolean flags.
     *
     * @param isTitleSupported Whether title display is supported
     * @param isDescriptionSupported Whether description/content display is supported
     * @param isIconSupported Whether icon display is supported
     * @param isActionSupported Whether action callbacks are supported
     * @param isPopSupported Whether dismissing/popping notifications is supported
     * @param isSoundSupported Whether playing sounds is supported
     * @param isCustomSoundSupported Whether custom sound files are supported
     * @param isGroupingSupported Whether grouping notifications is supported
     */
    constructor(
        isTitleSupported: Boolean,
        isDescriptionSupported: Boolean,
        isIconSupported: Boolean,
        isActionSupported: Boolean,
        isPopSupported: Boolean,
        isSoundSupported: Boolean,
        isCustomSoundSupported: Boolean,
        isGroupingSupported: Boolean,
        isStatusIconSupported: Boolean
    ) : this(
        if (isTitleSupported) IS_TITLE_SUPPORTED else 0U
                or if (isDescriptionSupported) IS_DESCRIPTION_SUPPORTED else 0U
                or if (isIconSupported) IS_ICON_SUPPORTED else 0U
                or if (isActionSupported) IS_ACTION_SUPPORTED else 0U
                or if (isPopSupported) IS_POP_SUPPORTED else 0U
                or if (isSoundSupported) IS_SOUND_SUPPORTED else 0U
                or if (isCustomSoundSupported) IS_CUSTOM_SOUND_SUPPORTED else 0U
                or if (isGroupingSupported) IS_GROUPING_SUPPORTED else 0U
                or if (isStatusIconSupported) IS_STATUS_ICON_SUPPORTED else 0U
    )

    /**
     * Indicates whether the notification system supports displaying a title.
     */
    inline val isTitleSupported: Boolean
        get() = (mask and IS_TITLE_SUPPORTED) == IS_TITLE_SUPPORTED

    /**
     * Indicates whether the notification system supports displaying a description/content.
     */
    inline val isDescriptionSupported: Boolean
        get() = (mask and IS_DESCRIPTION_SUPPORTED) == IS_DESCRIPTION_SUPPORTED

    /**
     * Indicates whether the notification system supports displaying an icon.
     */
    inline val isIconSupported: Boolean
        get() = (mask and IS_ICON_SUPPORTED) == IS_ICON_SUPPORTED

    /**
     * Indicates whether the notification system supports action callbacks.
     */
    inline val isActionSupported: Boolean
        get() = (mask and IS_ACTION_SUPPORTED) == IS_ACTION_SUPPORTED

    /**
     * Indicates whether the notification system supports dismissing/popping notifications.
     */
    inline val isPopSupported: Boolean
        get() = (mask and IS_POP_SUPPORTED) == IS_POP_SUPPORTED

    /**
     * Indicates whether the notification system supports playing sounds.
     */
    inline val isSoundSupported: Boolean
        get() = (mask and IS_SOUND_SUPPORTED) == IS_SOUND_SUPPORTED

    /**
     * Indicates whether the notification system supports custom sound files.
     */
    inline val isCustomSoundSupported: Boolean
        get() = (mask and IS_CUSTOM_SOUND_SUPPORTED) == IS_CUSTOM_SOUND_SUPPORTED

    /**
     * Indicates whether the notification system supports grouping notifications.
     */
    inline val isGroupingSupported: Boolean
        get() = (mask and IS_GROUPING_SUPPORTED) == IS_GROUPING_SUPPORTED

    /**
     * Indicates whether the notification system supports a separate status icon for the status bar (on Android etc.).
     */
    inline val isStatusIconSupported: Boolean
        get() = (mask and IS_STATUS_ICON_SUPPORTED) == IS_STATUS_ICON_SUPPORTED

    /**
     * If true, the underlying implementation supports at least
     * one of three ways to display a basic notification. That is,
     * either using a title, description or icon, or any combination
     * of the aforementioned components.
     */
    inline val isSupported: Boolean
        get() = isTitleSupported || isDescriptionSupported || isIconSupported || isSoundSupported
}
