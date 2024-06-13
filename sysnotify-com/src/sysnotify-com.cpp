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
 * @since 09/06/24
 */

#include "sysnotify-com.h"

#include "DesktopNotificationManagerCompat.h"
#include "unicode.h"

#include <NotificationActivationCallback.h>
#include <fmt/format.h>
#include <stdexcept>
#include <windows.ui.notifications.h>

// Anonymous namespace for implementing our COM activator
namespace {
    using namespace ABI::Windows::Data::Xml::Dom;
    using namespace ABI::Windows::UI::Notifications;
    using namespace Microsoft::WRL;

    class __declspec(uuid("54BF154E-5239-42FA-B80C-6E405A0F413E")) NotificationActivator final
        : public RuntimeClass<RuntimeClassFlags<ClassicCom>, INotificationActivationCallback> {
    public:
        [[nodiscard]] HRESULT __stdcall Activate(LPCWSTR appUserModelId,
                                                 LPCWSTR invokedArgs,
                                                 const NOTIFICATION_USER_INPUT_DATA* data,
                                                 ULONG dataCount) final {
            return S_OK;// TODO: Implement action callbacks.. somehow..
        }
    };

    CoCreatableClass(NotificationActivator)// There is no compile error here, just your IDE being a little confused by this macro
}// namespace

namespace sysnotify {
    [[nodiscard]] auto guid_to_string(const GUID& guid) noexcept -> std::string {
        LPOLESTR result = nullptr;
        StringFromCLSID(guid, &result);
        auto wide_result = kstd::unicode::convert<char>(std::wstring_view {result});
        CoTaskMemFree(result);
        return wide_result;
    }

    struct NotificationHandle final {
        IToastNotification* notification;

        [[nodiscard]] auto operator==(std::nullptr_t) const noexcept -> bool {
            return notification == nullptr;
        }

        [[nodiscard]] auto operator!=(std::nullptr_t) const noexcept -> bool {
            return notification != nullptr;
        }
    };

    class NotificationHandler final {
        IToastNotifier* _notifier;

    public:
        NotificationHandler()
            : _notifier(nullptr) {
            const auto guid = __uuidof(NotificationActivator);
            if(DesktopNotificationManagerCompat::RegisterComServer(guid) != S_OK) {
                throw std::runtime_error(fmt::format("Could not register COM server with GUID {}", guid_to_string(guid)));
            }
            if(DesktopNotificationManagerCompat::RegisterActivator() != S_OK) {
                throw std::runtime_error(fmt::format("Could not register activator for notification handler {}", guid_to_string(guid)));
            }
            if(DesktopNotificationManagerCompat::CreateToastNotifier(&_notifier) != S_OK) {
                throw std::runtime_error(
                    fmt::format("Could not create toast notifier instance for notification handler {}", guid_to_string(guid)));
            }
        }

        ~NotificationHandler() noexcept = default;

        [[nodiscard]] auto push(const sn_notification_content_t& content) noexcept -> NotificationHandle* {
            // @formatter:off
            const auto formatted_content = kstd::unicode::convert<wchar_t>(fmt::format(R"(
<toast>
    <visual>
        <binding template='ToastGeneric'>
            <text>{}</text>
            <text>{}</text>
        </binding>
    </visual>
</toast>)",
                                                                                       content.title, content.description));
            // @formatter:on
            IXmlDocument* document = nullptr;
            if(DesktopNotificationManagerCompat::CreateXmlDocumentFromString(formatted_content.c_str(), &document) != S_OK ||
               document == nullptr) {
                return nullptr;
            }
            IToastNotification* handle = nullptr;
            if(DesktopNotificationManagerCompat::CreateToastNotification(document, &handle) != S_OK || handle == nullptr) {
                return nullptr;
            }
            _notifier->Show(handle);
            return new NotificationHandle(handle);
        }

        auto update(NotificationHandle* handle, const sn_notification_content_t& content) noexcept -> void {
        }

        auto pop(NotificationHandle* handle) noexcept -> void {
            delete handle;// We only free the handle, COM objects don't get freed explicitly
        }
    };
}// namespace sysnotify

extern "C" {

SYSNOTIFY_API sn_error sn_com_init(sn_notification_handler* handler) {
    using namespace sysnotify;
    if(handler == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    *handler = reinterpret_cast<sn_notification_handler>(new NotificationHandler());
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sn_com_deinit(sn_notification_handler handler) {
    using namespace sysnotify;
    if(handler == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    delete reinterpret_cast<NotificationHandler*>(handler);
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sn_com_push(sn_notification_handler handler,
                                      const sn_notification_content_t* content,
                                      sn_notification* notification) {
    using namespace sysnotify;
    if(handler == nullptr || content == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    *notification = reinterpret_cast<sn_notification>(reinterpret_cast<NotificationHandler*>(handler)->push(*content));
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sn_com_update(sn_notification_handler handler,
                                        sn_notification notification,
                                        const sn_notification_content_t* content) {
    using namespace sysnotify;
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sn_com_pop(sn_notification_handler handler, sn_notification notification) {
    using namespace sysnotify;
    return SYSNOTIFY_OK;
}
}
