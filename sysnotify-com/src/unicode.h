#pragma once

/**
 * Borrowed from my own standard library implementation at https://github.com/karmakrafts/kstd-core/blob/master/include/kstd/unicode.hpp.
 * A highly optimized API for converting between different unicode codings.
 *
 * @author Alexander Hinze
 * @since 09/06/24
 */

#include <cstdint>
#include <cstring>
#include <string>
#include <type_traits>

namespace kstd::unicode {
    using CodePoint = char32_t;

    constexpr CodePoint illegal = 0xFFFF'FFFFU;
    constexpr CodePoint incomplete = 0xFFFF'FFFEU;
    constexpr CodePoint replacement = 0x0000'FFFD;

    constexpr auto is_valid_codepoint(CodePoint value) noexcept -> bool {
        if(value > 0x10FFFF) {
            return false;
        }
        if(0xD800 <= value && value <= 0xDFFF) {
            return false;
        }
        return true;
    }

    template<typename CHAR, size_t SIZE = sizeof(CHAR)>
    struct UTFTraits;

    template<typename CHAR>
    struct UTFTraits<CHAR, 1> {
        using CharType = CHAR;
        static constexpr int32_t max_width = 4;

        [[nodiscard]] static constexpr auto trail_length(CharType value) noexcept -> int32_t {
            const auto uc_value = static_cast<uint8_t>(value);
            if(uc_value < 128) {
                return 0;
            }
            if(uc_value < 194) {
                return -1;
            }
            if(uc_value < 224) {
                return 1;
            }
            if(uc_value < 240) {
                return 2;
            }
            if(uc_value <= 244) {
                return 3;
            }
            return -1;
        }

        [[nodiscard]] static constexpr auto width(CodePoint value) noexcept -> int32_t {
            if(value <= 0x7F) {
                return 1;
            }
            if(value <= 0x7FF) {
                return 2;
            }
            if(value <= 0xFFFF) {
                return 3;
            }
            return 4;
        }

        [[nodiscard]] static constexpr auto is_trail(CharType value) noexcept -> bool {
            return (static_cast<uint8_t>(value) & 0xC0) == 0x80;
        }

        [[nodiscard]] static constexpr auto is_lead(CharType value) noexcept -> bool {
            return !is_trail(value);
        }

        template<typename ITERATOR>
        [[nodiscard]] static constexpr auto decode(ITERATOR& current, ITERATOR end) noexcept -> CodePoint {
            if(current == end) {
                return incomplete;
            }
            const auto lead = static_cast<uint8_t>(*current++);
            const auto trail_size = trail_length(lead);
            if(trail_size < 0) {
                return illegal;
            }
            if(trail_size == 0) {
                return lead;
            }
            auto code_point = lead & ((1 << (6 - trail_size)) - 1);
            uint8_t temp = 0;
            for(int32_t remaining_size = trail_size; remaining_size > 0; --remaining_size) {
                if(current == end) {
                    return incomplete;
                }
                temp = static_cast<uint8_t>(*current++);
                if(!is_trail(temp)) {
                    return illegal;
                }
                code_point = (code_point << 6) | (temp & 0x3F);
            }
            if(!is_valid_codepoint(code_point) || width(code_point) != trail_size + 1) {
                current -= trail_size;
                return illegal;
            }
            return code_point;
        }

        template<typename ITERATOR>
        static constexpr auto encode(CodePoint value, ITERATOR& out) noexcept -> void {
            if(value <= 0x7F) {
                *out++ = static_cast<CharType>(value);
            }
            else if(value <= 0x7FF) {
                *out++ = static_cast<CharType>((value >> 6) | 0xC0);
                *out++ = static_cast<CharType>((value & 0x3F) | 0x80);
            }
            else if(value <= 0xFFFF) {
                *out++ = static_cast<CharType>((value >> 12) | 0xE0);
                *out++ = static_cast<CharType>(((value >> 6) & 0x3F) | 0x80);
                *out++ = static_cast<CharType>((value & 0x3F) | 0x80);
            }
            else {
                *out++ = static_cast<CharType>((value >> 18) | 0xF0);
                *out++ = static_cast<CharType>(((value >> 12) & 0x3F) | 0x80);
                *out++ = static_cast<CharType>(((value >> 6) & 0x3F) | 0x80);
                *out++ = static_cast<CharType>((value & 0x3F) | 0x80);
            }
        }
    };

    template<typename CHAR>
    struct UTFTraits<CHAR, 2> {
        using CharType = CHAR;
        static constexpr int32_t max_width = 2;

    private:
        [[nodiscard]] static constexpr auto is_single_codepoint(uint16_t value) noexcept -> bool {
            return value <= 0xD7FF || value >= 0xE000;
        }

        [[nodiscard]] static constexpr auto is_first_surrogate(uint16_t value) noexcept -> bool {
            return 0xD800 <= value && value <= 0xDBFF;
        }

        [[nodiscard]] static constexpr auto is_second_surrogate(uint16_t value) noexcept -> bool {
            return 0xDC00 <= value && value <= 0xDFFF;
        }

        [[nodiscard]] static constexpr auto combine_surrogate(uint16_t surr1, uint16_t value2) noexcept -> CodePoint {
            return ((CodePoint(surr1 & 0x3FF) << 10) | (value2 & 0x3FF)) + 0x10000;
        }

    public:
        [[nodiscard]] static constexpr auto trail_length(CharType value) noexcept -> int32_t {
            if(is_first_surrogate(value)) {
                return 1;
            }
            if(is_second_surrogate(value)) {
                return -1;
            }
            return 0;
        }

        [[nodiscard]] static constexpr auto width(CodePoint value) noexcept -> int32_t {
            return value >= 0x10000 ? 2 : 1;
        }

