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

#include "platform.hpp"

#define SYSNOTIFY_DEFINE_LOG_FUNCTION(n, L)                                         \
    template<bool TFlush = false, typename... TArgs>                                \
    auto n(fmt::format_string<TArgs...> format, TArgs&&... args) noexcept -> void { \
        log<L, TFlush>(std::move(format), std::forward<TArgs>(args)...);            \
    }

namespace sysnotify::log {
    enum class Level { DEBUG, INFO, ERROR };

    template<Level TLevel>
    [[nodiscard]] constexpr auto get_level_string() noexcept -> std::string_view {
        using namespace std::string_view_literals;
        if constexpr(TLevel == Level::INFO) {
            return "[-INFO--]"sv;
        }
        else if constexpr(TLevel == Level::ERROR) {
            return "[-ERROR-]"sv;
        }
        else {
            return "[-DEBUG-]"sv;// Everything else is debug!
        }
    }

    template<Level TLevel, bool TFlush = false, typename... TArgs>
    auto log(fmt::format_string<TArgs...> format, TArgs&&... args) noexcept -> void {
        auto formatted_message = fmt::format(std::move(format), std::forward<TArgs>(args)...);
        auto log_entry = fmt::format("{} {}\n", get_level_string<TLevel>(), std::move(formatted_message));
        if constexpr(TLevel >= Level::ERROR) {
            std::cerr << std::move(log_entry);
            if constexpr(TFlush) {
                std::cerr << std::flush;
            }
        }
        else {
            std::cout << std::move(log_entry);
            if constexpr(TFlush) {
                std::cout << std::flush;
            }
        }
    }

    SYSNOTIFY_DEFINE_LOG_FUNCTION(debug, Level::DEBUG)
    SYSNOTIFY_DEFINE_LOG_FUNCTION(info, Level::INFO)
    SYSNOTIFY_DEFINE_LOG_FUNCTION(error, Level::ERROR)
}// namespace sysnotify::log

// Make sure we entirely strip any debug log calls from the code conditionally @ compile time
#ifdef SYSNOTIFY_DEBUG
#define SYSNOTIFY_LOG_DEBUG(f, ...) sysnotify::log::debug(f, ##__VA_ARGS__)
#else
#define SYSNOTIFY_LOG_DEBUG(f, ...)
#endif