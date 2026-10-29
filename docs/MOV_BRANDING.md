# Mov. 品牌与兼容性

Mov. 是 Movement（乐章）的缩写，同时呼应视频格式 `.mov`。正式显示名称包含末尾句点；仓库、工程和安装包文件名前缀使用 `Mov`。

用户提供的两份 PNG 原稿保存在 `icon/mov-light.png` 与 `icon/mov-dark.png`，未修改原稿。应用内关于页通过 Android `drawable-night` 资源选择对应版本。

Android 自适应图标使用独立透明前景，搭配米白/炭黑背景与深色/浅金色前景。颜色资源位于 `values/colors_mov.xml` 和 `values-night/colors_mov.xml`。启动页和小组件使用同一标志。Android 13+ 的主题图标提供单色层，最终颜色由启动器决定；桌面图标何时刷新取决于启动器缓存，应用内主题不能保证即时切换桌面图标。

透明前景通过内置 `image_gen` 工具生成，使用 imagegen 技能的 `background-extraction` 模式。最终提示词：

> Use case: background-extraction. Edit target: supplied Mov. music player app icon. Extract ONLY the dark M musical-staff glyph, its attached bottom-left music note and inner right-pointing play triangle. Remove all ivory square tile, black outer background, paper texture, shadows and lighting. Preserve the exact original flowing five-staff-line M silhouette and arrangement faithfully, do not redesign it. Output one centered solid black glyph with true transparent background, square 1024x1024 canvas; glyph bounds approx x=180..844 y=240..770 (Android adaptive icon safe zone). All spaces between staff lines and around the play triangle are transparent. Crisp antialiased edges, no text, no tile, no border, no new elements. This is a production transparency mask used with native light/dark tints.

工具实际返回 1254×1254 RGBA；透明通道保留，Android 原生资源负责适配尺寸与着色。

更名覆盖用户可见品牌、工程名称、APK 文件名、GitHub 项目文档和反馈入口。以下旧标识保留用于兼容：

- `moe.ouom.neriplayer` 代码命名空间与 `.classical` 安装 ID，保证原生绑定和旧版本覆盖更新。
- 原有签名、数据库/偏好键、下载根目录、`neriplayer-sync.json`、备份格式和深链，避免丢失已有数据或断开同步。
- 上游版权、历史记录、外部依赖/子模块的名称与地址。

不能对源代码中所有 `neri` 进行无差别字符串替换；上述内容在代码和维护文档中可继续出现。
