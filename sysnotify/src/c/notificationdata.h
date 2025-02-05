#pragma once

#ifdef __cplusplus
#include <cstdint>
#else
#include <stdint.h>
#endif

#define SN_FALSE 0
#define SN_TRUE 1

typedef int32_t SNBool;

typedef struct _SNNotificationData {
    const char* title;          // 0
    const char* description;    // p
    const char* icon_data;      // p * 2
    int32_t icon_width;         // p * 3
    int32_t icon_height;        // p * 3 + 4
    const char* callback_data;  // p * 3 + 8
    SNBool dismissible;         // p * 4 + 8
    int32_t priority;           // p * 4 + 12
    SNBool play_sound;          // p * 4 + 16
    const char* sound_path;     // p * 4 + 20
    const char* group;          // p * 5 + 20
} SNNotificationData;
