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

// Toate contextele folosesc același IP. Păstrăm rata traficului automat sub limita nginx
// de 20 cereri API/s, inclusiv pe runner-ele care execută mult mai repede decât un utilizator.
let nextApiRequest = 0;
async function paceApi(context) {
  await context.route('**/RewardHub/api/**', async (route) => {
    const now = performance.now();
    const delay = Math.max(0, nextApiRequest - now);
    nextApiRequest = Math.max(now, nextApiRequest) + 75;
    if (delay) await new Promise((resolve) => setTimeout(resolve, delay));
    await route.continue();
  });
}

async function openSettings(page) {
  await page.getByRole('button', { name: 'Meniul contului', exact: true }).click();
  await page.locator('#user-menu a[href="/profil?tab=setari"]').click();
  await page.getByRole('heading', { name: 'Sesiuni active', exact: true }).waitFor();
}

function observe(page) {
  page.setDefaultTimeout(20_000);
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (entry) => {
    if (entry.type() === 'error' && /content security policy|mime type|module script/i.test(entry.text())) errors.push(entry.text());
  });
  page.on('response', (response) => {
    if (response.status() >= 429) errors.push(`${response.status()} ${new URL(response.url()).pathname}`);
  });
}

async function noOverflow(page, label) {
  const fits = await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth);
  if (!fits) {
    await page.screenshot({ path: 'test-results/browser/overflow.png', fullPage: true });
    console.error(label, await page.locator('body *').evaluateAll((nodes) => nodes
      .filter((n) => n.getBoundingClientRect().right > innerWidth + 1 && n.getBoundingClientRect().width > 0)
      .slice(0, 12).map((n) => ({ tag: n.tagName, class: n.className, text: n.textContent.slice(0, 80) }))));
  }
  assert.ok(fits, label);
}

