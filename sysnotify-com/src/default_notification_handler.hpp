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

#include "notification_handler.hpp"

namespace sysnotify {
    class DefaultNotificationHandler final : public NotificationHandler {
        struct NotificationTag final {
            GUID tag;
            std::optional<std::filesystem::path> icon_path;
        };

        wrl::ComPtr<abi::IToastNotificationManagerStatics> _manager;
        wrl::ComPtr<abi::IToastNotificationManagerStatics2> _manager2;
        wrl::ComPtr<abi::IToastNotifier> _notifier;          // For showing, updating and hiding toasts
        wrl::ComPtr<abi::IToastNotificationFactory> _factory;// For creating new toast notification COM objects
        wrl::ComPtr<abi::IToastNotificationHistory> _history;// For retrieving all active notifications belonging to our application
        std::wstring _app_name;                              // The associated application name (name of the NotificationHandler instance)
        std::wstring _aum_id;                                // The associated [A]pplication [U]ser [M]odel [ID]
        phmap::parallel_flat_hash_map<GUID, NotificationTag> _tags;// Keeps track of all valid/active notifications
        phmap::parallel_flat_hash_set<GUID>
            _cancelled;// Keeps track of all notifications that have been cancelled at least once but are still valid

        auto create_shortcut_if_needed() -> void;
        auto set_aumid() -> void;
        auto register_com_server() -> void;
        [[nodiscard]] auto create_content_document(const GUID& tag,
                                                   const NotificationContent& content,
                                                   const std::optional<std::filesystem::path>& icon_path) -> wrl::ComPtr<abi::IXmlDocument>;
        [[nodiscard]] auto get_notification(const GUID& tag) -> wrl::ComPtr<abi::IToastNotification>;
        auto register_actions(wrl::ComPtr<abi::IToastNotification>& notification,
                              const GUID& tag,
                              const NotificationContent& content,
                              const std::optional<std::filesystem::path>& icon_path) -> void;
        [[nodiscard]] auto format_content(const GUID& tag,
                                          const NotificationContent& content,
                                          const std::optional<std::filesystem::path>& icon_path) -> std::wstring;
        [[nodiscard]] auto format_image(const GUID& tag, const std::optional<std::filesystem::path>& icon_path) -> std::string;
        [[nodiscard]] auto make_icon_if_present(const NotificationContent& content,
                                                const GUID& tag) -> std::optional<std::filesystem::path>;

    public:
        DefaultNotificationHandler(const std::string& app_name, const std::string& aum_id);
        DefaultNotificationHandler(const DefaultNotificationHandler&) noexcept = delete;
        DefaultNotificationHandler(DefaultNotificationHandler&&) noexcept = default;
        ~DefaultNotificationHandler() final;

        auto operator=(const DefaultNotificationHandler&) noexcept -> DefaultNotificationHandler& = delete;
        auto operator=(DefaultNotificationHandler&&) noexcept -> DefaultNotificationHandler& = default;

        auto push(const NotificationContent& content) -> GUID final;
        auto update(const GUID& tag, const NotificationContent& content) -> void final;
        auto pop(const GUID& tag) -> void final;
        auto is_valid_tag(const GUID& tag) -> bool final;
    };
}// namespace sysnotify