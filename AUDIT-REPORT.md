# 发布前一致性审计报告 — Kaleidoscope Doll v1.0.9 (Fabric 1.21.11)

- **审计对象**：`C:\AI\ds harness\工作区\森罗物语移植\port-player-doll-branch`（git HEAD `25f3db5`）
- **产物**：`build/libs/kaleidoscopedoll-1.21.11-fabric-1.0.9.jar`（1,503,876 B）与测试实例部署件
- **审计方式**：**只读**。自写 Node 脚本（自解析 ZIP 读 1.21.11 原版 jar / 产物 jar）+ 项目自带 `tools/verify_assets.mjs`；遍历 Java 注册表、资源引用图、data 引用图，并对 jar 与源码文件集合做**逐路径精确比对**。
- **审计结论**：**4/4 项 PASS，0 FAIL**；另有 9 条可疑项（均非阻断，见第四节）。**未修改任何源码**，仅新增本报告。

| 检查项 | 结果 |
| --- | --- |
| 1. 源码无残留（PlayerDollEntity 等） | **PASS** |
| 2. 资源完整性（items/blockstates/models/textures/lang/data） | **PASS** |
| 3. Jar 一致性（sha256 / 无实体 class / 条目数） | **PASS** |
| 4. 可疑但不确定的项 | 9 条（0 阻断） |

---

## 一、源码无残留 — PASS

在 `src/main` 与 `src/client` 全量文件（含 json/lang/mcmeta）中按精确词边界搜索：

| 符号 | 命中 |
| --- | --- |
| `PlayerDollEntity` | 0 |
| `PlayerDollEntityItem` | 0 |
| `PLAYER_DOLL_ENTITY` | 0 |
| `EntityRendererRegistry`（独立词，注册调用） | 0 |
| `player_doll_entity`（大小写不敏感） | 0 |

证据：
- 唯一命中 `EntityRendererRegistry` 字样的行是 `src/client/.../KaleidoscopeDollClient.java:24` 的 `import ...v1.BlockEntityRendererRegistry;`——这是 **方块实体渲染器** API（`BlockEntityRendererRegistry`），不是已移除的实体渲染器注册表，属正常用法。
- 全工程 `ENTITY_TYPE` 只有 2 行，均在 `init/ModEntities.java`，只注册了 `kaleidoscope_doll:doll`（`DOLL_ID` / `DOLL`），没有玩家玩偶实体。
- 玩家玩偶为**方块 + 方块实体**实现：`block/PlayerDollBlock.java`、`block/entity/PlayerDollBlockEntity.java`、`item/PlayerDollItem.java`（BlockItem + GeoItem），客户端渲染在 `render/PlayerDoll*`，不含实体注册。

## 二、资源完整性 — PASS

### 2.1 物品模型绑定（`assets/kaleidoscope_doll/items/*.json`）
- 注册物品 **284** 个（276 个 `doll_0..doll_275` + 8 个具名物品），`items/*.json` **284** 个，**缺 0 / 多 0 / 重复定义 0**。
- 重点物品逐一核对（全部存在且引用可解析）：
  - `items/player_doll.json` → `minecraft:special`，`base=kaleidoscope_doll:item/player_doll_base`，`model.type=geckolib:geckolib`（`item/player_doll_base` 模型存在；`geckolib:geckolib` 已用 GeckoLib 5.4.5 字节码核实：`SpecialModelRenderersMixin` 用 `GeckoLibConstants.id("geckolib")` 注册该 type）。
  - `items/computer.json` → `kaleidoscope_doll:block/computer`（模型存在）。
  - `items/doll_icon.json` → `kaleidoscope_doll:item/doll_icon`（模型存在）。
  - `items/doll_entity_item.json` → `kaleidoscope_doll:item/doll_entity_item`（模型存在，layer0=`item/doll_icon`，贴图存在）。
- 284 个 items 的 `model` / `base` 引用 **全部可解析**（本地模型或原版模型）。

### 2.2 blockstates / models / textures
- 注册方块 **282** 个，blockstates **282** 个（resources 6 + generated 276），**缺 0 / 重复 0**；blockstates 合计 **2252 个模型引用全部存在**。
- models **294** 个（block 285 + item 9）：父模型引用全部可解析（`builtin/entity` 0 处）；贴图引用 **22733 处 / 去重 294 个 id 全部存在**（本地 `textures/**` 与原版 `minecraft:` 均按 1.21.11 规则解析）。
- textures：**298 个 PNG 魔数全部正确**；**6 个 `.mcmeta` 全部可解析且基础 png 存在**（`doll_154_2/4/5`、`doll_machine_shake_1/2/3`）。
- **`render_type`：assets 与 generated 全部 1595 个 JSON/mcmeta 中 0 处**（jar 内 0 处）。1.21.11 已移除该字段，符合要求。

