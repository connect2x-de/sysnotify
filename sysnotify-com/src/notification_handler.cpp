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
 * @since 20/06/24
 */

#include "notification_handler.hpp"

#include <NotificationActivationCallback.h>
#include <propkey.h>
#include <propvarutil.h>
#include <shobjidl_core.h>
#include <wrl/module.h>
#include <wrl/wrappers/corewrappers.h>

#include "log.hpp"

namespace sysnotify {
    constexpr size_t GUID_BUFFER_SIZE = 40;

    // COM objects

    class __declspec(uuid("54BF154E-5239-42FA-B80C-6E405A0F413E")) NotificationActivator final
        : public RuntimeClass<RuntimeClassFlags<ClassicCom>, INotificationActivationCallback> {
        long _ref_count = 0;

    public:
        [[nodiscard]] virtual HRESULT __stdcall Activate(LPCWSTR aum_id,
                                                         LPCWSTR invoked_args,
                                                         const NOTIFICATION_USER_INPUT_DATA* data,
                                                         ULONG data_count) override {
            return S_OK;// TODO: Implement action callbacks.. somehow..
        }

        [[nodiscard]] virtual HRESULT __stdcall QueryInterface(REFIID riid, void** ppObj) override {
            if(riid == IID_IUnknown || riid == IID_INotificationActivationCallback) {
                *ppObj = static_cast<INotificationActivationCallback*>(this);
                return S_OK;
            }
            *ppObj = nullptr;
            return E_NOINTERFACE;
        }

        [[nodiscard]] virtual ULONG __stdcall AddRef() override {
            return InterlockedIncrement(&_ref_count);
        }

        [[nodiscard]] virtual ULONG __stdcall Release() override {// NOLINT
            long ref_count = 0;
            ref_count = InterlockedDecrement(&_ref_count);
            if(ref_count == 0)
                delete this;
            return ref_count;
        }
    };

    CoCreatableClass(NotificationActivator);// Semicolon to help formatter
    constexpr GUID ACTIVATOR_CLSID = __uuidof(NotificationActivator);

    // ComAdaptor implementation

    NotificationHandler::NotificationHandler(const std::string& app_name, const std::string& aum_id, bool create_shortcut)
        : _app_name(kstd::unicode::convert<wchar_t>(app_name))
        , _aum_id(kstd::unicode::convert<wchar_t>(aum_id)) {
        SYSNOTIFY_LOG_DEBUG("Initializing COM interface");
        SYSNOTIFY_REQUIRE_SUCCESS(CoInitializeEx(nullptr, COINIT::COINIT_MULTITHREADED));
        if(create_shortcut) {
            register_com_server(create_shortcut_if_needed());
        }

        set_aumid();

        // Create notifier
        SYSNOTIFY_LOG_DEBUG("Creating toast notifier instance");
        SYSNOTIFY_REQUIRE_SUCCESS(
            GetActivationFactory(HStringReference(RuntimeClass_Windows_UI_Notifications_ToastNotificationManager).Get(), &_manager));
        SYSNOTIFY_REQUIRE(_manager);
        SYSNOTIFY_REQUIRE_SUCCESS(_manager->CreateToastNotifierWithId(HStringReference(_aum_id.c_str()).Get(), &_notifier));
        SYSNOTIFY_LOG_DEBUG("Created toast notifier at {}", fmt::ptr(_notifier.Get()));

        // Create factory
        SYSNOTIFY_REQUIRE_SUCCESS(
            GetActivationFactory(HStringReference(RuntimeClass_Windows_UI_Notifications_ToastNotification).Get(), &_factory));
        SYSNOTIFY_REQUIRE(_factory);
        SYSNOTIFY_LOG_DEBUG("Created toast notification factory at {}", fmt::ptr(_factory.Get()));

        // Create history
        SYSNOTIFY_LOG_DEBUG("Creating toast notification history");
        SYSNOTIFY_REQUIRE_SUCCESS(_manager.As(&_manager2));
        SYSNOTIFY_REQUIRE(_manager2);
        SYSNOTIFY_REQUIRE_SUCCESS(_manager2->get_History(&_history));
        SYSNOTIFY_REQUIRE(_history);
        SYSNOTIFY_LOG_DEBUG("Created toast notification history at {}", fmt::ptr(_history.Get()));
    }

