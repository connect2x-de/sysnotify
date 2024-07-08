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

#include "default_notification_handler.hpp"

#include <NotificationActivationCallback.h>
#include <ShlObj.h>
#include <propkey.h>
#include <propvarutil.h>
#include <roapi.h>
#include <shobjidl_core.h>
#include <wincodec.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Graphics.Imaging.h>
#include <winrt/Windows.Storage.Streams.h>
#include <winrt/Windows.Storage.h>
#include <winrt/Windows.UI.Notifications.h>
#include <wrl/event.h>
#include <wrl/implements.h>
#include <wrl/module.h>
#include <wrl/wrappers/corewrappers.h>

#include "log.hpp"

namespace sysnotify {
    // Windows foundation library
    namespace wfl {
        using namespace Windows::Foundation;
    }

    // Windows runtime (not WRL)
    namespace wrt {
        using namespace winrt::Windows;
        using namespace winrt::Windows::Storage;
        using namespace winrt::Windows::Storage::Streams;
        using namespace winrt::Windows::Graphics::Imaging;
        using namespace winrt::Windows::Foundation;
        using namespace winrt::Windows::UI::Notifications;
    }// namespace wrt

    // COM objects

    class __declspec(uuid("54BF154E-5239-42FA-B80C-6E405A0F413E")) NotificationActivator final
        : public wrl::RuntimeClass<wrl::RuntimeClassFlags<wrl::ClassicCom>, INotificationActivationCallback> {
        long _ref_count = 0;

    public:
        [[nodiscard]] virtual HRESULT __stdcall Activate(LPCWSTR aum_id,
                                                         LPCWSTR invoked_args,
                                                         const NOTIFICATION_USER_INPUT_DATA* data,
                                                         ULONG data_count) override {
            /*
             * This COM object is a dummy right now and might be used to implement
             * additional functionality in the future, such as cold-launching.
             */
            return S_OK;
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

    DefaultNotificationHandler::DefaultNotificationHandler(const std::string& app_name, const std::string& aum_id)
        : NotificationHandler()
        , _app_name(kstd::unicode::convert<wchar_t>(app_name))
        , _aum_id(kstd::unicode::convert<wchar_t>(aum_id)) {
        SYSNOTIFY_LOG_DEBUG("Initializing COM interface");
        SYSNOTIFY_REQUIRE_SUCCESS(CoInitializeEx(nullptr, COINIT::COINIT_MULTITHREADED));
        if(!is_packaged_app()) {
            create_shortcut_if_needed();
        }

        SYSNOTIFY_LOG_DEBUG("Creating COM module");
        auto& module = wrl::Module<wrl::OutOfProc>::Create([] {
        });
        SYSNOTIFY_LOG_DEBUG("Created COM module at {}", fmt::ptr(&module));
        module.IncrementObjectCount();
        SYSNOTIFY_LOG_DEBUG("Registering COM objects");
        module.RegisterObjects();
        set_aumid();

        // Create notifier
        SYSNOTIFY_LOG_DEBUG("Creating toast notifier instance");
        SYSNOTIFY_REQUIRE_SUCCESS(wfl::GetActivationFactory(
            wrl::HStringReference(RuntimeClass_Windows_UI_Notifications_ToastNotificationManager).Get(), &_manager));
        SYSNOTIFY_REQUIRE(_manager);
        SYSNOTIFY_REQUIRE_SUCCESS(_manager->CreateToastNotifierWithId(wrl::HStringReference(_aum_id.c_str()).Get(), &_notifier));
        SYSNOTIFY_LOG_DEBUG("Created toast notifier at {}", fmt::ptr(_notifier.Get()));

        // Create factory
        SYSNOTIFY_REQUIRE_SUCCESS(
            wfl::GetActivationFactory(wrl::HStringReference(RuntimeClass_Windows_UI_Notifications_ToastNotification).Get(), &_factory));
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

    DefaultNotificationHandler::~DefaultNotificationHandler() {
        auto& module = wrl::Module<wrl::OutOfProc>::GetModule();
        module.DecrementObjectCount();
        SYSNOTIFY_LOG_DEBUG("Unregistering COM objects");
        module.UnregisterObjects();
        SYSNOTIFY_LOG_DEBUG("Uninitializing COM interface");
        CoUninitialize();
    }

    auto DefaultNotificationHandler::format_image(const GUID& tag, const std::optional<std::filesystem::path>& icon_path) -> std::string {
        if(!icon_path.has_value()) {
            return "";
        }
        const auto guid_string = guid_to_string(tag);
        if(is_packaged_app()) {
            return fmt::format(R"(<image placement="appLogoOverride" src="ms-appdata:///local/{}" hint-crop="circle" />)", icon_path.value().filename().string());
        }
        return fmt::format(R"(<image placement="appLogoOverride" src="file:///{}" hint-crop="circle" />)", icon_path.value().string());
    }

    // https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/adaptive-interactive-toasts
    // https://learn.microsoft.com/en-us/uwp/schemas/tiles/toastschema/element-audio
    auto DefaultNotificationHandler::format_content(const GUID& tag,
                                                    const NotificationContent& content,
                                                    const std::optional<std::filesystem::path>& icon_path) -> std::wstring {
        return kstd::unicode::convert<wchar_t>(fmt::format(R"(
<toast>
    <visual>
        <binding template="ToastGeneric">
            <text>{}</text>
            <text>{}</text>
            {}
        </binding>
    </visual>
    <audio src="ms-winsoundevent:Notification.Default" loop="false" />
</toast>
)",
                                                           content.title, content.description, format_image(tag, icon_path)));
    }

    auto DefaultNotificationHandler::set_aumid() -> void {
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

    auto DefaultNotificationHandler::create_shortcut_if_needed() -> void {
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
            return;// We don't have to do anything if the shortcut already exists
        }
        log::info("Didn't find a shortcut, creating one at {}", shortcut_path.string());

        // Create shell link
        SYSNOTIFY_LOG_DEBUG("Creating COM shell link");
        wrl::ComPtr<IShellLinkW> shell_link {};
        SYSNOTIFY_REQUIRE_SUCCESS(CoCreateInstance(CLSID_ShellLink, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&shell_link)));
        SYSNOTIFY_REQUIRE(shell_link);
        SYSNOTIFY_REQUIRE_SUCCESS(shell_link->SetPath(executable_path.c_str()));
        SYSNOTIFY_REQUIRE_SUCCESS(shell_link->SetArguments(L""));
        SYSNOTIFY_LOG_DEBUG("Created shell link at {}", fmt::ptr(shell_link.Get()));

        // Set up property store interface
        SYSNOTIFY_LOG_DEBUG("Creating shell link property store");
        wrl::ComPtr<IPropertyStore> property_store {};
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
        wrl::ComPtr<IPersistFile> persist_file {};
        SYSNOTIFY_REQUIRE_SUCCESS(shell_link.As(&persist_file));
        SYSNOTIFY_REQUIRE(persist_file);
        SYSNOTIFY_LOG_DEBUG("Created shell link persist file at {}", fmt::ptr(persist_file.Get()));
        SYSNOTIFY_REQUIRE_SUCCESS(persist_file->Save(shortcut_path.c_str(), TRUE));
        SYSNOTIFY_LOG_DEBUG("Created COM service shortcut at {}", shortcut_path.string());
    }

    auto DefaultNotificationHandler::create_content_document(const GUID& tag,
                                                             const NotificationContent& content,
                                                             const std::optional<std::filesystem::path>& icon_path)
        -> wrl::ComPtr<abi::IXmlDocument> {
        wrl::ComPtr<abi::IXmlDocument> document {};
        SYSNOTIFY_REQUIRE_SUCCESS(
            wfl::ActivateInstance(wrl::HStringReference(RuntimeClass_Windows_Data_Xml_Dom_XmlDocument).Get(), &document));
        SYSNOTIFY_REQUIRE(document);
        SYSNOTIFY_LOG_DEBUG("Created XML content document at {}", fmt::ptr(document.Get()));

        wrl::ComPtr<abi::IXmlDocumentIO> document_io {};
        SYSNOTIFY_REQUIRE_SUCCESS(document.As(&document_io));
        SYSNOTIFY_REQUIRE(document_io);
        const auto content_string = format_content(tag, content, icon_path);
        SYSNOTIFY_REQUIRE_SUCCESS(document_io->LoadXml(wrl::HStringReference(content_string.c_str()).Get()));

        return document;
    }

    [[nodiscard]] inline auto swizzle_icon(const uint32_t width, const uint32_t height, const void* data) -> std::vector<uint8_t> {
        const auto pixel_count = width * height;
        const auto* src_data = reinterpret_cast<const uint32_t*>(data);
        std::vector<uint8_t> buffer(pixel_count << 2);
        for(uint32_t i = 0; i < pixel_count; ++i) {
            const auto pixel = src_data[i];
            const auto index = i << 2;
            buffer[index] = static_cast<uint8_t>(pixel >> 16);
            buffer[index + 1] = static_cast<uint8_t>(pixel >> 8);
            buffer[index + 2] = static_cast<uint8_t>(pixel & 0xFFU);
            buffer[index + 3] = static_cast<uint8_t>(pixel >> 24);
        }
        return buffer;
    }

    auto DefaultNotificationHandler::make_icon_if_present(const NotificationContent& content,
                                                          const GUID& tag) -> std::optional<std::filesystem::path> {
        const auto width = content.icon_width;
        const auto height = content.icon_height;
        if(width <= 0 || height <= 0) {
            return std::nullopt;// No icon if width or height are leq 0..
        }
        const auto* icon_data_src = content.icon;
        if(icon_data_src == nullptr) {
            return std::nullopt;// No icon if the data pointer is null..
        }

        // Create directory structure
        std::wstring app_data_dir(MAX_PATH, 0);
        const auto app_data_dir_length = GetEnvironmentVariableW(L"LOCALAPPDATA", app_data_dir.data(), MAX_PATH);
        SYSNOTIFY_REQUIRE(app_data_dir_length > 0);
        app_data_dir.resize(app_data_dir_length);

        const auto tag_string = guid_to_string(tag);
        auto file_path = std::filesystem::path(app_data_dir) / "Packages" / _aum_id / "LocalState" /
                               fmt::format("{}.png", tag_string.substr(1, tag_string.length() - 2));
        SYSNOTIFY_LOG_DEBUG("Calculated image path {} for notification {}", file_path.string(), guid_to_string(tag));
        const auto parent_path = file_path.parent_path();
        if(!std::filesystem::exists(parent_path)) {
            std::filesystem::create_directories(parent_path);
        }
        if(std::filesystem::exists(file_path)) {
            std::filesystem::remove(file_path);
        }

        // Create imaging factory
        wrl::ComPtr<IWICImagingFactory> factory {};
        SYSNOTIFY_REQUIRE_SUCCESS(CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER, IID_PPV_ARGS(&factory)));
        SYSNOTIFY_REQUIRE(factory);

        // Create stream
        wrl::ComPtr<IWICStream> stream {};
        SYSNOTIFY_REQUIRE_SUCCESS(factory->CreateStream(&stream));
        SYSNOTIFY_REQUIRE(stream);
        SYSNOTIFY_REQUIRE_SUCCESS(stream->InitializeFromFilename(file_path.c_str(), GENERIC_WRITE));

        // Create encoder
        wrl::ComPtr<IWICBitmapEncoder> encoder {};
        SYSNOTIFY_REQUIRE_SUCCESS(factory->CreateEncoder(GUID_ContainerFormatPng, nullptr, &encoder));
        SYSNOTIFY_REQUIRE(encoder);
        SYSNOTIFY_REQUIRE_SUCCESS(encoder->Initialize(stream.Get(), WICBitmapEncoderNoCache));

        // Create encode frame
        wrl::ComPtr<IWICBitmapFrameEncode> frame {};
        SYSNOTIFY_REQUIRE_SUCCESS(encoder->CreateNewFrame(&frame, nullptr));
        SYSNOTIFY_REQUIRE(frame);
        SYSNOTIFY_REQUIRE_SUCCESS(frame->Initialize(nullptr));
        SYSNOTIFY_REQUIRE_SUCCESS(frame->SetSize(width, height));
        auto format = GUID_WICPixelFormat32bppRGBA;
        SYSNOTIFY_REQUIRE_SUCCESS(frame->SetPixelFormat(&format));
        const auto swizzled_icon_data = swizzle_icon(width, height, icon_data_src);
        SYSNOTIFY_REQUIRE_SUCCESS(frame->WritePixels(height, static_cast<uint32_t>(width) << 2, (static_cast<uint32_t>(width) * height) << 2,
                                                     const_cast<BYTE*>(reinterpret_cast<const BYTE*>(swizzled_icon_data.data()))));
        SYSNOTIFY_REQUIRE_SUCCESS(frame->Commit());

        // Flush encoder and stream
        SYSNOTIFY_REQUIRE_SUCCESS(encoder->Commit());
        SYSNOTIFY_REQUIRE_SUCCESS(stream->Commit(STGC_DEFAULT));

        return file_path;
    }

    auto DefaultNotificationHandler::register_actions(wrl::ComPtr<abi::IToastNotification>& notification,
                                                      const GUID& tag,
                                                      const NotificationContent& content) -> void {
        // Set up event callbacks
        using ActivationCallback = wrt::TypedEventHandler<wrt::ToastNotification, wrt::IInspectable>;
        using DismissCallback = wrt::TypedEventHandler<wrt::ToastNotification, wrt::ToastDismissedEventArgs>;
        using FailCallback = wrt::TypedEventHandler<wrt::ToastNotification, wrt::ToastFailedEventArgs>;

        EventRegistrationToken activate_event_token {};
        EventRegistrationToken dismiss_event_token {};
        EventRegistrationToken fail_event_token {};

        /**
         * This deserves a small explanation as the code is a little bit convoluted;
         *
         * We inject lambda-based callbacks into the notification itself to avoid
         * using the provided COM object for click callbacks, since this allows us
         * to have a lot more granular control over how the toast is handled by the caller.
         *
         * We create a Windows Runtime based implementation of an ITypedEventHandler<IToastNotification, _>
         * and grab its underlying COM address to cast it to the desired interface type, similar to a
         * call to QueryInterface. The very long type name in the as<>() call is the ABI type of the
         * event handler implementation generated by Windows internal IDL (i suppose since the names are horrible?).
         *
         * The actual callback lambda copies the address of the notification's action function by value,
         * as well as the provided user_data (in the sn_notification_content structure) alongside a copy
         * of the this-pointer of the current NotificationHandler instance to allow accessing the _tags map.
         */

        // clang-format off
        if(content.activate_action != nullptr) {
            SYSNOTIFY_LOG_DEBUG("Injecting activate callback into notification {}", guid_to_string(tag));
            notification->add_Activated(ActivationCallback(
                [action = content.activate_action, user_data = content.user_data, tag, this]
                (const wrt::ToastNotification& n, const wrt::IInspectable& args){
                    SYSNOTIFY_LOG_DEBUG("Activated notification {}", guid_to_string(tag));
                    action(reinterpret_cast<const sn_notification_tag*>(&tag), user_data);
                    _tags.erase(tag); // If the toast is activated, it is removed
                    _cancelled.erase(tag); // If this was cancelled, remove it
                }).as<__FITypedEventHandler_2_Windows__CUI__CNotifications__CToastNotification_IInspectable>().get(),
                    &activate_event_token);
        }

        if(content.dismiss_action != nullptr) {
            SYSNOTIFY_LOG_DEBUG("Injecting dismiss callback into notification {}", guid_to_string(tag));
            notification->add_Dismissed(DismissCallback(
                [dismiss_action = content.dismiss_action, remove_action = content.remove_action, user_data = content.user_data, tag, this]
                (const wrt::ToastNotification& n, const wrt::ToastDismissedEventArgs& args){
                    sn_notification_dismissal_reason reason = -1;
                    switch(args.Reason()) {
                        case wrt::ToastDismissalReason::UserCanceled:
                            reason = SYSNOTIFY_DISMISSAL_REASON_USER_CANCELED;
                            break;
                        case wrt::ToastDismissalReason::ApplicationHidden:
                            reason = SYSNOTIFY_DISMISSAL_REASON_APP_HIDDEN;
                            break;
                        case wrt::ToastDismissalReason::TimedOut:
                            reason = SYSNOTIFY_DISMISSAL_REASON_TIMED_OUT;
                            break;
                    }
                    SYSNOTIFY_LOG_DEBUG("Dismissed notification {} ({})", guid_to_string(tag), reason);
                    if(_cancelled.find(tag) != _cancelled.cend()) {
                        remove_action(reinterpret_cast<const sn_notification_tag*>(&tag), user_data);
                        _cancelled.erase(tag);
                        _tags.erase(tag);
                        SYSNOTIFY_LOG_DEBUG("Notification {} was destroyed", guid_to_string(tag));
                        return;
                    }
                    dismiss_action(reinterpret_cast<const sn_notification_tag*>(&tag), user_data, reason);
                    _cancelled.insert(tag);
                }).as<__FITypedEventHandler_2_Windows__CUI__CNotifications__CToastNotification_Windows__CUI__CNotifications__CToastDismissedEventArgs>().get(),
                    &dismiss_event_token);
        }

        if(content.fail_action != nullptr) {
            SYSNOTIFY_LOG_DEBUG("Injecting fail callback into notification {}", guid_to_string(tag));
            notification->add_Failed(FailCallback(
                [action = content.fail_action, user_data = content.user_data, tag, this]
                (const wrt::ToastNotification& n, const wrt::ToastFailedEventArgs& args){
                    SYSNOTIFY_LOG_DEBUG("Failed notification {}", guid_to_string(tag));
                    action(reinterpret_cast<const sn_notification_tag*>(&tag), user_data);
                    _tags.erase(tag); // If the toast has failed, we need to remove it too
                    _cancelled.erase(tag); // If this was cancelled, remove it
                }).as<__FITypedEventHandler_2_Windows__CUI__CNotifications__CToastNotification_Windows__CUI__CNotifications__CToastFailedEventArgs>().get(),
                    &fail_event_token);
        }
        // clang-format on
    }

    auto DefaultNotificationHandler::push(const NotificationContent& content) -> GUID {
        GUID tag {};
        SYSNOTIFY_REQUIRE_SUCCESS(CoCreateGuid(&tag));
        const auto tag_string = guid_to_wstring(tag);
        SYSNOTIFY_LOG_DEBUG("Created notification tag GUID {}", kstd::unicode::convert<char>(tag_string));

        auto icon_path = make_icon_if_present(content, tag);

        wrl::ComPtr<abi::IToastNotification> notification {};
        SYSNOTIFY_REQUIRE_SUCCESS(_factory->CreateToastNotification(create_content_document(tag, content, icon_path).Get(), &notification));
        SYSNOTIFY_REQUIRE(notification);
        SYSNOTIFY_LOG_DEBUG("Created toast notification at {}", fmt::ptr(notification.Get()));

        wrl::ComPtr<abi::IToastNotification2> notification2 {};
        SYSNOTIFY_REQUIRE_SUCCESS(notification.As(&notification2));
        notification2->put_Tag(wrl::HStringReference(tag_string.c_str()).Get());
        SYSNOTIFY_LOG_DEBUG("Updated notification tag");

        register_actions(notification, tag, content);

        // Add to internal map and display toast
        _tags[tag] = {tag, std::move(icon_path)};
        SYSNOTIFY_REQUIRE_SUCCESS(_notifier->Show(notification.Get()));

        return tag;
    }

    auto DefaultNotificationHandler::get_notification(const GUID& tag) -> wrl::ComPtr<abi::IToastNotification> {
        wrl::ComPtr<abi::IToastNotificationHistory2> history2 {};
        SYSNOTIFY_REQUIRE_SUCCESS(_history.As(&history2));
        SYSNOTIFY_REQUIRE(history2);

        wrl::ComPtr<abi::IVectorView<abi::ToastNotification*>> notifications {};
        SYSNOTIFY_REQUIRE_SUCCESS(history2->GetHistoryWithId(wrl::HStringReference(_aum_id.c_str()).Get(), &notifications));
        SYSNOTIFY_REQUIRE(notifications);

        uint32_t notification_count = 0;
        SYSNOTIFY_REQUIRE_SUCCESS(notifications->get_Size(&notification_count));
        if(notification_count == 0) {
            return nullptr;
        }

        for(uint32_t index = 0; index < notification_count; ++index) {
            wrl::ComPtr<abi::IToastNotification> notification {};
            SYSNOTIFY_REQUIRE_SUCCESS(notifications->GetAt(index, &notification));
            SYSNOTIFY_REQUIRE(notification);

            wrl::ComPtr<abi::IToastNotification2> notification2 {};
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

    auto DefaultNotificationHandler::update(const GUID& tag, const NotificationContent& content) -> void {
        auto notification = get_notification(tag);
        wrl::ComPtr<abi::IXmlDocument> document {};
        SYSNOTIFY_REQUIRE_SUCCESS(notification->get_Content(&document));
        SYSNOTIFY_REQUIRE(document);
        SYSNOTIFY_REQUIRE(_tags.find(tag) != _tags.cend());
        SYSNOTIFY_REQUIRE_SUCCESS(create_content_document(tag, content, make_icon_if_present(content, tag)).CopyTo(&document));
        SYSNOTIFY_REQUIRE_SUCCESS(_notifier->Show(notification.Get()));
    }

    auto DefaultNotificationHandler::pop(const GUID& tag) -> void {
        SYSNOTIFY_REQUIRE_SUCCESS(_notifier->Hide(get_notification(tag).Get()));
    }

    auto DefaultNotificationHandler::is_valid_tag(const GUID& tag) -> bool {
        return _tags.contains(tag);
    }
}// namespace sysnotify