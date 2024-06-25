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

#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <functional>
#include <iostream>
#include <source_location>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>
#include <vector>
#include <optional>
#include <unordered_map>

#include <fmt/format.h>
#include <parallel_hashmap/phmap.h>

// @formatter:off
#define WIN32_LEAN_AND_MEAN// Disable parts of the API we don't need to save compile time
#define NOMINMAX           // Make sure min/max are not defined as macros by the Win32 API
#include <windows.h>
#undef ERROR
#include <psapi.h>
#include <windows.ui.notifications.h>
#include <wrl.h>
#include <appmodel.h>
// @formatter:on

#include "unicode.hpp"

namespace sysnotify {
    [[nodiscard]] inline auto get_last_error() noexcept -> std::string {
        const auto error_code = ::GetLastError();
        if(error_code == 0) {
            return "";
        }

        LPWSTR buffer = nullptr;
        constexpr auto lang_id = MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT);
        const auto new_length =
            ::FormatMessageW(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS, nullptr,
                             error_code, lang_id, reinterpret_cast<LPWSTR>(&buffer), 0, nullptr);
        auto message = kstd::unicode::convert<char>(std::wstring_view(buffer, new_length));
        LocalFree(buffer);

        return fmt::format("Error 0x{:X}: {}", error_code, message);
    }

    inline auto require(const bool condition,
                        std::string_view code,
                        const std::source_location& location = std::source_location::current()) -> void {
        if(condition) {
            return;
        }
        throw std::runtime_error(
            fmt::format("'{}' failed at {}:{}:{}", code, location.file_name(), location.function_name(), location.line()));
    }

    inline auto require_success(const DWORD error_code,
                                std::string_view code,
                                const std::source_location& location = std::source_location::current()) -> void {
        if(error_code == S_OK) {
            return;
        }
        throw std::runtime_error(fmt::format("'{}' failed at {}:{}:{} - {}", code, location.file_name(), location.function_name(),
                                             location.line(), get_last_error()));
    }

    template<typename T>
    inline auto combined_hash(size_t hash1, const T& value) noexcept -> size_t {
        hash1^= std::hash<T>()(value) + 0x9E3779B9 + (hash1 << 6) + (hash1 >> 2);
        return hash1;
    }

    inline auto combine_hashes(size_t hash1, size_t hash2) noexcept -> size_t {
        hash1^= hash2 + 0x9E3779B9 + (hash1 << 6) + (hash1 >> 2);
        return hash1;
    }

    namespace detail {
        template<typename T, size_t TSize, size_t TIndex = 0>
        inline auto array_hash(const T(&array)[TSize], size_t& hash) noexcept -> void {
            hash = combined_hash(hash, array[TIndex]);
            if constexpr(TIndex < TSize - 1) {
                array_hash<T, TSize, TIndex + 1>(array, hash);
            }
        }
    }

    template<typename T, size_t TSize>
    inline auto array_hash(const T(&array)[TSize]) noexcept -> size_t {
        size_t result = 0;
        detail::array_hash<T, TSize>(array, result);
        return result;
    }
}// namespace sysnotify

#define SYSNOTIFY_REQUIRE_SUCCESS(x) sysnotify::require_success(x, #x)
#define SYSNOTIFY_REQUIRE(x) sysnotify::require(x, #x)

namespace sysnotify {
    [[nodiscard]] inline auto get_process_path(HANDLE process = GetCurrentProcess()) -> std::filesystem::path {
        std::wstring executable_path(MAX_PATH, 0);
        const auto new_length = GetModuleFileNameExW(process, nullptr, executable_path.data(), MAX_PATH);
        SYSNOTIFY_REQUIRE(new_length > 0);
        executable_path.resize(new_length);
        return executable_path;
    }

    [[nodiscard]] inline auto is_packaged_app() -> bool {
        uint32_t length = 0U;
        return GetCurrentPackageId(&length, nullptr) == S_OK && length > 0;
    }
}// namespace sysnotify

// Allow GUIDs to be used as keys to hash tables
namespace std {
    template<>
    struct hash<GUID> final {
        inline auto operator ()(const GUID& value) noexcept -> size_t {
            auto hash = std::hash<decltype(value.Data1)>()(value.Data1);
            hash = sysnotify::combined_hash(hash, value.Data2);
            hash = sysnotify::combined_hash(hash, value.Data3);
            hash = sysnotify::combine_hashes(hash, sysnotify::array_hash(value.Data4));
            return hash;
        }
    };
}