try {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' });
  await paceApi(context);
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
  const refreshedProduct = await context.request.get(origin + '/produs/rew-templatecv', {
    headers: { 'If-Modified-Since': initialProduct.headers()['last-modified'] },
  });
  assert.equal(refreshedProduct.status(), 200);
  assert.notEqual(refreshedProduct.headers()['content-security-policy'], initialProduct.headers()['content-security-policy']);
  assert.match(refreshedProduct.headers()['content-security-policy'], /'nonce-[a-f0-9]{32}'/);
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
  const goalSaved = page.waitForResponse((r) => r.url().endsWith('/user/saving-goal') && r.request().method() === 'PUT');
  await page.locator('[data-goal-set]').click();
  assert.equal((await goalSaved).status(), 200);
  await page.locator('.site-footer a[href="/favorite"]').click();
  await page.locator('.saving-goal h3').filter({ hasText: 'Template CV' }).waitFor();
  await page.locator('[data-goal-status="READY"]').waitFor();
  for (const width of [320, 390, 768, 1440]) {
    await page.setViewportSize({ width, height: 844 });
    await noOverflow(page, `saving goal at ${width}`);
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'test-results/browser/goal-mobile.png', fullPage: true });
  await page.reload();
  await page.locator('.saving-goal h3').filter({ hasText: 'Template CV' }).waitFor();
  await page.locator('.saving-goal a[href="/produs/rew-templatecv"]').click();
  await page.getByRole('heading', { name: 'Template CV', level: 1 }).waitFor();
  await page.locator('[data-buy=cart]').click();
  await page.getByRole('link', { name: /^Coș/ }).click();
  await page.getByRole('heading', { name: 'Coșul tău', level: 1 }).waitFor();
  await page.getByRole('link', { name: /Finalizează comanda/ }).click();
  await page.getByRole('button', { name: /Plasează comanda/ }).click();
  await page.waitForURL(/\/comenzi\/ORD-/);
  const voucher = await page.locator('.voucher code').first().textContent();
  assert.match(voucher.trim(), /^GT-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$/);

  await page.locator('.site-footer a[href="/favorite"]').click();
  await page.locator('.saving-goal h3').filter({ hasText: 'Template CV' }).waitFor();
  await page.locator('[data-goal-clear]').click();
  await page.getByRole('dialog').getByRole('button', { name: 'Elimină obiectivul', exact: true }).click();
  await page.getByRole('link', { name: 'Alege un premiu țintă', exact: true }).waitFor();

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

  let alternateSession;
  for (const [route, heading] of [
    ['/ajutor', 'Întrebări frecvente'], ['/confidentialitate', 'Confidențialitate'], ['/termeni', 'Reguli de utilizare'],
    ['/wordle', /Tech Wordle/], ['/clasament', 'Clasament'], ['/activitati', 'Activități'], ['/comunitate', 'Comunitate'],
  ]) {
    assert.equal((await context.request.get(origin + route)).status(), 200);
    await page.locator(`a[href="${route}"]:visible`).first().click();
    await page.getByRole('heading', { name: heading, level: 1 }).waitFor();
    await noOverflow(page, route);
    if (route === '/wordle') {
      const history = page.locator('.wordle-history');
      await history.getByText('Nu ai încă runde încheiate.', { exact: false }).waitFor();
      let finished;
      for (const [index, word] of ['ZZZZZ', 'QQQQQ', 'XXXXX', 'JJJJJ', 'VVVVV', 'WWWWW'].entries()) {
        const response = page.waitForResponse((r) => r.url().endsWith('/games/wordle/guess') && r.request().method() === 'POST');
        await page.keyboard.type(word);
        await page.keyboard.press('Enter');
        const result = await response;
        assert.equal(result.status(), 200);
        finished = await result.json();
        await page.waitForFunction((i) => document.querySelectorAll('#wd-board .wd-row')[i]
          ?.querySelectorAll('[data-state="HIT"], [data-state="NEAR"], [data-state="MISS"]').length === 5, index);
      }
      assert.equal(finished.status, 'LOST');
      const statsDialog = page.getByRole('dialog');
      await statsDialog.getByRole('heading', { name: 'Statistici Tech Wordle', exact: true }).waitFor();
      await statsDialog.getByRole('button', { name: 'Închide', exact: true }).click();
      await history.locator('[data-wordle-replay]').waitFor();
      await page.getByLabel('Rezultat', { exact: true }).selectOption('WON');
      await history.getByText('Nu ai runde care să corespundă acestui filtru.', { exact: true }).waitFor();
      await page.getByLabel('Rezultat', { exact: true }).selectOption('ALL');
      await history.locator('[data-wordle-replay]').click();
      const replay = page.getByRole('dialog');
      await replay.getByRole('table', { name: 'Grila rundei arhivate' }).waitFor();
      assert.equal(await replay.getByRole('cell').count(), 30);
      assert.equal(await replay.locator('[data-key]').count(), 0);
      await context.grantPermissions(['clipboard-read', 'clipboard-write']);
      await page.evaluate(() => Object.defineProperty(navigator, 'share', { configurable: true, value: undefined }));
      await replay.locator('[data-share-replay]').click();
      const shared = await page.evaluate(() => navigator.clipboard.readText());
      assert.ok(shared.includes('X/6'));
      assert.ok(!shared.includes(finished.answer) && !shared.includes('ZZZZZ'));
      for (const width of [320, 390, 768, 1440]) {
        await page.setViewportSize({ width, height: 844 });
        await noOverflow(page, `Wordle replay at ${width}`);
        assert.ok(await replay.evaluate((el) => el.getBoundingClientRect().width <= innerWidth));
        assert.ok(await replay.locator('.modal-body').evaluate((el) => el.scrollWidth <= el.clientWidth));
      }
      await page.setViewportSize({ width: 390, height: 844 });
      await page.screenshot({ path: 'test-results/browser/wordle-history-mobile.png', fullPage: true });
      const originalSession = await page.evaluate(() => localStorage.getItem('gt.session'));
      const adminAuthResponse = await context.request.post(origin + '/RewardHub/api/auth/login', {
        data: { email: 'admin@rewardhub.test', password: 'Parola-Release-123' },
      });
      assert.equal(adminAuthResponse.status(), 200);
      const adminAuth = await adminAuthResponse.json();
      alternateSession = JSON.stringify({ token: adminAuth.token, expiresAt: Date.parse(adminAuth.expiresAt), user: adminAuth.user });
      const actor = await context.newPage();
      await actor.goto(origin + '/offline.html');
      await actor.evaluate((auth) => localStorage.setItem('gt.session', JSON.stringify({
        token: auth.token, expiresAt: Date.parse(auth.expiresAt), user: auth.user,
      })), adminAuth);
      await history.getByText('Nu ai încă runde încheiate.', { exact: false }).waitFor();
      assert.equal(await page.getByRole('dialog').count(), 0, 'account switch closes private replay dialogs');
      await actor.evaluate((session) => localStorage.setItem('gt.session', session), originalSession);
      await history.locator('[data-wordle-replay]').waitFor();
      await actor.close();
      await page.reload();
      await history.locator('[data-wordle-replay]').waitFor();
    }
    if (route === '/activitati') {
      const notebook = page.locator('.quiz-notebook');
      await notebook.getByText('Caietul este gol.', { exact: false }).waitFor();
      const originalSession = await page.evaluate(() => localStorage.getItem('gt.session'));
      const actor = await context.newPage();
      await actor.goto(origin + '/offline.html');
      // Serverul salvează răspunsul primului cont. Întârziem numai livrarea răspunsului,
      // pentru a verifica schimbarea contului în timp ce cererea este în curs.
      let releaseSubmit;
      let submitted;
      const submittedReady = new Promise((resolve) => { submitted = resolve; });
      const submitGate = new Promise((resolve) => { releaseSubmit = resolve; });
      await page.route('**/RewardHub/api/gamification/quiz/submit', async (route) => {
        const response = await route.fetch();
        assert.equal(response.status(), 200);
        submitted(await response.json());
        await submitGate;
        await route.fulfill({ response }).catch(() => {}); // schimbarea contului anulează pagina veche
      });
      await page.locator('#quiz [data-answer="0"]').click();
      const quizResult = await submittedReady;
      await actor.evaluate((session) => localStorage.setItem('gt.session', session), alternateSession);
      await notebook.getByText('Caietul este gol.', { exact: false }).waitFor();
      releaseSubmit();
      await page.unroute('**/RewardHub/api/gamification/quiz/submit');
      assert.equal(await notebook.locator('[data-quiz-review]').count(), 0);
      await actor.evaluate((session) => localStorage.setItem('gt.session', session), originalSession);
      await notebook.locator('[data-quiz-review]').waitFor();
      assert.equal(await page.locator('#quiz [data-answer="0"][aria-pressed="true"]').count(), 1);
      await page.getByLabel('Rezultatul la quiz', { exact: true }).selectOption(quizResult.isCorrect ? 'WRONG' : 'CORRECT');
      await notebook.getByText('Nu ai răspunsuri care să corespundă filtrelor alese.', { exact: true }).waitFor();
      await page.getByLabel('Rezultatul la quiz', { exact: true }).selectOption(quizResult.isCorrect ? 'CORRECT' : 'WRONG');
      await notebook.locator('[data-quiz-review]').waitFor();
      const category = await page.locator('#quiz .tag-brand').textContent();
      await page.getByLabel('Categoria quiz-ului', { exact: true }).selectOption(category);
      await notebook.locator('[data-quiz-review]').click();
      const review = page.getByRole('dialog');
      await review.getByText('Răspuns salvat', { exact: false }).waitFor();
      const correct = Number(await review.locator('.quiz-opt.is-correct').getAttribute('data-quiz-review-answer'));
      const options = await review.locator('.quiz-opt').count();
      let practiceWrites = 0;
      const watchPractice = (request) => { if (request.method() !== 'GET' && request.url().includes('/RewardHub/api/gamification/')) practiceWrites++; };
      page.on('request', watchPractice);
      await review.getByRole('button', { name: 'Testează-mă din nou', exact: true }).click();
      assert.equal(await review.locator('[data-quiz-review-feedback]').count(), 0);
      await review.locator(`[data-quiz-review-answer="${(correct + 1) % options}"]`).click();
      await review.getByText('Revezi explicația.', { exact: true }).waitFor();
      await review.getByRole('button', { name: 'Testează-mă din nou', exact: true }).click();
      await review.locator(`[data-quiz-review-answer="${correct}"]`).click();
      await review.getByText('Corect.', { exact: true }).waitFor();
      assert.equal(practiceWrites, 0, 'recap exercises never submit rewards or change stored answers');
      page.off('request', watchPractice);
      for (const width of [320, 390, 768, 1440]) {
        await page.setViewportSize({ width, height: 844 });
        await noOverflow(page, `quiz recap at ${width}`);
        assert.ok(await review.locator('.modal-body').evaluate((el) => el.scrollWidth <= el.clientWidth));
      }
      await page.setViewportSize({ width: 390, height: 844 });
      await page.screenshot({ path: 'test-results/browser/quiz-recap-mobile.png' });
      await actor.evaluate((session) => localStorage.setItem('gt.session', session), alternateSession);
      await notebook.getByText('Caietul este gol.', { exact: false }).waitFor();
      assert.equal(await page.getByRole('dialog').count(), 0, 'account switch closes private quiz details');
      await actor.evaluate((session) => localStorage.setItem('gt.session', session), originalSession);
      await notebook.locator('[data-quiz-review]').waitFor();
      await actor.close();
      await page.reload();
      await notebook.locator('[data-quiz-review]').waitFor();
      assert.equal(await page.locator('#quiz [data-answer="0"][aria-pressed="true"]').count(), 1);
      for (const width of [320, 390, 768, 1440]) {
        await page.setViewportSize({ width, height: 844 });
        await noOverflow(page, `quiz notebook at ${width}`);
      }
      await page.setViewportSize({ width: 390, height: 844 });
      await notebook.screenshot({ path: 'test-results/browser/quiz-notebook-mobile.png' });
    }
  }
  await page.locator('.site-footer a[href="/activitati"]').click();
  const claim = page.locator('[data-claim]');
  await claim.waitFor();
  await claim.click();
  await page.waitForFunction(() => !document.querySelector('[data-claim]') || document.querySelector('[data-claim]').disabled);

  const sessionToken = await page.evaluate(() => JSON.parse(localStorage.getItem('gt.session')).token);
  const secondLogin = await context.request.post(origin + '/RewardHub/api/auth/login', {
    data: { email, password: 'Parola-Release-123' },
  });
  assert.equal(secondLogin.status(), 200);
  const secondSession = (await secondLogin.json()).token;
  await openSettings(page);
  assert.equal(await page.locator('[data-revoke-session]').count(), 1);
  for (const width of [320, 390, 768, 1440]) {
    await page.setViewportSize({ width, height: 844 });
    await noOverflow(page, `session settings at ${width}`);
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'test-results/browser/sessions-mobile.png', fullPage: true });
  await page.locator('[data-revoke-session]').click();
  await page.getByRole('dialog').getByRole('button', { name: 'Deconectează', exact: true }).click();
  await page.waitForFunction(() => document.querySelectorAll('[data-revoke-session]').length === 0);
  assert.equal((await context.request.get(origin + '/RewardHub/api/user/me', {
    headers: { Authorization: `Bearer ${secondSession}` },
  })).status(), 401);

  await page.getByRole('link', { name: 'Preferințe de notificare', exact: true }).click();
  const salesAlert = page.getByRole('checkbox', { name: /Reduceri la favorite/ });
  const progressAlert = page.getByRole('checkbox', { name: /Progres și realizări/ });
  await salesAlert.waitFor();
  await salesAlert.uncheck();
  await progressAlert.uncheck();
  const readAll = page.locator('[data-all-read]');
  if (await readAll.isVisible()) {
    const read = page.waitForResponse((r) => r.url().endsWith('/notifications/read-all'));
    await readAll.click();
    assert.equal((await read).status(), 200);
    assert.equal(await salesAlert.isChecked(), false, 'feed updates preserve unsaved preferences');
  }
  await page.getByRole('button', { name: 'Salvează preferințele', exact: true }).click();
  await page.getByRole('status').filter({ hasText: 'Preferințele au fost salvate.' }).waitFor();
  await page.reload();
  await salesAlert.waitFor();
  assert.equal(await salesAlert.isChecked(), false);
  assert.equal(await progressAlert.isChecked(), false);
  assert.equal(await page.getByRole('checkbox', { name: /Reveniri în stoc/ }).isChecked(), true);
  for (const width of [320, 390, 768, 1440]) {
    await page.setViewportSize({ width, height: 844 });
    await noOverflow(page, `notification preferences at ${width}`);
  }
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: 'test-results/browser/notifications-mobile.png', fullPage: true });

  const adminContext = await browser.newContext({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' });
  await paceApi(adminContext);
  const admin = await adminContext.newPage();
  observe(admin);
  await admin.goto(origin + '/autentificare');
  await admin.getByLabel('Email').fill('admin@rewardhub.test');
  await admin.getByLabel('Parolă', { exact: true }).fill('Parola-Release-123');
  await admin.getByRole('button', { name: 'Intră în cont', exact: true }).click();
  await admin.waitForURL(origin + '/');
  await admin.goto(origin + '/admin');
  await admin.locator('.admin-main h1').waitFor();
  for (const section of ['orders', 'users', 'rewards', 'audit']) {
    await admin.locator(`.admin-nav a[href="/admin/${section}"]`).click();
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
  await openSettings(page);
  await page.locator('[data-logout-all]').click();
  const rotated = page.waitForResponse((r) => r.url().endsWith('/auth/logout-all'));
  await page.getByRole('dialog').getByRole('button', { name: 'Deconectează', exact: true }).click();
  const rotatedResponse = await rotated;
  assert.equal(rotatedResponse.status(), 200);
  const rotatedToken = (await rotatedResponse.json()).token;
  assert.notEqual(rotatedToken, sessionToken);
  await page.waitForFunction((token) => JSON.parse(localStorage.getItem('gt.session')).token === token, rotatedToken);
  assert.equal((await context.request.get(origin + '/RewardHub/api/user/me', {
    headers: { Authorization: `Bearer ${sessionToken}` },
  })).status(), 401);
  await page.getByRole('button', { name: 'Meniul contului', exact: true }).click();
  const loggedOut = page.waitForResponse((r) => r.url().endsWith('/auth/logout'));
  await page.locator('[data-logout]').click();
  assert.equal((await loggedOut).status(), 200);
  await page.waitForFunction(() => localStorage.getItem('gt.session') === null);
  assert.equal((await context.request.get(origin + '/RewardHub/api/user/me', {
    headers: { Authorization: `Bearer ${rotatedToken}` },
  })).status(), 401);
  assert.deepEqual(errors, []);
  console.log('Browser checks passed for responsive navigation, registration, comparison, wishlist, saving goals, checkout, vouchers, CSV, daily rewards, Wordle history/replay/sharing/account isolation, sessions, notification preferences, admin and offline recovery.');
} catch (error) {
  if (errors.length) console.error('Browser HTTP or script errors', errors);
  throw error;
} finally {
  await browser.close();
}
