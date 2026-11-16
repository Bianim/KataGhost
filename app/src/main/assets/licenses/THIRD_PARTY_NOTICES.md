# Third-party notices

KataGhost's original code is licensed under [MIT](LICENSE). Third-party code,
weights and runtimes retain their respective licenses. MIT does not relicense
those dependencies. License copies for bundled source are also packaged under
`app/src/main/assets/licenses/` in the APK.

## Application origin and attribution

KataGhost is maintained by Bianim and derives from
[KataDroid](https://github.com/zhzy0077/KataDroid). The original application's
MIT copyright remains in `LICENSE`; Bianim's copyright covers Bianim's own
contributions. Historical upstream screenshots, benchmarks and device audits
are identified as such and are not validation of a KataGhost release.
Keep legal notices here, in `LICENSE`, and in the APK's `licenses/` assets;
do not replace third-party source notices with the maintainer's name.

## KataGo source

The source subset in `third_party/katago/` is from KataGo v1.18.2, commit
`fd0723fdbc0e9d82cf269c9630af8c27c57c07c4`, without modifications. See its
[LICENSE](third_party/katago/LICENSE), [CONTRIBUTORS](third_party/katago/CONTRIBUTORS)
and [file hash manifest](third_party/katago/upstream-files.json).

Included external components have separate notices:

| Component | License / preserved notice |
| --- | --- |
| ghc filesystem 1.5.8 | [MIT](third_party/katago/cpp/external/filesystem-1.5.8/LICENSE) |
| TCLAP 1.2.5 | [MIT](third_party/katago/cpp/external/tclap-1.2.5/COPYING) |
| nlohmann/json | MIT and embedded acknowledgments in [json.hpp](third_party/katago/cpp/external/nlohmann_json/json.hpp) |
| SHA-2 implementation | BSD 3-Clause notice embedded in [sha2.cpp](third_party/katago/cpp/core/sha2.cpp) |

## Neural network weights

The bundled original descriptors and converted TFLite files derive from these
official, pre-distributed-training **g170** networks:

- `g170-b6c96-s175395328-d26788732`
- `g170e-b10c128-s1141046784-d204142634`

The official [KataGo neural network license page](https://katagotraining.org/network_license/),
section **Exceptions**, states that the old g170 networks are under
[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) and effectively
public domain. This exception is specific to g170; do not assume that every newer
KataGo or community model has the same license. Source URLs and SHA-256 hashes
are retained in the bundled fixture manifests and the conversion script.

The optional tf2-b10c384, tf3-b10c512 and tf3-b11c768 packages derive from official
kata1 networks and checkpoints. Their converted graphs and descriptors retain
the [KataGo Neural Network License](app/src/main/assets/licenses/katago-neural-networks.txt).
Each published `.kdmodel` embeds this permission notice in `manifest.json`;
release assets also include a standalone copy. These networks are independent
of the bundled g170/CC0 models.

## Build dependencies and optional runtimes

- AndroidX, Jetpack Compose and Google LiteRT use Apache License 2.0. Their own
  dependency distributions may include additional third-party notices.
  The actual LiteRT 2.2.0 AAR's `LICENSE` and `THIRD_PARTY_NOTICE.txt` are
  preserved in `Android-DEPENDENCY-NOTICES.txt` in the APK's `licenses/`
  assets, together with the other declared runtime dependencies' notices.
  Byte-identical notice texts are included once. The upstream notice includes MPL-2.0 Eigen
  source availability information and a broader dependency/build-tool inventory;
  inclusion of a license in that inventory alone does not establish that the
  corresponding component is linked into this APK.
  LiteRT v2.2.0's pinned TensorFlow/XLA workspace selects Eigen source commit
  `ea13a98decd497a8c5588fb5de71b57bcf10d864`, available with its MPL/BSD notices
  from [Eigen's source repository](https://gitlab.com/libeigen/eigen/-/tree/ea13a98decd497a8c5588fb5de71b57bcf10d864).
- LiteRT's transitive Play AI/Asset Delivery dependencies also use the
  [Play Core SDK terms](https://developer.android.com/guide/playcore#license)
  and [Google APIs terms](https://developers.google.com/terms), not just
  Apache-2.0. The actual Play AAR terms and notices are included in
  `Android-DEPENDENCY-NOTICES.txt`.
- Transitive `play-services-basement:18.4.0` and `play-services-tasks:18.2.0`
  declare the [Android SDK license](https://developer.android.com/studio/terms),
  including its compatible-Android development restrictions; they are not
  Apache-2.0 components merely because their own bundled third-party notices
  contain open-source licenses. Google's integration guide permits including
  their client libraries in Android applications; do not redistribute the full
  Google Play services application or the SDK as an independent product.
- The Gradle wrapper is from Gradle, under Apache License 2.0.
- The NDK's LLVM C++ runtime uses Apache License 2.0 with LLVM exceptions.
- `com.qualcomm.qti:qnn-runtime:2.47.0` declares the **Qualcomm AI Hub Model License**
  in its Maven POM, but the actual AAR includes an **AI Stack License** and a
  separate third-party NOTICE. Copies are packaged as `Qualcomm-AI-Stack-LICENSE.pdf`
  and `Qualcomm-QNN-NOTICE.txt` under the APK's `licenses/` assets. Section 1
  permits distribution in object-code form incorporated into an application,
  not standalone distribution. Preserve notices and comply with all remaining
  restrictions. The actual QAIRT 2.47.0.260601 SDK's `LICENSE.pdf`, `NOTICE.txt`
  and `QNN_NOTICE.txt` were checked against the AAR: the license and notices
  are byte-identical to the copies above. This covers the matching SDK's
  `libQnnIr.so` and `libQnnSaver.so`, rather than assuming terms from Maven metadata.
- Optional Qualcomm compiler/dispatch and QAIRT SDK libraries downloaded by
  `tools/prepare_npu.py` remain subject to their respective terms. Google's
  LiteRT 2.2.0 Qualcomm plugin source uses Apache-2.0; associated QAIRT code
  remains under the AI Stack License. The official JIT archive checksum was
  verified against the pinned value. Downloaded vendor libraries are excluded
  from this Git repository; do not redistribute the SDK as a standalone product.
- Optional MediaTek LiteRT compiler/dispatch plugins are built from LiteRT's
  Apache-2.0 source. The upstream build downloads NeuroPilot SDK headers under
  the SDK's own NeuroPilot Express SDK license agreement. The actual archive
  selected by LiteRT 2.2.0 maps v8 headers to 8.0.10 and v9 headers to 9.0.3.
  Section 1.1 permits royalty-and-fee-free use and object-code distribution
  incorporated into applications used with MediaTek chipsets, not standalone
  SDK distribution. Sections 2.2 and 2.3 require retaining proprietary notices
  and not subjecting the SDK to open-source license terms. These vendor rights
  are not replaced by this repository's MIT license. The SDK and generated
  plugins are excluded from Git; device-provided Neuron libraries are not bundled.

Consult the dependency distributions and their terms when redistributing a
build containing vendor runtime binaries. This repository's MIT license applies
to KataGhost's own integration code, not to the vendor runtimes.

### KataGhost settlement jingles

Kenney, [85 Short music jingles](https://opengameart.org/content/85-short-music-jingles),
[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).
The Android resources use `jingles_PIZZA02.ogg` (win) and `jingles_PIZZA03.ogg` (loss).
