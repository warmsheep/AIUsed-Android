# 验收记录

日期：2026-10-29  
环境：macOS 15.7.9 (x86_64)、JDK 21.0.13、Gradle 8.13、AGP 8.13.2、compileSdk 36、minSdk 26  
设备：Pixel_10_API_36 AVD（Android 16）

## 1. 构建

| 命令 | 结果 |
| --- | --- |
| `./gradlew :app:assembleDebug` | 通过 |
| `./gradlew :app:assembleRelease` | 通过（R8 + resource shrink） |
| `./gradlew :app:lintDebug` | 通过，0 error |
| `./gradlew :app:connectedDebugAndroidTest` | 通过，14/14 |

产物：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`
- `dist/AIUsage-1.0.0.apk`（zipalign + debug keystore 签名，仅侧载；SHA-256 `ecddd357a926fe20a354e1574490e9a9badc9d1326e431314166d5e20bb10fbc`）

## 2. JVM 单元测试

`./gradlew :app:testDebugUnitTest` → 39/39 通过。

覆盖：

- 智谱 5h/周百分比、字符串数字、auth code、空 limits
- MiniMax 百分比、积分余额、status 2/3、HTTP 200 内 auth code
- OpenCode Go used→remaining、rate-limited、缺失 usage
- DeepSeek CNY 选择、字符串金额、不可用状态
- Kimi Code 周/5h/月窗口、字符串数字、epoch 时间、套餐名
- Kimi Moonshot 余额
- HTTP 401/429/5xx/非 JSON/空 body 错误映射
- 时间格式化、重置倒计时、金额/百分比格式化

## 3. Android instrumentation 测试

`./gradlew :app:connectedDebugAndroidTest` → 14/14 通过。

覆盖：

- 空状态主行动按钮
- 供应商四格布局（不支持项仍占位）
- 添加供应商面板出现全部 5 家
- Keystore AES-GCM 加解密与损坏密文拒绝
- 小组件缓存写入/读取、不含密钥、receiver 导出与 metadata
- 5 家供应商 MockWebServer 集成：URL path、Authorization、OpenCode session header、Kimi API Key 不带 OAuth 专用 header、响应解析

## 4. 官方端点连通性

使用假 Key 直接请求（仅验证 TLS/DNS/错误结构，不包含任何真实凭据）：

| 供应商 | 端点 | 结果 |
| --- | --- | --- |
| 智谱国内 | `open.bigmodel.cn/api/monitor/usage/quota/limit` | HTTP 200 + `code:401` |
| 智谱国际 | `api.z.ai/api/monitor/usage/quota/limit` | HTTP 200 + `code:401` |
| MiniMax 国际 | `api.minimax.io/v1/token_plan/remains` | HTTP 200 + `status_code:1004` |
| MiniMax 国内 | `api.minimaxi.com/v1/token_plan/remains` | HTTP 200 + `status_code:1004` |
| OpenCode Go | `opencode.ai/zen/go/v1/usage` | HTTP 401 |
| DeepSeek | `api.deepseek.com/user/balance` | HTTP 401 |
| Kimi Code 国内 | `api.kimi.com/coding/v1/usages` | HTTP 401 |
| Kimi Code 国际 | `api.kimi.ai/coding/v1/usages` | HTTP 401 |
| Moonshot 国内 | `api.moonshot.cn/v1/users/me/balance` | HTTP 401 |
| Moonshot 国际 | `api.moonshot.ai/v1/users/me/balance` | HTTP 401 |

## 5. 手工 UI / 小组件验收

- 深色首页、浅色首页、阈值告警（Kimi 月 18%）、DeepSeek 仅余额、MiniMax 积分余额
- 空状态、添加面板、供应商选择、区域选择、密码遮罩、错误提示
- 设置面板主题切换即时生效，深/浅色均检查
- 添加面板真实请求假 Key，显示“密钥无效，或没有访问该套餐的权限”
- 小组件：4×2 三列显示 3 家（智谱/MiniMax/OpenCode），每张卡片内 5H/周/月三条横向进度条纵向排列，不支持项显示灰色空条和“—”
- 小组件：点击标题栏 `‹`/`›` 翻到第 2 页；DeepSeek 在第 1 列、Kimi 在第 2 列，第 3 列留空；x 坐标 132/428/724、y=263 与第 1 页完全一致
- 小组件触摸区：翻页箭头/刷新按钮使用 48dp 方形触摸区；实测点在箭头周围（非字形上）也能翻页
- 小组件固定卡片网格：卡片高度由本机字体度量动态计算，供应商卡片不再固定高度裁剪；列 x=132/428/724 固定，行间距 7dp
- 防遮挡：月额度条不再被固定卡片高度裁剪；供应商卡片内容本身决定行高，下一行从内容之后开始
- 小组件：拉高到 4×3（约 360×363dp）后显示两行卡片（3+2），5 家供应商一次全部显示，取消翻页；2×2 降级为单列单家
- 小组件：圆角平滑 Bitmap 红→绿渐变条，99% 精确显示、余额、未提供项置灰；浅色模式使用与主 App 一致的明亮渐变
- 小组件深色/浅色两套主题；App 设置深色 → 深色，设置浅色 → 浅色；无需再次点击刷新即可即时切换
- App 选择「跟随系统」后，系统深色/浅色切换，小组件同步（进程存活时）；进程被系统回收时最迟在下一次刷新/打开 App 更新
- 小组件点击供应商卡片打开 App；点击右上角刷新不会误打开 App，而会入队 WorkManager
- 小组件 header 显示更新时间与供应商总数
- Release APK 安装启动正常，WorkManager 周期任务已注册，AppWidget provider 已注册

截图：`docs/screenshots/`。本轮小组件证据：`widget-4x2.png`（深色第 1 页）、`widget-light.png`（浅色第 1 页）、`widget-page2.png`（浅色第 2 页）、`widget-4x3.png`（三列卡片、拉高后 5 家全量 + 占位）、`widget-4x4.png`（4×4 空槽位占位）。

## 6. 小组件主题说明

- 跟随的是 App 的「外观」设置：深色固定深色、浅色固定浅色、跟随时读取系统 `uiMode`。
- `Activity.onStop` 会在离开 App 时再补发一次 RemoteViews 更新，避免 Launcher 在前台时忽略后台更新。
- App 进程存活时通过 runtime `CONFIGURATION_CHANGED` / `AppWidgetManager.updateAll` 跟随系统；进程被系统杀死时只能等下一次 WorkManager 刷新或打开 App。
- 翻页状态保存在 Glance state（`widget_page`），由 `currentState()` 驱动重组；缓存和主题刷新会 bump `widget_version`，强制 Glance 会话重新读取缓存，避免 RemoteViews 复用旧内容。
- Glance Row/Column 最多 10 个直接子元素，三列 Row 只有 5 个子元素；渐变条改为单张平滑 Bitmap，按百分比透明裁剪，99% 精确显示。
- 三列卡片高度由本机字体度量动态计算，不再固定裁剪；更高尺寸每多一行增加 3 家，放得下时取消翻页。空槽位显示添加提示。标题栏左右按钮和刷新均为 48dp 触摸区。Bitmap 使用圆角矩形裁剪，和主 App 胶囊条一致。

## 7. 未能在本机验证的部分

1. **真实 Key 的成功响应**：没有用户凭据，成功响应结构依据官方文档/社区实测代码与 MockWebServer fixture 覆盖。首次使用建议对照各控制台数值。
2. **小米 HyperOS / 一加 ColorOS 实机**：使用标准 AppWidget，未在对应真机验证桌面宿主行为；小组件已在 Pixel Launcher 完整验证。
3. **系统主题切换实时性**：App 进程被系统杀死后无法立即响应 `CONFIGURATION_CHANGED`，需要下一次刷新/打开 App；这是 Android 广播模型限制，不是崩溃。
4. **长期后台刷新**：WorkManager + Doze/厂商省电策略无法用短时间测试完全覆盖；已做初始延迟、网络约束、手动刷新与前台刷新兜底。
5. **正式签名**：交付 APK 使用调试证书，仅适合侧载。

## 7. 已知风险与缓解

- 供应商内部接口可能变化：解析器对字段缺失/字符串数字/多种时间格式容错；失败保留旧数据并提示。
- MiniMax 以 HTTP 200 + body status 表示鉴权失败：已单独处理。
- 智谱国际使用裸 Authorization：已与国内 Bearer 区分。
- OpenCode `percent` 是已用百分比：已转换。
- Kimi API Key 与 OAuth 的 header 差异：API Key 路径不发送 `X-Msh-Platform`，并用集成测试锁定。

结论：构建、单元测试、UI 集成测试、lint、release 构建和小组件手工验收均通过；没有已知阻断性 bug。
