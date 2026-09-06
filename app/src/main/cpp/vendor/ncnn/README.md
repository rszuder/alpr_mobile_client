# NCNN Android prebuilt

This directory contains CPU-only NCNN Android static builds for `armeabi-v7a`
and `arm64-v8a`.

- version: `20260526`
- source: `https://github.com/Tencent/ncnn/tree/e54f7b1f88434e1d844ea0551b880a1cfb079ce1`
- Android NDK: `29.0.14033849`
- Android platform: `29`
- CMake options: `NCNN_OPENMP=ON`, `NCNN_SIMPLEOMP=ON`, `NCNN_THREADS=ON`
- disabled components: Vulkan, tools, examples, benchmarks and tests
- upstream license: BSD 3-Clause, copied in `LICENSE.txt`

`NCNN_SIMPLEOMP` supplies NCNN's pthread-based OpenMP compatibility layer and
avoids linking LLVM `libomp`, whose affinity initialization can abort on some
Android devices. The installed static archives are stripped of debug symbols.

The x86, x86_64 and riscv64 builds are intentionally not vendored because the
Android application packages only ARM ABIs.
