import assert from 'node:assert/strict';
import test from 'node:test';
import { html, internalPath, safeHref, productImage } from '../js/dom.js';

globalThis.location = new URL('https://shop.micutu.com/');

test('HTML templates escape values and preserve nested safe fragments', () => {
  const name = '<img src=x onerror="alert(1)">';
  assert.equal(String(html`<p>${name}</p>`), '<p>&lt;img src=x onerror=&quot;alert(1)&quot;&gt;</p>');
  assert.equal(String(html`<a ${html`aria-current="page"`}>Profil</a>`), '<a aria-current="page">Profil</a>');
});

test('notification links and return paths reject browser URL parsing ambiguities', () => {
  for (const unsafe of ['//evil.test', '/\\evil.test', '/\tevil.test', 'javascript:alert(1)', 'https://evil.test', '#//evil.test', '/.//evil.test']) {
    assert.equal(internalPath(unsafe), null, unsafe);
    assert.equal(safeHref(unsafe), '#', unsafe);
  }
  assert.equal(safeHref('#/comenzi/ORD-123'), '/comenzi/ORD-123');
  assert.equal(internalPath('/magazin?q=tricou'), '/magazin?q=tricou');
});

test('legacy catalog entries receive local images and remote image URLs are rejected', () => {
  assert.equal(productImage({ id: 'rew-tricou', image: '' }), '/images/products/tricou.svg');
  assert.equal(productImage({ id: 'rew-tricou', image: 'images/tricou.svg' }), '/images/products/tricou.svg');
  assert.equal(productImage({ rewardId: 'rew-templatecv', image: 'https://tracker.test/a.png' }), '/images/products/cv.svg');
});
