#!/usr/bin/env node
/**
 * verify_assets.mjs — Minecraft 1.21.11 资源包静态校验（kaleidoscope_doll）
 *
 * 用法（在 port 目录下）：
 *   node tools/verify_assets.mjs            # 常规输出：[OK] / [MISS] / [WARN] / [SKIP] / [INFO]
 *   node tools/verify_assets.mjs -v         # 额外逐文件打印 [OK]（288 个模型等）
 *   node tools/verify_assets.mjs --game-dir "D:/MC/.minecraft"
 *
 * 退出码：0 = 没有 [MISS]；1 = 存在 [MISS]（硬错误）
 *
 * 校验内容：
 *   1) assets/** 下所有 JSON（含 mcmeta）可解析、无 BOM
 *   2) 模型：无 render_type（1.21.11 已移除）、父模型可解析、贴图变量与贴图文件可解析
 *   3) blockstates：variants / multipart 引用的模型存在
 *   4) items/*.json（1.21.11 物品模型绑定层）：model / base 引用可解析；
 *      generated 尚未产出时只做 [SKIP]，不判失败
 *   5) lang：Java 用到的 key（translatable、动态 tooltip、方块/物品名、advancement、
 *      sounds subtitle）在 en_us / zh_cn 中齐全，且各语言 key 集合一致
 *   6) sounds.json：事件 id 与 ModSounds 一致、ogg 存在且为 Ogg 容器、subtitle 有翻译
 *   7) pack.mcmeta：min_format / max_format 覆盖 1.21.11 的 resource 75.0 与 data 94.1
 *   8) 手写 data 配方：key / ingredients 必须是 1.21.11 的纯字符串格式
 *
 * 原版参考从 C:\MC\.minecraft\versions\<版本>\<版本>.jar 直接读取（自写 ZIP 解析，
 * 不依赖 unzip）；原版音效用 assets/indexes/<assetIndex>.json。
 */
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(SCRIPT_DIR, '..');
const NS = 'kaleidoscope_doll';
const RES = path.join(ROOT, 'src', 'main', 'resources');
const GEN = path.join(ROOT, 'src', 'main', 'generated');
const ASSETS = path.join(RES, 'assets', NS);

const ARGS = process.argv.slice(2);
const VERBOSE = ARGS.includes('--verbose') || ARGS.includes('-v');
const argValue = (name) => {
  const i = ARGS.indexOf(name);
  return i >= 0 ? ARGS[i + 1] : undefined;
};

const counts = { ok: 0, miss: 0, warn: 0, skip: 0, info: 0 };
const PREFIX = { ok: '[OK]  ', miss: '[MISS]', warn: '[WARN]', skip: '[SKIP]', info: '[INFO]' };
function emit(level, msg) {
  counts[level]++;
  console.log(`${PREFIX[level]} ${msg}`);
}
const ok = (m) => emit('ok', m);
const miss = (m) => emit('miss', m);
const warn = (m) => emit('warn', m);
const skip = (m) => emit('skip', m);
const info = (m) => emit('info', m);
const okVerbose = (m) => { if (VERBOSE) ok(m); };

const posix = (p) => p.split(path.sep).join('/');
const rel = (p) => posix(path.relative(ROOT, p));
const relsafe = (p) => path.relative(ROOT, p) || p;

function section(title) {
  console.log(`\n--- ${title} ---`);
}

/* ------------------------------------------------------------------ */
/* 文件工具                                                             */
/* ------------------------------------------------------------------ */
function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(p, out);
    else if (entry.isFile()) out.push(p);
  }
  return out;
}

function readJsonChecked(file, label) {
  const raw = fs.readFileSync(file);
  if (raw[0] === 0xef && raw[1] === 0xbb && raw[2] === 0xbf) {
    miss(`${label}: 文件带 UTF-8 BOM（资源 JSON 必须无 BOM）`);
  }
  try {
    return JSON.parse(raw.toString('utf8'));
  } catch (e) {
    miss(`${label}: JSON 解析失败 — ${e.message}`);
    return null;
  }
}

function readIf(file) {
  try {
    return fs.readFileSync(file, 'utf8');
  } catch {
    return null;
  }
}

/* ------------------------------------------------------------------ */
/* ZIP 读取（原版 jar）                                                  */
/* ------------------------------------------------------------------ */
class ZipFile {
  constructor(file) {
    this.file = file;
    this.buf = fs.readFileSync(file);
    const buf = this.buf;
    let eocd = -1;
    const min = Math.max(0, buf.length - 65557);
    for (let i = buf.length - 22; i >= min; i--) {
      if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
    }
    if (eocd < 0) throw new Error(`不是有效的 zip/jar：${file}`);
    const count = buf.readUInt16LE(eocd + 10);
    let off = buf.readUInt32LE(eocd + 16);
    this.names = new Set();
    this.entries = Object.create(null);
    for (let i = 0; i < count; i++) {
      if (buf.readUInt32LE(off) !== 0x02014b50) throw new Error(`zip 中央目录损坏：${file}`);
      const method = buf.readUInt16LE(off + 10);
      const csize = buf.readUInt32LE(off + 20);
      const nameLen = buf.readUInt16LE(off + 28);
      const extraLen = buf.readUInt16LE(off + 30);
      const commentLen = buf.readUInt16LE(off + 32);
      const localOff = buf.readUInt32LE(off + 42);
      const name = buf.toString('utf8', off + 46, off + 46 + nameLen);
      if (!name.endsWith('/')) this.names.add(name);
      this.entries[name] = { method, csize, localOff };
      off += 46 + nameLen + extraLen + commentLen;
    }
  }

  has(name) { return this.names.has(name); }

  read(name) {
    const e = this.entries[name];
    if (!e) return null;
    const b = this.buf;
    const nameLen = b.readUInt16LE(e.localOff + 26);
    const extraLen = b.readUInt16LE(e.localOff + 28);
    const start = e.localOff + 30 + nameLen + extraLen;
    const data = b.subarray(start, start + e.csize);
    return e.method === 0 ? data : zlib.inflateRawSync(data);
  }
}

