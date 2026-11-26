# 第三方许可与署名 / Third-party notices

颜颜 KaoToFace 使用了以下开源资源。分发本 App 或其源码时请保留本文件。

## 字体

### Jost

- 来源：Google Fonts，https://fonts.google.com/specimen/Jost
- 版权：Copyright 2020 The Jost Project Authors
- 许可：SIL Open Font License, Version 1.1
- 文件：`app/src/main/res/font/jost.ttf`（可变字重）

### 思源黑体 / Noto Sans CJK SC

- 来源：Adobe Source Han Sans / Google Noto CJK，https://github.com/notofonts/noto-cjk
- 版权：Copyright 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source'.
- 许可：SIL Open Font License, Version 1.1
- 文件：`app/src/main/res/font/source_han_sans_regular.otf`、`source_han_sans_bold.otf`

Noto Sans CJK SC 与思源黑体（Source Han Sans SC）为同一字体设计的不同发行名称。

### SIL Open Font License, Version 1.1（摘要）

本软件中嵌入的字体按 SIL Open Font License 1.1 授权，允许自由使用、研究、修改和再分发，
包括嵌入到应用中；再分发字体文件时须保留版权声明与本许可，且不得单独以 "Reserved Font Name"
销售字体。完整许可文本见 https://openfontlicense.org/open-font-license-official-text/

## Phosphor Icons (Fill / Regular)

- 来源：https://github.com/phosphor-icons/web
- 版权：Copyright (c) 2023 Phosphor Icons
- 许可：MIT License
- 使用方式：`app/src/main/res/font/phosphor_fill.ttf` 作为按钮图标字体嵌入；
  聆听键的线稿耳朵来自 Phosphor Regular，置于 `app/src/main/res/drawable/ic_ear.xml`。

### MIT License

Copyright (c) 2023 Phosphor Icons

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Android / Kotlin 生态

- AndroidX、Jetpack Compose、Material Components — Apache License 2.0
- Kotlin / kotlinx.coroutines — Apache License 2.0
- OkHttp — Apache License 2.0
- Google ML Kit 未在本项目中使用（语种判定由 DeepSeek 完成）

## DeepSeek

翻译与语言判定通过 DeepSeek 的 OpenAI 兼容 API 完成。API Key 由使用者自行提供，
仅保存在本机 `SharedPreferences` 中；音频不上传，只有转写后的文字会被发送。

## 演示音频

`app/src/main/assets/demo/conversation.wav` 由 Windows 系统 TTS 合成（Microsoft Huihui / Zira），
仅用于演示与自测，不含第三方版权素材。

## 本项目

KaoToFace / 颜颜 为个人项目，未附带任何明示或暗示的担保。请勿将四位暗号房间用于不可信网络，
也不要将 API Key 提交到公开仓库。
