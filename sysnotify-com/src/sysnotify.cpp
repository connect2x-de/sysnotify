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
#include "notification_handler.hpp"

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
    namespace {
        std::string g_last_error = "";
    }

    [[nodiscard]] auto make_content(const sn_notification_content* content) noexcept -> NotificationContent {
        const size_t icon_size = (content->icon_width * content->icon_height) << 2;
        std::vector<uint8_t> icon_data(icon_size, 0);
        memcpy(icon_data.data(), content->icon, icon_size);
        return {
            .title = content->title,
            .description = content->description,
            .icon = std::move(icon_data),
            .action = [action = content->action]{ action(); }
        };
    }
}// namespace sysnotify

extern "C" {

SYSNOTIFY_API sn_error sysnotify_handler_create(const char* app_name,
                                                const char* aum_id,
                                                sn_bool create_shortcut,
                                                sn_notification_handler* handler) {
    if(app_name == nullptr || aum_id == nullptr || handler == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    SYSNOTIFY_HANDLE_EXCEPTIONS({
        *handler = reinterpret_cast<sn_notification_handler>(
            new sysnotify::NotificationHandler(app_name, aum_id, static_cast<bool>(create_shortcut)));
    });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_handler_destroy(sn_notification_handler handler) {
    if(handler == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    SYSNOTIFY_HANDLE_EXCEPTIONS({ delete reinterpret_cast<sysnotify::NotificationHandler*>(handler); });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_handler_push(sn_notification_handler handler,
                                              const sn_notification_content* content,
                                              sn_notification* notification) {
    if(handler == nullptr || content == nullptr || notification == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    SYSNOTIFY_HANDLE_EXCEPTIONS({
        auto* handle = new GUID();
        *handle = reinterpret_cast<sysnotify::NotificationHandler*>(handler)->push(sysnotify::make_content(content));
        *notification = reinterpret_cast<sn_notification>(handle);
    });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_handler_update(sn_notification_handler handler,
                                                sn_notification notification,
                                                const sn_notification_content* content) {
    if(handler == nullptr || notification == nullptr || content == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    SYSNOTIFY_HANDLE_EXCEPTIONS({
        reinterpret_cast<sysnotify::NotificationHandler*>(handler)->update(*reinterpret_cast<GUID*>(notification),
                                                                           sysnotify::make_content(content));
    });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API sn_error sysnotify_handler_pop(sn_notification_handler handler, sn_notification notification) {
    if(handler == nullptr || notification == nullptr) {
        return SYSNOTIFY_ERR_INVALID_ARG;
    }
    SYSNOTIFY_HANDLE_EXCEPTIONS({
        auto* handle = reinterpret_cast<GUID*>(notification);
        reinterpret_cast<sysnotify::NotificationHandler*>(handler)->pop(*handle);
        delete handle;
    });
    return SYSNOTIFY_OK;
}

SYSNOTIFY_API const char* sysnotify_get_last_error() {
    return sysnotify::g_last_error.c_str();
}
}