/* ------------------------------------------------------------------ */
/* 原版资源索引                                                         */
/* ------------------------------------------------------------------ */
function detectMcVersion() {
  const props = readIf(path.join(ROOT, 'gradle.properties')) || '';
  const m = props.match(/^minecraft_version\s*=\s*(\S+)/m);
  return m ? m[1] : '1.21.11';
}

const mcVersion = detectMcVersion();
const gameDir = argValue('--game-dir') || process.env.MC_GAME_DIR || 'C:\\MC\\.minecraft';

class VanillaAssets {
  constructor(jarFile, assetObjects) {
    this.zip = new ZipFile(jarFile);
    this.assetObjects = assetObjects; // assets/indexes/<id>.json 的 objects
    this.modelIds = new Set();
    for (const name of this.zip.names) {
      const m = /^assets\/minecraft\/models\/(.+)\.json$/.exec(name);
      if (m) this.modelIds.add(`minecraft:${m[1]}`);
    }
  }

  hasModel(idStr) {
    return this.modelIds.has(idStr);
  }

  hasTexture(idStr) {
    const { ns, p } = splitId(idStr);
    if (ns !== 'minecraft') return false;
    return this.zip.has(`assets/minecraft/textures/${p}.png`) ||
      this.zip.has(`assets/minecraft/textures/${p}.png.mcmeta`);
  }

  hasSound(idStr) {
    if (!this.assetObjects) return null;
    const { ns, p } = splitId(idStr);
    return !!this.assetObjects[`${ns}/sounds/${p}.ogg`];
  }
}

function splitId(idStr) {
  const i = idStr.indexOf(':');
  if (i < 0) return { ns: 'minecraft', p: idStr };
  return { ns: idStr.slice(0, i), p: idStr.slice(i + 1) };
}

/** 无命名空间前缀时按原版规则补 minecraft: */
function normId(idStr) {
  return idStr.includes(':') ? idStr : `minecraft:${idStr}`;
}

function loadVanilla() {
  const jarFile = path.join(gameDir, 'versions', mcVersion, `${mcVersion}.jar`);
  if (!fs.existsSync(jarFile)) {
    skip(`原版参考：未找到 ${jarFile}（--game-dir 可指定），minecraft: 引用与 pack_format 对照跳过`);
    return null;
  }

  let assetObjects = null;
  try {
    const versionJson = JSON.parse(fs.readFileSync(
      path.join(gameDir, 'versions', mcVersion, `${mcVersion}.json`), 'utf8'));
    const indexId = versionJson.assetIndex?.id;
    if (indexId) {
      const indexFile = path.join(gameDir, 'assets', 'indexes', `${indexId}.json`);
      if (fs.existsSync(indexFile)) {
        assetObjects = JSON.parse(fs.readFileSync(indexFile, 'utf8')).objects || null;
      }
    }
  } catch {
    assetObjects = null;
  }

  try {
    const va = new VanillaAssets(jarFile, assetObjects);
    ok(`原版参考：${jarFile}（${va.zip.names.size} 个文件，模型 ${va.modelIds.size} 个` +
      `${assetObjects ? '，含音效索引' : '，无音效索引'}）`);
    return va;
  } catch (e) {
    miss(`原版参考：读取 ${jarFile} 失败 — ${e.message}`);
    return null;
  }
}

/* ------------------------------------------------------------------ */
/* 资源索引                                                             */
/* ------------------------------------------------------------------ */
class Assets {
  constructor(vanilla) {
    this.vanilla = vanilla;
    this.models = new Map();      // id -> { file, json, origin }
    this.textures = new Set();    // id（不含 .png）
    this.modelFiles = [];
    this.textureFiles = [];
    this.duplicates = [];
  }

  addModelDir(baseDir, origin) {
    for (const file of walk(baseDir).filter((f) => f.endsWith('.json'))) {
      const id = `${NS}:${posix(path.relative(baseDir, file)).replace(/\.json$/, '')}`;
      const json = readJsonChecked(file, `${origin}/${rel(file)}`);
      if (json) {
        this.models.set(id, { file, json, origin });
        this.modelFiles.push({ file, id, json, origin });
      }
    }
  }

  addTextureDir(baseDir) {
    for (const file of walk(baseDir).filter((f) => f.endsWith('.png'))) {
      this.textures.add(`${NS}:${posix(path.relative(baseDir, file)).replace(/\.png$/, '')}`);
      this.textureFiles.push(file);
    }
  }

  /** 能否判断这个引用（原版 jar 缺失时 minecraft: 引用无法判定） */
  canCheck(idStr) {
    const id = normId(idStr);
    if (id.startsWith('minecraft:')) return !!this.vanilla;
    return true;
  }

  modelExists(idStr) {
    const id = normId(idStr);
    if (this.models.has(id)) return true;
    if (id.startsWith('minecraft:')) return !!(this.vanilla && this.vanilla.hasModel(id));
    return false;
  }

  textureExists(idStr) {
    const id = normId(idStr);
    if (this.textures.has(id)) return true;
    if (id.startsWith('minecraft:')) return !!(this.vanilla && this.vanilla.hasTexture(id));
    return false;
  }

  resolveTextureVar(value, textures, seen = new Set()) {
    if (typeof value !== 'string') return { error: '不是字符串' };
    if (!value.startsWith('#')) return { id: normId(value) };
    const key = value.slice(1);
    if (seen.has(key)) return { error: `贴图变量循环引用 ${value}` };
    if (!(key in textures)) return { error: `未定义的贴图变量 ${value}` };
    seen.add(key);
    return this.resolveTextureVar(textures[key], textures, seen);
  }
}