### 2.3 lang / sounds / GeckoLib 模型
- `en_us.json` / `zh_cn.json` 各 **262 key**，key 集合完全一致，无重复 key。
- 代码与 data 引用的翻译 key（`Component.translatable` 字面量、`registerSpecialTooltips` 动态 tooltip、tooltip/成就/标题/消息/GUI 字面量、advancement 的 `translate`、sounds subtitle）**缺失 0**（独立审计 250 个引用 key；项目 `tools/verify_assets.mjs` 口径 244 个，二者均 0 缺失）。
- `sounds.json`：1 个事件 `block.duck_toy` 可解析，`sounds/block/duck_toy.ogg` 存在，subtitle key 有翻译，事件 id 与 `ModSounds` 一致。
- `geckolib/models/{block,item}/player_doll_{alex,steve}.geo.json` 共 4 个，JSON 可解析且含 `minecraft:geometry`；与代码中的模型 id `block/player_doll_steve|alex` / `item/player_doll_steve|alex` 对应。

### 2.4 data（recipe / advancement / tags / loot_table）
- **全部 JSON 可解析**（本次扫描 1595 个资源 JSON/mcmeta，0 解析失败、0 BOM）。
- **trinkets 头饰标签**：`data/trinkets/tags/item/head/hat.json` = **277 项**，唯一 277，**未注册 id 0**（`player_doll` + `doll_0..275` 各一次）。
- 其余标签 6 个均可解析、引用已注册：
  - `computer_tokens` 1 项（`#minecraft:wool`）、`doll_machine_tokens` 1 项（`minecraft:diamond`）
  - `all_dolls` 276 项、`vanilla_dolls` 65 项
  - `minecraft/tags/block/mineable/pickaxe` 1 项（`doll_machine`）
- **配方 214 个**：`crafting_shaped` 2、自定义 `kaleidoscope_doll:doll_entity_crafting` 1、`stonecutting` **211**（结果 id 唯一 211 个，全为已注册玩偶；输入为 4 种羊毛）。全部为 1.21.11 字符串 ingredient 格式，`result.id` 全部存在。
- **advancement 220 个**（模组成就 9 + 配方解锁 211）：JSON 可解析；`translate` key 全部存在；7 个 `display.background` 均为 1.21.11 贴图 id 写法（`kaleidoscope_doll:advancement/background`）且贴图存在；图标物品 id 全部已注册。
- **loot_table 282 个**：JSON 可解析；**282/282 个注册方块都有掉落表**；344 个掉落条目中的物品 id 全部已注册。
- 配方/标签引用的跨命名空间 id 已核实存在：`#c:ingots/copper` 在 Fabric API 0.141.5+1.21.11 的 `fabric-convention-tags-v2` 内；`#minecraft:wool` 在原版数据包内。

## 三、Jar 一致性 — PASS

- **SHA-256 完全一致**：
  - `build/libs/kaleidoscopedoll-1.21.11-fabric-1.0.9.jar` = `1082c4239978c3f55a13415a72752ef5628cb0937e848b45a703416ad22dbd6b`（1,503,876 B）
  - `C:\MC\Axolot\profiles\测试\mods\【森罗物语：玩偶1.21.11移植】kaleidoscopedoll-1.21.11-fabric-1.0.9.jar` = **同一 sha256、同一字节数**（部署件文件名带展示用中文前缀；mods 内只有这一个活动 jar，其余为 `.bak-*` 备份，不会被 Fabric 加载）。
- **jar 内无 PlayerDollEntity 相关类**：59 个 class 的**条目名**与**常量池字节**双重扫描均 0 命中；非 class 条目（json/lang 等）也 0 命中。
- **jar 与源码资源集合精确一致**（不是"数量级"）：源码 `src/main/resources` + `src/main/generated` 的 assets+data 文件共 **1894** 个，jar 内 **1894** 个，**缺 0 / 多 0**。分目录对照：

