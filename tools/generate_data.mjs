#!/usr/bin/env node
/**
 * generate_data.mjs — 森罗物语 (Kaleidoscope Doll) 1.21.11 静态数据/资源生成器
 *
 * 上游 1.21.1 用 Fabric datagen 生成这些文件；移植到 1.21.11 后改为本脚本一次性生成静态 JSON。
 * 产物目录：src/main/generated/（Lead 会把它加进 resources）
 *
 * 用法：在 port 目录执行 `node tools/generate_data.mjs`
 * 幂等：每次运行先删除 src/main/generated，再完整重建。
 *
 * 依据（均已用 1.21.11 原版 jar / 1.21.8 NeoForge 分支实际产物核对）：
 *  - assets/<ns>/items/<id>.json：
 *      C:\MC\.minecraft\versions\1.21.11\1.21.11.jar -> assets/minecraft/items/stone.json
 *  - blockstates：1.21.11 jar -> assets/minecraft/blockstates/furnace.json（variants + facing/y）
 *  - loot_table：1.21.11 jar -> data/minecraft/loot_table/blocks/white_wool.json（dropSelf）
 *                 data/minecraft/loot_table/blocks/oak_door.json（双半方块只掉 lower）
 *  - recipe：1.21.11 jar -> data/minecraft/recipe/andesite_slab_from_andesite_stonecutting.json
 *            （ingredient 为纯字符串，result 用 id/count）
 *  - advancement：1.21.11 jar -> data/minecraft/advancement/story/root.json（icon 用 id+count；
 *                 1.21.11 的 DisplayInfo.background 是 ClientAsset.ResourceTexture，
 *                 值是去前缀的资源 id，实际解析为 textures/<path>.png，故写 kaleidoscope_doll:advancement/background）
 *                 以及 1.21.8 分支 data/kaleidoscope_doll/advancement/doll/*.json
 *  - recipe advancement：1.21.11 jar -> data/minecraft/advancement/recipes/building_blocks/andesite_slab_from_andesite_stonecutting.json
 *  - tags：1.21.11 jar -> data/minecraft/tags/block/mineable/pickaxe.json（tags 目录单数）
 *
 * 内容逻辑（来自上游 datagen 源码）：
 *  - ModRegisterEvent：doll_0..doll_275；SPECIAL_TOOLTIPS 里的玩偶不进 vanilla_dolls / full_collection
 *  - ModRecipeProvider：跳过索引 2..66；其余每 64 个配方换一种羊毛（white→orange→magenta→light_blue→...）
 *  - LootTableGenerator：doll 全部 dropSelf；doll_machine 只在 half=lower 掉落
 *  - TagItem / TagBlock：doll_machine_tokens / vanilla_dolls / all_dolls / mineable/pickaxe
 *  - AdvancementGenerator：root / doll_machine / 3 个 gift box / credits / full_collection
 *
 * 本脚本还会解析上述 Java 源，保证生成结果与实际注册范围一致，并做完整自检。
 */

import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(SCRIPT_DIR, '..');
const GEN = path.join(ROOT, 'src', 'main', 'generated');
const RES = path.join(ROOT, 'src', 'main', 'resources');
const NS = 'kaleidoscope_doll';

const JAVA_EVENT_DIR = path.join(
  ROOT,
  'src/main/java/com/github/ysbbbbbb/kaleidoscopedoll/event',
);
const JAVA_INIT_DIR = path.join(
  ROOT,
  'src/main/java/com/github/ysbbbbbb/kaleidoscopedoll/init',
);

const WOOLS = [
  'white_wool',
  'orange_wool',
  'magenta_wool',
  'light_blue_wool',
  'yellow_wool',
  'lime_wool',
  'pink_wool',
  'gray_wool',
  'light_gray_wool',
  'cyan_wool',
  'purple_wool',
  'blue_wool',
  'brown_wool',
  'green_wool',
  'red_wool',
  'black_wool',
];

const RECIPE_SKIP_FROM = 2; // 含
const RECIPE_SKIP_TO = 66; // 含
const RECIPE_GROUP_SIZE = 64; // 每 64 个配方换一种羊毛

const BLOCK_ITEMS = [
  'doll_machine',
  'purple_doll_gift_box',
  'green_doll_gift_box',
  'yellow_doll_gift_box',
];
const PLAIN_ITEMS = ['doll_icon', 'doll_entity_item'];
const GIFT_BOXES = [
  'purple_doll_gift_box',
  'green_doll_gift_box',
  'yellow_doll_gift_box',
];

const BACKGROUND = `${NS}:advancement/background`;
const REQUESTED_OUTPUTS = []; // { rel, category, json }

const counters = new Map();
function count(category) {
  counters.set(category, (counters.get(category) ?? 0) + 1);
}

function writeJson(category, rel, json) {
  const abs = path.join(GEN, rel);
  mkdirSync(path.dirname(abs), { recursive: true });
  writeFileSync(abs, `${JSON.stringify(json, null, 2)}\n`, 'utf8');
  REQUESTED_OUTPUTS.push({ rel: path.normalize(rel.split(path.sep).join('/')), category });
  count(category);
}