/* ------------------------------------------------------------------ */
/* 1. 模型                                                              */
/* ------------------------------------------------------------------ */
function checkModels(assets) {
  section('模型 models/**');

  const withoutRenderType = assets.modelFiles.filter(({ json }) => !('render_type' in json)).length;
  const withRenderType = assets.modelFiles.length - withoutRenderType;
  for (const { file, json } of assets.modelFiles) {
    if ('render_type' in json) {
      miss(`${rel(file)}: 含 1.21.11 已移除的 render_type 字段（渲染层改用 BlockRenderLayerMap 注册）`);
    }
    if (JSON.stringify(json).includes('render_type')) {
      // 嵌套位置出现（例如元素/face 内）同样视为格式问题
      if (!('render_type' in json)) miss(`${rel(file)}: 嵌套位置出现 render_type`);
    }
  }
  if (withRenderType === 0) ok(`models: ${assets.modelFiles.length} 个模型均不含 render_type`);
  else ok(`models: ${withoutRenderType}/${assets.modelFiles.length} 个模型不含 render_type（${withRenderType} 个待修）`);

  const BUILTIN_PARENTS = new Set(['builtin/generated']);
  let parentOk = 0;
  const parentMisses = new Map(); // 消息 -> { count, sample }
  const noteParentMiss = (msg, file) => {
    const hit = parentMisses.get(msg);
    if (hit) { hit.count++; return; }
    parentMisses.set(msg, { count: 1, sample: rel(file) });
  };

  for (const { file, id, json } of assets.modelFiles) {
    const parent = json.parent;
    if (!parent) { parentOk++; okVerbose(`${id}: 无父模型`); continue; }
    if (typeof parent !== 'string') {
      noteParentMiss('parent 不是字符串', file);
      continue;
    }
    if (parent.startsWith('builtin/')) {
      if (BUILTIN_PARENTS.has(parent)) {
        parentOk++;
        okVerbose(`${id}: 父模型 ${parent}（内置，允许）`);
      } else {
        noteParentMiss(`父模型 "${parent}" 在 1.21.11 不存在（builtin/entity 已改为 minecraft:special 物品模型）`, file);
      }
      continue;
    }
    const pid = parent.includes(':') ? parent : `minecraft:${parent}`;
    if (!assets.canCheck(pid)) {
      parentOk++;
      okVerbose(`${id}: 父模型 ${pid}（无原版参考，跳过判断）`);
      continue;
    }
    if (assets.modelExists(pid)) {
      parentOk++;
      okVerbose(`${id}: 父模型 ${pid}`);
    } else {
      noteParentMiss(`父模型 ${pid} 不存在`, file);
    }
  }
  for (const [msg, hit] of parentMisses) {
    miss(`${hit.count} 个模型存在父模型问题：${msg}（例如 ${hit.sample}）`);
  }
  if (parentMisses.size === 0) ok(`models: 父模型引用全部可解析（${parentOk} 个）`);

  const referenced = new Set();
  let texOk = 0;
  const texMisses = new Map(); // 缺失 key -> { count, sample }
  const noteTexMiss = (key, msg, where) => {
    const hit = texMisses.get(key);
    if (hit) { hit.count++; return; }
    texMisses.set(key, { count: 1, sample: where, msg });
  };
  const checkTex = (value, where, textures) => {
    const resolved = assets.resolveTextureVar(value, textures);
    if (resolved.error) {
      noteTexMiss(`var:${resolved.error}`, resolved.error, where);
      return;
    }
    referenced.add(resolved.id);
    if (!assets.canCheck(resolved.id)) {
      texOk++;
      okVerbose(`${where}: 贴图 ${resolved.id}（无原版参考，跳过判断）`);
      return;
    }
    if (assets.textureExists(resolved.id)) {
      texOk++;
      okVerbose(`${where}: 贴图 ${resolved.id}`);
    } else {
      noteTexMiss(`tex:${resolved.id}`, `贴图 ${resolved.id} 不存在`, where);
    }
  };

  for (const { file, json } of assets.modelFiles) {
    const textures = json.textures && typeof json.textures === 'object' ? json.textures : {};
    for (const [key, value] of Object.entries(textures)) {
      if (typeof value === 'string' && value.startsWith('#')) {
        const resolved = assets.resolveTextureVar(value, textures);
        if (resolved.error) noteTexMiss(`var:${resolved.error}`, resolved.error, `${rel(file)}: textures.${key}`);
        continue;
      }
      checkTex(value, `${rel(file)}: textures.${key}`, textures);
    }
    for (const [elIndex, el] of (json.elements || []).entries()) {
      for (const [face, def] of Object.entries(el.faces || {})) {
        if (!def || def.texture === undefined) continue;
        checkTex(def.texture, `${rel(file)}: elements[${elIndex}].faces.${face}`, textures);
      }
    }
  }
  for (const hit of texMisses.values()) {
    miss(`${hit.count} 处模型贴图引用无法解析：${hit.msg}（例如 ${hit.sample}）`);
  }
  if (texMisses.size === 0) {
    ok(`models: 贴图引用全部存在（${texOk} 处引用，去重 ${referenced.size} 个 id）`);
  }

  return referenced;
}

