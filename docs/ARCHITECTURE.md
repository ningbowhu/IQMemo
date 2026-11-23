# 架构

独立的本地 Android 记录应用。Kotlin/XML 单 app 模块，界面使用 Activity/Fragment、View Binding 和 ViewModel，数据通过 Repository/DAO 存入 Room，异步写入使用协程。

## 代码导航

- app/src/main/java/com/example/iqmemo/：应用入口和各业务模块。
- data/：实体、DAO、Repository、JSON 备份与数据库迁移。
- ui/：备忘录、记事本、搜索及编辑流程。
- speech/：可选讯飞 IAT WebSocket、音频采集和识别结果合并；默认无服务凭据。
- widget/：只展示备忘录的桌面卡片。
- app/schemas/：Room 结构快照，不包含用户数据。
- app/src/test/、app/src/androidTest/：单元及隔离设备测试。

## 本地数据

当前 Room schema 为 7，活动实体仅 notes、task_metadata 与 notebooks。备忘录和记事本使用独立列表/DAO/Repository；记事本不进入桌面卡片。JSON v3 分开保存 notes/notebooks，兼容旧数组备份，按内容与时间等指纹合并去重，不覆盖已有记录。失败恢复应保持两个板块的事务一致。

公开版使用独立 applicationId，不直接读取原私人应用的数据库。旧 schema 3—6 和迁移仅供数据兼容验证；6→7 删除旧绑定/队列表，保留备忘录、历史属性和记事本正文。历史结构和迁移不提供联网功能。

## 已移除的外部关联

本公开版不包含工作台菜单、地址、配对接口、设备身份存储、同步模块、待上传队列业务或网络恢复监听。所有文字记录仅在本地保存，不自动上传。INTERNET 仅供开发者自行配置后使用在线语音；默认公开 APK 未配置语音。

## 编辑安全

保留自动保存、离开补保存、记事本失败草稿及删除后迟到保存保护。数据库变更使用事务；公共备份通过 Mutex 串行、写后校验与 previous 回退保护。不要使用破坏性迁移替代正常升级。
