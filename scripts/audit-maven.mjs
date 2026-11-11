import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { runtimeQueries } from './maven-dependencies.mjs';

const source = await readFile(process.argv[2] || 'target/runtime-dependencies.txt', 'utf8');
const queries = runtimeQueries(source);
const response = await fetch('https://api.osv.dev/v1/querybatch', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ queries }), signal: AbortSignal.timeout(30_000),
});
assert.ok(response.ok, `OSV returned ${response.status}.`);
const { results } = await response.json();
assert.equal(results.length, queries.length);
const exceptions = JSON.parse(await readFile(new URL('./dependency-exceptions.json', import.meta.url), 'utf8'));
const today = new Date().toISOString().slice(0, 10);
let failures = 0;
for (let i = 0; i < results.length; i += 1) {
  for (const vulnerability of results[i].vulns || []) {
    const name = queries[i].package.name;
    const exception = exceptions.find((entry) => entry.package === name && entry.id === vulnerability.id && entry.reviewBy >= today);
    if (exception) console.log(`Review ${name} ${vulnerability.id} by ${exception.reviewBy}. ${exception.reason}`);
    else { failures += 1; console.error(`Affected ${name}@${queries[i].version} ${vulnerability.id}`); }
  }
}
console.log(`OSV checked ${queries.length} runtime artifacts. ${failures} findings require review.`);
if (failures) process.exitCode = 1;
