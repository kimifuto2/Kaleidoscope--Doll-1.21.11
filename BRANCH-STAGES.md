# 六阶段实施记录（port-player-doll-branch）

> 依据 `KaleidoscopeDoll-1.21.11-Fabric-完整移植分析与修复方案.md`，在独立分支完成 6 个阶段的代码改动。
> 构建：`BUILD SUCCESSFUL`（jar 1,481,197 字节）；`node tools/verify_assets.mjs`：17 OK / 0 MISS / 0 WARN。

## 阶段 1：诊断日志（已完成）

- 新增 `debug/DollDebug.java`，在解析/制作/放置/BE 保存/读取/UpdateTag/GeoModel/皮肤缓存 10 处埋点。
- 实机确认：BER 注册与 GeoModel 构造正常；`getModelResource`/`getTextureResource` 每帧被调用（77k 次）。
- 发现旧实现 `skin()` 依赖 `DataTickets.BLOCKPOS` 反查 BE，属于不可靠路径（日志缺失的疑点）。

## 阶段 2：服务端档案解析（已完成）

`PlayerProfileResolver` 重写：

- `Result(status, profile, detail)`，状态：`SUCCESS_FULL` / `SUCCESS_UUID_ONLY` / `NOT_FOUND` / `FAILED`；
- **移除离线 UUID 自动兜底**（不再用 `OfflinePlayer:` 冒充正版玩家）；
- 只缓存带 `textures` 的完整档案；失败/不完整不缓存；
- 记录 `fetchByName` / `fetchProfile` 异常；
- `ComputerMenu`：`NOT_FOUND` → “找不到玩家 %s”，`FAILED` → “玩家档案解析失败”，不再无声返回；
- 新增中英文语言键 `player_not_found` / `resolve_failed`。

## 阶段 3：统一 ResolvableProfile（已完成）

- `PlayerDollBlockEntity.ownerProfile` 改为 `ResolvableProfile`；
- 保存/读取统一走 `ResolvableProfile.CODEC`（`output.store` / `input.read`）；
- 旧存档 `OwnerName`/`OwnerUuidMost`/`OwnerUuidLeast` 仍可回退读取；
- `collectImplicitComponents` / `applyImplicitComponents` 直接透传 `ResolvableProfile`；
- `PlayerDollBlock.getCloneItemStack/useWithoutItem`、`PlayerDollItem.getName` 全部适配。

## 阶段 4：放置与同步（已完成）

- **删除** `PlayerDollItem.place()` 手动按 `clickedPos.relative(face)` 写 BE 的逻辑（该坐标此前实测偏移一格，`beFound=false`）；
- 统一由原版 `BlockItem` 放置流程 → `applyImplicitComponents` 写入档案；
- `applyImplicitComponents` 写完后 `setChanged()` + `sendBlockUpdated(...,3)`；
- `getUpdateTag()` 仍使用 `saveWithoutMetadata`，日志确认包含 `Owner`。

## 阶段 5：原版皮肤解析（已完成）

- `PlayerDollSkins.get(ResolvableProfile)`：**不再因缺少 textures 直接返回默认皮肤**，统一交给 `PlayerSkinRenderCache.getOrDefault(...)`；失败才兜底默认皮肤，并记录 `skins.resolved` / `skins.fallback` / `skins.error`；
- 新增 `CompileBlockRenderStateEvent.EVENT` 监听：渲染状态编译时写入自定义 `DataTicket<ResolvableProfile>`；
- `PlayerDollGeoModel` 改为从渲染状态的 DataTicket 取档案，**删除 BLOCKPOS 反查**；
- 实机日志已证实：
  ```
  stage=skins.resolved name=KimiFuto ... hasTextures=true texture=minecraft:skins/27df4d6fa055a712328c3b9b0ae57eb38f72dbdc
  stage=skins.resolved name=Notch   ... hasTextures=false texture=minecraft:textures/entity/player/wide/noor.png
  stage=skins.fallback name=? ... reason=no-profile
  ```

## 阶段 6：物品渲染统一（已完成）

- 选择方案：物品继续使用**普通方块模型**（与其他固定玩偶完全同一渲染管线，图标/手持一致）；
- 移除 `PlayerDollItem implements GeoItem`、`geoRenderProvider`、`createGeoRenderer/getRenderProvider`；
- 删除 `PlayerDollItemModel` / `PlayerDollItemRenderer` 与客户端 provider 注册；
- `items/player_doll.json` 指向 `kaleidoscope_doll:block/doll/player_doll`（白模），不再出现“Java 声称 GeckoLib、资源却走普通模型”的不一致。

## 已验证 / 未验证

已验证（开发服 + 开发客户端）：

- 编译、构建、资源静态检查通过；
- BER 被调用、GeoModel 构造；
- 新皮肤链路 `skins.resolved` 命中真实纹理 id（含 `minecraft:skins/...` 动态皮肤）；
- 旧存档 `OwnerName/OwnerUuid` 回退读取；
- 方块实体 `Owner` 写入同步数据。

未验证（受离线开发环境限制，需要用户在线客户端）：

- 真实玩家皮肤在画面上的最终呈现（开发端离线，皮肤下载不可用）；
- 电脑 GUI 制作 → 放置 → 皮肤 的完整手动流程（GUI 自动化不稳定）；
- 多人、重进世界、区块重载、破坏拾取回收的持久化验证。

## 建议的验收步骤

1. 在测试实例安装分支 jar（需 GeckoLib 5.4.5），启动在线客户端；
2. 电脑输入 `@+正版ID` 点 ✔ → 取输出 → 放置；
3. 检查玩偶是否显示真实皮肤、朝向/坐姿正确；
4. 退出重进、区块重载、破坏拾取重放，确认档案不丢；
5. 回归固定玩偶与电脑功能。
