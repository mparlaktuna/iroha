set(FLAGS
    -Wextra
    -O0                             # no optimizations
    -g                              # include debug symbolx

    CACHE INTERNAL ""
    )

# JOINED_FLAGS = " ".join([w for w in FLAGS])
JOIN("${FLAGS}" " " JOINED_FLAGS)

set(CMAKE_CXX_FLAGS_DEBUG
    ${JOINED_FLAGS}
    CACHE STRING "Flags used by the C++ compiler during Debug builds."
    FORCE
    )

set(CMAKE_C_FLAGS_DEBUG
    ${JOINED_FLAGS}
    CACHE STRING "Flags used by the C compiler during Debug builds."
    FORCE
    )