/* ------------------------------------------------------------------ */
/* 2. blockstates                                                       */
/* ------------------------------------------------------------------ */
function checkBlockstates(assets) {
  section('方块状态 blockstates/**');

  const dirs = [
    { dir: path.join(ASSETS, 'blockstates'), origin: 'resources' },
    { dir: path.join(GEN, 'assets', NS, 'blockstates'), origin: 'generated' },
  ];
  const seen = new Set();
  let files = 0;
  let variants = 0;
  let bad = 0;

  for (const { dir, origin } of dirs) {
    for (const file of walk(dir).filter((f) => f.endsWith('.json'))) {
      files++;
      const json = readJsonChecked(file, `${origin}/${rel(file)}`);
      if (!json) { bad++; continue; }
      const refs = [];
      if (json.variants && typeof json.variants === 'object') {
        for (const value of Object.values(json.variants)) {
          if (Array.isArray(value)) for (const v of value) refs.push(v?.model);
          else refs.push(value?.model);
        }
      }
      if (Array.isArray(json.multipart)) {
        for (const part of json.multipart) refs.push(part?.apply?.model);
      }
      if (!json.variants && !json.multipart) {
        miss(`${origin}/${rel(file)}: 既没有 variants 也没有 multipart`);
        bad++;
        continue;
      }
      for (const ref of refs) {
        if (typeof ref !== 'string') continue;
        variants++;
        const id = ref.includes(':') ? ref : `minecraft:${ref}`;
        if (!assets.canCheck(id)) {
          okVerbose(`${origin}/${rel(file)}: 模型 ${id}（无原版参考，跳过判断）`);
          continue;
        }
        if (!assets.modelExists(id)) {
          miss(`${origin}/${rel(file)}: 引用的模型 ${id} 不存在`);
          bad++;
        } else {
          okVerbose(`${origin}/${rel(file)}: 模型 ${id} 存在`);
        }
      }
      seen.add(posix(path.relative(ROOT, file)));
    }
  }

  if (files === 0) {
    miss('blockstates: 一个文件都没有找到');
  } else if (bad === 0) {
    const genCount = fs.existsSync(dirs[1].dir)
      ? walk(dirs[1].dir).filter((f) => f.endsWith('.json')).length
      : 0;
    ok(`blockstates: ${files} 个文件（resources ${files - genCount} + generated ${genCount}）、` +
      `${variants} 个模型引用全部存在`);
  }
}

/* ------------------------------------------------------------------ */
/* 3. 物品模型绑定层 assets/<ns>/items/*.json                           */
/* ------------------------------------------------------------------ */
function collectItemIds() {
  const ids = new Set();
  const blockIds = new Set();
  const javaRoots = [path.join(ROOT, 'src', 'main', 'java'), path.join(ROOT, 'src', 'client', 'java')];
  const javaFiles = javaRoots.flatMap((d) => walk(d).filter((f) => f.endsWith('.java')));
  const readById = new Map(javaFiles.map((f) => [path.basename(f), fs.readFileSync(f, 'utf8')]));

  const modBlocks = readById.get('ModBlocks.java') || '';
  const modItems = readById.get('ModItems.java') || '';
  const modRegister = readById.get('ModRegisterEvent.java') || '';

  // 1.21.11 写法：public static final ResourceKey<Item> XXX_ID = id("path");
  const keyRe = /ResourceKey<(Item|Block)>\s+(\w+)\s*=\s*(?:id|makeId)\(\s*"([^"]+)"\s*\)/g;
  for (const m of modBlocks.matchAll(keyRe)) if (m[1] === 'Block') blockIds.add(m[3]);
  for (const m of modItems.matchAll(keyRe)) if (m[1] === 'Item') ids.add(m[3]);

  // 旧写法兜底：Registry.register(BuiltInRegistries.ITEM, ResourceLocation...fromNamespaceAndPath(..., "id")
  const regRe = (kind) => new RegExp(
    `Registry\\.register\\(BuiltInRegistries\\.${kind},\\s*(?:ResourceLocation|Identifier)\\.fromNamespaceAndPath\\(KaleidoscopeDoll\\.MOD_ID,\\s*"([^"]+)"\\)`,
    'g');
  for (const m of modBlocks.matchAll(regRe('BLOCK'))) blockIds.add(m[1]);
  for (const m of modItems.matchAll(regRe('ITEM'))) ids.add(m[1]);

  // 玩偶：IntStream.range(0, MAX_DOLL_COUNT) + "doll_" + i
  const maxDolls = Number((modRegister.match(/MAX_DOLL_COUNT\s*=\s*(\d+)/) || [])[1]);
  if (maxDolls > 0 && /"doll_"\s*\+\s*i/.test(modRegister)) {
    for (let i = 0; i < maxDolls; i++) {
      ids.add(`doll_${i}`);
      blockIds.add(`doll_${i}`);
    }
  }
  return { itemIds: ids, blockIds };
}

function checkItemDefinitions(assets) {
  section('物品模型绑定 items/*.json');

  const dirs = [
    { dir: path.join(ASSETS, 'items'), origin: 'resources' },
    { dir: path.join(GEN, 'assets', NS, 'items'), origin: 'generated' },
  ];
  const present = dirs.filter((d) => fs.existsSync(d.dir));
  const refsSeen = new Set();
  let files = 0;
  let bad = 0;
  const presentIds = new Set();

  for (const { dir, origin } of present) {
    for (const file of walk(dir).filter((f) => f.endsWith('.json'))) {
      files++;
      presentIds.add(path.basename(file, '.json'));
      const json = readJsonChecked(file, `${origin}/${rel(file)}`);
      if (!json) { bad++; continue; }
      const refs = [];
      const walkDef = (node, where) => {
        if (!node || typeof node !== 'object') return;
        if (Array.isArray(node)) {
          node.forEach((v, i) => walkDef(v, `${where}[${i}]`));
          return;
        }
        for (const [key, value] of Object.entries(node)) {
          if ((key === 'model' || key === 'base') && typeof value === 'string') {
            refs.push({ ref: value, where: `${where}.${key}` });
          } else if (value && typeof value === 'object') {
            walkDef(value, `${where}.${key}`);
          }
        }
      };
      walkDef(json, '');
      for (const { ref, where } of refs) {
        const id = ref.includes(':') ? ref : `minecraft:${ref}`;
        refsSeen.add(id);
        if (!assets.canCheck(id)) {
          okVerbose(`${origin}/${rel(file)}: ${where} → ${id}（无原版参考，跳过判断）`);
          continue;
        }
        if (!assets.modelExists(id)) {
          miss(`${origin}/${rel(file)}: ${where} 引用的模型 ${id} 不存在`);
          bad++;
        } else {
          okVerbose(`${origin}/${rel(file)}: ${where} → ${id}`);
        }
      }
    }
  }

  if (files === 0) {
    skip(`items/*.json: 尚未产出（data-gen 未生成 ${rel(path.join(GEN, 'assets', NS, 'items'))}），只校验已有文件`);
  } else if (bad === 0) {
    ok(`items/*.json: ${files} 个文件、${refsSeen.size} 个模型引用全部可解析`);
  }

  // 覆盖度只作为提示：data-gen 尚未完成时不判失败
  const { itemIds } = collectItemIds();
  const expected = [...itemIds];
  const missing = expected.filter((id) => !presentIds.has(id));
  if (files > 0 && missing.length > 0) {
    warn(`items/*.json: ${missing.length}/${expected.length} 个物品缺少模型绑定（data-gen 可能仍在生成，例如 ${missing.slice(0, 6).join(', ')}${missing.length > 6 ? ' …' : ''}）`);
  } else if (files > 0) {
    ok(`items/*.json: 全部 ${expected.length} 个物品都有模型绑定`);
  }
}