    NotificationHandler::~NotificationHandler() {
        auto& module = Module<OutOfProc>::GetModule();
        module.DecrementObjectCount();
        SYSNOTIFY_LOG_DEBUG("Unregistering COM objects");
        module.UnregisterObjects();
        SYSNOTIFY_LOG_DEBUG("Uninitializing COM interface");
        CoUninitialize();
    }

    // https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/adaptive-interactive-toasts
    // https://learn.microsoft.com/en-us/uwp/schemas/tiles/toastschema/element-audio
    [[nodiscard]] inline auto format_content(const NotificationContent& content) -> std::wstring {
        return kstd::unicode::convert<wchar_t>(fmt::format(R"(
<toast scenario="reminder">
    <visual>
        <binding template="ToastGeneric">
            <text>{}</text>
            <text>{}</text>
        </binding>
    </visual>
    <audio src="ms-winsoundevent:Notification.Default" loop="false" />
</toast>
)",
                                                           content.title, content.description));
    }

    auto NotificationHandler::set_aumid() -> void {
        PWSTR current_aum_id = nullptr;
        if(GetCurrentProcessExplicitAppUserModelID(&current_aum_id) != S_OK) {
            SYSNOTIFY_REQUIRE_SUCCESS(SetCurrentProcessExplicitAppUserModelID(_aum_id.c_str()));
            log::info("Set up AUMID {} for current process", kstd::unicode::convert<char>(_aum_id));
            return;
        }

        SYSNOTIFY_REQUIRE(current_aum_id);
        std::wstring current_aum_id_string(current_aum_id);
        CoTaskMemFree(current_aum_id);

        if(current_aum_id_string.empty()) {
            SYSNOTIFY_REQUIRE_SUCCESS(SetCurrentProcessExplicitAppUserModelID(_aum_id.c_str()));
            log::info("Set up AUMID {} for current process", kstd::unicode::convert<char>(_aum_id));
        }
        else if(current_aum_id_string == _aum_id) {
            log::info("An AUMID '{}' was set for your process, but differs from the one ('{}') specified for Sysnotify. This is a bug, but "
                      "i'll fix it for you at runtime.",
                      kstd::unicode::convert<char>(current_aum_id_string), kstd::unicode::convert<char>(_aum_id));
            SYSNOTIFY_REQUIRE_SUCCESS(SetCurrentProcessExplicitAppUserModelID(_aum_id.c_str()));
        }
    }

    auto NotificationHandler::create_shortcut_if_needed() -> std::filesystem::path {
        const auto executable_path = get_process_path();

        SYSNOTIFY_LOG_DEBUG("Looking for registered COM server shortcut");
        std::wstring app_data_dir(MAX_PATH, 0);
        const auto app_data_dir_length = GetEnvironmentVariableW(L"APPDATA", app_data_dir.data(), MAX_PATH);
        SYSNOTIFY_REQUIRE(app_data_dir_length > 0);
        app_data_dir.resize(app_data_dir_length);

        auto shortcut_path = std::filesystem::path(app_data_dir) / "Microsoft" / "Windows" / "Start Menu" / "Programs" /
                             fmt::format("{}.lnk", kstd::unicode::convert<char>(_app_name));
        if(std::filesystem::exists(shortcut_path)) {
            log::info("Found shortcut at {}, skipping creation", shortcut_path.string());
            return shortcut_path;// We don't have to do anything if the shortcut already exists
        }
        log::info("Didn't find a shortcut, creating one at {}", shortcut_path.string());

        // Create shell link
        SYSNOTIFY_LOG_DEBUG("Creating COM shell link");
        ComPtr<IShellLinkW> shell_link {};
        SYSNOTIFY_REQUIRE_SUCCESS(CoCreateInstance(CLSID_ShellLink, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&shell_link)));
        SYSNOTIFY_REQUIRE(shell_link);
        SYSNOTIFY_REQUIRE_SUCCESS(shell_link->SetPath(executable_path.c_str()));
        SYSNOTIFY_REQUIRE_SUCCESS(shell_link->SetArguments(L""));
        SYSNOTIFY_LOG_DEBUG("Created shell link at {}", fmt::ptr(shell_link.Get()));

        // Set up property store interface
        SYSNOTIFY_LOG_DEBUG("Creating shell link property store");
        ComPtr<IPropertyStore> property_store {};
        SYSNOTIFY_REQUIRE_SUCCESS(shell_link.As(&property_store));
        SYSNOTIFY_REQUIRE(property_store);
        SYSNOTIFY_LOG_DEBUG("Created shell link property store at {}", fmt::ptr(property_store.Get()));
        PROPVARIANT property;
        SYSNOTIFY_LOG_DEBUG("Applying shell link attributes");

        // AUMID
        SYSNOTIFY_REQUIRE_SUCCESS(InitPropVariantFromString(_aum_id.c_str(), &property));
        SYSNOTIFY_REQUIRE_SUCCESS(property_store->SetValue(PKEY_AppUserModel_ID, property));

        // CLSID
        SYSNOTIFY_REQUIRE_SUCCESS(PropVariantClear(&property));
        property.vt = VT_CLSID;
        property.puuid = const_cast<CLSID*>(&ACTIVATOR_CLSID);
        SYSNOTIFY_REQUIRE_SUCCESS(property_store->SetValue(PKEY_AppUserModel_ToastActivatorCLSID, property));

        // Commit changes
        SYSNOTIFY_LOG_DEBUG("Committing changes to shell link persist file");
        SYSNOTIFY_REQUIRE_SUCCESS(property_store->Commit());

        // Save shortcut to file
        ComPtr<IPersistFile> persist_file {};
        SYSNOTIFY_REQUIRE_SUCCESS(shell_link.As(&persist_file));
        SYSNOTIFY_REQUIRE(persist_file);
        SYSNOTIFY_LOG_DEBUG("Created shell link persist file at {}", fmt::ptr(persist_file.Get()));
        SYSNOTIFY_REQUIRE_SUCCESS(persist_file->Save(shortcut_path.c_str(), TRUE));
        SYSNOTIFY_LOG_DEBUG("Created COM service shortcut at {}", shortcut_path.string());

        return shortcut_path;
    }

    auto NotificationHandler::register_com_server(const std::filesystem::path& path) -> void {
        SYSNOTIFY_LOG_DEBUG("Creating COM module");
        auto& module = Module<OutOfProc>::Create([] {
        });
        SYSNOTIFY_LOG_DEBUG("Created COM module at {}", fmt::ptr(&module));
        module.IncrementObjectCount();
        SYSNOTIFY_LOG_DEBUG("Registering COM objects");
        module.RegisterObjects();

        //OLECHAR* clsid = nullptr;
        //SYSNOTIFY_REQUIRE_SUCCESS(StringFromCLSID(ACTIVATOR_CLSID, &clsid));
        //std::wstring clsid_string(clsid);
        //CoTaskMemFree(clsid);
        //const auto sub_key = kstd::unicode::convert<wchar_t>(
        //    fmt::format(R"(SOFTWARE\Classes\CLSID\{}\LocalServer32)", kstd::unicode::convert<char>(clsid_string)));
        //
        //HKEY key {};
        //if(RegOpenKeyExW(HKEY_CURRENT_USER, sub_key.c_str(), 0, KEY_READ, &key) == ERROR_SUCCESS) {
        //    log::info("Registry key for COM activator already installed, skipping");
        //    return;// The registry key is already installed
        //}
        //
        //const auto path_string = get_process_path().wstring();
        //SYSNOTIFY_REQUIRE_SUCCESS(HRESULT_FROM_WIN32(RegSetKeyValueW(HKEY_CURRENT_USER, sub_key.c_str(), nullptr, REG_SZ,
        //                                                             path_string.c_str(), (path_string.length() + 1) * sizeof(wchar_t))));
        //log::info("Created COM activator registry key {}", kstd::unicode::convert<char>(sub_key));
    }

    auto NotificationHandler::push(const NotificationContent& content) -> GUID {
        ComPtr<IXmlDocument> document {};
        SYSNOTIFY_REQUIRE_SUCCESS(
            Windows::Foundation::ActivateInstance(HStringReference(RuntimeClass_Windows_Data_Xml_Dom_XmlDocument).Get(), &document));
        SYSNOTIFY_REQUIRE(document);
        SYSNOTIFY_LOG_DEBUG("Created XML content document at {}", fmt::ptr(document.Get()));

        ComPtr<IXmlDocumentIO> document_io {};
        SYSNOTIFY_REQUIRE_SUCCESS(document.As(&document_io));
        SYSNOTIFY_REQUIRE(document_io);
        const auto content_string = format_content(content);
        SYSNOTIFY_REQUIRE_SUCCESS(document_io->LoadXml(HStringReference(content_string.c_str()).Get()));

        ComPtr<IToastNotification> notification {};
        SYSNOTIFY_REQUIRE_SUCCESS(_factory->CreateToastNotification(document.Get(), &notification));
        SYSNOTIFY_REQUIRE(notification);
        SYSNOTIFY_LOG_DEBUG("Created toast notification at {}", fmt::ptr(notification.Get()));

        GUID tag {};
        SYSNOTIFY_REQUIRE_SUCCESS(CoCreateGuid(&tag));
        std::wstring tag_string(GUID_BUFFER_SIZE, 0);
        const auto tag_length = StringFromGUID2(tag, tag_string.data(), GUID_BUFFER_SIZE);
        SYSNOTIFY_REQUIRE(tag_length > 0);
        tag_string.resize(tag_length);
        SYSNOTIFY_LOG_DEBUG("Created notification tag GUID {}", kstd::unicode::convert<char>(tag_string));

        ComPtr<IToastNotification2> notification2 {};
        SYSNOTIFY_REQUIRE_SUCCESS(notification.As(&notification2));
        notification2->put_Tag(HStringReference(tag_string.c_str()).Get());
        SYSNOTIFY_LOG_DEBUG("Updated notification tag");

        SYSNOTIFY_REQUIRE_SUCCESS(_notifier->Show(notification.Get()));

        return tag;
    }

    auto NotificationHandler::get_notification(const GUID& tag) -> ComPtr<IToastNotification> {
        ComPtr<IToastNotificationHistory2> history2 {};
        SYSNOTIFY_REQUIRE_SUCCESS(_history.As(&history2));
        SYSNOTIFY_REQUIRE(history2);

        ComPtr<IVectorView<ToastNotification*>> notifications {};
        SYSNOTIFY_REQUIRE_SUCCESS(history2->GetHistoryWithId(HStringReference(_aum_id.c_str()).Get(), &notifications));
        SYSNOTIFY_REQUIRE(notifications);

        uint32_t notification_count = 0;
        SYSNOTIFY_REQUIRE_SUCCESS(notifications->get_Size(&notification_count));
        if(notification_count == 0) {
            return nullptr;
        }

        for(uint32_t index = 0; index < notification_count; ++index) {
            ComPtr<IToastNotification> notification {};
            SYSNOTIFY_REQUIRE_SUCCESS(notifications->GetAt(index, &notification));
            SYSNOTIFY_REQUIRE(notification);

            ComPtr<IToastNotification2> notification2 {};
            SYSNOTIFY_REQUIRE_SUCCESS(notification.As(&notification2));
            SYSNOTIFY_REQUIRE(notification2);

            HSTRING tag_string = nullptr;
            SYSNOTIFY_REQUIRE_SUCCESS(notification2->get_Tag(&tag_string));
            SYSNOTIFY_REQUIRE(tag_string);

            uint32_t tag_length = 0;
            const auto* tag_string_data = WindowsGetStringRawBuffer(tag_string, &tag_length);
            SYSNOTIFY_REQUIRE(tag_length > 0);

            GUID current_tag {};
            SYSNOTIFY_REQUIRE_SUCCESS(IIDFromString(tag_string_data, &current_tag));
            if(tag != current_tag) {
                continue;
            }

            return notification;
        }
        return nullptr;
    }

    auto NotificationHandler::update(const GUID& tag, const NotificationContent& content) -> void {
        auto notification = get_notification(tag);

        ComPtr<IXmlDocument> document {};
        SYSNOTIFY_REQUIRE_SUCCESS(notification->get_Content(&document));
        SYSNOTIFY_REQUIRE(document);

        SYSNOTIFY_REQUIRE_SUCCESS(_notifier->Show(notification.Get()));

        // TODO: ...
    }

    auto NotificationHandler::pop(const GUID& tag) -> void {
        SYSNOTIFY_REQUIRE_SUCCESS(_notifier->Hide(get_notification(tag).Get()));
    }
}// namespace sysnotify