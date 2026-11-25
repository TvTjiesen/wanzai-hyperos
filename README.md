# 玩在城院 · HyperOS 原生版

浙大城市学院校园聚合入口的**原生 Android 重写**：Kotlin + Jetpack Compose + [Miuix](https://github.com/compose-miuix-ui/miuix)
（小米 HyperOS 设计语言的 Compose 实现），配合本地 vendored 的
[Glasmio](https://github.com/NEORUAA/Glasmio) 液态玻璃组件（iOS 26/27 观感）。

> 这是个人学习项目，与学校官方无关。仓库内不含任何真实学号、姓名、密码或设备取证资料。

---

## 它做什么

把「玩在城院」的 Flutter 版重写为原生 Android 应用：四个主页面、门户登录与业务端点的真数据接入、
教务（课表 / 考试 / 成绩 / 讲座）与「学在城院」手机版免密登录、液态玻璃 UI 与自定义主题。

---

## 快速开始

```bash
# 需要 JDK 21 + Android SDK（compileSdk 37）
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"

./gradlew :app:testDebugUnitTest --no-daemon --console=plain   # 单元测试
./gradlew :app:assembleOptimized  --no-daemon --console=plain   # R8 发布的 APK
```

产物：`app/build/outputs/apk/optimized/app-optimized.apk`

---

## 模块结构

```
app/src/main/java/com/muyao/wanzaihyperos/
├── MainActivity.kt / WanzaiRoot.kt   应用入口与根导航
├── nav/           路由与转场
├── ui/            Compose 页面（main / home / jw / tronclass / szty / web …）与设计系统
├── data/          仓库层：门户、教务（jw）、CJH52、TronClass、主题与偏好
├── net/           HTTP 客户端、Cookie 罐、限流、脱敏、CAS 加密
└── session/       会话与 Android Keystore 加密存储

third_party/glasmio/   本地 vendored 的 Glasmo 玻璃组件库（Apache-2.0，含上游声明）
tools/gen_ai_ball_glyphs.py   AI 小球字形生成器（单元测试会读取它）
```

网络层组装的唯一入口是 `AppGraph`：它持有唯一的 `HzcuClient`（持有 Cookie 罐，两份客户端 = 两份罐 = 登录状态漂移）。

---

## 几条硬性纪律

1. **凭据不进入日志或仓库**：普通登录只在流程内使用密码；教务的「记住凭据」须由用户选择，保存时使用 Android Keystore 加密。
2. **日志只记 host 与参数名，永不记值**（`net/Redaction.kt`）。
3. **限流严格串行**：全局 ≥1000ms、单端点 ≥1000ms，不是令牌桶。
4. **抓取失败保留旧内容**：`retainCachedOnFailure` 语义，失败不清缓存。
5. **不伪造成功**：拿不到的数据显示「未知 / —」，不用入口截图冒充成功状态。

---

## 测试

`./gradlew :app:testDebugUnitTest` 覆盖 1589 项单元测试（其中 12 项按平台条件跳过）。
部分测试会读取仓库内的 `tools/gen_ai_ball_glyphs.py` 与 debug/androidTest 源码做契约校验，
因此须在仓库根目录运行。

---

## 署名与许可

- 应用代码：© TvTjiesen（[GitHub](https://github.com/TvTjiesen)），采用 [Apache License 2.0](LICENSE)。
- `third_party/glasmio/`：摘自 [Glasmio](https://github.com/NEORUAA/Glasmio)（Apache-2.0），
  其上游第三方声明见 `third_party/glasmio/THIRD_PARTY_NOTICES.md`（含 Kyant0 的
  AndroidLiquidGlass / Backdrop / Capsule / Shapes、AndroidX、Reorderable、Miuix 等）。
- 依赖库各自的许可证随其分发包提供。

你可以依据 Apache License 2.0 使用、修改和分发本项目；第三方服务（学校门户、教务系统）的访问权限及使用仍须遵守其自身条款。本项目不授予这些服务的数据或账号权限。
