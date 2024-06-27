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
 * @since 24/06/24
 */

#include "sysnotify.h"
#include "default_notification_handler.hpp"
#include "fallback_notification_handler.hpp"
#include "log.hpp"

#define SYSNOTIFY_HANDLE_EXCEPTIONS(x)              \
    do {                                            \
        try {                                       \
            x                                       \
        }                                           \
        catch(const std::exception& error) {        \
            sysnotify::g_last_error = error.what(); \
            return SYSNOTIFY_ERR_EXCEPTION;         \
        }                                           \
    } while(false)

namespace sysnotify {
    // Ensure our type sizes match at compile time
    static_assert(sizeof(sn_notification_tag) == sizeof(GUID));

    namespace {
        std::string g_last_error = "";
    }
}// namespace sysnotify

extern "C" {

SYSNOTIFY_API sn_error sysnotify_handler_create(const char* app_name,
                                                const char* aum_id,
                                                sn_notification_handler* handler) {
    using namespace sysnotify;
    if(app_name == nullptr || aum_id == nullptr || handler == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    SYSNOTIFY_HANDLE_EXCEPTIONS({
        DWORD major = 0;
        DWORD minor = 0;
        DWORD patch = 0;
        get_kernel_version(&major, &minor, &patch);
        SYSNOTIFY_LOG_DEBUG("Detected Windows kernel version {}.{}.{}", major, minor, patch);
        if(major >= 10) {
            *handler = reinterpret_cast<sn_notification_handler>(new DefaultNotificationHandler(app_name, aum_id));
            return SYSNOTIFY_OK;
        }
        *handler = reinterpret_cast<sn_notification_handler>(new FallbackNotificationHandler());
    });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_handler_destroy(sn_notification_handler handler) {
    using namespace sysnotify;
    if(handler == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    SYSNOTIFY_HANDLE_EXCEPTIONS({ delete reinterpret_cast<NotificationHandler*>(handler); });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_handler_push(sn_notification_handler handler,
                                              const sn_notification_content* content,
                                              sn_notification_tag* notification) {
    using namespace sysnotify;
    if(handler == nullptr || content == nullptr || notification == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    SYSNOTIFY_HANDLE_EXCEPTIONS({
        const auto tag = reinterpret_cast<NotificationHandler*>(handler)->push(*content);
        *notification = *reinterpret_cast<const sn_notification_tag*>(&tag);
    });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_handler_update(sn_notification_handler handler,
                                                const sn_notification_tag* notification,
                                                const sn_notification_content* content) {
    using namespace sysnotify;
    if(handler == nullptr || notification == nullptr || content == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    SYSNOTIFY_HANDLE_EXCEPTIONS(
        { reinterpret_cast<NotificationHandler*>(handler)->update(*reinterpret_cast<const GUID*>(notification), *content); });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_handler_pop(sn_notification_handler handler, const sn_notification_tag* notification) {
    using namespace sysnotify;
    if(handler == nullptr || notification == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    SYSNOTIFY_HANDLE_EXCEPTIONS({ reinterpret_cast<NotificationHandler*>(handler)->pop(*reinterpret_cast<const GUID*>(notification)); });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_handler_is_valid_tag(sn_notification_handler handler, const sn_notification_tag* tag, sn_bool* is_valid) {
    using namespace sysnotify;
    if(handler == nullptr || tag == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    SYSNOTIFY_HANDLE_EXCEPTIONS({
        *is_valid = static_cast<sn_bool>(
            reinterpret_cast<NotificationHandler*>(handler)->is_valid_tag(*reinterpret_cast<const GUID*>(tag)));
    });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API const char* sysnotify_get_last_error() {
    using namespace sysnotify;
    return g_last_error.c_str();
}
}