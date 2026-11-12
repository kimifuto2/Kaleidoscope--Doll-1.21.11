# KaleidoscopeDoll 1.21.1 → 1.21.11 Fabric 移植说明

上游：`https://github.com/KaleidoscopeMods/KaleidoscopeDoll` 分支 `1.21.1-fabric`（commit `46453cc`）
本工程：`C:\AI\ds harness\工作区\森罗物语移植\port`
产物：`build/libs/kaleidoscopedoll-1.21.11-fabric-1.0.9.jar`

## 1. 构建环境

| 项 | 值 |
| --- | --- |
| Minecraft | 1.21.11 |
| 映射 | 官方 Mojang 映射（mojmap，`loom.officialMojangMappings()`，无 parchment） |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.141.5+1.21.11 |
| Loom | 1.17.19（Gradle 9.5.1） |
| Java | 21 |

```powershell
$env:JAVA_HOME="C:\BianChengJA\JDK21"
$env:JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7899 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7899"
cd port
.\gradlew.bat build -x test
```

数据/资源 JSON 由 `tools/generate_data.mjs` 生成到 `src/main/generated`（已加入 resources），
**不再运行 Fabric datagen**；`src/main/java/.../datagen/**` 与 `compat/jei/**` 保留源码但被 `build.gradle` 排除编译。

## 2. Java 侧主要迁移点（1.21.1 mojmap → 1.21.11 mojmap）

- `ResourceLocation` → `Identifier`；`ResourceKey.location()` → `identifier()`
- 注册：`BlockBehaviour.Properties.setId(ResourceKey)` / `Item.Properties.setId(...)` 成为必需
- `EntityType.Builder.build(ResourceKey)`；`Registry.register(registry, ResourceKey, value)`
- `Level.isClientSide` 字段 → `isClientSide()` 方法
- `Entity`：`hurt` → `hurtServer(ServerLevel, DamageSource, float)`；`kill()` → `kill(ServerLevel)`；`spawnAtLocation` 需要 `ServerLevel`；`addAdditionalSaveData/readAdditionalSaveData` 改用 `ValueOutput/ValueInput`（配合 `TagValueOutput/TagValueInput`）；`hasImpulse` 移除，改用 `hurtMarked`；`canBeCollidedWith(Entity)`
- `SynchedEntityData` 的 `EntityDataSerializers.VECTOR3` 类型变为 `Vector3fc`
- `Block.updateShape(...)` 新签名（带 `ScheduledTickAccess` + `RandomSource`）；`Block` 不再有 `appendHoverText`，方块物品 tooltip 改由 `ModBlockItem` 提供
- `Item.use` 返回 `InteractionResult`；`releaseUsing` 返回 boolean；`getUseAnimation` 返回 `ItemUseAnimation`
- 1.21.11 物品 tooltip 签名：`appendHoverText(ItemStack, TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)`
- `CustomRecipe.getSerializer()` 返回 `RecipeSerializer<? extends CustomRecipe>`；`SimpleCraftingRecipeSerializer` 已删除，改用 `CustomRecipe.Serializer<>(Factory)`
- `GameRules` 移到 `net.minecraft.world.level.gamerules.GameRules`，读取用 `getGameRules().get(GameRules.ENTITY_DROPS)`
- 实体渲染改为 render state 管线：`createRenderState()` / `extractRenderState(...)` / `submit(..., SubmitNodeCollector, CameraRenderState)`，方块用 `submitBlock(...)` 提交
- `BlockRenderLayerMap.putBlock(block, ChunkSectionLayer.CUTOUT)`（模型里的 `render_type` 字段已失效，全部删除）
- `BuiltinItemRendererRegistry` 已从 Fabric API 移除：实体玩偶物品改为在物品栈上写 `DataComponents.ITEM_MODEL`（指向 `assets/kaleidoscope_doll/items/<doll>.json` 的模型），实现"物品外观随内部方块变化"

