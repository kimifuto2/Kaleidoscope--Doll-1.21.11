# Kaleidoscope Doll — Minecraft 1.21.11 Fabric 移植版

将 **Kaleidoscope Doll（森罗物语：玩偶）** 移植到 **Minecraft 1.21.11 / Fabric / mojmap**，并新增可自定义玩家皮肤的玩家玩偶。

> 上游项目：<https://github.com/KaleidoscopeMods/KaleidoscopeDoll>
> 参考实现：<https://github.com/lisuchen118/Kaleidoscope-Doll-Workshop>（玩家玩偶的渲染与头饰方案）

## 功能

- **普通玩偶**：`doll_0` ~ `doll_275` 共 276 个方块玩偶，可放置、可击打、可放入展示框等
- **玩家玩偶**：用白色羊毛在**电脑**中合成，或用 `@玩家ID` 指定任意玩家
  - GeckoLib 3D 模型 + 真实玩家皮肤（含 Steve/Alex 模型自动识别）
  - 坐姿待机动作、物品栏图标带皮肤
  - 可放置、可击打掉落，掉落物大小/高度/旋转与普通玩偶一致
- **电脑 GUI**
  - 羊毛 → 玩偶，实时搜索、`@玩家名` 在线预览
- **成就**：获得属于自己的玩家玩偶时解锁【这是我吗】
- **头盔 / Trinkets**：玩家玩偶可装备到原版头盔槽；全部玩偶支持 **Trinkets** 头饰槽（含 276 个普通玩偶），加载动画资源包后仍贴合头部（FA系列）

## 环境要求

| 项 | 版本 |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.3+ |
| Fabric API | 0.141.5+1.21.11 |
| GeckoLib | 5.4.5（Fabric 1.21.11） |
| JDK | 21 |

可选：**Trinkets 3.11.1+**（头饰槽；未安装时模组自动跳过，不影响启动）。

## 构建

```bash
git clone https://github.com/kimifuto2/Kaleidoscope--Doll-1.21.11.git
cd Kaleidoscope--Doll-1.21.11
./gradlew build
```

产物：`build/libs/kaleidoscopedoll-1.21.11-fabric-1.0.9.jar`

> GeckoLib 以本地依赖形式放在 `libs/`，克隆后可直接构建，无需额外配置。

## 安装

1. 安装 Fabric Loader 0.19.3+ 与 Fabric API；
2. 把 GeckoLib 5.4.5（Fabric 1.21.11）与本模组 jar 放进 `mods/`；
3. 可选：安装 Trinkets 以使用头饰槽。

## 使用

| 操作 | 说明 |
| --- | --- |
| 电脑合成 | 放置电脑方块，放入白色羊毛 → 选择玩偶 → 等待进度完成 |
| 指定玩家 | 在电脑输入框输入 `@玩家ID`，预览确认后合成 |
| 装备头饰 | 玩家玩偶放入头盔槽；任意玩偶放入 Trinkets 帽子槽 |
| 击打玩偶 | 空手/任意物品左键即可掉落（创造模式不消耗） |

## 项目结构

```
src/main/java/...       通用逻辑（方块、物品、实体、网络、配方、事件）
src/client/java/...     客户端渲染（GeckoLib、GUI、Trinkets 头饰）
src/main/resources/     模型、贴图、语言、配方、成就、标签
libs/                   GeckoLib 本地依赖
```

## 已知限制

- 玩家玩偶的皮肤依赖在线档案解析：离线/无网络时回退为默认皮肤
- Trinkets 头饰渲染通过反射注册，未安装 Trinkets 时该功能静默跳过
- 实体版玩家玩偶（粘液球实体化）未完成

## 许可与致谢

- 本仓库是 **Kaleidoscope Doll** 的 1.21.11 Fabric 移植版，原模组版权归上游作者所有；如上游有 License 声明，以其为准。
- 玩家玩偶的物品/头饰变换参考 **Kaleidoscope Doll Workshop**（作者 Li Suchen）。
- 头饰贴合方案参考 **SimpleHats**（作者 fonnymunkey）。
- GeckoLib 版权归其作者所有。
