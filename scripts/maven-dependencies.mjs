import assert from 'node:assert/strict';

export function runtimeQueries(source) {
  // Unele distribuții Maven scriu secvențe ANSI și în outputFile, inclusiv în modul quiet.
  const plain = source.replace(/\u001b\[[0-?]*[ -/]*[@-~]/g, '');
  const queries = [];
  for (const line of plain.split('\n')) {
    const coordinate = line.trim().split(' -- ')[0].split(':');
    if (coordinate.length < 5 || !['compile', 'runtime'].includes(coordinate.at(-1))) continue;
    const [group, artifact] = coordinate;
    queries.push({ package: { ecosystem: 'Maven', name: `${group}:${artifact}` }, version: coordinate.at(-2) });
  }
  assert.ok(queries.length > 0, 'Runtime dependency list is empty.');
  return queries;
}