/* ------------------------------------------------------------------ */
/* 4. lang                                                              */
/* ------------------------------------------------------------------ */
function collectExpectedLangKeys(assets) {
  const keys = new Set();
  const javaRoots = [path.join(ROOT, 'src', 'main', 'java'), path.join(ROOT, 'src', 'client', 'java')];
  const javaText = javaRoots.flatMap((d) => walk(d).filter((f) => f.endsWith('.java')))
    .map((f) => fs.readFileSync(f, 'utf8')).join('\n');

  for (const m of javaText.matchAll(/Component\.translatable\(\s*"([^"]+)"/g)) keys.add(m[1]);

  // 构造函数/普通字符串里直接传入的翻译 key（例如 ModBlockItem(..., "tooltip.xxx")）
  for (const m of javaText.matchAll(
    /"((?:tooltip|item_group|advancements|subtitle|item|block)\.kaleidoscope_doll\.[A-Za-z0-9_.]*[A-Za-z0-9_])"/g)) {
    if (m[1].length > 0 && !m[1].endsWith('.')) keys.add(m[1]);
  }

  // 玩偶 tooltip：tooltip.<ns>.doll.<langKey>，另有 vanilla
  const modRegister = javaText;
  for (const m of modRegister.matchAll(/registerSpecialTooltips\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)/g)) {
    keys.add(`tooltip.${NS}.doll.${m[2]}`);
  }
  if (/registerSpecialTooltips|SPECIAL_TOOLTIPS/.test(modRegister)) keys.add(`tooltip.${NS}.doll.vanilla`);

  // 动态 advancement key：优先从已生成的 advancement JSON 读取
  const advRoot = path.join(GEN, 'data');
  let advFromGen = 0;
  for (const file of walk(advRoot).filter((f) => f.endsWith('.json') && f.includes(`${path.sep}advancement${path.sep}`))) {
    const json = readJsonChecked(file, rel(file));
    if (!json) continue;
    const visit = (node) => {
      if (!node || typeof node !== 'object') return;
      if (Array.isArray(node)) { node.forEach(visit); return; }
      for (const [k, v] of Object.entries(node)) {
        if (k === 'translate' && typeof v === 'string') { keys.add(v); advFromGen++; }
        else if (v && typeof v === 'object') visit(v);
      }
    };
    visit(json);
  }
  if (advFromGen === 0) {
    // 退化：从 AdvancementGenerator 的 make(..., "key") / makeChallenge(..., "key") 推导
    const gen = readIf(path.join(ROOT, 'src', 'main', 'java', 'com', 'github', 'ysbbbbbb',
      'kaleidoscopedoll', 'datagen', 'AdvancementGenerator.java'));
    if (gen) {
      for (const m of gen.matchAll(/make(?:Challenge)?\([^,]+,\s*"([^"]+)"\)/g)) {
        keys.add(`advancements.${NS}.doll.${m[1]}.title`);
        keys.add(`advancements.${NS}.doll.${m[1]}.description`);
      }
    }
  }

  // sounds.json 的 subtitle
  const soundsFile = path.join(ASSETS, 'sounds.json');
  if (fs.existsSync(soundsFile)) {
    try {
      const soundJson = JSON.parse(fs.readFileSync(soundsFile, 'utf8'));
      for (const def of Object.values(soundJson)) {
        if (def && typeof def === 'object' && typeof def.subtitle === 'string') keys.add(def.subtitle);
      }
    } catch { /* sounds 段会报错 */ }
  }

  // 说明：不按“注册 id 推导 block.* / item.* 名称 key”。
  // DollItem.getName() 硬编码返回 block.<ns>.doll，BlockItem 的名称 key 也无法从
  // 注册 id 可靠推导（useBlockDescriptionPrefix 等），因此这里只校验代码里
  // 字面量出现的 key（Component.translatable(...) 与构造函数里的 key 字符串）。

  return keys;
}

