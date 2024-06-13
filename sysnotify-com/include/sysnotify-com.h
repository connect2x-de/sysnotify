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
 * A simple C-API wrapper around the WRL notification C++ API
 * provided by windows to allow Kotlin/Native to call into it.
 * See https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/send-local-toast-desktop-cpp-wrl
 *
 * The API is designed in such a way that the obtained sn_notification_handler has a randomly generated GUID
 * for its underlying COM interface. This allows us to hide all the ugly GUID stuff from the API consumer.
 *
 * @author Alexander Hinze
 * @since 09/06/24
 */

#pragma once

#ifdef __cplusplus
#include <cstdint>
#else
#include <stdint.h>
#endif

#define SYSNOTIFY_API __declspec(dllexport) // Make functions visible when building a shared object
#define SYSNOTIFY_OK 0
#define SYSNOTIFY_ERR_INVALID_ARG 1

#ifdef __cplusplus
extern "C" {
#endif

typedef int32_t sn_error;
typedef void (*sn_action_callback)();

typedef struct sn_notification_content {
    const char* title;
    const char* description;
    const char* icon;
    sn_action_callback action_callback;
} sn_notification_content_t;

typedef struct sn_notification_handler_* sn_notification_handler;
typedef struct sn_notification_* sn_notification;

SYSNOTIFY_API sn_error sn_com_init(sn_notification_handler* handler);
SYSNOTIFY_API sn_error sn_com_deinit(sn_notification_handler handler);
SYSNOTIFY_API sn_error sn_com_push(sn_notification_handler handler, const sn_notification_content_t* content, sn_notification* notification);
SYSNOTIFY_API sn_error sn_com_update(sn_notification_handler handler, sn_notification notification, const sn_notification_content_t* content);
SYSNOTIFY_API sn_error sn_com_pop(sn_notification_handler handler, sn_notification notification);

#ifdef __cplusplus
}
#endif
