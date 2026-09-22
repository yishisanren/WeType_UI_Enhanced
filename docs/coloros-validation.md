# ColorOS 实验版离线验证记录

日期：2026-09-23。版本：`1.28.0-coloros.1-test`（versionCode 36）。

## 已验证

- `:app:testDebugUnitTest`：8 个测试套件、63 项测试通过，0 失败、0 错误、0 跳过。
- 新增 14 项 ColorOS 测试覆盖 ROM 属性识别、透明/不透明/半透明背景回退、四角参数顺序、异常半径边界、接口缺失与调用失败、单层复用、键盘隐藏/重复清理、ViewRoot 切换、系统模糊关闭与恢复、失败通道不反复分配、OPlus 调用失败后清理并切换 AOSP、未附着窗口时不分配。
- `:app:lintRelease`：通过，0 错误、85 条警告；新增的 ColorOS 实现文件没有 Lint 告警。原有项目的警告未批量压制。
- `:app:assembleRelease -PcolorosTestSigning=true`：通过，包含 Release 混淆/资源压缩。
- `apksigner verify --verbose --print-certs`：APK v2 签名通过；本机 Android Debug 测试证书，非上游作者证书。
- `aapt2 dump badging`：包名 `com.xposed.wetypehook`，版本 `1.28.0-coloros.1-test`，minSdk 31、targetSdk/compileSdk 37。
- `git diff --check`：通过。

APK 的最终提交号、文件 SHA-256、证书 SHA-256 随预发布版本的 `validation.md` / `SHA256SUMS.txt` 交付。

## 未验证

本轮未连接、安装或操作真机。以上检查不证明 ColorOS 16/17 系统接口实际可用，不证明视觉效果、功耗、输入法兼容性或系统凝光已实现。ColorOS 17 凝光折射尚未接入。

[适配说明及真机验收范围](coloros-material.md)。