function checkLang(assets) {
  section('语言 lang/**');

  const langDir = path.join(ASSETS, 'lang');
  const langFiles = walk(langDir).filter((f) => f.endsWith('.json'));
  if (langFiles.length === 0) {
    miss(`lang: ${rel(langDir)} 下没有语言文件`);
    return;
  }

  const expected = collectExpectedLangKeys(assets);
  const tables = new Map();
  let bad = 0;

  for (const file of langFiles) {
    const name = path.basename(file);
    const json = readJsonChecked(file, `resources/${rel(file)}`);
    if (!json) { bad++; continue; }
    tables.set(name, json);

    // 重复 key 检测（JSON.parse 会静默保留最后一个）
    const raw = fs.readFileSync(file, 'utf8');
    const rawKeys = [...raw.matchAll(/^\s*"((?:[^"\\]|\\.)+)"\s*:/gm)].map((m) => m[1]);
    const dupes = rawKeys.filter((k, i) => rawKeys.indexOf(k) !== i);
    if (dupes.length > 0) {
      miss(`${rel(file)}: 存在重复 key ${[...new Set(dupes)].slice(0, 5).join(', ')}`);
      bad++;
    }

    const missing = [...expected].filter((k) => !(k in json));
    if (missing.length > 0) {
      miss(`${name}: 缺少 ${missing.length} 个 Java 使用的 key：${missing.slice(0, 10).join(', ')}${missing.length > 10 ? ' …' : ''}`);
      bad++;
    } else {
      ok(`${name}: ${Object.keys(json).length} 个 key，Java 用到的 ${expected.size} 个 key 齐全`);
    }
    if (VERBOSE) {
      for (const k of [...expected].filter((k) => k in json)) ok(`${name}: ${k}`);
    }
  }

  // 不同语言的 key 集合必须一致
  const names = [...tables.keys()];
  if (names.length >= 2) {
    const base = new Set(Object.keys(tables.get(names[0])));
    for (const other of names.slice(1)) {
      const cur = new Set(Object.keys(tables.get(other)));
      const onlyBase = [...base].filter((k) => !cur.has(k));
      const onlyOther = [...cur].filter((k) => !base.has(k));
      if (onlyBase.length || onlyOther.length) {
        miss(`lang: ${names[0]} 与 ${other} 的 key 集合不一致（仅前者 ${onlyBase.length} 个，仅后者 ${onlyOther.length} 个）`);
        bad++;
      } else {
        ok(`lang: ${names[0]} 与 ${other} key 集合一致（${base.size} 个）`);
      }
    }
  }
  if (bad === 0) ok(`lang: ${langFiles.length} 个语言文件全部通过`);
}

