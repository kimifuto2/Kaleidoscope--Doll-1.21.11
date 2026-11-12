# 玩家玩偶分支进展（port-player-doll-branch）

> 分支起点：`port-player-doll-WIP-src-20261112.zip` 的完整源码复制（独立可构建工程）。
> 依据文档：`KaleidoscopeDoll-1.21.11-Fabric-完整移植分析与修复方案.md`。
> 当前阶段：**阶段 1（只加日志）已完成并在开发服实机跑过一轮**。

## 1. 本分支已做的改动

- 新增 `debug/DollDebug.java`：统一诊断日志，字段 side/stage/name/uuid/profileNull/profileType/propertyKeys/hasTextures，带渲染热路径节流；不会打印完整 textures Base64。
- 已在 10 个位置埋点（仅日志，未改业务逻辑）：
  1. `PlayerProfileResolver`：cacheCheck / fetchByName / fetchProfile / exception / final。
  2. `ComputerMenu.craftPlayerDoll`：完成回调、异常、输出栈写入。
  3. `PlayerDollItem.place`：持有物品的组件、点击位置/朝向、落点、BE 是否找到、写入后状态。
  4. `PlayerDollBlockEntity.applyImplicitComponents`：来源组件、赋值后 Profile。
  5. `PlayerDollBlockEntity.saveAdditional`：写入前 Profile 状态。
  6. `PlayerDollBlockEntity.loadAdditional`：codec 读取结果、旧存档回退。
  7. `PlayerDollBlockEntity.getUpdateTag`：Owner 是否进入同步数据。
  8. `PlayerDollGeoModel`：渲染时 BLOCKPOS、BE 查找、Profile。
  9. `PlayerDollSkins.get`：缓存是否初始化、是否有 textures、命中哪条分支。
  10. `PlayerDollItemRenderer.addRenderData`：是否执行、ItemStack 组件、DataTicket 写入。

## 2. 构建与资源验证

- `./gradlew compileJava compileClientJava`：成功。
- `./gradlew build -x test`：BUILD SUCCESSFUL，产物 `build/libs/kaleidoscopedoll-1.21.11-fabric-1.0.9.jar`（1,486,134 字节）。
- `node tools/verify_assets.mjs`：17 OK / 0 MISS / 0 WARN / 2 INFO（硬性检查全部通过）。

## 3. 实机日志证据（开发服 + 开发客户端）

测试流程：`setblock` 放一个带旧字段 `OwnerName/OwnerUuidMost/OwnerUuidLeast` 的玩家玩偶 → 用电脑 `@+KimiFuto` 制作 → 连续放置 4 个。

### 3.1 服务端 Profile 解析（关键：有代理时完全正常）

```
[SERVER] stage=resolver.fetchByName name=KimiFuto uuid=fa63d95c-... profileType=GameProfile propertyKeys=[textures] hasTextures=true
[SERVER] stage=resolver.fetchProfile name=KimiFuto uuid=fa63d95c-... propertyKeys=[textures] hasTextures=true
[SERVER] stage=resolver.final name=KimiFuto ... hasTextures=true offlineFallback=false cached=true
[SERVER] stage=computer.craft.stack name=KimiFuto ... profileType=ResolvableProfile hasTextures=true
```

→ 解析、缓存、制作栈组件都带 textures。用户环境此前看到 Steve，更可能是本地网络/解析失败路径（需按文档阶段 2 处理失败状态与缓存）。

### 3.2 放置（发现一个确定的 bug）

```
[SERVER] stage=place.stack name=KimiFuto ... hasTextures=true clickedPos={x=5,y=106,z=2} face=up
[SERVER] stage=place.be placedPos={x=5,y=107,z=2} beFound=false
[SERVER] stage=be.updateTag pos={x=5,y=106,z=2} hasOwner=true hasOwnerName=true
```

→ **`place()` 用 `getClickedPos().relative(face)` 算出来的落点比实际方块高 1 格（y=107），`beFound=false`**。
→ 但 `applyImplicitComponents`（BlockItem 原版放置流程）仍然把带 textures 的 Profile 写入了 BE（`updateTag hasOwner=true`）。
→ 结论：`PlayerDollItem.place()` 这段手动写 BE 是**多余且坐标错误**的，应删除，或改用 `Block#setPlacedBy` 回调；当前它没有造成数据丢失只是因为它写入失败。

### 3.3 客户端同步（关键：同步本身是成功的）

```
[CLIENT] stage=be.loadAdditional.afterCodec name=KimiFuto uuid=fa63d95c-... profileType=GameProfile propertyKeys=[textures] hasTextures=true pos={x=5,y=106,z=2}
```

→ 客户端方块实体拿到了**带 textures 的完整 Profile**。所以“客户端没收到数据”这一假设可以排除（至少在本环境下）。

### 3.4 尚未解释的现象：渲染模型日志没有触发

- 客户端日志中 `stage=geoModel.*`、`stage=skins.*`、`stage=itemRenderer.*` 均为 **0 条**（已确认编译产物里含 DollDebug 调用）。
- 场景中能看到坐姿玩偶，但可能来自模组自带的固定玩偶（作者/赞助玩偶）或其它渲染路径；不能据此认定玩家玩偶 BER 在渲染。
- 需要下一步验证：在 `PlayerDollGeoModel` 构造函数加一条日志，确认 BER 是否被实例化；再对一个**明确知道坐标**的玩家玩偶方块截图。若 BER 未实例化，检查 `BlockEntityRendererRegistry.register` 与 GeckoLib 注册时机。

