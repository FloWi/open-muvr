CMAKE_MINIMUM_REQUIRED (VERSION 2.8) 
PROJECT (aa) 

# SET_PROPERTY(DIRECTORY . APPEND PROPERTY COMPILE_DEFINITIONS DEBUG=1)
SET(CMAKE_MODULE_PATH ${CMAKE_CURRENT_SOURCE_DIR}/cmake)
ENABLE_TESTING()

FIND_PACKAGE(Boost REQUIRED)

SET(GTEST_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../gtest-1.7.0")

SET(GTEST_INCLUDE_DIR "${GTEST_ROOT}/include")
ADD_SUBDIRECTORY(${GTEST_ROOT} "${CMAKE_CURRENT_BINARY_DIR}/gtest")

INCLUDE_DIRECTORIES(${GTEST_INCLUDE_DIR})
INCLUDE_DIRECTORIES(${Boost_INCLUDE_DIRS})

ADD_SUBDIRECTORY(core)