const itemId = (name) => `${NS}:${name}`;

// ---------------------------------------------------------------------------
// 0. 读取上游 Java 源码，确定实际注册范围（避免脚本与代码漂移）
// ---------------------------------------------------------------------------
function readJava(file) {
  const abs = path.join(JAVA_EVENT_DIR, file);
  if (!existsSync(abs)) {
    throw new Error(`找不到上游源码：${abs}`);
  }
  return readFileSync(abs, 'utf8');
}

const registerEventSrc = readJava('ModRegisterEvent.java');
const maxDollMatch = registerEventSrc.match(/MAX_DOLL_COUNT\s*=\s*(\d+)\s*;/);
if (!maxDollMatch) {
  throw new Error('ModRegisterEvent.java 中找不到 MAX_DOLL_COUNT');
}
const DOLL_COUNT = Number.parseInt(maxDollMatch[1], 10);
if (!Number.isInteger(DOLL_COUNT) || DOLL_COUNT <= 0) {
  throw new Error(`MAX_DOLL_COUNT 解析失败：${maxDollMatch[1]}`);
}
const DOLL_IDS = Array.from({ length: DOLL_COUNT }, (_, i) => `doll_${i}`);

const specialTooltips = new Set();
for (const m of registerEventSrc.matchAll(/registerSpecialTooltips\(\s*"([^"]+)"/g)) {
  specialTooltips.add(m[1]);
}
if (specialTooltips.size === 0) {
  throw new Error('ModRegisterEvent.java 中解析不到任何 SPECIAL_TOOLTIPS 条目');
}
for (const id of specialTooltips) {
  if (!DOLL_IDS.includes(id)) {
    throw new Error(`SPECIAL_TOOLTIPS 中的 ${id} 不在 doll_0..doll_${DOLL_COUNT - 1} 范围内`);
  }
}
const vanillaDollIds = DOLL_IDS.filter((id) => !specialTooltips.has(id));

// ModItems.java：注册的 6 个非 doll 物品
// 直接从源码里的字符串字面量核对（对 id("x") / fromNamespaceAndPath(...) 等写法都成立）
const modItemsSrc = readFileSync(path.join(JAVA_INIT_DIR, 'ModItems.java'), 'utf8');
const KNOWN_PLAIN_ITEMS = [...BLOCK_ITEMS, ...PLAIN_ITEMS];
const javaStringLiterals = new Set(
  [...modItemsSrc.matchAll(/"([^"\\\r\n]+)"/g)].map((m) => m[1]),
);
const missingPlainItemLiterals = KNOWN_PLAIN_ITEMS.filter(
  (id) => !javaStringLiterals.has(id),
);
if (missingPlainItemLiterals.length > 0) {
  throw new Error(
    `ModItems.java 中找不到这些物品 id 的字符串字面量（可能已改名）：${missingPlainItemLiterals.join(', ')}`,
  );
}
const registeredPlainItems = KNOWN_PLAIN_ITEMS;
if (registeredPlainItems.length !== BLOCK_ITEMS.length + PLAIN_ITEMS.length) {
  throw new Error(
    `ModItems.java 解析到 ${registeredPlainItems.length} 个物品，期望 ${BLOCK_ITEMS.length + PLAIN_ITEMS.length}：${registeredPlainItems.join(', ')}`,
  );
}

// 每个物品对应的模型 id（1.21.4+：assets/<ns>/items/<id>.json 是唯一模型绑定来源）
const itemModelById = new Map();
for (const id of DOLL_IDS) itemModelById.set(id, `${NS}:block/doll/${id}`);
for (const id of BLOCK_ITEMS) itemModelById.set(id, `${NS}:block/${id}`);
for (const id of PLAIN_ITEMS) itemModelById.set(id, `${NS}:item/${id}`);

const allRegisteredItemIds = [...DOLL_IDS, ...BLOCK_ITEMS, ...PLAIN_ITEMS].sort();
const registeredItemIdSet = new Set(allRegisteredItemIds);
const parsedItemIdSet = new Set([...DOLL_IDS, ...registeredPlainItems]);
if (parsedItemIdSet.size !== registeredItemIdSet.size) {
  throw new Error(
    `Java 注册的物品数量 (${parsedItemIdSet.size}) 与预期 ${registeredItemIdSet.size} 不一致`,
  );
}
for (const id of parsedItemIdSet) {
  if (!registeredItemIdSet.has(id)) {
    throw new Error(`Java 中注册了未预期的物品：${id}`);
  }
}

// ---------------------------------------------------------------------------
// 1. 清空旧输出（幂等）
// ---------------------------------------------------------------------------
rmSync(GEN, { recursive: true, force: true });
mkdirSync(GEN, { recursive: true });
console.log(`[generate] 输出目录：${path.relative(ROOT, GEN)}`);
console.log(
  `[generate] 注册范围：doll_0..doll_${DOLL_COUNT - 1}（${DOLL_COUNT} 个）+ ${registeredPlainItems.length} 个其他物品；` +
    `SPECIAL_TOOLTIPS ${specialTooltips.size} 个，vanilla_dolls ${vanillaDollIds.length} 个`,
);

