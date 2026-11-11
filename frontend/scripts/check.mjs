import assert from 'node:assert/strict';
import { readdir, readFile, access } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import path from 'node:path';

const root = path.resolve('frontend');

async function files(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map((entry) => entry.isDirectory()
    ? files(path.join(directory, entry.name)) : [path.join(directory, entry.name)]));
  return nested.flat();
}

const scripts = (await files(path.join(root, 'js'))).filter((file) => file.endsWith('.js'));
scripts.push(path.join(root, 'sw.js'));
for (const file of scripts) {
  const check = spawnSync(process.execPath, ['--check', file], { encoding: 'utf8' });
  assert.equal(check.status, 0, check.stderr);
  const source = await readFile(file, 'utf8');
  for (const match of source.matchAll(/\bfrom\s+['"]([^'"]+)['"]/g)) {
    await access(path.resolve(path.dirname(file), match[1]));
  }
}

const main = await readFile(path.join(root, 'js/main.js'), 'utf8');
for (const match of main.matchAll(/\bv\('([^']+)'\)/g)) {
  await access(path.join(root, 'js/views', match[1] + '.js'));
}
const manifest = JSON.parse(await readFile(path.join(root, 'manifest.webmanifest'), 'utf8'));
for (const icon of manifest.icons) await access(path.join(root, icon.src));
console.log(`Verified ${scripts.length} JavaScript files, module paths, lazy routes and PWA icons.`);
