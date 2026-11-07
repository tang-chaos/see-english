# 从官方来源下载模型

这个小体积指引包由看见英语项目整理，模型文件直接来自 MNN 团队的公开仓库。下载在使用者自己的电脑上执行，不运行 GitHub Actions，不需要 API Key。模型仍约 5.45GB，下载完成后 APP 的识别过程可以离线。

## Windows 推荐步骤

1. 安装 [最新 APK](https://github.com/tang-chaos/see-english/releases/download/v0.8.5/see-english-0.8.5-arm64-debug.apk)。
2. 下载并解压 [官方源下载指引包](https://github.com/tang-chaos/see-english/releases/download/v0.8.5/see-english-0.8.5-model-download-guide.zip)。不要在 ZIP 内直接运行。
3. 双击 `download-model.cmd`。使用 Windows 自带 PowerShell，不需要额外安装 Python；需要电脑联网访问 Hugging Face。
4. 等待模型文件下载到同目录的 `model` 文件夹。中断后可以重新运行，支持续传；所有文件均按固定版本清单核对字节数和 SHA-256。
5. 将 `model` 文件夹复制到手机。在 APP 打开「模型识别 → 识别设置 → 导入离线识别文件」，选择整个文件夹并等待校验完成。

下载约需要 5.45GB，电脑需足够可用空间。手机导入会暂时保留源文件与 APP 内副本，预留空间说明见 [安装说明](offline-install.txt)。已安装正确模型时，只更新 APK 即可，无需再次下载。

## 模型作者的下载入口

- 当前 APP 兼容的是 [taobao-mnn/Qwen3-VL-8B-Instruct-MNN](https://huggingface.co/taobao-mnn/Qwen3-VL-8B-Instruct-MNN)，MNN 格式、INT4 量化版本。
- [本项目固定版本的完整文件列表](https://huggingface.co/taobao-mnn/Qwen3-VL-8B-Instruct-MNN/tree/890e672be412e84d34bc318925aa4b7b61ad24c7)。需要完整下载 10 个文件，保留文件名与目录结构。
- 原始 [Qwen3-VL-8B-Instruct](https://huggingface.co/Qwen/Qwen3-VL-8B-Instruct) 的 Transformers 权重与 GGUF 等其他格式不能直接导入当前 APP。

已安装 Hugging Face 官方 `hf` CLI 的用户也可运行：

```sh
hf download taobao-mnn/Qwen3-VL-8B-Instruct-MNN --revision 890e672be412e84d34bc318925aa4b7b61ad24c7 --local-dir model
```

CLI 安装及用法见 [Hugging Face 官方文档](https://huggingface.co/docs/huggingface_hub/guides/cli)。APP 导入时仍会核对固定文件清单。

## Actions 与完整离线套装

此前的打包工作流已结束，只允许手动触发。用户下载 Release、下载此指引包、运行下载脚本、使用 APP 都不会启动 Actions。现有 11 卷完整套装作为可选下载保留在 Release 附件中，与 Actions 的计算资源分开。