/* ------------------------------------------------------------------ */
/* 5. sounds.json 与 ogg                                                */
/* ------------------------------------------------------------------ */
function checkSounds(assets, vanilla) {
  section('音效 sounds.json / ogg');

  const soundsFile = path.join(ASSETS, 'sounds.json');
  if (!fs.existsSync(soundsFile)) {
    miss(`sounds.json: 缺少 ${rel(soundsFile)}（SoundEvent 已注册但没有任何音频定义）`);
    return;
  }
  const json = readJsonChecked(soundsFile, `resources/${rel(soundsFile)}`);
  if (!json) return;

  // Java 里注册的 sound event id
  const modSounds = readIf(path.join(ROOT, 'src', 'main', 'java', 'com', 'github', 'ysbbbbbb',
    'kaleidoscopedoll', 'init', 'ModSounds.java')) || '';
  const registered = new Set();
  for (const m of modSounds.matchAll(
    /createFixedRangeEvent\(\s*ResourceLocation\.fromNamespaceAndPath\(KaleidoscopeDoll\.MOD_ID,\s*"([^"]+)"\)/g)) {
    registered.add(m[1]);
  }
  const defined = new Set(Object.keys(json));

  for (const id of registered) {
    if (!defined.has(id)) miss(`sounds.json: Java 注册的 ${NS}:${id} 没有音频定义`);
  }
  for (const id of defined) {
    if (registered.size > 0 && !registered.has(id)) {
      warn(`sounds.json: 事件 "${id}" 在 ModSounds.java 中没有对应注册（死定义）`);
    }
  }

  let referencedOgg = new Set();
  let bad = 0;
  let vanillaSoundChecked = 0;
  for (const [event, def] of Object.entries(json)) {
    if (!def || typeof def !== 'object') {
      miss(`sounds.json: 事件 "${event}" 不是对象`);
      bad++;
      continue;
    }
    if ('category' in def) {
      warn(`sounds.json: 事件 "${event}" 含 1.21.11 已移除的 category 字段（原版 1837 个事件中为 0）`);
    }
    if (!Array.isArray(def.sounds) || def.sounds.length === 0) {
      miss(`sounds.json: 事件 "${event}" 缺少非空 sounds 数组`);
      bad++;
      continue;
    }
    let okCount = 0;
    for (const [i, sound] of def.sounds.entries()) {
      if (typeof sound === 'string') {
        // 简单形式
      } else if (sound && typeof sound === 'object') {
        if (typeof sound.name !== 'string') {
          miss(`sounds.json: ${event}.sounds[${i}] 缺少 name`);
          bad++;
          continue;
        }
        if ('type' in sound && sound.type !== 'file' && sound.type !== 'event') {
          warn(`sounds.json: ${event}.sounds[${i}] type=${sound.type}`);
        }
      } else {
        miss(`sounds.json: ${event}.sounds[${i}] 既不是字符串也不是对象`);
        bad++;
        continue;
      }
      const rawName = typeof sound === 'string' ? sound : sound.name;
      const { ns, p } = splitId(rawName);
      if (ns === NS) {
        const ogg = path.join(ASSETS, 'sounds', `${p}.ogg`);
        if (!fs.existsSync(ogg)) {
          miss(`sounds.json: ${event}.sounds[${i}] → ${NS}:${p} 缺少 ${rel(ogg)}`);
          bad++;
          continue;
        }
        const head = fs.readFileSync(ogg).subarray(0, 4).toString('latin1');
        if (head !== 'OggS') {
          miss(`${rel(ogg)}: 不是 Ogg 容器（魔数 ${JSON.stringify(head)}）`);
          bad++;
          continue;
        }
        referencedOgg.add(posix(path.relative(path.join(ASSETS, 'sounds'), ogg)));
        okCount++;
        okVerbose(`sounds.json: ${event} → ${rel(ogg)}`);
      } else {
        const found = vanilla ? vanilla.hasSound(`${ns}:${p}`) : null;
        if (found === null) {
          skip(`sounds.json: ${event} → ${ns}:${p}（无原版音效索引，跳过）`);
        } else if (!found) {
          miss(`sounds.json: ${event} → ${ns}:${p} 在原版音效索引中不存在`);
          bad++;
        } else {
          vanillaSoundChecked++;
          okCount++;
        }
      }
    }
    if (okCount > 0) okVerbose(`sounds.json: 事件 ${event} 的 ${okCount} 个音效可解析`);
  }

  if (bad === 0) {
    ok(`sounds.json: ${defined.size} 个事件、${Object.values(json).reduce((n, d) => n + (Array.isArray(d?.sounds) ? d.sounds.length : 0), 0)} 个音效全部可解析` +
      (vanillaSoundChecked ? `（含 ${vanillaSoundChecked} 个原版音效）` : ''));
  }

  // 未被引用、Java 也没用到的 ogg（不判失败，只提示）
  const allOgg = walk(path.join(ASSETS, 'sounds')).filter((f) => f.endsWith('.ogg'))
    .map((f) => posix(path.relative(path.join(ASSETS, 'sounds'), f)));
  const orphans = allOgg.filter((f) => !referencedOgg.has(f));
  if (orphans.length) info(`sounds: 未被 sounds.json 引用的 ogg：${orphans.join(', ')}`);
}

/* ------------------------------------------------------------------ */
/* 6. 贴图 / mcmeta                                                    */
/* ------------------------------------------------------------------ */
function checkTextures(assets, referenced) {
  section('贴图 textures/**');

  const pngs = assets.textureFiles;
  if (pngs.length === 0) {
    miss('textures: 没有找到任何 png');
    return;
  }
  let bad = 0;
  for (const file of pngs) {
    const b = fs.readFileSync(file);
    if (b.readUInt32BE(0) !== 0x89504e47) {
      miss(`${rel(file)}: PNG 魔数不正确`);
      bad++;
    }
  }
  if (bad === 0) ok(`textures: ${pngs.length} 个 png 魔数全部正确`);

  // mcmeta
  const metas = walk(path.join(ASSETS, 'textures')).filter((f) => f.endsWith('.mcmeta'));
  let metaBad = 0;
  for (const file of metas) {
    const json = readJsonChecked(file, `resources/${rel(file)}`);
    if (!json) { metaBad++; continue; }
    const base = file.replace(/\.mcmeta$/, '');
    if (!fs.existsSync(base)) {
      miss(`${rel(file)}: 缺少对应的 ${path.basename(base)}`);
      metaBad++;
      continue;
    }
    // 动画贴图校验：高度应为宽度的整数倍
    if (json.animation) {
      const png = fs.readFileSync(base);
      const w = png.readUInt32BE(16);
      const h = png.readUInt32BE(20);
      if (w > 0 && h % w !== 0) {
        warn(`${rel(base)}: 动画贴图高度 ${h} 不是宽度 ${w} 的整数倍`);
      }
    }
  }
  if (metaBad === 0) ok(`textures: ${metas.length} 个 .mcmeta 均可解析且基础 png 存在`);

  // 未被模型引用的贴图（只提示）
  const unused = pngs
    .map((f) => `${NS}:${posix(path.relative(path.join(ASSETS, 'textures'), f)).replace(/\.png$/, '')}`)
    .filter((id) => !referenced.has(id));
  if (unused.length) {
    info(`textures: ${unused.length} 张贴图未被模型引用：${unused.slice(0, 12).join(', ')}${unused.length > 12 ? ' …' : ''}`);
  }
}

/* ------------------------------------------------------------------ */
/* 7. pack.mcmeta                                                       */
/* ------------------------------------------------------------------ */
function checkPackMeta(vanilla) {
  section('pack.mcmeta');

  const file = path.join(RES, 'pack.mcmeta');
  if (!fs.existsSync(file)) {
    // 有意不发布 pack.mcmeta：1.21.11 的资源侧与数据侧校验规则互斥
    // （资源侧拒绝 supported_formats，数据侧要求 pack_format + supported_formats + min/max），
    // 实测不提供该文件时客户端/服务端都无报错且资源与数据均正常加载。
    info('pack.mcmeta: 有意不提供（1.21.11 资源/数据两侧校验规则互斥，省略后两侧均正常加载）');
    return;
  }
  const json = readJsonChecked(file, 'resources/pack.mcmeta');
  if (!json) return;
  const pack = json.pack || {};

  // 1.21.11: resource_major/minor = 75.0, data_major/minor = 94.1
  const expectedResource = [75, 0];
  const expectedData = [94, 1];
  const asPair = (v) => (Array.isArray(v) ? v : [Number(v), 0]);
  const covers = (min, max, [major, minor]) => {
    const m = asPair(min);
    const x = asPair(max);
    return (m[0] < major || (m[0] === major && m[1] <= minor)) &&
      (x[0] > major || (x[0] === major && x[1] >= minor));
  };

  const hasOld = 'pack_format' in pack;
  const hasRange = 'min_format' in pack || 'max_format' in pack;
  if (hasOld && !hasRange) {
    miss(`pack.mcmeta: 只有旧字段 pack_format=${pack.pack_format}，1.21.11 需 min_format / max_format`);
    return;
  }
  const min = pack.min_format ?? pack.pack_format;
  const max = pack.max_format ?? pack.pack_format;
  if (min === undefined || max === undefined) {
    miss('pack.mcmeta: 缺少 min_format / max_format');
    return;
  }
  const resourceOk = covers(min, max, expectedResource);
  const dataOk = covers(min, max, expectedData);
  if (resourceOk && dataOk) {
    ok(`pack.mcmeta: min_format=${JSON.stringify(min)} max_format=${JSON.stringify(max)} 同时覆盖 resource 75.0 与 data 94.1`);
  } else {
    miss(`pack.mcmeta: 格式范围 ${JSON.stringify(min)}..${JSON.stringify(max)} 未覆盖 ` +
      `${resourceOk ? '' : `resource 75.0 `}${dataOk ? '' : 'data 94.1'}`.trim());
  }
}

/* ------------------------------------------------------------------ */
/* 8. 手写 data 配方格式                                                */
/* ------------------------------------------------------------------ */
function checkHandwrittenData() {
  section('data/ 手写文件');

  const dataDir = path.join(RES, 'data');
  const recipeFiles = walk(path.join(dataDir, NS, 'recipe')).filter((f) => f.endsWith('.json'));
  if (recipeFiles.length === 0) {
    skip('data: 没有手写配方文件');
    return;
  }
  let bad = 0;
  for (const file of recipeFiles) {
    const json = readJsonChecked(file, `resources/${rel(file)}`);
    if (!json) { bad++; continue; }
    const type = json.type || '';
    const oldIngredient = (v) => v && typeof v === 'object' && !Array.isArray(v) && ('item' in v || 'tag' in v);

    if (typeof json.key === 'object' && json.key !== null) {
      for (const [slot, value] of Object.entries(json.key)) {
        if (oldIngredient(value)) {
          miss(`${rel(file)}: key.${slot} 是 1.20.1 旧格式 ${JSON.stringify(value)}；1.21.11 必须写成字符串（如 "#c:ingots/copper"）`);
          bad++;
        }
      }
    }
    if (Array.isArray(json.ingredients)) {
      for (const [i, value] of json.ingredients.entries()) {
        if (oldIngredient(value)) {
          miss(`${rel(file)}: ingredients[${i}] 是 1.20.1 旧格式 ${JSON.stringify(value)}；1.21.11 必须写成字符串`);
          bad++;
        }
      }
    }
    if (json.result && typeof json.result === 'object' && 'item' in json.result && !('id' in json.result)) {
      miss(`${rel(file)}: result 使用旧字段 item，1.21.11 用 id`);
      bad++;
    }
    okVerbose(`${rel(file)}: 配方格式 OK（${type || '未知类型'}）`);
  }
  if (bad === 0) ok(`data: ${recipeFiles.length} 个手写配方均为 1.21.11 字符串 ingredient 格式`);
}

/* ------------------------------------------------------------------ */
/* 9. advancement 背景贴图（1.21.2+ 用贴图 id，不再写 textures/ 与 .png）  */
/* ------------------------------------------------------------------ */
function checkAdvancementBackgrounds(assets) {
  section('advancement 背景');

  const dir = path.join(GEN, 'data');
  const files = walk(dir).filter((f) => f.endsWith('.json') &&
    f.includes(`${path.sep}advancement${path.sep}`));
  if (files.length === 0) {
    skip('advancement: generated/data 尚未产出，跳过背景检查');
    return;
  }

  let checked = 0;
  let bad = 0;
  for (const file of files) {
    const json = readJsonChecked(file, rel(file));
    if (!json) { bad++; continue; }
    const bg = json.display?.background;
    if (typeof bg !== 'string') continue;
    checked++;
    if (bg.includes('textures/') || bg.endsWith('.png')) {
      miss(`${rel(file)}: display.background="${bg}" 是 1.20/1.21.1 旧写法；` +
        '1.21.2+ 必须写成贴图 id（去掉 textures/ 前缀与 .png，例如 kaleidoscope_doll:advancement/background）');
      bad++;
    } else if (!assets.textureExists(bg)) {
      miss(`${rel(file)}: display.background="${bg}" 对应的贴图不存在` +
        `（应为 assets/${splitId(bg).ns}/textures/${splitId(bg).p}.png）`);
      bad++;
    }
  }
  if (checked === 0) {
    skip('advancement: 没有需要检查的 display.background');
  } else if (bad === 0) {
    ok(`advancement: ${checked} 个背景贴图引用均为 1.21.11 贴图 id 写法且文件存在`);
  }
}

/* ------------------------------------------------------------------ */
/* 10. resources 与 generated 重复资源                                   */
/* ------------------------------------------------------------------ */
function checkDuplicateResources() {
  section('资源目录重复检查');

  const spaces = [
    { dir: path.join(RES, 'assets', NS), label: 'resources/assets' },
    { dir: path.join(GEN, 'assets', NS), label: 'generated/assets' },
  ];
  const sets = spaces.map(({ dir, label }) => ({
    label,
    files: new Set(walk(dir).map((f) => posix(path.relative(dir, f)))),
  }));
  if (sets[1].files.size === 0) {
    skip('duplicates: generated/assets 尚未产出，跳过');
    return;
  }
  const dupes = [...sets[0].files].filter((f) => sets[1].files.has(f));
  if (dupes.length) {
    warn(`duplicates: ${dupes.length} 个文件同时存在于 resources 与 generated（打包时可能产生重复资源）：` +
      `${dupes.slice(0, 8).join(', ')}${dupes.length > 8 ? ' …' : ''}`);
  } else {
    ok('duplicates: resources 与 generated 没有重复文件');
  }
}

/* ------------------------------------------------------------------ */
/* main                                                                 */
/* ------------------------------------------------------------------ */
console.log(`=== kaleidoscope_doll 1.21.11 资源审计（${ROOT}）===`);
console.log(`命名空间：${NS} / MC 版本：${mcVersion} / 原版目录：${gameDir}`);

if (!fs.existsSync(ASSETS)) {
  miss(`资源根目录不存在：${relsafe(ASSETS)}`);
  process.exit(1);
}

const vanilla = loadVanilla();
const assets = new Assets(vanilla);
assets.addModelDir(path.join(ASSETS, 'models'), 'resources');
assets.addModelDir(path.join(GEN, 'assets', NS, 'models'), 'generated');
assets.addTextureDir(path.join(ASSETS, 'textures'));
assets.addTextureDir(path.join(GEN, 'assets', NS, 'textures'));

const referencedTextures = checkModels(assets);
checkBlockstates(assets);
checkItemDefinitions(assets);
checkLang(assets);
checkSounds(assets, vanilla);
checkTextures(assets, referencedTextures);
checkPackMeta(vanilla);
checkHandwrittenData();
checkAdvancementBackgrounds(assets);
checkDuplicateResources();

console.log('\n=== 汇总 ===');
console.log(`[OK] ${counts.ok}  [MISS] ${counts.miss}  [WARN] ${counts.warn}  [SKIP] ${counts.skip}  [INFO] ${counts.info}`);
if (counts.miss === 0) {
  console.log('[OK]  全部硬性检查通过');
  process.exit(0);
} else {
  console.log(`[MISS] 存在 ${counts.miss} 项硬性问题，见上方 [MISS] 行`);
  process.exit(1);
}
