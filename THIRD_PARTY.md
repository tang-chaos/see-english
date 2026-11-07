# 第三方组件、模型与图片

原创 APP 代码采用 Apache-2.0；本声明不重新许可第三方材料。下面是当前 0.8.5 分发的组件，旧实验权重和私人评测材料不在公开仓库或套装中。

| 组件 | 用途与来源 | 许可 |
| --- | --- | --- |
| AndroidX / CameraX / Compose | 相机、界面、生命周期；Google Maven | Apache-2.0，保留各组件许可 |
| Kotlin / kotlinx.coroutines | Kotlin 语言与协程；JetBrains | Apache-2.0 |
| MediaPipe Tasks Vision | [google-ai-edge/mediapipe](https://github.com/google-ai-edge/mediapipe) | Apache-2.0，依赖另含其第三方声明 |
| EfficientDet-Lite0 | [Google 官方对象检测模型](https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector#models)，COCO 80 类 | Apache-2.0；模型哈希见 `scripts/verify-assets.py` |
| MNN 与 MNN Chat JNI | [alibaba/MNN](https://github.com/alibaba/MNN)，ARM64 原生运行库 | Apache-2.0；许可随 APK 的 `assets/MNN-LICENSE.txt` 和套装分发 |
| Qwen3-VL-8B-Instruct-MNN | [Qwen 原模型](https://huggingface.co/Qwen/Qwen3-VL-8B-Instruct)、[taobao-mnn INT4 转换](https://huggingface.co/taobao-mnn/Qwen3-VL-8B-Instruct-MNN) | 上游模型卡 Apache-2.0；独立文件随套装分发，不在 APK 内 |
| 公开桌面示例照片 | [Unsplash 原图页面](https://unsplash.com/photos/black-framed-eyeglasses-beside-black-leather-case-and-macbook-pro-M5BxrU2nSTM)；文件 `docs/media/desk-original.jpg` | [Unsplash License](https://unsplash.com/license)，不适用本项目 Apache-2.0 |

MNN 库从官方 `https://meta.alicdn.com/data/mnn/apks/mnn_chat_0_8_3.apk` 提取，未修改二进制；本 APP 提供最小 JNI ABI 适配，不包含 MNN Chat 界面或云服务。

- `libMNN.so` SHA-256：`ce37885f31f31e75aa01ee59b26ec72c8041e7b3a02266a5242c38fd10c5fadf`
- `libmnnllmapp.so` SHA-256：`11cb79c031a51c3ee7e83a903afb2d9047ced699f1a44f1f58292bd4710c29f2`
- 模型固定提交：`890e672be412e84d34bc318925aa4b7b61ad24c7`；每个文件的字节数与 SHA-256 见 `app/src/main/assets/vlm-model.json`。

完整套装包含 `LICENSE`、`MNN-LICENSE.txt`、`QWEN-LICENSE.txt`、本声明及 `NOTICE`。图片上的商品外观和商标仅作识别示例，不表示品牌参与或背书。测试中曾有的私人照片与无明确许可的旧测试图均未分发；界面布局测试改为代码绘制的色块。