        [[nodiscard]] static constexpr auto is_trail(CharType value) noexcept -> bool {
            return is_second_surrogate(value);
        }

        [[nodiscard]] static constexpr auto is_lead(CharType value) noexcept -> bool {
            return !is_second_surrogate(value);
        }

        template<typename ITERATOR>
        [[nodiscard]] static constexpr auto decode(ITERATOR& current, ITERATOR end) noexcept -> CodePoint {
            if(current == end) {
                return incomplete;
            }
            const auto surr1 = static_cast<uint16_t>(*current++);
            if(is_single_codepoint(surr1)) {
                return surr1;
            }
            if(surr1 >= 0xDC00) {
                return illegal;
            }
            if(current == end) {
                return incomplete;
            }
            const auto surr2 = static_cast<uint16_t>(*current++);
            if(!is_second_surrogate(surr2)) {
                return illegal;
            }
            return combine_surrogate(surr1, surr2);
        }

        template<typename ITERATOR>
        static constexpr auto encode(CodePoint value, ITERATOR& out) noexcept -> void {
            if(value <= 0xFFFF) {
                *out++ = static_cast<CharType>(value);
            }
            else {
                value -= 0x10000;
                *out++ = static_cast<CharType>(0xD800 | (value >> 10));
                *out++ = static_cast<CharType>(0xDC00 | (value & 0x3FF));
            }
        }
    };

    template<typename CHAR>
    struct UTFTraits<CHAR, 4> {
        using CharType = CHAR;
        static constexpr int32_t max_width = 1;

        [[nodiscard]] static constexpr auto trail_length(CharType value) noexcept -> int32_t {
            if(is_valid_codepoint(value)) {
                return 0;
            }
            return -1;
        }

        [[nodiscard]] static constexpr auto width([[maybe_unused]] CodePoint value) noexcept -> int32_t {
            return 1;
        }

        [[nodiscard]] static constexpr auto is_trail([[maybe_unused]] CodePoint value) noexcept -> bool {
            return false;
        }

        [[nodiscard]] static constexpr auto is_lead([[maybe_unused]] CodePoint value) noexcept -> bool {
            return true;
        }

        template<typename ITERATOR>
        [[nodiscard]] static constexpr auto decode(ITERATOR& current, ITERATOR end) noexcept -> CodePoint {
            if(current == end) {
                return incomplete;
            }
            const auto code_point = static_cast<CodePoint>(*current++);
            if(!is_valid_codepoint(code_point)) {
                return illegal;
            }
            return code_point;
        }

        template<typename ITERATOR>
        static constexpr auto encode(CodePoint value, ITERATOR& out) noexcept -> void {
            *out++ = static_cast<CharType>(value);
        }
    };

    template<typename T>
    [[nodiscard]] auto get_string_length(const T* str) noexcept -> size_t {
        if constexpr(std::is_same_v<T, char>) {
            return strlen(str);
        }
        else if constexpr(std::is_same_v<T, wchar_t>) {
            return wcslen(str);
        }
        else {// If we can't use optimized intrinsics, use a simple loop based implementation
            size_t length = 0;
            while((str + length) != '\0') {
                ++length;
            }
            return length;
        }
    }

    template<typename CHAR_OUT, typename CHAR_IN>
    auto convert_buffer(const CHAR_IN* value, CHAR_OUT* out, size_t* out_count) noexcept -> void {
        if(value == nullptr) {
            return;
        }
        const auto length = get_string_length(value);
        if(length == 0) {
            return;
        }
        const auto* end = &value[length];
        if(out_count != nullptr) {
            const auto* current = value;
            while(current != end) {
                auto code_point = UTFTraits<CHAR_IN>::decode(current, end);
                if(code_point == illegal || code_point == incomplete) {
                    code_point = replacement;
                }
                *out_count += UTFTraits<CHAR_OUT>::width(code_point);
            }
        }
        if(out != nullptr) {
            const auto* current = value;
            while(current != end) {
                auto code_point = UTFTraits<CHAR_IN>::decode(current, end);
                if(code_point == illegal || code_point == incomplete) {
                    code_point = replacement;
                }
                UTFTraits<CHAR_OUT>::encode(code_point, out);
            }
        }
    }

    template<typename CHAR_OUT, typename... OUT_ARGS, typename CHAR_IN, typename... IN_ARGS>
    [[nodiscard]] auto convert(const std::basic_string<CHAR_IN, IN_ARGS...>& value) noexcept -> std::basic_string<CHAR_OUT, OUT_ARGS...> {
        const auto* value_address = value.c_str();
        size_t count = 0;
        convert_buffer<CHAR_OUT, CHAR_IN>(value_address, nullptr, &count);
        std::basic_string<CHAR_OUT, OUT_ARGS...> result(count, ' ');
        convert_buffer<CHAR_OUT, CHAR_IN>(value_address, result.data(), nullptr);
        return result;
    }

    template<typename CHAR_OUT, typename... OUT_ARGS, typename CHAR_IN, typename... IN_ARGS>
    [[nodiscard]] auto convert(const std::basic_string_view<CHAR_IN, IN_ARGS...>& value) noexcept -> std::basic_string<CHAR_OUT, OUT_ARGS...> {
        const auto* value_address = value.data();
        size_t count = 0;
        convert_buffer<CHAR_OUT, CHAR_IN>(value_address, nullptr, &count);
        std::basic_string<CHAR_OUT, OUT_ARGS...> result(count, ' ');
        convert_buffer<CHAR_OUT, CHAR_IN>(value_address, result.data(), nullptr);
        return result;
    }
}// namespace kstd::unicode