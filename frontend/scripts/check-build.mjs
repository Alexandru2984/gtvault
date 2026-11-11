import assert from 'node:assert/strict';
import { readdir, stat } from 'node:fs/promises';
import path from 'node:path';

const output = path.resolve('target/frontend');
const root = await stat(output);
assert.equal(root.mode & 0o005, 0o005, 'The nginx worker needs read and traverse access to the frontend directory.');
for (const entry of await readdir(output, { recursive: true, withFileTypes: true })) {
  const file = path.join(entry.parentPath, entry.name);
  const required = entry.isDirectory() ? 0o005 : 0o004;
  const metadata = await stat(file);
  assert.equal(metadata.mode & required, required, `Public build permissions for ${path.relative(output, file)}`);
}
console.log('Public build permissions verified for nginx.');