// ---------------------------------------------------------------------------
// 2. assets/<ns>/items/<id>.json
// ---------------------------------------------------------------------------
for (const [id, model] of itemModelById) {
  writeJson('items', `assets/${NS}/items/${id}.json`, {
    model: {
      type: 'minecraft:model',
      model,
    },
  });
}

// ---------------------------------------------------------------------------
// 3. assets/<ns>/blockstates/doll_N.json（facing × waterlogged）
// ---------------------------------------------------------------------------
const FACING_YAW = { north: 0, east: 90, south: 180, west: 270 };
for (const id of DOLL_IDS) {
  const variants = {};
  for (const [facing, yaw] of Object.entries(FACING_YAW)) {
    for (const waterlogged of ['false', 'true']) {
      const value = { model: `${NS}:block/doll/${id}` };
      if (yaw !== 0) value.y = yaw;
      variants[`facing=${facing},waterlogged=${waterlogged}`] = value;
    }
  }
  writeJson('blockstates', `assets/${NS}/blockstates/${id}.json`, { variants });
}

// ---------------------------------------------------------------------------
// 4. data/<ns>/loot_table/blocks/*.json
//    276 dolls dropSelf；doll_machine 只在 half=lower 掉落；3 个 gift box 已有手写文件，跳过
// ---------------------------------------------------------------------------
function dropSelfLoot(id) {
  return {
    type: 'minecraft:block',
    pools: [
      {
        bonus_rolls: 0.0,
        conditions: [{ condition: 'minecraft:survives_explosion' }],
        entries: [{ type: 'minecraft:item', name: itemId(id) }],
        rolls: 1.0,
      },
    ],
    random_sequence: `${NS}:blocks/${id}`,
  };
}

for (const id of DOLL_IDS) {
  writeJson('loot_table', `data/${NS}/loot_table/blocks/${id}.json`, dropSelfLoot(id));
}

writeJson('loot_table', `data/${NS}/loot_table/blocks/doll_machine.json`, {
  type: 'minecraft:block',
  pools: [
    {
      bonus_rolls: 0.0,
      conditions: [{ condition: 'minecraft:survives_explosion' }],
      entries: [
        {
          type: 'minecraft:item',
          conditions: [
            {
              block: itemId('doll_machine'),
              condition: 'minecraft:block_state_property',
              properties: { half: 'lower' },
            },
          ],
          name: itemId('doll_machine'),
        },
      ],
      rolls: 1.0,
    },
  ],
  random_sequence: `${NS}:blocks/doll_machine`,
});

// ---------------------------------------------------------------------------
// 5. 切石机配方 + 配方解锁 advancement
//    上游都通过 RecipeProvider.save(String) 写出 → 落在 data/minecraft/ 命名空间
//    参考分支：data/minecraft/recipe/doll_85_from_white_wool_stonecutting.json
// ---------------------------------------------------------------------------
const stonecuttingRecipes = []; // { name, dollId, wool }
let recipeCount = 0;
for (let i = 0; i < DOLL_IDS.length; i++) {
  if (i >= RECIPE_SKIP_FROM && i <= RECIPE_SKIP_TO) continue;
  const dollId = DOLL_IDS[i];
  const wool = WOOLS[Math.floor(recipeCount / RECIPE_GROUP_SIZE)];
  if (!wool) {
    throw new Error(
      `配方数超出羊毛分组表：count=${recipeCount}，需要扩展 WOOLS（上游 getWoolFromIndex 会抛异常）`,
    );
  }
  recipeCount++;
  stonecuttingRecipes.push({ name: `${dollId}_from_${wool}_stonecutting`, dollId, wool });
}

for (const recipe of stonecuttingRecipes) {
  writeJson('recipe', `data/minecraft/recipe/${recipe.name}.json`, {
    type: 'minecraft:stonecutting',
    ingredient: `minecraft:${recipe.wool}`,
    result: { count: 1, id: itemId(recipe.dollId) },
  });

  // 配方解锁 advancement（vanilla RecipeProvider 的输出结构）
  writeJson(
    'recipe_advancement',
    `data/minecraft/advancement/recipes/decorations/${recipe.name}.json`,
    {
      parent: 'minecraft:recipes/root',
      criteria: {
        [`has_${recipe.wool}`]: {
          conditions: {
            items: [{ items: `minecraft:${recipe.wool}` }],
          },
          trigger: 'minecraft:inventory_changed',
        },
        has_the_recipe: {
          conditions: { recipe: `minecraft:${recipe.name}` },
          trigger: 'minecraft:recipe_unlocked',
        },
      },
      requirements: [[`has_${recipe.wool}`, 'has_the_recipe']],
      rewards: { recipes: [`minecraft:${recipe.name}`] },
    },
  );
}

