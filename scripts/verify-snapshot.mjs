import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const base = resolve(root, 'design/snapshots/2026-10-24-two-page-baseline');
const manifest = JSON.parse(await readFile(resolve(base, 'manifest.json'), 'utf8'));
const archiveOnly = process.argv.includes('--archive-only');
let verified = 0;
for (const item of manifest.files) {
  const bytes = await readFile(resolve(base, item.file));
  const actual = createHash('sha256').update(bytes).digest('hex');
  if (actual !== item.sha256 || bytes.length !== item.bytes) throw new Error(`Frozen file changed: ${item.file}`);
  if (item.pixels && (bytes.readUInt32BE(16) !== item.pixels[0] || bytes.readUInt32BE(20) !== item.pixels[1])) throw new Error(`PNG size changed: ${item.file}`);
  if (!archiveOnly && item.file.startsWith('source/')) {
    const live = await readFile(resolve(root, item.file.slice('source/'.length)));
    if (!bytes.equals(live)) throw new Error(`Live source differs from freeze: ${item.file}`);
  }
  verified++;
}
console.log(`Verified ${verified} frozen files${archiveOnly ? '' : ' and matching live sources'}; browser reference only.`);
