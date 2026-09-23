# ColorOS 原生材质实验版

当前版本：`1.28.0-coloros.2-test`。上游基线：`3661a65f90d52e64f96285bc13a3faa92547ee67`。

本分支为微信输入法增加系统磨砂背景，并在接口可用时使用 OPlus 原生混色、边缘高光与阴影。2026-09-23 已在一台 ColorOS 17 设备完成开关对照、收起展开、输入法切换及进程重启验证。**完整凝光折射和触摸光学反馈未实现；ColorOS 16 仍属于未实测的兼容目标。**

## 依据与边界

- [ColorOS 17 官方说明](https://www.coloros.com/version/coloros17/)及 [ColorOS 16 官方说明](https://www.oppo.com/en/coloros16/)提供视觉方向，不能当作输入法可调用的公开 SDK。首版没有套用小米的玻璃参数。
- [Android 官方跨窗口模糊指南](https://source.android.com/docs/core/display/window-blurs)用于确定模糊作用域、运行时状态监听和关闭模糊后的可读性回退。
- [AOSP BackgroundBlurDrawable 源码](https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/core/java/com/android/internal/graphics/drawable/BackgroundBlurDrawable.java)用于内部模糊 Drawable 的可失败适配；OEM 可以修改其实现。
- 第二版的 OPlus 方法签名与使用条件来自当前设备框架和系统 COUI 组件的静态检查，并经过本机运行验证。这些是私有接口，不构成其他 ROM 的兼容承诺。系统文件和反编译内容仅用于本地检查，不随项目分发。
- SystemUI 中另有依赖签名权限的独立后处理服务。本版没有调用该服务，没有更改系统权限或扩大 Xposed 作用域。不能把已接通的混色、描边称为完整凝光折射。

## 实现与回退

- 按 OPlus ROM 属性识别系统。其他系统继续使用上游路径；ColorOS 不调用小米玻璃方法。
- 背景模糊优先使用 `ViewRootManager`，不可用时尝试 AOSP `ViewRootImpl` 通道。只在真实键盘背景区域绘制，沿用运行时尺寸、导航栏 inset 与四角计算。
- 可选 `OplusBlurParam` 使用原生颜色混合。参数为本项目调校，并非 OPPO 官方材质 token。
- 高光开启且模糊可用时，探测 `OplusMaterialUtil` 的 corner、edge、shadow、base 参数，以及 RenderNode 背景效果通道。两层仅用于装饰的 View 分别裁切上下半区，保留顶部用户圆角与底部系统圆角；不参与输入交互或撑高布局。
- 原生圆角使用当前系统提供的平滑权重，尺寸取自实际 View。私有接口缺失、调用失败、硬件绘制不可用或系统关闭相应效果时，恢复普通 tint 和模块高光。
- 系统关闭跨窗口模糊时，恢复不透明的浅/深色可读背景。模糊半径为 0 时尊重用户的纯色/透明选择。
- 键盘隐藏、销毁、材质关闭或背景 View 更换时释放模糊对象和原生装饰；系统模糊监听随输入法可见生命周期注册和注销。
- 设置页预览仍是配色示意，不是系统材质的实时预览。实际效果以弹出的键盘为准。

## 构建与安装

需要 JDK 21、Android SDK 37 和仓库自带的 Gradle wrapper。

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease -PcolorosTestSigning=true
```

`colorosTestSigning=true` 为 Release 构建使用本机 Android debug 测试证书，仍执行 Release 混淆与资源压缩。不传该参数时沿用上游未签名 Release 构建。密钥和密码不进入仓库。

作者专用密钥在本机不存在；本次按已确认的测试签名方案构建。第二版与首个 ColorOS 测试包证书相同，包名保持 `com.xposed.wetypehook`。覆盖安装失败时保留应用与设置，不能自动卸载。

## 启用

1. 安装测试包，在支持 Xposed API 102 的框架中启用模块，作用域选择微信输入法。
2. 重新启动微信输入法进程，确认当前默认输入法仍是微信输入法。当前实测 ColorOS 在强制停止它后会自动切换到搜狗，需要通过系统键盘选择器切回。
3. 打开模块设置，在“外观”启用“ColorOS 原生材质（实验）”，保存。
4. 打开“开启边缘高光效果”启用可用的原生描边和阴影；关闭它仍保留背景模糊与原生混色。沿用原有颜色、透明度、模糊、圆角和高光强度设置。

遇到视觉问题可关闭原生材质开关并保存，恢复普通美化路径。不要为了该效果重启系统框架或强改系统权限。

## 验证范围

当前设备、文件校验值、已通过场景与未测试项目见[第二版实机验证](coloros-native-validation.md)。首版证据分别保存在[离线验证](coloros-validation.md)与[首次实机检查](coloros-device-validation.md)。未测试的深色、横屏、分屏、表情/剪贴板面板、性能、耗电及其他 ROM，不以本次结果代替验证。
