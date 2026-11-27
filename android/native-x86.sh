#!/usr/bin/env bash
# Emulator ABI, using the same KataGo revision, backend and Eigen license restriction.
set -euo pipefail
cd "$(dirname "$0")/.."
rev=fd0723fdbc0e9d82cf269c9630af8c27c57c07c4
vendor="$PWD/external/native"
src="$vendor/KataGo"
if [[ ! -d "$src" ]]; then
  git clone --depth 1 --branch v1.18.2 https://github.com/lightvector/KataGo.git "$src"
fi
[[ "$(git -C "$src" rev-parse HEAD)" == "$rev" ]]
ndk="$ANDROID_HOME/ndk/27.2.12479018"
cmake -S "$src/cpp" -B "$vendor/build-x86" -G Ninja -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=x86_64 -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static \
  -DUSE_BACKEND=EIGEN -DEIGEN3_INCLUDE_DIRS=/usr/include/eigen3 -DLIBZIP_LIBRARY=OFF \
  -DCMAKE_CXX_FLAGS="-DEIGEN_MPL2_ONLY -include endian.h" \
  -DCMAKE_EXE_LINKER_FLAGS="-Wl,-z,max-page-size=16384"
cmake --build "$vendor/build-x86" --target katago --parallel 3
mkdir -p "$vendor/katago-android/x86_64"
"$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-all \
  -o "$vendor/katago-android/x86_64/libkatago.so" "$vendor/build-x86/katago"
