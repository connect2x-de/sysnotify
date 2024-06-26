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
 * The ComAdaptor class implements glue code to allow
 * the Sysnotify service to forward notifications to Windows/the shell
 * via a unified COM interface.
 * This allows applications to easily send notifications without being
 * a registered Windows (UWP) app
 *
 * @author Alexander Hinze
 * @since 20/06/24
 */

#pragma once

#include "platform.hpp"
#include "sysnotify_types.h"

namespace sysnotify {
    // Windows runtime library (not WRT)
    namespace wrl {
        using namespace Microsoft::WRL;
        using namespace Microsoft::WRL::Wrappers;
    }

    // Windows ABI
    namespace abi {
        using namespace ABI::Windows::Foundation::Collections;
        using namespace ABI::Windows::Data::Xml::Dom;
        using namespace ABI::Windows::UI::Notifications;
    }

    using NotificationContent = sn_notification_content;

    class NotificationHandler final {
        struct NotificationTag final {
            GUID tag;
            std::optional<std::filesystem::path> icon_path;
        };

        wrl::ComPtr<abi::IToastNotificationManagerStatics> _manager;
        wrl::ComPtr<abi::IToastNotificationManagerStatics2> _manager2;
        wrl::ComPtr<abi::IToastNotifier> _notifier;
        wrl::ComPtr<abi::IToastNotificationFactory> _factory;
        wrl::ComPtr<abi::IToastNotificationHistory> _history;
        std::wstring _app_name;
        std::wstring _aum_id;
        phmap::parallel_flat_hash_map<GUID, NotificationTag> _tags;

        auto create_shortcut_if_needed() -> void;
        auto set_aumid() -> void;
        [[nodiscard]] auto create_content_document(const NotificationContent& content) -> wrl::ComPtr<abi::IXmlDocument>;
        [[nodiscard]] auto get_notification(const GUID& tag) -> wrl::ComPtr<abi::IToastNotification>;
        auto register_actions(wrl::ComPtr<abi::IToastNotification>& notification, const GUID& tag, const NotificationContent& content) -> void;

    public:
        NotificationHandler(const std::string& app_name, const std::string& aum_id);
        NotificationHandler(const NotificationHandler&) noexcept = delete;
        NotificationHandler(NotificationHandler&&) noexcept = default;
        ~NotificationHandler();

        auto operator=(const NotificationHandler&) noexcept -> NotificationHandler& = delete;
        auto operator=(NotificationHandler&&) noexcept -> NotificationHandler& = default;

        auto push(const NotificationContent& content) -> GUID;
        auto update(const GUID& tag, const NotificationContent& content) -> void;
        auto pop(const GUID& tag) -> void;
        auto is_valid_tag(const GUID& tag) -> bool;
    };
}// namespace sysnotify