// ---------------------------------------------------------------------------
// 6. data/<ns>/advancement/doll/*.json
// ---------------------------------------------------------------------------
function advancementDisplay(iconId, key, extra = {}) {
  return {
    announce_to_chat: false,
    background: BACKGROUND,
    description: {
      translate: `advancements.${NS}.doll.${key}.description`,
    },
    ...extra,
    icon: { count: 1, id: itemId(iconId) },
    show_toast: false,
    title: {
      translate: `advancements.${NS}.doll.${key}.title`,
    },
  };
}

function inventoryChangedCriterion(item) {
  return {
    conditions: {
      items: [{ items: itemId(item) }],
    },
    trigger: 'minecraft:inventory_changed',
  };
}

// root
writeJson('advancement', `data/${NS}/advancement/doll/root.json`, {
  criteria: {
    tick: { trigger: 'minecraft:tick' },
  },
  display: advancementDisplay('doll_52', 'root'),
  requirements: [['tick']],
  sends_telemetry_event: true,
});

// doll_machine
writeJson('advancement', `data/${NS}/advancement/doll/doll_machine.json`, {
  parent: `${NS}:doll/root`,
  criteria: {
    doll_machine: inventoryChangedCriterion('doll_machine'),
  },
  display: advancementDisplay('doll_machine', 'doll_machine'),
  requirements: [['doll_machine']],
  sends_telemetry_event: true,
});

// 3 个 gift box（都挂在 doll_machine 下）
for (const gift of GIFT_BOXES) {
  writeJson('advancement', `data/${NS}/advancement/doll/${gift}.json`, {
    parent: `${NS}:doll/doll_machine`,
    criteria: {
      [gift]: inventoryChangedCriterion(gift),
    },
    display: advancementDisplay(gift, gift),
    requirements: [[gift]],
    sends_telemetry_event: true,
  });
}

// credits（tick 触发，icon 是 doll_0）
writeJson('advancement', `data/${NS}/advancement/doll/credits.json`, {
  parent: `${NS}:doll/root`,
  criteria: {
    credits: { trigger: 'minecraft:tick' },
  },
  display: advancementDisplay('doll_0', 'credits'),
  requirements: [['credits']],
  sends_telemetry_event: true,
});

// full_collection：challenge，criteria = vanilla_dolls（不含 SPECIAL_TOOLTIPS），AND 要求，100 经验
const fullCollectionCriteria = {};
const fullCollectionRequirements = [];
for (const id of vanillaDollIds) {
  fullCollectionCriteria[id] = inventoryChangedCriterion(id);
  fullCollectionRequirements.push([id]);
}
writeJson('advancement', `data/${NS}/advancement/doll/full_collection.json`, {
  parent: `${NS}:doll/yellow_doll_gift_box`,
  criteria: fullCollectionCriteria,
  display: advancementDisplay('doll_5', 'full_collection', { frame: 'challenge' }),
  requirements: fullCollectionRequirements,
  rewards: { experience: 100 },
  sends_telemetry_event: true,
});

// ---------------------------------------------------------------------------
// 7. tags
// ---------------------------------------------------------------------------
writeJson('item_tag', `data/${NS}/tags/item/all_dolls.json`, {
  values: DOLL_IDS.map(itemId),
});
writeJson('item_tag', `data/${NS}/tags/item/vanilla_dolls.json`, {
  values: vanillaDollIds.map(itemId),
});
writeJson('item_tag', `data/${NS}/tags/item/doll_machine_tokens.json`, {
  values: ['minecraft:diamond'],
});
writeJson('block_tag', 'data/minecraft/tags/block/mineable/pickaxe.json', {
  replace: false,
  values: [itemId('doll_machine')],
});

// ---------------------------------------------------------------------------
// 8. 自检
// ---------------------------------------------------------------------------
const errors = [];
const failures = (msg) => errors.push(msg);

function walk(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const abs = path.join(dir, entry);
    if (statSync(abs).isDirectory()) out.push(...walk(abs));
    else out.push(abs);
  }
  return out;
}

function resolveModel(modelId) {
  const [namespace, modelPath] = modelId.split(':');
  const candidates = [
    path.join(RES, 'assets', namespace, 'models', `${modelPath}.json`),
    path.join(GEN, 'assets', namespace, 'models', `${modelPath}.json`),
  ];
  return candidates.find((candidate) => existsSync(candidate));
}

function resolveTexture(assetId) {
  const [namespace, texturePath] = assetId.split(':');
  return [
    path.join(RES, 'assets', namespace, 'textures', `${texturePath}.png`),
    path.join(GEN, 'assets', namespace, 'textures', `${texturePath}.png`),
  ].find((candidate) => existsSync(candidate));
}

// 8.1 所有生成文件必须可 JSON.parse
const generatedAbsFiles = walk(GEN).filter((file) => file.endsWith('.json'));
for (const abs of generatedAbsFiles) {
  try {
    JSON.parse(readFileSync(abs, 'utf8'));
  } catch (error) {
    failures(`JSON 解析失败：${path.relative(ROOT, abs)} (${error.message})`);
  }
}

