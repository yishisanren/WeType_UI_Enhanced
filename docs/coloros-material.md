# ColorOS 16 / 17 磨砂材质实验版

版本：`1.28.0-coloros.1-test`。上游基线：`3661a65f90d52e64f96285bc13a3faa92547ee67`。

## 当前交付范围

这是微信输入法 Xposed 美化模块的 ColorOS 兼容分支。新增 ColorOS 系统背景模糊通道、可读性回退、动态模糊开关监听和独立的材质生命周期管理。目标为 ColorOS 17，兼顾 ColorOS 16；尚未做物理设备验证，也不声明所有机型或 ROM 小版本兼容。

**本版实现磨砂背景，不包含 ColorOS 17 原生“凝光”的折射/触摸反馈。** OPPO 官方产品说明展示了凝光设计，但截至本次查阅，没有核实到输入法可调用的公开凝光 SDK、类名、方法签名或参数规范。不能把小米的 `setMiGlass(float[])` 参数直接移植或改名为 ColorOS 参数。真机调试阶段需要确认设备框架暴露的实际能力，再决定是否扩展。

## 依据与证据边界

| 来源 | 本版采用的内容 | 不能据此证明的内容 |
| --- | --- | --- |
| [ColorOS 17 官方说明](https://www.coloros.com/version/coloros17/) | 通透、清晰可读的凝光设计方向 | 没有在该页面看到可调用的凝光 API |
| [ColorOS 16 官方说明](https://www.oppo.com/en/coloros16/) | 系统视觉与渲染的产品背景 | 不能从宣传效果推断输入法接口或 ROM 参数 |
| [Android 官方跨窗口模糊指南](https://source.android.com/docs/core/display/window-blurs) | 背景模糊的作用域、透明背景、运行时关闭模糊时的可读性回退 | 无法证明某台 ColorOS 设备开启此能力 |
| [Android WindowManager API](https://developer.android.com/reference/android/view/WindowManager#addCrossWindowBlurEnabledListener(java.util.function.Consumer%3Cjava.lang.Boolean%3E)) | 查询并监听跨窗口模糊状态、注销监听 | 不提供 ColorOS 凝光折射 |
| [AOSP Android 16 BackgroundBlurDrawable 源码](https://github.com/aosp-mirror/platform_frameworks_base/blob/99b01a65cc4c104933788b3143285ab6bae65827/core/java/com/android/internal/graphics/drawable/BackgroundBlurDrawable.java) | 键盘区域的系统模糊 Drawable；四角调用顺序为 TL、TR、BL、BR；半径归零与隐藏用于释放模糊区域 | 属于内部 API，OEM 可以修改，必须运行时探测 |
| [OPlus 兼容层中的 ViewRootManager 接口形状](https://github.com/yaap/hardware_oplus/blob/6d726d44eac52d6ef09060c11c05a1c689a9dd32/oplus-fwk/src/com/oplus/view/ViewRootManager.java) | 可选探测 `View` 构造器、取 Drawable、设置模糊半径、颜色及四角签名 | 这是社区兼容桩，不是 OPPO 官方文档或 ColorOS 16/17 实测证据；没有采用其中无实现的 `setBlurParams` |

官方文档和公开源码指导了本版的行为；OPlus 包装类只作为可失败的实验通道。它的签名存在也不能证明系统实际绘制出了效果。

## 实现

- 按 `ro.build.version.oplusrom` / `ro.build.version.opporom` 识别 OPlus ROM。版本字符串不作为“已兼容”的证据；其他系统保留上游路径。
- “外观”中的高级材质开关在 ColorOS 上显示为“ColorOS 磨砂材质（实验）”。沿用已有开关存储，默认关闭，不重置原配色与玻璃参数。
- 优先探测 OPlus `ViewRootManager`；缺少完整签名、返回空 Drawable 或调用失败，尝试 AOSP `ViewRootImpl.createBackgroundBlurDrawable()`。所有调用仅作用于微信输入法自己的背景 View。
- 在真实键盘背景区域绘制，不对整屏调用 `FLAG_BLUR_BEHIND`；沿用上游的输入法可见区域与实时导航栏/硬件圆角计算，不固定设备尺寸。
- 两条通道均不可用，或系统关闭跨窗口模糊时，将用户 tint 合成在不透明的浅/深中性色上。恢复系统模糊后自动重建；用户设置模糊半径为 0 且系统模糊可用时尊重其纯色/透明选择。
- 每个当前 ViewRoot 仅保留一个原生模糊对象；样式变化复用。键盘隐藏、销毁、ViewRoot 更换或材质关闭时将半径归零、隐藏 Drawable 并解除引用；系统监听在输入法隐藏/销毁时注销。
- 配色、模糊强度、圆角、已有边缘高光仍可调整。中性色及高光属于模块效果，不是 OPPO 官方材质 token。
- 小米玻璃数组编辑器在 ColorOS 隐藏；不调用 `setMiGlass`、`setMiViewMaterialType` 或推测的 OPlus 折射函数。
- 设置页预览为本地图片配色示意，明确标注“实际键盘效果待真机验证”。它不代表跨窗口渲染已经通过。

## 构建和签名

需要 JDK 21、Android SDK 37 和仓库自带的 Gradle wrapper。

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease -PcolorosTestSigning=true
```

`colorosTestSigning=true` 明确为 Release 构建启用本机 Android debug 测试证书。不开此参数时沿用上游的未签名 Release 构建。测试包仍经过 Release 混淆和资源压缩；不会在仓库中保存任何签名密钥或密码。生成结果位于 `app/build/outputs/apk/release/`。

包名仍为 `com.xposed.wetypehook`，测试证书与上游作者签名不同。已有上游版本时，是否允许覆盖取决于设备环境；若安装提示签名不兼容，保留旧应用与设置，先核对情况，不自动卸载。仓库 `AGENTS.md` 中的作者专用密钥路径在本机不存在；本轮未执行任何真机安装或验证。

## 启用

1. 安装本测试包，并在支持 Xposed API 102 的 LSPosed/兼容框架中启用模块，作用域选择微信输入法。
2. 重启微信输入法进程，在其“关于”页点击 Logo 进入模块设置。
3. 在“外观”打开“ColorOS 磨砂材质（实验）”，保存。
4. 模糊强度、圆角和高光使用同组现有选项，浅深色背景在“颜色”中设置。

## 真机调试验收

本次按用户要求只完成离线构建与逻辑检查；下列项目待拿到设备后执行：

- 核对手机型号、完整 ROM 版本、微信输入法版本、LSPosed API，以及安装签名和原设置备份。
- 确认 ColorOS 材质入口和保存后重新进入的状态；确认没有小米玻璃参数入口。
- 查看 `ColorOS material:` 日志，实际选中的通道可能是 `oplus`、`aosp`、`tint-only` 或 `opaque-fallback`。不记录输入文本、用户内容或账号数据。
- 浅/深色、普通键盘/表情/剪贴板、横竖屏、输入法实际切换、反复弹出/收起、分屏和硬件键盘下检查背景范围与四角。
- 在支持的设备上切换省电或窗口模糊状态，验证背景仍可读、恢复后重新模糊，且无残留模糊层。
- 比对系统凝光元素，并只读检查真实框架接口。若没有可用的凝光接口，保持本版磨砂路径；不强开系统全局属性或编造兼容声明。

具体构建、签名与测试结果见 [离线验证记录](coloros-validation.md)；最终文件校验值随测试版本的 `validation.md` 交付。
