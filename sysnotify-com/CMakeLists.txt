cmake_minimum_required(VERSION 3.18)
project(sysnotify-com)

set(CMAKE_CXX_STANDARD 20)

file(GLOB_RECURSE SYSNOTIFY_SOURCE_FILES
        "${CMAKE_CURRENT_SOURCE_DIR}/src/*.c*"
        "${CMAKE_CURRENT_SOURCE_DIR}/src/*.h*"
        "${CMAKE_CURRENT_SOURCE_DIR}/src/*.rc"
        "${CMAKE_CURRENT_SOURCE_DIR}/include/*.h*")
add_library(sysnotify-com SHARED ${SYSNOTIFY_SOURCE_FILES})

target_link_libraries(sysnotify-com PRIVATE
        kernel32.lib
        ole32.lib
        user32.lib
        runtimeobject.lib
        WindowsApp.lib)

target_compile_definitions(sysnotify-com PUBLIC
        FMT_HEADER_ONLY=1
        FMT_EXCEPTIONS=1
        FMT_NOEXCEPT=0)
target_include_directories(sysnotify-com PUBLIC "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_compile_definitions(sysnotify-com PRIVATE SYSNOTIFY_DEBUG=$ENV{SYSNOTIFY_DEBUG})