// 8.2 items 定义与注册范围一致
const itemsDir = path.join(GEN, 'assets', NS, 'items');
const generatedItemIds = readdirSync(itemsDir)
  .filter((file) => file.endsWith('.json'))
  .map((file) => path.basename(file, '.json'))
  .sort();
if (generatedItemIds.length !== allRegisteredItemIds.length) {
  failures(
    `items 数量不一致：生成 ${generatedItemIds.length}，注册 ${allRegisteredItemIds.length}`,
  );
}
const generatedItemIdSet = new Set(generatedItemIds);
for (const id of allRegisteredItemIds) {
  if (!generatedItemIdSet.has(id)) failures(`缺少 items 定义：${id}`);
}
for (const id of generatedItemIds) {
  if (!registeredItemIdSet.has(id)) failures(`生成了未注册物品的 items 定义：${id}`);
}

// 8.3 items 引用的模型存在，且模型本身可解析
let resolvedItemModels = 0;
for (const id of generatedItemIds) {
  const json = JSON.parse(readFileSync(path.join(itemsDir, `${id}.json`), 'utf8'));
  const model = json?.model?.model;
  if (json?.model?.type !== 'minecraft:model' || typeof model !== 'string') {
    failures(`items/${id}.json 结构不正确`);
    continue;
  }
  const modelFile = resolveModel(model);
  if (!modelFile) {
    failures(`items/${id}.json 引用的模型不存在：${model}`);
    continue;
  }
  resolvedItemModels++;
  try {
    JSON.parse(readFileSync(modelFile, 'utf8'));
  } catch (error) {
    failures(`模型 JSON 解析失败：${path.relative(ROOT, modelFile)} (${error.message})`);
  }
}

// 8.4 blockstates：8 个 variant（facing n/e/s/w × waterlogged false/true），模型存在
const blockstatesDir = path.join(GEN, 'assets', NS, 'blockstates');
const generatedBlockstateIds = readdirSync(blockstatesDir)
  .filter((file) => file.endsWith('.json'))
  .map((file) => path.basename(file, '.json'));
if (generatedBlockstateIds.length !== DOLL_COUNT) {
  failures(`blockstates 数量不一致：生成 ${generatedBlockstateIds.length}，期望 ${DOLL_COUNT}`);
}
const expectedVariantKeys = new Set();
for (const facing of Object.keys(FACING_YAW)) {
  for (const waterlogged of ['false', 'true']) {
    expectedVariantKeys.add(`facing=${facing},waterlogged=${waterlogged}`);
  }
}
let resolvedBlockstateModels = 0;
for (const id of generatedBlockstateIds) {
  const json = JSON.parse(readFileSync(path.join(blockstatesDir, `${id}.json`), 'utf8'));
  const variants = json?.variants;
  if (!variants || typeof variants !== 'object') {
    failures(`blockstates/${id}.json 缺少 variants`);
    continue;
  }
  const keys = Object.keys(variants);
  if (keys.length !== expectedVariantKeys.size) {
    failures(`blockstates/${id}.json 有 ${keys.length} 个 variant，期望 8 个`);
  }
  for (const key of expectedVariantKeys) {
    if (!(key in variants)) failures(`blockstates/${id}.json 缺少 variant：${key}`);
  }
  for (const [key, value] of Object.entries(variants)) {
    if (!value || typeof value.model !== 'string') {
      failures(`blockstates/${id}.json 的 ${key} 缺少 model`);
      continue;
    }
    if (!resolveModel(value.model)) {
      failures(`blockstates/${id}.json 的 ${key} 引用了不存在的模型：${value.model}`);
      continue;
    }
    resolvedBlockstateModels++;
  }
}

// 8.5 276 个 doll 方块模型必须真实存在（不生成模型本身）
let dollModelCount = 0;
for (const id of DOLL_IDS) {
  const modelFile = path.join(RES, 'assets', NS, 'models', 'block', 'doll', `${id}.json`);
  if (!existsSync(modelFile)) failures(`缺少 doll 方块模型：${path.relative(ROOT, modelFile)}`);
  else dollModelCount++;
}
// doll_machine / gift box 的方块模型 + 手写 blockstate 必须存在
for (const id of ['doll_machine', ...GIFT_BOXES]) {
  const blockModel = path.join(RES, 'assets', NS, 'models', 'block', `${id}.json`);
  if (!existsSync(blockModel)) failures(`缺少方块模型：${path.relative(ROOT, blockModel)}`);
  const blockstate = path.join(RES, 'assets', NS, 'blockstates', `${id}.json`);
  if (!existsSync(blockstate)) failures(`缺少手写 blockstate：${path.relative(ROOT, blockstate)}`);
}

