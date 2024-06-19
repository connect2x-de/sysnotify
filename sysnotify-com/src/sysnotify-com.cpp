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
#include <iostream>
#include <stdexcept>
#include <utility>
#include <windows.ui.notifications.h>

using namespace ABI::Windows::Data::Xml::Dom;
using namespace ABI::Windows::UI::Notifications;
using namespace Microsoft::WRL;

class __declspec(uuid("54BF154E-5239-42FA-B80C-6E405A0F413E")) NotificationActivator final
    : public RuntimeClass<RuntimeClassFlags<ClassicCom>, INotificationActivationCallback> {
public:
    [[nodiscard]] virtual HRESULT __stdcall Activate(_In_ LPCWSTR appUserModelId,
                                                     _In_ LPCWSTR invokedArgs,
                                                     _In_reads_(dataCount) const NOTIFICATION_USER_INPUT_DATA* data,
                                                     ULONG dataCountc) override {
        return S_OK;// TODO: Implement action callbacks.. somehow..
    }
};

CoCreatableClass(NotificationActivator)// There is no compile error here, just your IDE being a little confused by this macro

    namespace sysnotify {
    std::string g_last_error {};

    [[nodiscard]] auto guid_to_string(const GUID& guid) noexcept -> std::string {
        LPOLESTR result = nullptr;
        StringFromCLSID(guid, &result);
        auto wide_result = kstd::unicode::convert<char>(std::wstring_view(result));
        CoTaskMemFree(result);
        return wide_result;
    }

    class NotificationHandler final {
        ComPtr<IToastNotifier> _notifier;

    public:
        explicit NotificationHandler(const char* name)
            : _notifier(nullptr) {
            if(CoInitialize(nullptr) != S_OK) {
                throw std::runtime_error("Could not initialize COM");
            }
            const auto guid = __uuidof(NotificationActivator);
            if(DesktopNotificationManagerCompat::RegisterComServer(guid) != S_OK) {
                throw std::runtime_error(fmt::format("Could not register COM server with GUID {}", guid_to_string(guid)));
            }
            if(DesktopNotificationManagerCompat::RegisterActivator() != S_OK) {
                throw std::runtime_error(fmt::format("Could not register activator for notification handler {}", guid_to_string(guid)));
            }
            IToastNotifier* notifier = nullptr;
            const auto wide_name = kstd::unicode::convert<wchar_t>(std::string_view(name));
            if(DesktopNotificationManagerCompat::CreateToastNotifier(wide_name.c_str(), &notifier) != S_OK || notifier == nullptr) {
                throw std::runtime_error(
                    fmt::format("Could not create toast notifier instance for notification handler {}", guid_to_string(guid)));
            }
            _notifier = ComPtr<IToastNotifier>(notifier);
        }

        ~NotificationHandler() {
            CoUninitialize();
        }

        [[nodiscard]] inline auto format_content(const sn_notification_content_t& content) noexcept -> ComPtr<IXmlDocument> {
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
            return {document};
        }

        [[nodiscard]] inline auto create_notification(const sn_notification_content_t& content) noexcept -> ComPtr<IToastNotification>* {
            auto document = format_content(content);
            IToastNotification* notification = nullptr;
            if(DesktopNotificationManagerCompat::CreateToastNotification(document.Get(), &notification) != S_OK ||
               notification == nullptr) {
                return nullptr;
            }
            return new ComPtr<IToastNotification>(notification);
        }

        [[nodiscard]] inline auto push(const sn_notification_content_t& content) noexcept -> ComPtr<IToastNotification>* {
            auto notification = create_notification(content);
            _notifier->Show(notification->Get());
            return notification;
        }

        inline auto update(ComPtr<IToastNotification>* handle, const sn_notification_content_t& content) noexcept -> void {
            _notifier->Hide(handle->Get());
            auto new_notification = create_notification(content);
            _notifier->Show(new_notification->Get());
            *handle = std::move(*new_notification);
        }

        inline auto pop(ComPtr<IToastNotification>* handle) noexcept -> void {
            _notifier->Hide(handle->Get());
            delete handle;// We only free the handle, COM objects don't get freed explicitly
        }
    };
}// namespace sysnotify

#define TRY_OR_RETURN(b)                     \
    do {                                     \
        try {                                \
            b                                \
        }                                    \
        catch(const std::exception& error) { \
            using namespace sysnotify;       \
            g_last_error = error.what();     \
            return SYSNOTIFY_ERR_ABORTED;    \
        }                                    \
    } while(false)

extern "C" {

SYSNOTIFY_API sn_error sysnotify_com_create(const char* name, sn_notification_handler* handler) {
    using namespace sysnotify;
    if(handler == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    TRY_OR_RETURN({ *handler = reinterpret_cast<sn_notification_handler>(new NotificationHandler(name)); });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_com_destroy(sn_notification_handler handler) {
    using namespace sysnotify;
    if(handler == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    TRY_OR_RETURN({ delete reinterpret_cast<NotificationHandler*>(handler); });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_com_push(sn_notification_handler handler,
                                          const sn_notification_content_t* content,
                                          sn_notification* notification) {
    using namespace sysnotify;
    if(handler == nullptr || content == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    TRY_OR_RETURN({ *notification = reinterpret_cast<sn_notification>(reinterpret_cast<NotificationHandler*>(handler)->push(*content)); });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_com_update(sn_notification_handler handler,
                                            sn_notification notification,
                                            const sn_notification_content_t* content) {
    using namespace sysnotify;
    if(handler == nullptr || notification == nullptr || content == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    TRY_OR_RETURN({
        reinterpret_cast<NotificationHandler*>(handler)->update(reinterpret_cast<ComPtr<IToastNotification>*>(notification), *content);
    });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_com_pop(sn_notification_handler handler, sn_notification notification) {
    using namespace sysnotify;
    if(handler == nullptr || notification == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    TRY_OR_RETURN({ reinterpret_cast<NotificationHandler*>(handler)->pop(reinterpret_cast<ComPtr<IToastNotification>*>(notification)); });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API const char* sysnotify_com_get_last_error() {
    using namespace sysnotify;
    return g_last_error.c_str();
}
}
