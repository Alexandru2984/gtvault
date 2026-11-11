import assert from 'node:assert/strict';
import test from 'node:test';
import { runtimeQueries } from './maven-dependencies.mjs';

test('runtime audit accepts Maven colors and modules without dropping compile or runtime dependencies', () => {
  const plain = '\nThe following files have been resolved:\n'
    + '  example:core:jar:1.2.3:compile -- module example.core [auto]\n'
    + '  example:driver:jar:linux:4.5.6:runtime -- module driver\n'
    + '  example:testing:jar:7.8.9:test\n';
  const colored = plain.replace(':compile --', ':compile\u001b[36m --').replace(':runtime --', ':runtime\u001b[36m --') + '\u001b[0;1m\u001b[m';
  const queries = runtimeQueries(colored);
  assert.deepEqual(queries, runtimeQueries(plain));
  assert.deepEqual(queries.map((q) => [q.package.name, q.version]), [['example:core', '1.2.3'], ['example:driver', '4.5.6']]);
});

test('runtime audit fails closed when the dependency output is empty or invalid', () => {
  for (const source of ['', '\u001b[36mNo dependencies resolved\u001b[m', 'example:only-tests:jar:1.2.3:test']) {
    assert.throws(() => runtimeQueries(source), /Runtime dependency list is empty/);
  }
});
