# IQMemo · 速记

IQMemo 是一款面向 Android 11 及以上设备的文字记录应用，采用 Kotlin、XML 布局和 Room。此仓库公开 5.5.3（versionCode 22）的客户端源码，使用 MIT 许可证。

## 功能

- **备忘录**：文字记录、查看与编辑、自动保存、单条与多选删除。
- **记事本**：独立的标题与正文、自动保存和失败草稿保护。
- **统一搜索**：即时查找备忘录正文及记事本标题、正文。
- **桌面卡片**：显示备忘录，点击进入应用。
- **本地备份**：JSON 导出与合并恢复，兼容历史备忘录数组格式；新版备份区分备忘录与记事本。
- **可选在线语音**：使用自行配置的讯飞 IAT 服务，支持中文与英文识别；应用不保存音频文件。

文字记录可离线使用，公开版已移除工作台绑定、配对和同步，不上传文字记录。在线语音需要你自己的讯飞账号与凭据，服务商条款和费用独立于本项目。

## 下载安装

不需要编程即可使用：打开 [Releases](https://github.com/ningbowhu/IQMemo/releases/latest)，下载 `IQMemo-V5.5.3.apk`，在 Android 11 及以上设备允许来自当前下载来源的安装后打开。公开版包名为 `io.github.ningbowhu.iqmemo`，与原自用版独立安装，不会自动导入原应用的数据；需要迁移时先在原应用导出 JSON，再在公开版合并恢复。

公开 APK 的文字功能可直接使用，不内置讯飞密钥。语音需要开发者配置自己的服务后构建；没有工作台绑定或同步功能。源码和 MIT 许可支持修改、二次开发和商用，保留必要的版权与许可声明即可。

## 快速开始

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0。项目使用 Gradle 8.7、AGP 8.5.2 和 Kotlin 1.9.25。首次构建需要联网下载构建工具与依赖。

```bash
git clone https://github.com/ningbowhu/IQMemo.git
cd IQMemo
cp local.properties.example local.properties
# 修改 local.properties 中的 sdk.dir；Android Studio 也可以为你创建此文件。
sh ./gradlew assembleDebug
```

无需填写任何服务密钥即可编译、使用文字记录。在线语音可在被忽略的 `local.properties` 中设置自己的 XFYUN_APP_ID、XFYUN_API_KEY、XFYUN_API_SECRET，或通过同名环境变量配置；详见 [密钥隔离说明](docs/CONFIGURATION.md)。Windows 可用 Android Studio 导入项目，或使用 `gradlew.bat assembleDebug`；Unix/WSL 使用上面的命令。

Debug 包名为 `io.github.ningbowhu.iqmemo.preview`，与原正式应用分开安装，使用本机生成的 Debug 证书。它不能作为原正式应用的覆盖升级包。

## 检查

```bash
sh ./gradlew testDebugUnitTest testReleaseUnitTest lintRelease assembleDebugAndroidTest
```

Android 仪器测试需要自行准备隔离的设备或模拟器，再运行 `connectedDebugAndroidTest`。单元测试、编译和静态检查不代表真实语音或所有手机已验证。

## 数据与隐私

备忘录和记事本存入应用私有 Room 数据库。Documents/IQMemoOpenSource 中的 JSON 备份是明文，请自行保护。使用语音会将识别音频发送给讯飞；文字记录不会自动上传，也没有工作台配对入口。记事本不进入桌面卡片。

应用禁止 Android 自动备份；卸载或清除数据前请先导出并核实备份。本仓库不包含真实笔记、录音、备份、令牌、签名私钥或服务凭据。

## 文档与贡献

- [架构与数据边界](docs/ARCHITECTURE.md)
- [构建与发布](docs/BUILDING.md)
- [源码公开范围](docs/PUBLIC_SOURCE.md)
- [贡献说明](CONTRIBUTING.md) · [安全问题报告](SECURITY.md)
- [第三方声明](THIRD_PARTY_NOTICES.md) · [MIT 许可证](LICENSE)

正式 APK 的生成保留源码绑定的界面确认门禁，公开仓库不携带截图、过程资料或私人历史授权，也不提供默认跳过选项。公开安装包可从 [Releases](https://github.com/ningbowhu/IQMemo/releases/latest) 下载；不分发嵌入私人语音凭据的历史 APK。