## 3. 依赖与配置的取舍

- **ForgeConfigAPIPort 已移除**：其 Fabric 21.11.1 构件的包名/入口与旧版不同，为避免脆弱依赖，`GeneralConfig` 改为自带的 JSON 配置
  （`config/kaleidoscope_doll-common.json`，键名与旧 TOML 一致：`YellowDollGiftBoxWeight` 等）。
- **JEI 兼容层未编译**：JEI 27.x API 与 19.x 差异较大，本次交付不包含 JEI 插件（源码保留）。
- **Fabric datagen 未启用**：模型/数据改由脚本生成，见第 4 节。
- `pack.mcmeta` **有意不随 jar 发布**：1.21.11 里同一个 pack.mcmeta 会被资源侧（resource 75.0）与数据侧（data 94.1）
  用不同的校验器读：资源侧拒绝 `supported_formats`（"deprecated starting from pack format 65"），数据侧则要求
  `pack_format` + `supported_formats` + `min_format`/`max_format` 四件套（`[75,0]..[94,1]` 的写法仍会报
  "require a supported_formats field"）。实测**不放 pack.mcmeta** 时客户端与服务端都不报错、资源与数据都正常加载
  （与原版 mod 的常见做法一致），故采用此方案。若将来必须带 description，需要拆成 asset/data 两个内置包。

## 4. 数据/资源生成

```powershell
cd port
node tools/generate_data.mjs        # 重新生成 src/main/generated（1268 个 JSON，幂等）
node tools/verify_assets.mjs        # 资源/模型/语言/音效静态校验（全 OK）
```

生成内容：
- `assets/kaleidoscope_doll/items/*.json` 282（276 个玩偶 + 4 个方块物品 + doll_icon + doll_entity_item）
- `assets/kaleidoscope_doll/blockstates/doll_*.json` 276（8 个变体：4 facing × waterlogged）
- `data/kaleidoscope_doll/loot_table/blocks/*` 277（276 玩偶 + doll_machine 只掉下半）
- `data/minecraft/recipe/*_stonecutting.json` 211（跳号规则与上游 datagen 一致）
- `data/minecraft/advancement/recipes/decorations/*` 211（配方解锁）
- `data/kaleidoscope_doll/advancement/doll/*` 7
- `data/kaleidoscope_doll/tags/item/*` 3 + `data/minecraft/tags/block/mineable/pickaxe.json`

注意：1.21.11 的 `advancement.display.background` 是 `ClientAsset.ResourceTexture`，写 `kaleidoscope_doll:advancement/background`
（实际解析为 `textures/advancement/background.png`），**不能**沿用 1.21.8 的 `textures/...png` 全路径。

## 5. 与上游的行为差异（已知）

1. 实体玩偶的物品形态外观改为「与该玩偶方块物品相同的 3D 模型」，不再有原版渲染器在 GUI 里画的背景框
   （1.21.11 已无 `BuiltinItemRendererRegistry`）。
