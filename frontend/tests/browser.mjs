import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
import { chromium } from 'playwright';

const base = new URL(process.env.REWARDHUB_TEST_URL || 'http://127.0.0.1:0');
assert.equal(base.hostname, '127.0.0.1', 'Browser checks require an isolated loopback preview.');
assert.ok(Number(base.port) > 0, 'Set REWARDHUB_TEST_URL using browser-check.sh.');
const origin = base.origin;
const browser = await chromium.launch();
const errors = [];
const email = `browser-${Date.now()}@rewardhub.test`;
await mkdir('test-results/browser', { recursive: true });

function observe(page) {
  page.setDefaultTimeout(20_000);
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (entry) => {
    if (entry.type() === 'error' && /content security policy|mime type|module script/i.test(entry.text())) errors.push(entry.text());
  });
  page.on('response', (response) => {
    if (response.status() >= 500) errors.push(`${response.status()} ${new URL(response.url()).pathname}`);
  });
}

async function noOverflow(page, label) {
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), label);
}

try {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' });
  const page = await context.newPage();
  observe(page);
  await page.goto(origin + '/magazin');
  await page.getByRole('link', { name: 'Template CV', exact: true }).waitFor();
  for (const width of [320, 390, 768, 1023, 1024, 1440]) {
    await page.setViewportSize({ width, height: 844 });
    assert.equal(await page.locator('.mainnav').isVisible(), width >= 1024, `main navigation at ${width}`);
    assert.equal(await page.locator('.tabbar').isVisible(), width < 1024, `mobile navigation at ${width}`);
    await noOverflow(page, `catalog at ${width}`);
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: 'Compară Tricou GT', exact: true }).click();
  await page.getByRole('button', { name: 'Compară Sticker GT', exact: true }).click();
  await page.locator('[data-compare-link]').click();
  await page.locator('.compare-table').waitFor();
  assert.equal(await page.locator('thead a').count(), 2);
  assert.ok(page.url().includes('ids='));
  await page.getByRole('button', { name: 'Scoate Sticker GT din comparație' }).click();
  await page.waitForFunction(() => document.querySelectorAll('thead a').length === 1);

  await page.goto(origin + '/magazin');
  const search = page.getByRole('searchbox', { name: 'Caută în magazin' });
  await search.fill('Template CV');
  await search.press('Enter');
  await page.waitForFunction(() => document.querySelectorAll('.pcard').length === 1);
  await page.getByRole('link', { name: 'Template CV', exact: true }).click();
  await page.getByRole('heading', { name: 'Template CV', level: 1 }).waitFor();

  const initialProduct = await context.request.get(origin + '/produs/rew-templatecv');
  assert.equal(initialProduct.status(), 200);
  const initialHtml = await initialProduct.text();
  assert.ok(initialHtml.includes('<title>Template CV · RewardHub</title>'));
  assert.ok(initialHtml.includes('href="https://shop.micutu.com/produs/rew-templatecv"'));
  assert.ok(initialHtml.includes('"@type":"Product"'));
  assert.ok((await (await context.request.get(origin + '/sitemap.xml')).text()).includes('/produs/rew-templatecv'));
  assert.equal((await context.request.get(origin + '/js/missing.js')).status(), 404);
  assert.match((await context.request.get(origin + '/admin')).headers()['x-robots-tag'], /noindex/);

  await page.goto(origin + '/inregistrare/?next=/produs/rew-templatecv');
  await page.getByLabel('Nume afișat').fill('Tester Browser');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Parolă', { exact: true }).fill('Parola-Release-123');
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: /Creează contul/ }).click();
  await page.waitForURL(origin + '/produs/rew-templatecv');
  await page.getByRole('button', { name: 'Mai târziu' }).click();
  await page.locator('[data-wish]').first().click();
  await page.waitForFunction(() => document.querySelector('[data-wish]')?.getAttribute('aria-pressed') === 'true');
  await page.locator('[data-buy=cart]').click();
  await page.getByRole('link', { name: /^Coș/ }).click();
  await page.getByRole('heading', { name: 'Coșul tău', level: 1 }).waitFor();
  await page.getByRole('link', { name: /Finalizează comanda/ }).click();
  await page.getByRole('button', { name: /Plasează comanda/ }).click();
  await page.waitForURL(/\/comenzi\/ORD-/);
  const voucher = await page.locator('.voucher code').first().textContent();
  assert.match(voucher.trim(), /^GT-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);

  await page.goto(origin + '/vouchere');
  await page.getByLabel('Cod voucher').fill(voucher.trim());
  const verified = page.waitForResponse((response) => response.url().endsWith('/vouchers/verify') && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Verifică voucherul', exact: true }).click();
  assert.equal((await verified).status(), 200);
  await page.getByRole('heading', { name: 'Voucher activ' }).waitFor();

  await page.goto(origin + '/istoric');
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descarcă tot istoricul CSV' }).click();
  const download = await downloadPromise;
  assert.equal(download.suggestedFilename(), 'rewardhub-istoric-puncte.csv');
  await download.saveAs('test-results/browser/ledger.csv');

  await page.goto(origin + '/admin');
  await page.waitForURL(origin + '/profil');
  await page.goto(origin + '/autentificare?next=' + encodeURIComponent('/\\external.test/'));
  await page.waitForURL(origin + '/');
  const missing = await page.goto(origin + '/ruta-inexistenta');
  assert.equal(missing.status(), 404);
  await page.getByRole('heading', { name: 'Pagina nu a fost găsită' }).waitFor();

  for (const [route, heading] of [
    ['/ajutor', 'Întrebări frecvente'], ['/confidentialitate', 'Confidențialitate'], ['/termeni', 'Reguli de utilizare'],
    ['/wordle', /Tech Wordle/], ['/clasament', 'Clasament'], ['/activitati', 'Activități'], ['/comunitate', 'Comunitate'],
  ]) {
    await page.goto(origin + route);
    await page.getByRole('heading', { name: heading, level: 1 }).waitFor();
    await noOverflow(page, route);
  }
  await page.goto(origin + '/activitati');
  const claim = page.locator('[data-claim]');
  await claim.waitFor();
  await claim.click();
  await page.waitForFunction(() => !document.querySelector('[data-claim]') || document.querySelector('[data-claim]').disabled);

  const adminContext = await browser.newContext({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' });
  const admin = await adminContext.newPage();
  observe(admin);
  await admin.goto(origin + '/autentificare');
  await admin.getByLabel('Email').fill('admin@rewardhub.test');
  await admin.getByLabel('Parolă', { exact: true }).fill('Parola-Release-123');
  await admin.getByRole('button', { name: 'Intră în cont', exact: true }).click();
  await admin.waitForURL(origin + '/');
  for (const section of ['orders', 'users', 'rewards', 'audit']) {
    await admin.goto(origin + '/admin/' + section);
    await admin.locator('.admin-main h1').waitFor();
    if (['orders', 'users'].includes(section)) {
      await admin.locator('.admin-item a').first().click();
      await admin.getByRole('link', { name: section === 'orders' ? 'Înapoi la comenzi' : 'Înapoi la membri' }).waitFor();
    }
    await noOverflow(admin, `admin ${section}`);
  }

  await page.goto(origin + '/magazin');
  await page.locator('.pcard').last().waitFor();
  await page.screenshot({ path: 'test-results/browser/mobile.png', fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({ path: 'test-results/browser/desktop.png', fullPage: true });
  const brokenImages = await page.locator('img').evaluateAll((images) => images.filter((img) => img.complete && img.naturalWidth === 0).map((img) => img.getAttribute('src')));
  assert.deepEqual(brokenImages, []);

  await page.evaluate(async () => { await navigator.serviceWorker.ready; });
  await page.reload();
  await page.getByRole('heading', { name: 'Magazin', level: 1 }).waitFor();
  await context.setOffline(true);
  await page.goto(origin + '/magazin');
  await page.getByRole('heading', { name: 'RewardHub este offline' }).waitFor();
  await context.setOffline(false);
  await page.getByRole('link', { name: 'Încearcă din nou' }).click();
  await page.locator('h1').waitFor();
  assert.deepEqual(errors, []);
  console.log('Browser checks passed for responsive navigation, registration, comparison, wishlist, checkout, vouchers, CSV, daily rewards, admin and offline recovery.');
} finally {
  await browser.close();
}
