// ******************************************************************
// Copyright (c) Microsoft. All rights reserved.
// This code is licensed under the MIT License (MIT).
// THE CODE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
// INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
// IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
// DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
// TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH
// THE CODE OR THE USE OR OTHER DEALINGS IN THE CODE.
// ******************************************************************

/**
 * Modified to remove redundant UWP checks/integration since this code will
 * never run as a UWP app anyways. Also changed some string-dependant function macros
 * to their wide-string counterparts as we never use ANSI strings here in Windows land.
 * If the code style is questionable, it is probably original Microsoft reference code.
 *
 * @author Alexander Hinze
 * @since 11/06/24
 */

#include "DesktopNotificationManagerCompat.h"
#include <wrl\wrappers\corewrappers.h>

#define RETURN_IF_FAILED(hr)  \
    do {                      \
        HRESULT _hrTemp = hr; \
        if(FAILED(_hrTemp)) { \
            return _hrTemp;   \
        }                     \
    } while(false)

using namespace ABI::Windows::Data::Xml::Dom;
using namespace Microsoft::WRL;
using namespace Microsoft::WRL::Wrappers;

namespace DesktopNotificationManagerCompat {
    HRESULT RegisterComServer(GUID clsid, const wchar_t exePath[]);

    HRESULT EnsureRegistered();

    bool s_registeredComServer = false;
    bool s_registeredActivator = false;

    HRESULT RegisterComServer(GUID clsid) {
        // Get the EXE path
        wchar_t exePath[MAX_PATH];
        // EDIT: changed call from GetModuleFileName macro to GetModuleFileNameW since we always use a wide string here.. - AH
        DWORD charWritten = ::GetModuleFileNameW(nullptr, exePath, ARRAYSIZE(exePath));
        RETURN_IF_FAILED(charWritten > 0 ? S_OK : HRESULT_FROM_WIN32(::GetLastError()));

        // Register the COM server
        RETURN_IF_FAILED(RegisterComServer(clsid, exePath));
        return S_OK;
    }

    HRESULT RegisterActivator() {
        // Module<OutOfProc> needs a callback registered before it can be used.
        // Since we don't care about when it shuts down, we'll pass an empty lambda here.
        Module<OutOfProc>::Create([] {
        });

        // If a local server process only hosts the COM object then COM expects
        // the COM server host to shutdown when the references drop to zero.
        // Since the user might still be using the program after activating the notification,
        // we don't want to shutdown immediately.  Incrementing the object count tells COM that
        // we aren't done yet.
        Module<OutOfProc>::GetModule().IncrementObjectCount();

        RETURN_IF_FAILED(Module<OutOfProc>::GetModule().RegisterObjects());
        s_registeredActivator = true;
        return S_OK;
    }

    HRESULT RegisterComServer(GUID clsid, const wchar_t exePath[]) {
        // Turn the GUID into a string
        OLECHAR* clsidOlechar;
        StringFromCLSID(clsid, &clsidOlechar);
        std::wstring clsidStr(clsidOlechar);
        ::CoTaskMemFree(clsidOlechar);

        // Create the subkey
        // Something like SOFTWARE\Classes\CLSID\{23A5B06E-20BB-4E7E-A0AC-6982ED6A6041}\LocalServer32
        std::wstring subKey = LR"(SOFTWARE\Classes\CLSID\)" + clsidStr + LR"(\LocalServer32)";

        // Include -ToastActivated launch args on the exe
        std::wstring exePathStr(exePath);
        exePathStr = L"\"" + exePathStr + L"\" " + TOAST_ACTIVATED_LAUNCH_ARG;

        // We don't need to worry about overflow here as ::GetModuleFileName won't
        // return anything bigger than the max file system path (much fewer than max of DWORD).
        DWORD dataSize = static_cast<DWORD>((exePathStr.length() + 1) * sizeof(WCHAR));

        // Register the EXE for the COM server
        // EDIT: changed call from RegSetKeyValue macro to RegSetKeyValueW since we always use a wide string here.. - AH
        return HRESULT_FROM_WIN32(::RegSetKeyValueW(HKEY_CURRENT_USER, subKey.c_str(), nullptr, REG_SZ,
                                                    reinterpret_cast<const BYTE*>(exePathStr.c_str()), dataSize));
    }