| 目录 | 源码 | jar |
| --- | --- | --- |
| `assets/kaleidoscope_doll/items/` | 284 | 284 |
| `assets/kaleidoscope_doll/blockstates/` | 282 | 282 |
| `assets/kaleidoscope_doll/models/` | 294 | 294 |
| `assets/kaleidoscope_doll/geckolib/` | 4 | 4 |
| `assets/kaleidoscope_doll/textures/` | 304 | 304 |
| `assets/kaleidoscope_doll/lang/` | 2 | 2 |
| `assets/kaleidoscope_doll/sounds/` | 1 | 1 |
| `data/`（全部命名空间，含 loot/advancement/recipe/tags） | 722 | 722 |

- jar 内 `fabric.mod.json`：`id=kaleidoscope_doll`，`version=1.0.9`，`depends: fabricloader>=0.19.3 / fabric-api / minecraft 1.21.11 / geckolib>=5.4.5`。
- jar 内**无 `pack.mcmeta`**（与项目既有结论一致：1.21.11 资源侧/数据侧校验规则互斥，模组 jar 省略后两侧均正常；项目根保留 `pack.mcmeta.disabled`）。
- 项目自带校验：`node tools/verify_assets.mjs` → **`[OK] 17  [MISS] 0  [WARN] 0`**，exit 0。

## 四、可疑项（仅记录，未修改）

> 均为非阻断项；第 1 条建议下一版顺手修。

1. **`PlayerDollItemModel.java:16-17` 兜底贴图路径是 1.20 旧式全路径**：`kaleidoscope_doll:textures/block/doll/player_doll.png`。1.21.11 纹理 id 会解析为 `textures/textures/block/doll/player_doll.png.png`（不存在），应为 `kaleidoscope_doll:block/doll/player_doll`（对应文件确实存在，64×64）。
   - 触发面：`getTextureResource` 仅在 `profile == null` 时用它；而创造模式页签是 `new ItemStack(ModItems.PLAYER_DOLL)`（无 `DOLL_OWNER` 组件），合成产出则带 owner。即**无 owner 的玩家玩偶物品可能显示紫黑缺失贴图**（创建/给予物品时）。未在本次审计中实机逐项截图，属"基于解析规则的可疑项"。
2. `textures/entity/player_doll_white.png` 与 `textures/block/doll/player_doll.png` **字节完全相同**（64×64、181 B），且前者在 Java/JSON 中 0 引用——玩家玩偶从"实体实现"改为"方块实体实现"后的遗留文件。
3. `assets/.../models/item/player_doll.json` 无任何引用（`items/player_doll.json` 用 `special` + `item/player_doll_base`）。
4. `assets/.../models/block/doll/player_doll.json` 无 blockstate/items 引用（`blockstates/player_doll.json` 指向 `block/player_doll`，后者是只有 particle 的空模型）。
5. `textures/item/doll_entity_item_bg.png` 无引用（`doll_entity_item` 模型已改用 `item/doll_icon`）——旧的实体物品渲染遗留。
6. `textures/block/doll/doll_267_e.png`、`doll_269_e.png` 是 OptiFine 自发光遮罩；1.21.11 + Iris 不识别该机制（无副作用），保留无害。
7. lang 中 `advancement.kaleidoscope_doll.player_doll_self.*` 与 `advancements.kaleidoscope_doll.doll.player_doll_self.*` 语义重复（当前 datagen/资源使用的是后者），无害。
8. `data/kaleidoscope_doll/tags/item/{all_dolls,vanilla_dolls}.json` 在运行时代码中无消费者（仅被已排除编译的 `datagen/TagItem.java` 引用），属冗余数据（可供数据包使用）。
9. 若有人把项目根 `pack.mcmeta.disabled` 改回 resources，会重新触发 1.21.11 资源侧/数据侧 pack 格式冲突（当前 jar 无 pack.mcmeta，是正确状态）。

**补充（非问题，避免误判）**：211 个切石配方对应"非原版玩偶"子集，65 个 `vanilla_dolls` 通过礼品盒/抽奖机掉落获取，与上游 1.21.1 产物（同为 211 个切石配方）一致，不是缺失。

---

### 附：复现方式

```powershell
cd "C:\AI\ds harness\工作区\森罗物语移植\port-player-doll-branch"
node tools/verify_assets.mjs          # 项目自带校验：OK 17 / MISS 0
# 本次审计另用只读 Node 脚本完成：
#  - 自写 ZIP 解析读取 1.21.11.jar 与产物 jar（条目/常量池/SHA-256）
#  - 正则提取 Java 注册表（ResourceKey<Item/Block> + 276 玩偶循环）
#  - 遍历 models→textures、items→models、blockstates→models、tags/recipes/advancements/loot 引用图
#  - jar 条目集合与 src/main/resources + src/main/generated 做逐路径比对
```
