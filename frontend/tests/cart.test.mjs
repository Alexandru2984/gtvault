import assert from 'node:assert/strict';
import test from 'node:test';
import { configureApi, get, post } from '../js/api.js';

test('cart reads wait for in-flight writes from the same session', async () => {
  const originalFetch = globalThis.fetch;
  const methods = [];
  let completeWrite;
  const write = new Promise((resolve) => { completeWrite = resolve; });
  configureApi({ getToken: () => 'cart-test-token', unauthorized: () => {} });
  globalThis.fetch = async (url, options) => {
    methods.push(options.method);
    if (options.method === 'POST') await write;
    return Response.json({ itemCount: 1 });
  };
  try {
    const mutation = post('/cart/items', { rewardId: 'rew-templatecv', quantity: 1 });
    const cart = get('/cart');
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(methods, ['POST']);
    completeWrite();
    assert.equal((await cart).itemCount, 1);
    await mutation;
    assert.deepEqual(methods, ['POST', 'GET']);
  } finally {
    completeWrite();
    globalThis.fetch = originalFetch;
  }
});
