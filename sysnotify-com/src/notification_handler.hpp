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

#include "platform.hpp"
#include "sysnotify_types.h"

namespace sysnotify {
    // Windows runtime library (not WRT)
    namespace wrl {
        using namespace Microsoft::WRL;
        using namespace Microsoft::WRL::Wrappers;
    }// namespace wrl

    // Windows ABI
    namespace abi {
        using namespace ABI::Windows::Foundation::Collections;
        using namespace ABI::Windows::Data::Xml::Dom;
        using namespace ABI::Windows::UI::Notifications;
    }// namespace abi

    using NotificationContent = sn_notification_content;

    class NotificationHandler {
    protected:
        NotificationHandler() noexcept = default;

    public:
        virtual ~NotificationHandler() noexcept = default;

        virtual auto push(const NotificationContent& content) -> GUID = 0;
        virtual auto update(const GUID& tag, const NotificationContent& content) -> void = 0;
        virtual auto pop(const GUID& tag) -> void = 0;
        virtual auto is_valid_tag(const GUID& tag) -> bool = 0;
    };
}// namespace sysnotify