// 8.6 loot table
const lootDir = path.join(GEN, 'data', NS, 'loot_table', 'blocks');
const lootFiles = readdirSync(lootDir).filter((file) => file.endsWith('.json'));
const expectedLootCount = DOLL_COUNT + 1; // 276 dolls + doll_machine
if (lootFiles.length !== expectedLootCount) {
  failures(`loot_table 数量不一致：生成 ${lootFiles.length}，期望 ${expectedLootCount}`);
}
for (const file of lootFiles) {
  const id = path.basename(file, '.json');
  const json = JSON.parse(readFileSync(path.join(lootDir, file), 'utf8'));
  if (json.type !== 'minecraft:block') failures(`loot_table/${file} 的 type 不是 minecraft:block`);
  if (json.random_sequence !== `${NS}:blocks/${id}`) {
    failures(`loot_table/${file} 的 random_sequence 不正确：${json.random_sequence}`);
  }
  const entries = json?.pools?.[0]?.entries ?? [];
  const names = entries.map((entry) => entry.name);
  if (!names.includes(itemId(id))) failures(`loot_table/${file} 未掉落自身 ${itemId(id)}`);
  if (id === 'doll_machine') {
    const condition = entries[0]?.conditions?.find(
      (c) => c.condition === 'minecraft:block_state_property',
    );
    if (!condition || condition.properties?.half !== 'lower') {
      failures('loot_table/doll_machine.json 缺少 half=lower 条件');
    }
  }
}
// gift box 的手写 loot 不再生成，但必须存在且可解析
for (const id of GIFT_BOXES) {
  const handWritten = path.join(RES, 'data', NS, 'loot_table', 'blocks', `${id}.json`);
  if (!existsSync(handWritten)) {
    failures(`缺少手写 gift box loot：${path.relative(ROOT, handWritten)}`);
  } else {
    try {
      JSON.parse(readFileSync(handWritten, 'utf8'));
    } catch (error) {
      failures(`手写 loot JSON 解析失败：${path.relative(ROOT, handWritten)}`);
    }
  }
}

// 8.7 切石机配方
const recipeDir = path.join(GEN, 'data', 'minecraft', 'recipe');
const recipeFiles = readdirSync(recipeDir)
  .filter((file) => file.endsWith('.json'))
  .sort();
const expectedRecipeCount = DOLL_COUNT - (RECIPE_SKIP_TO - RECIPE_SKIP_FROM + 1);
if (recipeFiles.length !== expectedRecipeCount) {
  failures(`recipe 数量不一致：生成 ${recipeFiles.length}，期望 ${expectedRecipeCount}`);
}
const recipeNameSet = new Set();
const recipeWoolCounts = new Map();
const expectedRecipeByName = new Map(stonecuttingRecipes.map((recipe) => [recipe.name, recipe]));
for (const file of recipeFiles) {
  const name = path.basename(file, '.json');
  recipeNameSet.add(name);
  const json = JSON.parse(readFileSync(path.join(recipeDir, file), 'utf8'));
  if (json.type !== 'minecraft:stonecutting') failures(`recipe/${file} 类型不是 stonecutting`);
  if (typeof json.ingredient !== 'string') {
    failures(`recipe/${file} 的 ingredient 必须是纯字符串`);
    continue;
  }
  const wool = json.ingredient.replace(/^minecraft:/, '');
  if (!WOOLS.includes(wool)) failures(`recipe/${file} 使用了未知羊毛：${json.ingredient}`);
  recipeWoolCounts.set(wool, (recipeWoolCounts.get(wool) ?? 0) + 1);
  const resultId = json?.result?.id;
  if (typeof resultId !== 'string' || !resultId.startsWith(`${NS}:`)) {
    failures(`recipe/${file} 的 result.id 不正确：${resultId}`);
  } else if (!registeredItemIdSet.has(resultId.slice(NS.length + 1))) {
    failures(`recipe/${file} 的结果物品未注册：${resultId}`);
  }
  if (json?.result?.count !== 1) failures(`recipe/${file} 的 result.count 不是 1`);
  const expectedName = `${resultId?.slice(NS.length + 1)}_from_${wool}_stonecutting`;
  if (name !== expectedName) failures(`recipe 文件名与内容不一致：${name} != ${expectedName}`);
  // 与上游分组逻辑交叉验证：生成顺序第 n 个配方使用 WOOLS[floor(n/64)]
  const expectedRecipe = expectedRecipeByName.get(name);
  if (!expectedRecipe) {
    failures(`recipe/${file} 不在上游逻辑的生成清单中`);
  } else if (wool !== expectedRecipe.wool) {
    failures(`recipe/${file} 的羊毛分组不正确：${wool}，期望 ${expectedRecipe.wool}`);
  } else if (resultId !== itemId(expectedRecipe.dollId)) {
    failures(`recipe/${file} 的结果物品不正确：${resultId}，期望 ${itemId(expectedRecipe.dollId)}`);
  }
}

// 8.8 配方解锁 advancement
const recipeAdvDir = path.join(GEN, 'data', 'minecraft', 'advancement', 'recipes', 'decorations');
const recipeAdvFiles = readdirSync(recipeAdvDir)
  .filter((file) => file.endsWith('.json'))
  .sort();
