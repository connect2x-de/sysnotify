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

#pragma once

#include "sysnotify_types.h"

#define SYSNOTIFY_API __declspec(dllexport)

#ifdef __cplusplus
extern "C" {
#endif//__cplusplus

SYSNOTIFY_API sn_error sysnotify_handler_create(const char* app_name, const char* aum_id, sn_notification_handler* handler);

SYSNOTIFY_API sn_error sysnotify_handler_destroy(sn_notification_handler handler);

SYSNOTIFY_API sn_error sysnotify_handler_push(sn_notification_handler handler,
                                              const sn_notification_content* content,
                                              sn_notification_tag* tag);

SYSNOTIFY_API sn_error sysnotify_handler_update(sn_notification_handler handler,
                                                const sn_notification_tag* tag,
                                                const sn_notification_content* content);

SYSNOTIFY_API sn_error sysnotify_handler_pop(sn_notification_handler handler, const sn_notification_tag* tag);

SYSNOTIFY_API sn_error sysnotify_handler_is_valid_tag(sn_notification_handler handler, const sn_notification_tag* tag, sn_bool* is_valid);

SYSNOTIFY_API const char* sysnotify_get_last_error();

#ifdef __cplusplus
}
#endif//__cplusplus