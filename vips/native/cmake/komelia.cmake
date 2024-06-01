include(ExternalProject)

if (TARGET ep_vips)
    set(DEPENDENCY ep_vips)
endif()

ExternalProject_Add(ep_komelia
    DOWNLOAD_COMMAND ""
    DEPENDS ${DEPENDENCY}
    BUILD_ALWAYS 1
    SOURCE_DIR ${PROJECT_SOURCE_DIR}
    CMAKE_ARGS
        -DCMAKE_BUILD_TYPE=Release
        -DUSE_SUPERBUILD=OFF
        -DCMAKE_INSTALL_PREFIX=${CMAKE_BINARY_DIR}/fakeroot
        -DCUSTOM_LIB_PATH=${THIRD_PARTY_LIB_PATH}
        -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
        -DANDROID_ABI=${ANDROID_ABI}
        -DANDROID_PLATFORM=${ANDROID_PLATFORM}
        -DCMAKE_PREFIX_PATH=${CMAKE_PREFIX_PATH}
        -DCMAKE_FIND_ROOT_PATH=${CMAKE_FIND_ROOT_PATH}
        -DCMAKE_SYSROOT=${CMAKE_SYSROOT}
)