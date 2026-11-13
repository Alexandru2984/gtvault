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
    const preview = get('/cart/repeat-order/ORD-test');
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(methods, ['POST']);
    completeWrite();
    assert.equal((await cart).itemCount, 1);
    assert.equal((await preview).itemCount, 1);
    await mutation;
    assert.deepEqual(methods, ['POST', 'GET', 'GET']);
  } finally {
    completeWrite();
    globalThis.fetch = originalFetch;
  }
});

test('repeating an order waits for older cart writes and later reads wait for the repeat', async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  let finishFirst, finishRepeat;
  const firstGate = new Promise((resolve) => { finishFirst = resolve; });
  const repeatGate = new Promise((resolve) => { finishRepeat = resolve; });
  configureApi({ getToken: () => 'cart-test-token', unauthorized: () => {} });
  globalThis.fetch = async (url, options) => {
    calls.push(`${options.method} ${url}`);
    if (url.endsWith('/cart/items')) await firstGate;
    if (url.includes('/cart/repeat-order/')) await repeatGate;
    return Response.json({ itemCount: 2 });
  };
  const pending = [];
  try {
    pending.push(post('/cart/items', { rewardId: 'rew-templatecv', quantity: 1 }));
    pending.push(post('/cart/repeat-order/ORD-test', { version: 'reviewed' }));
    pending.push(get('/cart'));
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(calls, ['POST /RewardHub/api/cart/items']);
    finishFirst();
    await new Promise((resolve) => setImmediate(resolve));
    assert.deepEqual(calls, ['POST /RewardHub/api/cart/items', 'POST /RewardHub/api/cart/repeat-order/ORD-test']);
    finishRepeat();
    await Promise.all(pending);
    assert.deepEqual(calls, ['POST /RewardHub/api/cart/items', 'POST /RewardHub/api/cart/repeat-order/ORD-test', 'GET /RewardHub/api/cart']);
  } finally {
    finishFirst();
    finishRepeat();
    await Promise.allSettled(pending);
    globalThis.fetch = originalFetch;
  }
});
