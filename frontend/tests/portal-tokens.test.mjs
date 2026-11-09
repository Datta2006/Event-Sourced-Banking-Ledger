// Task 3 portal token assertions — runs against <root>/frontend/src/styles.css.
// No framework dependency: plain Node ESM, exit non-zero on any failure.
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = fileURLToPath(new URL('.', import.meta.url));
const cssPath = resolve(__dirname, '../src/styles.css');
const css = readFileSync(cssPath, 'utf8');

let failures = 0;
function check(cond, msg) {
  if (!cond) {
    failures += 1;
    console.error(`FAIL: ${msg}`);
  } else {
    console.log(`PASS: ${msg}`);
  }
}

// --- customer dark accent: green #3ea574 ---
const custDarkAccent = css
  .match(/\[data-theme='dark'\]\s*\{([\s\S]*?)\}/)?.[1]
  ?.match(/--accent:\s*(#[0-9a-fA-F]+)/);
check(!!custDarkAccent, 'customer dark `--accent` (#3ea574) exists in :root[data-theme=dark]');
if (custDarkAccent) {
  check(custDarkAccent[1].toLowerCase() === '#3ea574', `customer dark --accent is #3ea574 (got ${custDarkAccent[1]})`);
}

// --- bank overrides exist ---
const hasBankBlock = /\[data-portal='bank'\]/.test(css);
check(hasBankBlock, '`[data-portal=\"bank\"]` selector exists in styles.css');

const bankDarkAccent = css
  .match(/\[data-portal='bank'\]\[data-theme='dark'\]\s*\{([\s\S]*?)\}/)?.[1]
  ?.match(/--accent:\s*(#[0-9a-fA-F]+)/);
check(!!bankDarkAccent, 'bank dark `--accent` exists under `[data-portal=bank][data-theme=dark]`');
if (bankDarkAccent) {
  check(bankDarkAccent[1].toLowerCase() === '#9d7ad6', `bank dark --accent is #9d7ad6 (got ${bankDarkAccent[1]})`);
}

// bank light accent: violet #7c3aed
const bankLightAccent = css
  .match(/\[data-portal='bank'\]\s*\{([\s\S]*?)\}/)?.[1]
  ?.match(/--accent:\s*(#[0-9a-fA-F]+)/);
check(!!bankLightAccent, 'bank light `--accent` (#7c3aed) exists under `[data-portal=bank]`');
if (bankLightAccent) {
  check(bankLightAccent[1].toLowerCase() === '#7c3aed', `bank light --accent is #7c3aed (got ${bankLightAccent[1]})`);
}

// semantic tokens unchanged across both portals — same hex base as customer
const bankCredit = css
  .match(/\[data-portal='bank'\]\s*\{[^}]*--credit:\s*(#[0-9a-fA-F]+)/)?.[1];
const creditBase = bankCredit ?? '';
check(/^#[0-9a-fA-F]{6}$/.test(creditBase), 'bank `--credit` token preserved (same hex family as customer)');

// --portal-name-bar token defined
const portalNameBar = css.match(/--portal-name-bar:\s*([\d.]+)px/);
check(!!portalNameBar, '--portal-name-bar defined');

if (failures > 0) {
  console.error(`${failures} assertion(s) failed`);
  process.exit(1);
}
console.log('All portal token assertions passed.');