    HRESULT CreateToastNotifier(IToastNotifier** notifier) {
        RETURN_IF_FAILED(EnsureRegistered());
        ComPtr<IToastNotificationManagerStatics> toastStatics;
        RETURN_IF_FAILED(Windows::Foundation::GetActivationFactory(
            HStringReference(RuntimeClass_Windows_UI_Notifications_ToastNotificationManager).Get(), &toastStatics));
        return toastStatics->CreateToastNotifier(notifier);
    }

    HRESULT CreateXmlDocumentFromString(const wchar_t* xmlString, IXmlDocument** doc) {
        ComPtr<IXmlDocument> answer;
        RETURN_IF_FAILED(
            Windows::Foundation::ActivateInstance(HStringReference(RuntimeClass_Windows_Data_Xml_Dom_XmlDocument).Get(), &answer));

        ComPtr<IXmlDocumentIO> docIO;
        RETURN_IF_FAILED(answer.As(&docIO));

        // Load the XML string
        RETURN_IF_FAILED(docIO->LoadXml(HStringReference(xmlString).Get()));

        return answer.CopyTo(doc);
    }

    HRESULT CreateToastNotification(IXmlDocument* content, IToastNotification** notification) {
        ComPtr<IToastNotificationFactory> factory;
        RETURN_IF_FAILED(Windows::Foundation::GetActivationFactory(
            HStringReference(RuntimeClass_Windows_UI_Notifications_ToastNotification).Get(), &factory));

        return factory->CreateToastNotification(content, notification);
    }

    HRESULT get_History(std::unique_ptr<DesktopNotificationHistoryCompat>* history) {
        RETURN_IF_FAILED(EnsureRegistered());

        ComPtr<IToastNotificationManagerStatics> toastStatics;
        RETURN_IF_FAILED(Windows::Foundation::GetActivationFactory(
            HStringReference(RuntimeClass_Windows_UI_Notifications_ToastNotificationManager).Get(), &toastStatics));

        ComPtr<IToastNotificationManagerStatics2> toastStatics2;
        RETURN_IF_FAILED(toastStatics.As(&toastStatics2));

        ComPtr<IToastNotificationHistory> nativeHistory;
        RETURN_IF_FAILED(toastStatics2->get_History(&nativeHistory));

        *history = std::make_unique<DesktopNotificationHistoryCompat>(nativeHistory);
        return S_OK;
    }

    HRESULT EnsureRegistered() {
        // If not registered activator yet
        if(!s_registeredActivator) {
            // Incorrect usage, must call RegisterActivator first
            return E_ILLEGAL_METHOD_CALL;
        }

        return S_OK;
    }
}// namespace DesktopNotificationManagerCompat

DesktopNotificationHistoryCompat::DesktopNotificationHistoryCompat(ComPtr<IToastNotificationHistory> history) {
    m_history = history;
}

HRESULT DesktopNotificationHistoryCompat::Clear() {
    return m_history->Clear();
}

HRESULT DesktopNotificationHistoryCompat::GetHistory(ABI::Windows::Foundation::Collections::IVectorView<ToastNotification*>** toasts) {
    ComPtr<IToastNotificationHistory2> history2;
    RETURN_IF_FAILED(m_history.As(&history2));
    return history2->GetHistory(toasts);
}

HRESULT DesktopNotificationHistoryCompat::Remove(const wchar_t* tag) {
    return m_history->Remove(HStringReference(tag).Get());
}

HRESULT DesktopNotificationHistoryCompat::RemoveGroupedTag(const wchar_t* tag, const wchar_t* group) {
    return m_history->RemoveGroupedTag(HStringReference(tag).Get(), HStringReference(group).Get());
}

HRESULT DesktopNotificationHistoryCompat::RemoveGroup(const wchar_t* group) {
    return m_history->RemoveGroup(HStringReference(group).Get());
}