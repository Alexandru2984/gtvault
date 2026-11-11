import assert from 'node:assert/strict';
import test from 'node:test';
import { ledgerCsv } from '../js/csv.js';

test('ledger export preserves numeric debits, quotes text and neutralizes spreadsheet formulas', async () => {
  const csv = await ledgerCsv([
    { id: 'tx-1', type: 'ORDER_PAYMENT', description: '=HYPERLINK("https://example.test")', amount: -50, balance_after: 950 },
    { id: 'tx-2', type: 'GIFT_RECEIVED', description: '  +1,2\nMulțumesc!', amount: 10, balance_after: 960 },
  ]).text();
  assert.ok(csv.includes('"\'=HYPERLINK(""https://example.test"")",-50,950,'));
  assert.ok(csv.includes('"\'  +1,2\nMulțumesc!",10,960,'));
  assert.ok(csv.endsWith('\r\n'));
});
