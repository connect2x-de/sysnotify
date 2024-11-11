// Copyright 2024 connect2x GmbH
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @author Alexander Hinze
 * @since 27/06/24
 */

#pragma once

#include "notification_handler.hpp"

namespace sysnotify {
    struct FallbackNotificationHandler final : public NotificationHandler {
        FallbackNotificationHandler() noexcept;
        FallbackNotificationHandler(const FallbackNotificationHandler&) noexcept = default;
        FallbackNotificationHandler(FallbackNotificationHandler&&) noexcept = default;
        ~FallbackNotificationHandler() noexcept = default;

        auto operator=(const FallbackNotificationHandler&) noexcept -> FallbackNotificationHandler& = default;
        auto operator=(FallbackNotificationHandler&&) noexcept -> FallbackNotificationHandler& = default;

        auto push(const NotificationContent& content) -> GUID;
        auto update(const GUID& tag, const NotificationContent& content) -> void;
        auto pop(const GUID& tag) -> void;
        auto is_valid_tag(const GUID& tag) -> bool;
    };
}// namespace sysnotify