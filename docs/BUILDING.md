# 构建与发布

## 本地开发

使用 JDK 17、SDK 35、Build Tools 35.0.0。复制根目录 local.properties.example 并填写 SDK 路径；不填服务凭据即可构建文字功能；在线语音使用本地配置或环境变量中的自有讯飞参数，详见 CONFIGURATION.md。

`sh ./gradlew assembleDebug` 不需要原作者的签名文件。Debug 使用 `.preview` 包名与标准本机 Debug 签名，隔离于正式应用。

## 自测

执行 README 中的单元测试、Lint 和 AndroidTest 编译命令。仪器测试需要隔离模拟器；真实手机、麦克风、真实语音服务和真实笔记测试需要单独获得相关用户授权。不要把合成数据测试描述为真实服务验收。

## 正式发布

公开版保留 verifyUiApproval Gradle 任务，签名、打包与 Release Bundle 依赖 `.agents/scripts/verify_release.py`。公开源码不上传界面截图目录和评审记录，直接生成正式包会被阻断，这是预期行为。历史私人免确认授权没有复制到此仓库。

维护者需要先执行内部构建、自测，保存真实运行截图，并取得当前源码及版本对应的明确界面确认。检查器的接口为：

```bash
python3 .agents/scripts/ui_release_gate.py snapshot --project .
python3 .agents/scripts/ui_release_gate.py check --project . --version 5.5.3
```

截图位于 `界面截图/`，当前指针为 `界面截图/current.json`；策略在 `.ui-review-policy.json`。公开版不提供评审文件和截图，缺少证据时正式生成保持阻断，不能将历史证据或测试通过当作新的用户批准。检查器验证记录和哈希，实际批准来源与截图真实性仍需维护者核实。

发布签名使用自己的私钥，通过被忽略的 local.properties 或同名环境变量配置 IQMEMO_SIGNING_STORE、IQMEMO_SIGNING_STORE_PASSWORD、IQMEMO_SIGNING_KEY_ALIAS、IQMEMO_SIGNING_KEY_PASSWORD。不会提供原作者私钥，也不保证第三方签名 APK 能覆盖原正式应用。

将真实服务密钥编进 APK 后，密钥可被提取。不要公开分发带有私人讯飞凭据的 APK；公开服务应另行设计受控后端鉴权与额度管理。本仓库公开 APK 不内置服务凭据。
