# NCNN Android prebuilt

This directory contains the official CPU-only NCNN Android static package for
`armeabi-v7a` and `arm64-v8a`.

- version: `20260526`
- source: `https://github.com/Tencent/ncnn/releases/tag/20260526`
- archive: `ncnn-20260526-android.zip`
- archive SHA-256: `85b18b875488585c2d21360430e0e54abb6c04aa88094b471c20208ab55ff796`
- upstream license: BSD 3-Clause, copied in `LICENSE.txt`

The x86, x86_64 and riscv64 builds from the upstream archive are intentionally
not vendored because the Android application packages only ARM ABIs.