if (recipeAdvFiles.length !== expectedRecipeCount) {
  failures(
    `recipe advancement 数量不一致：生成 ${recipeAdvFiles.length}，期望 ${expectedRecipeCount}`,
  );
}
for (const file of recipeAdvFiles) {
  const name = path.basename(file, '.json');
  if (!recipeNameSet.has(name)) failures(`recipe advancement 没有对应配方：${name}`);
  const json = JSON.parse(readFileSync(path.join(recipeAdvDir, file), 'utf8'));
  if (json.parent !== 'minecraft:recipes/root') {
    failures(`recipe advancement/${file} 的 parent 不正确：${json.parent}`);
  }
  if (json?.rewards?.recipes?.length !== 1 || json.rewards.recipes[0] !== `minecraft:${name}`) {
    failures(`recipe advancement/${file} 的 rewards.recipes 不正确`);
  }
  if (!json.criteria?.has_the_recipe) {
    failures(`recipe advancement/${file} 缺少 has_the_recipe 条件`);
  } else if (json.criteria.has_the_recipe.conditions?.recipe !== `minecraft:${name}`) {
    failures(`recipe advancement/${file} 的 has_the_recipe 指向了错误的配方`);
  }
}

// 8.9 doll advancements
const advancementDir = path.join(GEN, 'data', NS, 'advancement', 'doll');
const expectedAdvancementIds = [
  'credits',
  'doll_machine',
  'full_collection',
  'green_doll_gift_box',
  'purple_doll_gift_box',
  'root',
  'yellow_doll_gift_box',
];
const advancementFiles = readdirSync(advancementDir)
  .filter((file) => file.endsWith('.json'))
  .sort();
if (advancementFiles.length !== expectedAdvancementIds.length) {
  failures(
    `advancement 数量不一致：生成 ${advancementFiles.length}，期望 ${expectedAdvancementIds.length}`,
  );
}
const advancementIds = new Set(advancementFiles.map((file) => path.basename(file, '.json')));
const advancementFullIds = new Set(
  advancementFiles.map((file) => `${NS}:doll/${path.basename(file, '.json')}`),
);
for (const id of expectedAdvancementIds) {
  if (!advancementIds.has(id)) failures(`缺少 advancement：doll/${id}.json`);
}
for (const file of advancementFiles) {
  const id = path.basename(file, '.json');
  const json = JSON.parse(readFileSync(path.join(advancementDir, file), 'utf8'));
  if (json.parent) {
    const parentExists =
      json.parent === 'minecraft:recipes/root' || advancementFullIds.has(json.parent);
    if (!parentExists) failures(`advancement/${file} 的 parent 不存在：${json.parent}`);
  }
  if (json.display) {
    const iconId = json.display.icon?.id;
    if (typeof iconId !== 'string' || !iconId.startsWith(`${NS}:`)) {
      failures(`advancement/${file} 的 icon 不正确：${iconId}`);
    } else if (!registeredItemIdSet.has(iconId.slice(NS.length + 1))) {
      failures(`advancement/${file} 的 icon 物品未注册：${iconId}`);
    }
    if (json.display.background !== BACKGROUND) {
      failures(
        `advancement/${file} 的 background 不正确：${json.display.background}，期望 ${BACKGROUND}`,
      );
    }
    if (json.display.icon?.count !== 1) {
      failures(`advancement/${file} 的 icon.count 不是 1`);
    }
  }
  if (id === 'root') {
    if (json.parent) failures('advancement/doll/root.json 不应该有 parent');
    if (!json.criteria?.tick) failures('advancement/doll/root.json 缺少 tick 条件');
  }
  if (id === 'full_collection') {
    const criteriaIds = Object.keys(json.criteria ?? {}).sort();
    const expectedCriteria = [...vanillaDollIds].sort();
    if (criteriaIds.length !== expectedCriteria.length) {
      failures(
        `full_collection criteria 数量不正确：${criteriaIds.length}，期望 ${expectedCriteria.length}`,
      );
    }
    for (const expected of expectedCriteria) {
      if (!json.criteria?.[expected]) failures(`full_collection 缺少 criteria：${expected}`);
    }
    for (const special of specialTooltips) {
      if (json.criteria?.[special]) {
        failures(`full_collection 不应包含 SPECIAL_TOOLTIPS 条目：${special}`);
      }
    }
    if (json.requirements?.length !== expectedCriteria.length) {
      failures(`full_collection requirements 数量不等于 criteria 数量`);
    }
    if (json.display?.frame !== 'challenge') {
      failures('full_collection 的 display.frame 应为 challenge');
    }
    if (json.rewards?.experience !== 100) {
      failures('full_collection 的 rewards.experience 应为 100');
    }
  }
}
// background 贴图必须存在
if (!resolveTexture(BACKGROUND)) {
  failures(`advancement 背景贴图不存在：${BACKGROUND} -> textures/${BACKGROUND.split(':')[1]}.png`);
}

