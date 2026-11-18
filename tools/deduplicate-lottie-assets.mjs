import { createHash } from "node:crypto";
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const toolsRoot = dirname(fileURLToPath(import.meta.url));
const repoRoot = dirname(toolsRoot);
const rawRoot = join(repoRoot, "app", "src", "main", "res", "raw");
const imageRoot = join(repoRoot, "app", "src", "main", "assets", "images");

function sha256(path) {
  return createHash("sha256").update(readFileSync(path)).digest("hex");
}

function imageReferenceNeutralClone(composition) {
  const clone = structuredClone(composition);
  for (const asset of clone.assets ?? []) {
    if (asset.p) asset.p = "__IMAGE_REFERENCE__";
  }
  return clone;
}

const imageNames = readdirSync(imageRoot)
  .filter((name) => name.toLowerCase().endsWith(".png"))
  .sort((left, right) => left.localeCompare(right, "en"));

const hashGroups = new Map();
for (const name of imageNames) {
  const path = join(imageRoot, name);
  const hash = sha256(path);
  const group = hashGroups.get(hash) ?? [];
  group.push({ name, path, bytes: statSync(path).size, hash });
  hashGroups.set(hash, group);
}

const nameMap = new Map();
const duplicateFiles = [];
for (const group of hashGroups.values()) {
  if (group.length < 2) continue;
  group.sort((left, right) => left.name.localeCompare(right.name, "en"));
  const canonical = group[0];
  for (const duplicate of group.slice(1)) {
    nameMap.set(duplicate.name, canonical.name);
    duplicateFiles.push(duplicate);
  }
}

// This composition references the same 512x210 scan artwork used by the
// app, battery, and document scan compositions, but its original file is absent.
const sharedScanAsset = "app_scan_1.png";
if (!existsSync(join(imageRoot, sharedScanAsset))) {
  throw new Error(`Shared scan asset is missing: ${sharedScanAsset}`);
}
nameMap.set("process_scan_6.png", sharedScanAsset);

const rewrites = [];
const jsonNames = readdirSync(rawRoot)
  .filter((name) => name.toLowerCase().endsWith(".json"))
  .sort((left, right) => left.localeCompare(right, "en"));

for (const name of jsonNames) {
  const path = join(rawRoot, name);
  const originalText = readFileSync(path, "utf8");
  const composition = JSON.parse(originalText);
  const beforeSemantic = JSON.stringify(imageReferenceNeutralClone(composition));
  let changed = false;

  for (const asset of composition.assets ?? []) {
    if (typeof asset.p !== "string") continue;
    const canonicalName = nameMap.get(asset.p);
    if (!canonicalName) continue;
    asset.p = canonicalName;
    changed = true;
  }

  if (!changed) continue;
  const text = JSON.stringify(composition);
  const reparsed = JSON.parse(text);
  const afterSemantic = JSON.stringify(imageReferenceNeutralClone(reparsed));
  if (beforeSemantic !== afterSemantic) {
    throw new Error(`Non-image Lottie data changed while preparing ${name}`);
  }
  for (const asset of reparsed.assets ?? []) {
    if (asset.p && !existsSync(join(imageRoot, asset.p))) {
      throw new Error(`Prepared Lottie reference is missing: ${name} -> ${asset.p}`);
    }
  }
  rewrites.push({ name, path, text });
}

const timestamp = new Date().toISOString().replace(/[-:T]/g, "").slice(0, 14);
const backupRoot = join(
  repoRoot,
  "app",
  "build",
  "optimization-backups",
  `lottie-${timestamp}`,
);
const backupJsonRoot = join(backupRoot, "raw");
const backupImageRoot = join(backupRoot, "images");
mkdirSync(backupJsonRoot, { recursive: true });
mkdirSync(backupImageRoot, { recursive: true });

for (const rewrite of rewrites) {
  copyFileSync(rewrite.path, join(backupJsonRoot, rewrite.name));
}
for (const duplicate of duplicateFiles) {
  copyFileSync(duplicate.path, join(backupImageRoot, duplicate.name));
}

for (const rewrite of rewrites) {
  writeFileSync(rewrite.path, rewrite.text, "utf8");
  JSON.parse(readFileSync(rewrite.path, "utf8"));
}

let removedBytes = 0;
for (const duplicate of duplicateFiles) {
  if (sha256(duplicate.path) !== duplicate.hash) {
    throw new Error(`Refusing to delete a changed asset: ${duplicate.name}`);
  }
  rmSync(duplicate.path);
  removedBytes += duplicate.bytes;
}

console.log(`Rewrote ${rewrites.length} Lottie JSON files.`);
console.log(
  `Removed ${duplicateFiles.length} exact duplicate PNG files (${(removedBytes / 1024 / 1024).toFixed(2)} MiB).`,
);
console.log(`Backup: ${backupRoot}`);
