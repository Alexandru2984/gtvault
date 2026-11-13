import test from 'node:test';
import assert from 'node:assert/strict';
import { promotionInstant, promotionLocalTime, flashPreview, promoPreview } from '../js/promotion-math.js';

test('programările folosesc ora României și resping orele inexistente sau ambigue', () => {
  assert.equal(promotionInstant('2026-11-13T10:00'), '2026-11-13T08:00:00.000Z');
  assert.equal(promotionInstant('2026-07-01T10:00'), '2026-07-01T07:00:00.000Z');
  assert.equal(promotionLocalTime('2026-11-13T08:00:59Z'), '2026-11-13T10:00');
  assert.throws(() => promotionInstant('2026-03-29T03:30'), /nu există/);
  assert.throws(() => promotionInstant('2026-10-25T03:30'), /două ori/);
  for (const bad of ['', '2026-02-30T10:00', '2026-11-13T24:00', '2026-11-13T10:00Z', '0000-01-01T00:00']) assert.throws(() => promotionInstant(bad));
});

test('previzualizarea respectă rotunjirea, plafonul procentual, minimul și sumele mari', () => {
  assert.equal(flashPreview(101, 15), 85);
  assert.equal(flashPreview(1, 80), 1);
  const p = { discountPercent: 15, discountPoints: 0, maxDiscount: 20, minOrderPoints: 100 };
  assert.deepEqual(promoPreview(99, p), { discount: 0, total: 99, minimum: true });
  assert.deepEqual(promoPreview(101, p), { discount: 15, total: 86, minimum: false });
  assert.deepEqual(promoPreview(1000, p), { discount: 20, total: 980, minimum: false });
  assert.deepEqual(promoPreview(100, { ...p, discountPercent: 0, discountPoints: 200 }), { discount: 100, total: 0, minimum: false });
  assert.equal(promoPreview(1_980_000_000, { ...p, maxDiscount: 0, discountPercent: 90 }).total, 198_000_000);
  assert.equal(promoPreview(1_980_000_001, p), null);
});