## 4. 下一步（按方案文档阶段）

1. **阶段 1 收尾**：确认玩家玩偶 BER 是否实际被调用（构造日志 + 定点截图）；确认可见玩偶的来源。
2. **阶段 2**：`PlayerProfileResolver` 去掉“离线 UUID 自动兜底”，引入成功/不完整/失败状态，不缓存失败，GUI 明确提示。
3. **阶段 3**：BE/物品/渲染统一使用 `ResolvableProfile`（含旧存档回退）。
4. **阶段 4**：删除 `place()` 手动写 BE（或改 `setPlacedBy`），保留 `applyImplicitComponents` 唯一写入路径。
5. **阶段 5**：`PlayerDollSkins` 不再因缺少 textures 直接返回默认皮肤；先交给 `PlayerSkinRenderCache` 解析，失败再兜底。
6. **阶段 6**：物品 GeckoLib 渲染入口与 `items/player_doll.json` 统一（当前 JSON 指向方块模型，GeoItemRenderer 实际未走）。

## 5. 如何继续

```powershell
cd C:\AI\ds harness\工作区\森罗物语移植\port-player-doll-branch
$env:JAVA_HOME="C:\BianChengJA\JDK21"
$env:JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7899 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7899 -Dfile.encoding=UTF-8"
.\gradlew.bat build -x test
```

开发服/客户端：

```powershell
.\gradlew.bat runServer
.\gradlew.bat runClient "-PquickPlayServer=localhost:25565"
```

日志关键词：`KaleidoscopeDoll/Diag`、`stage=`。


---

## 6. 第一步结论（BER 是否被调用）

### 已确认

1. `BlockEntityRendererRegistry.register(...)` 的 provider 被调用一次：
   ```
   [CLIENT] stage=ber.register blockEntity=...
   ```
2. `PlayerDollGeoModel` 被构造一次：
   ```
   [CLIENT] stage=geoModel.constructed class=PlayerDollGeoModel
   ```
3. 在已知坐标 (0,97,0) 用 `setblock` 放置一个带旧字段 Profile 的玩家玩偶，画面中**确实渲染出坐姿玩家玩偶**（截图 `scratch/branch-step3.png`），说明 GeckoLib 方块实体渲染链路在工作。

### 仍未解释

- `getModelResource` / `getTextureResource` 内部的 `geoModel.skinBE` 与 `PlayerDollSkins.get` 日志**仍然 0 条**（构造日志之后没有任何皮肤路径日志）。
- 可能性：
  a. GeckoLib 在构造/烘焙阶段缓存了模型与贴图，调用发生在我们的日志点之外（需要在 `GeoRenderer.getTextureLocation` 或渲染事件里再埋一个点验证）；
  b. 画面中的玩偶不是该 BER 渲染（需换一个绝对可区分的贴图/模型测试）。
- 下一步探针：在 `PlayerDollGeoModel.getTextureResource` 最前面无条件打一行日志（当前日志在 `skin()` 内且带节流），以及尝试 GeckoLib 的 `GeoRenderEvent.Block.CompileRenderState` 事件打点，确认渲染状态管线是否真的经过本模型。


---

## 7. 第一步完成：BER 渲染链路确认

### 直接证据

1. provider 被调用一次：`[CLIENT] stage=ber.register`
2. GeoModel 被构造：`[CLIENT] stage=geoModel.constructed class=PlayerDollGeoModel`
3. 上一轮构建（带入口日志）里 `getTextureResource` / `getModelResource` 各被调用 **77,403 / 77,402 次**（每帧）：
   ```
   stage=geoModel.getTextureResource called=true
   stage=geoModel.getModelResource  called=true
   ```
4. 已知坐标 (0,95,0) 放置的玩家玩偶在画面中正常渲染为坐姿玩家模型（截图 `scratch/branch-step4.png` / `branch-step5.png`）。

→ **结论：GeckoLib 方块实体渲染器确实被调用，模型/贴图钩子确实在执行。**

### 新发现的疑点（下一步排查目标）

- 在 `getTextureResource` / `getModelResource` 入口日志能打出来的同一构建里，`skin()` 内部的
  `geoModel.skinBE` 与 `PlayerDollSkins.get` 日志 **一次都没有出现**。
- 这强烈暗示 `skin()` 在第一条语句就抛异常（最可疑：`state.getGeckolibData(DataTickets.BLOCKPOS)`），
  异常被 GeckoLib 的调用方吞掉，随后回退默认贴图 → 画面显示 Steve。
- 已在该分支加入 try/catch 探针（`geoModel.getTextureResource.error` / `getModelResource.error`），
  但当前这次运行模型/贴图钩子没有再次被调用（疑似 GeckoLib 烘焙缓存），需要在“新放置一个玩偶/重新加载资源”时触发一次再读日志。
- 若确认是 `getGeckolibData` 抛异常，修复方向：不要从渲染状态取 BLOCKPOS，改为在 `GeoBlockRenderer`
  的渲染数据里显式塞入 BE 引用/Profile（或换用 GeckoLib 的 Block 渲染事件获取 BlockEntity）。

### 下一步

1. 触发一次全新渲染（重进世界/重新放置玩偶）并读取 `geoModel.*.error` 探针日志；
2. 确认异常来源后修正 `PlayerDollGeoModel.skin()` 的取数方式；
3. 然后进入方案文档阶段 2（解析失败状态与缓存）。