// 8.10 tags
const allDollsTag = JSON.parse(
  readFileSync(path.join(GEN, 'data', NS, 'tags', 'item', 'all_dolls.json'), 'utf8'),
);
if (
  allDollsTag.values.length !== DOLL_COUNT ||
  JSON.stringify(allDollsTag.values) !== JSON.stringify(DOLL_IDS.map(itemId))
) {
  failures('all_dolls tag 内容不正确');
}
const vanillaDollsTag = JSON.parse(
  readFileSync(path.join(GEN, 'data', NS, 'tags', 'item', 'vanilla_dolls.json'), 'utf8'),
);
if (JSON.stringify(vanillaDollsTag.values) !== JSON.stringify(vanillaDollIds.map(itemId))) {
  failures('vanilla_dolls tag 内容不正确');
}
const tokensTag = JSON.parse(
  readFileSync(path.join(GEN, 'data', NS, 'tags', 'item', 'doll_machine_tokens.json'), 'utf8'),
);
if (JSON.stringify(tokensTag.values) !== JSON.stringify(['minecraft:diamond'])) {
  failures('doll_machine_tokens tag 内容不正确');
}
const pickaxeTag = JSON.parse(
  readFileSync(
    path.join(GEN, 'data', 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'),
    'utf8',
  ),
);
if (
  pickaxeTag.replace !== false ||
  JSON.stringify(pickaxeTag.values) !== JSON.stringify([itemId('doll_machine')])
) {
  failures('mineable/pickaxe tag 内容不正确');
}

// 8.11 生成目录不能与手写 resources 同名（processResources 合并时同路径会冲突/覆盖）
for (const { rel } of REQUESTED_OUTPUTS) {
  if (existsSync(path.join(RES, rel))) {
    failures(`生成文件与手写资源同路径冲突：${rel}`);
  }
}

// 8.12 手写配方文件必须保留且不被覆盖
for (const handWritten of ['doll_machine.json', 'doll_entity_crafting.json']) {
  const abs = path.join(RES, 'data', NS, 'recipe', handWritten);
  if (!existsSync(abs)) failures(`缺少手写配方：${path.relative(ROOT, abs)}`);
}

// ---------------------------------------------------------------------------
// 9. 输出统计与结果
// ---------------------------------------------------------------------------
const CATEGORY_LABELS = new Map([
  ['items', `assets/${NS}/items/`],
  ['blockstates', `assets/${NS}/blockstates/`],
  ['loot_table', `data/${NS}/loot_table/blocks/`],
  ['recipe', 'data/minecraft/recipe/'],
  ['recipe_advancement', 'data/minecraft/advancement/recipes/decorations/'],
  ['advancement', `data/${NS}/advancement/doll/`],
  ['item_tag', `data/${NS}/tags/item/`],
  ['block_tag', 'data/minecraft/tags/block/mineable/'],
]);

console.log('');
let total = 0;
for (const [category, label] of CATEGORY_LABELS) {
  const n = counters.get(category) ?? 0;
  total += n;
  console.log(`[generate]  ${label.padEnd(52, ' ')} ${String(n).padStart(4, ' ')} files`);
}
console.log(`[generate]  ${'总计'.padEnd(50, ' ')} ${String(total).padStart(4, ' ')} files`);

const recipeWoolSummary = [...recipeWoolCounts.entries()]
  .map(([wool, n]) => `${wool}=${n}`)
  .join(' ');
const summaryLines = [
  `JSON 解析：${generatedAbsFiles.length}/${generatedAbsFiles.length} 个文件`,
  `items：${generatedItemIds.length} 个（注册范围完全一致）`,
  `item 定义 -> 模型：${resolvedItemModels}/${generatedItemIds.length} 解析成功`,
  `blockstates：${generatedBlockstateIds.length} 个 × 8 variants；模型引用 ${resolvedBlockstateModels}/${generatedBlockstateIds.length * 8} 解析成功`,
  `doll 方块模型：${dollModelCount}/${DOLL_COUNT} 存在`,
  `loot_table：${lootFiles.length} 个（doll_machine half=lower；3 个 gift box 手写文件保留）`,
  `recipe：${recipeFiles.length} 个（${recipeWoolSummary}）`,
  `recipe advancement：${recipeAdvFiles.length} 个`,
  `advancement：${advancementFiles.length} 个（full_collection criteria=${vanillaDollIds.length}）`,
  `tags：all_dolls=${allDollsTag.values.length}，vanilla_dolls=${vanillaDollsTag.values.length}`,
  `SPECIAL_TOOLTIPS：${specialTooltips.size} 个（vanilla_dolls/full_collection 已按上游逻辑排除）`,
];
console.log('');
for (const line of summaryLines) console.log(`[self-check] ${line}`);

if (errors.length > 0) {
  console.error('');
  console.error(`[self-check] FAILED：${errors.length} 个问题`);
  for (const error of errors) console.error(`  - ${error}`);
  process.exitCode = 1;
} else {
  console.log('');
  console.log('[self-check] OK：全部检查通过');
}
