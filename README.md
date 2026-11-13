# Kaleidoscope Doll — Minecraft 1.21.11 Fabric 移植版

将 **Kaleidoscope Doll（森罗物语：玩偶）** 移植到 **Minecraft 1.21.11 / Fabric / mojmap**，并新增可自定义玩家皮肤的玩家玩偶与实体玩家玩偶。

> 上游项目：<https://github.com/KaleidoscopeMods/KaleidoscopeDoll>
> 参考实现：<https://github.com/lisuchen118/Kaleidoscope-Doll-Workshop>（玩家玩偶的渲染与头饰方案）

## 功能特性

### 普通玩偶

- `doll_0` ~ `doll_275` 共 **276 个**方块玩偶，可放置、击打音效、放进展示框；
- 支持切石机与礼品盒获取，与上游一致。

### 玩家玩偶

- 用白色羊毛在**电脑**中合成，或输入 `@玩家名` 指定任意玩家；
- GeckoLib 3D 模型 + **真实玩家皮肤**（自动识别 Steve/Alex 模型）；
- 坐姿待机动作，物品栏图标同样是带皮肤的 3D 模型；
- 可放置、可击打掉落，掉落物的大小/高度/旋转与普通玩偶一致。

### 实体玩家玩偶

- **玩家玩偶 + 粘液球** → 实体玩偶物品（保留玩家档案）；
- 放下后生成带皮肤、坐姿的实体；
- **Shift + 右键蓄力投掷**：按住越久扔得越远，飞行中旋转；
- 可把玩偶放上可骑乘的实体（牛/船/矿车等）；
- 右键玩偶有 **Q 弹压扁/回弹**形变；

### 电脑 GUI

- 白色羊毛 → 玩偶，实时搜索；
- `@玩家名` 在线预览，**停止输入 0.5 秒**后自动查询，只查询合法玩家名；
- 非合法 ID 会提示「非合法玩家 ID」；
- 输出槽支持堆叠：连续制作时玩偶数量递增，**只有成功产出才扣羊毛**；
- 查询失败会区分「找不到玩家」「查询过于频繁」「网络失败」，并对结果做缓存与节流，避免触发 Mojang 限流（429）。

### 头盔与 Trinkets

- 玩家玩偶可放入**原版头盔槽**；
- 全部玩偶支持 **Trinkets** 帽子槽（277 项：玩家玩偶 + 276 个普通玩偶）；

### 成就

- 获得属于自己的玩家玩偶时解锁【这是我吗】。

## 环境要求

| 项 | 版本 |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.3+ |
| Fabric API | 0.141.5+1.21.11 |
| GeckoLib | 5.4.5（Fabric 1.21.11） |
| JDK（仅构建） | 21 |

可选依赖：

- **Trinkets 3.11.1+**：头饰槽；未安装时自动跳过，不影响启动；
- **Xaero's Minimap / World Map**：地图实体图标兼容（不装也能正常玩）。

## 安装

1. 安装 Fabric Loader 0.19.3+ 与 Fabric API；
2. 把 **GeckoLib 5.4.5（Fabric 1.21.11）** 与本模组 jar 放进 `mods/`；
3. 可选：安装 Trinkets（头饰槽）与 Xaero's Minimap / World Map（地图图标）。

## 使用说明

| 操作 | 说明 |
| --- | --- |
| 普通玩偶 | 玩偶抽奖机的礼品盒获取，放置后左键可击落 |
| 电脑合成玩家玩偶 | 放置电脑方块 → 放入白色羊毛 → 选择玩偶 → 等待进度完成 |
| 指定玩家 | 输入框输入 `@玩家名`，停手 0.5 秒预览，确认后制作 |
| 指定玩家实体玩偶 | 玩家玩偶 + 粘液球 → 右键地面放置 |
| 蓄力投掷 | 手持实体玩偶物品，Shift + 右键按住后松开 |
| 骑乘放置 | 手持实体玩偶物品右键可骑乘实体 |
| 头饰 | 玩家玩偶 → 头盔槽；任意玩偶 → Trinkets 帽子槽 |

## 构建

```bash
git clone https://github.com/kimifuto2/Kaleidoscope--Doll-1.21.11.git
cd Kaleidoscope--Doll-1.21.11
./gradlew build
```

产物：`build/libs/kaleidoscopedoll-1.21.11-fabric-1.0.9.jar`

> GeckoLib 以本地依赖形式放在 `libs/`，克隆后可直接构建，无需额外配置。

## 项目结构

```
src/main/java/...       通用逻辑（方块、物品、实体、网络、配方、事件、配置）
src/client/java/...     客户端渲染（GeckoLib、GUI、Trinkets 头饰、mixin）
src/main/resources/     模型、贴图、语言、配方、成就、标签、Xaero 图标定义
libs/                   GeckoLib 本地依赖
```

## 配置项

`config/kaleidoscope_doll.json`（或同名 `.properties`，取决于加载器）中提供：

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `DollCanBeThrown` | `true` | 是否允许 Shift + 右键蓄力投掷 |
| `DollThrowParticleEffect` | `true` | 投掷飞行时是否显示粒子 |
| `DollCanMountEntities` | `true` | 是否允许把玩偶放到可骑乘实体上 |

## 已知限制

- 玩家玩偶皮肤依赖在线档案解析：离线/无网络时回退为默认皮肤；
- Mojang 玩家名查询有频率限制（429），频繁查询后需要等待几分钟恢复；
- Trinkets 头饰通过反射注册，未安装 Trinkets 时该功能静默跳过；
- Xaero 地图图标需要 Xaero's Minimap / World Map（通过模组内置资源包定义生效）；

## 更新日志

### v1.0.9

- 新增实体玩家玩偶：粘液球合成、放置、骑乘跟随、蓄力投掷、Q 弹形变、掉落保留档案；
- 新增 `@玩家名` 在线预览、0.5 秒防抖、合法名校验与查询限流处理；
- 电脑输出槽支持堆叠，修复重复制作时羊毛被扣但玩偶被覆盖的问题；
- 修复帽子槽与头盔槽同时装备玩偶时的渲染重叠；
- 新增 Xaero's Minimap / World Map 实体图标；
- 修复玩家玩偶方块/实体渲染器注册缺失、皮肤缓存未初始化的问题。

## 许可与致谢

- 本仓库是 **Kaleidoscope Doll** 的 1.21.11 Fabric 移植版，原模组版权归上游作者所有；如上游有 License 声明，以其为准。
- 玩家玩偶的物品/头饰变换参考 **Kaleidoscope Doll Workshop**（作者 Li Suchen）。
- 头饰贴合方案参考 **SimpleHats**（作者 fonnymunkey）。
- 地图图标定义格式参考 **Xaero's Minimap**。
- GeckoLib 版权归其作者所有。
