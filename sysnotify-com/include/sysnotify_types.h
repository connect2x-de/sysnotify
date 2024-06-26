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

#ifdef __cplusplus
#include <cstdint>
#else
#include <stdint.h>
#endif

#define SYSNOTIFY_OK 0
#define SYSNOTIFY_ERR_INVALID_ARG 1
#define SYSNOTIFY_ERR_EXCEPTION 2

#define SYSNOTIFY_FALSE 0
#define SYSNOTIFY_TRUE 1

#ifdef __cplusplus
extern "C" {
#endif//__cplusplus

typedef int sn_bool;
typedef int sn_error;
typedef struct sn_notification_handler_* sn_notification_handler;

typedef struct sn_notification_tag_ {
    uint64_t lower;
    uint64_t upper;
} sn_notification_tag;

typedef void (*sn_notification_action)(const sn_notification_tag* tag, void* user_data);

typedef struct sn_notification_content_ {
    const char* title;
    const char* description;
    const char* icon;
    int icon_width;
    int icon_height;
    sn_notification_action activate_action;
    sn_notification_action dismiss_action;
    sn_notification_action fail_action;
    void* user_data;
} sn_notification_content;

#ifdef __cplusplus
}
#endif//__cplusplus