2. 实体玩偶的光照取渲染状态里的实体位置光照；带生物群系染色的方块（不属于本模组）会退回默认色，对本模组玩偶无影响。
3. 配置从 TOML 变为 JSON，键名保持不变。
4. 新增 `waterlogged=true` 的方块状态模型（上游 datagen 实际漏生成，水下玩偶会缺模型）。
5. **只有被投掷出去的实体玩偶才会翻滚**：上游的"托马斯大回旋"条件只有「未着地 + 速度 > 0.5」，
   放置实体玩偶时生成瞬间的微小落地速度也会触发，表现为放置后闪一下/歪一下。
   修正方式：条件追加 `inThrowing`（该字段只在 `DollEntityItem.throwDollEntity` 里置位），
   放置出来的玩偶保持站姿，投掷玩法不变（[DollEntity.java#L70-L82](src/main/java/com/github/ysbbbbbb/kaleidoscopedoll/entity/DollEntity.java#L70-L82)）。
6. **新增「电脑」**（上游 1.4.2 才有的功能，Fabric 分支 1.0.9 没有；本次从 `1.21.1-neoforge` 分支回移）：
   - 方块 `kaleidoscope_doll:computer`（可朝向前后左右 + `waterlogged`），右键打开界面。
   - 界面：8×4 玩偶网格（每页 32 个，共 211 个「玩家/作者/赞助玩偶」）+ 搜索框（按物品 id、语言 key、本地化 tooltip 搜索）+ 滚动条 + 输入槽/输出槽 + 玩家背包。
   - 玩法：输入槽放任意羊毛（标签 `#kaleidoscope_doll:computer_tokens` = `#minecraft:wool`），点选玩偶 → 输出槽出现对应玩偶，取走消耗 1 个羊毛。
   - 实现：`ComputerBlock` + `ComputerMenu`（Fabric `ExtendedScreenHandlerType` 注册菜单）+ `ComputerMenuScreen`/`DollButton`（客户端）+ `ComputerDollClickPayload`（C2S 自定义载荷，服务端再校验一次「输入是羊毛 + 目标确实是玩家玩偶」）+ 配方/战利品/配方解锁 advancement/lang/贴图模型。
   - 与上游 1.4.2 的差异：上游界面里还有「自定义玩偶（CustomDoll）/玩家皮肤玩偶」入口，那属于 1.4.x 的 CustomDoll + BedrockModel 整套系统（玩偶+ addon 在此基础上扩展），本移植未包含；电脑的可选列表因此就是 211 个「玩家玩偶」。

## 5.1 玩家皮肤玩偶（集成在电脑里）

- 交互：电脑界面搜索框输入 `@+正版玩家ID`（如 `@+Notch`）→ 网格变成 1 个玩家头颅按钮 → 点击后服务端解析 UUID
  （Mojang API，失败自动退化为离线档案）→ 输出槽生成「该玩家的玩偶」，取走消耗 1 个羊毛（与普通玩偶一致）。
- 新增方块/物品：`kaleidoscope_doll:player_doll`（玩家玩偶，可放置、可水洗、有朝向），物品带
  `kaleidoscope_doll:doll_owner`（ResolvableProfile）组件；名字显示为「XXX的玩偶」。
- 渲染：方块实体 + `PlayerDollRenderer`（原版 `PlayerModel`，宽/细两种模型，皮肤走原版 `PlayerSkinRenderCache`，
  约 0.75 格高）。方块模型为空（`RenderShape` 默认，模型无 elements），完全由 BER 绘制。
- 数据：`data/kaleidoscope_doll/loot_table/blocks/player_doll.json` 使用 `copy_components` 保留 `doll_owner`，
  破坏回收仍记得主人；创造模式玩家玩偶页签里有一个无主人的通用玩偶（默认皮肤）。
- 已验证：`@+Notch` 搜索出玩家头颅按钮、点击后输出槽生成玩偶、羊毛 64→63、物品名随玩家变化、
  方块放置后 BER 能画出玩家模型（模型比例/位置正确）。
- 第二轮修复（用户实测反馈后）：
  1. 放下的玩偶原本**头朝下、陷进方块**：补上原版生物模型的 `scale(-1,-1,1)` 翻正，并加坐姿（双腿前伸、手臂下垂、整体下沉 0.18 格）。
  2. 物品栏图标：`items/player_doll.json` 改为原版 `minecraft:special` 的 `minecraft:player_head` 渲染器，
     物品同时带 `minecraft:profile` 组件 → 图标显示**带皮肤的 3D 头颅**；创造栏的通用玩偶用 Steve 档案兜底。
  3. 搜索框第二次点击不能聚焦：删掉屏幕里手写的 `searchField.mouseClicked`/`setFocused` 逻辑，改由 `super.mouseClicked` 统一处理焦点。
  4. 错误提示不显示：动作栏提示改为**聊天栏** `sendSystemMessage`。
  5. 皮肤白色：服务端解析玩家时再请求一次 sessionserver，把带 `textures` 属性的完整档案写进 `ResolvableProfile`，
     客户端拿到属性后可以直接解析皮肤（不需要再联网查询）；开发端因无法访问 Mojang 仍会显示白色/黑色（原版玩家头颅在开发端也是黑的），
     需要在线客户端验证。

## 6. 验证记录（2026-11-11）

- `compileJava` / `compileClientJava` / `build` 全部通过（0 error）。
- 开发服务器（`runServer`）：`Loaded 1683 recipes`（原版 1470 + 本模组 213）、`Loaded 1802 advancements`
  （原版 1584 + 本模组 7 + 配方解锁 211），`Done (...)`，无 pack metadata / 数据解析报错。
- 开发客户端（`runClient`）：资源加载无 `Unable to load model` / `Missing texture` 报错；276 玩偶模型与贴图正常进入方块图集。
- **游戏内冒烟测试**：用 `runServer` + RCON 在一个超平坦世界放置了 `doll_0/doll_5/doll_10/doll_52/doll_200`、
  紫色礼盒、`doll_machine`（上下两半），并 `/summon kaleidoscope_doll:doll` 带 `doll_block_state` 生成实体玩偶；
  把该世界复制成单人存档，用 `-PquickPlayWorld=autotest` 直接进入世界截图，确认：
  玩偶方块（含 3D 模型与贴图）、礼盒、机器与实体玩偶都能正常渲染，无紫黑模型/缺贴图。
- 静态校验：`node tools/verify_assets.mjs` → 18 项 OK、0 MISS。
- 最终 jar 内容核对：282 个 `items/*.json`、280 个 blockstate、288 个模型、292 张贴图、280 个 loot table、
  213 个 recipe、218 个 advancement、4 个 tag；不含 `pack.mcmeta`、不含 datagen/JEI 类。
- **电脑功能验证（2026-11-11，`-PquickPlayWorld` 实机）**：
  - 服务端：`Loaded 1684 recipes`（+电脑配方）、`Loaded 1803 advancements`（+配方解锁），无数据解析报错。
  - 客户端：右键电脑成功打开界面；网格显示 32 个玩偶（共 211 个，可翻页），悬停显示 `Doll / Author: …` tooltip；
    输入槽放入羊毛后点选玩偶 → 输出槽出现玩偶；取走输出后羊毛 63 → 62（只消耗 1 个）；
    搜索框输入 `chicken` → 只筛出鸡玩偶（id / 语言 key / 本地化 tooltip 三路匹配均生效）。
  - 实现细节修正：`DollButton` 必须**先画槽位底图再画物品**——电脑 GUI 的按钮底图是不透明贴图，
    顺序反过来会把物品图标完全盖住（截图验证：修复前 32 个格子全是空槽色块，修复后玩偶图标正常）。
  - 实现细节修正：1.21.11 的 `GuiGraphics.drawString` / `EditBox.setTextColor` 需要**不透明颜色**（alpha 必须 0xFF），
    原版 `ContainerScreen.renderLabels` 传的是 `0xFF404040`。按 1.21.1 习惯写 `0x404040` / `0xFFFFFF` / `0x39FF56`
    会因 alpha=0 而完全不可见（标题与搜索框文字消失，且不报错）。

### 复跑冒烟测试

```powershell
cd port
# 1) 起服务端，拿 RCON 布置场景（见 tools/ 下的脚本可自行编写），stop 后：
Copy-Item -Recurse run/world run/saves/autotest
# 2) 直接进存档：
.\gradlew.bat runClient -PquickPlayWorld=autotest
```

`tools/mc_input.ps1` / `tools/mc_chat.ps1` / `tools/mc_key.ps1` 是本地自动化输入/截图辅助脚本（仅供开发验证用，不参与打包）。

