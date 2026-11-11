import { chmod, cp, mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { esc, productImage } from '../js/dom.js';
import { SITE } from '../js/seo.js';
import { legalContent } from '../js/views/legal.js';

const catalogUrl = process.env.REWARDHUB_CATALOG_URL;
if (!catalogUrl) throw new Error('Set REWARDHUB_CATALOG_URL to the catalog API for this release.');
const response = await fetch(catalogUrl, { signal: AbortSignal.timeout(20_000) });
if (!response.ok) throw new Error(`Catalog unavailable (${response.status}).`);
const catalog = await response.json();
if (!Array.isArray(catalog.rewards)) throw new Error('Catalog response must contain rewards.');
const rewards = catalog.rewards;
const output = path.resolve('target/frontend');
await rm(output, { recursive: true, force: true });
await mkdir(output, { recursive: true });
for (const file of ['index.html', 'offline.html', 'sw.js', 'manifest.webmanifest', 'css', 'js', 'images']) {
  await cp(path.join('frontend', file), path.join(output, file), { recursive: true });
}
const shell = await readFile(path.join(output, 'index.html'), 'utf8');

function page(title, description, pathname, content, schema = null) {
  const url = SITE.origin + pathname;
  let document = shell.replace(/<title>.*?<\/title>/s, () => `<title>${esc(title)}</title>`)
    .replace(/<meta name="description"[^>]*>/, () => `<meta name="description" content="${esc(description)}">`)
    .replace(/<link rel="canonical"[^>]*>/, () => `<link rel="canonical" href="${esc(url)}">`)
    .replace(/<meta property="og:title"[^>]*>/, () => `<meta property="og:title" content="${esc(title)}">`)
    .replace(/<meta property="og:description"[^>]*>/, () => `<meta property="og:description" content="${esc(description)}">`)
    .replace(/<meta property="og:url"[^>]*>/, () => `<meta property="og:url" content="${esc(url)}">`)
    .replace(/<meta name="twitter:title"[^>]*>/, () => `<meta name="twitter:title" content="${esc(title)}">`)
    .replace(/<meta name="twitter:description"[^>]*>/, () => `<meta name="twitter:description" content="${esc(description)}">`)
    .replace(/(<main id="main"[^>]*>)[\s\S]*?<\/main>/, (_, opening) => `${opening}${content}</main>`);
  if (schema) {
    const json = JSON.stringify(schema).replaceAll('<', '\\u003c');
    document = document.replace('</head>', () => `<script type="application/ld+json" id="page-jsonld">${json}</script>\n</head>`);
  }
  return document;
}

const catalogLinks = rewards.map((reward) => `<article class="card card-pad stack">
  <a href="/produs/${encodeURIComponent(reward.id)}"><strong>${esc(reward.name)}</strong></a>
  <p>${esc(reward.description)}</p><span>${esc(reward.category)}</span></article>`).join('');
await writeFile(path.join(output, 'index.html'), page(SITE.name, SITE.description, '/',
  `<section class="page"><h1>Premii pentru comunitatea tech</h1><p>${esc(SITE.description)}</p><a class="btn btn-primary" href="/magazin">Explorează magazinul</a><div class="products">${catalogLinks}</div></section>`));
await mkdir(path.join(output, 'pages/produs'), { recursive: true });
await writeFile(path.join(output, 'pages/magazin.html'), page('Magazin de premii · RewardHub', SITE.description, '/magazin',
  `<section class="page"><h1>Magazin</h1><div class="products">${catalogLinks}</div></section>`));

const publicPages = [
  ['/activitati', 'Activități', 'Quiz, recompensă zilnică și misiuni pentru membrii comunității Generația Tech.'],
  ['/wordle', 'Tech Wordle', 'Descoperă cuvântul tehnic al zilei și câștigă puncte de activitate.'],
  ['/clasament', 'Clasament', 'Urmărește clasamentele comunității după activitate și streak.'],
  ['/bresle', 'Bresle', 'Descoperă breslele comunității și progresul lor în clasament.'],
  ['/comunitate', 'Comunitate', 'Propune recompense și votează ideile celorlalți membri.'],
  ['/ajutor', 'Întrebări frecvente', 'Cum câștigi puncte, comanzi premii și gestionezi contul RewardHub.'],
  ['/confidentialitate', 'Confidențialitate', 'Informații despre datele folosite în RewardHub și opțiunile contului.'],
  ['/termeni', 'Reguli de utilizare', 'Reguli pentru puncte, recompense, comenzi și participarea în comunitate.'],
];
for (const [pathname, title, description] of publicPages) {
  const content = ['/confidentialitate', '/termeni'].includes(pathname)
    ? String(legalContent(pathname === '/confidentialitate'))
    : `<section class="page"><h1>${esc(title)}</h1><p>${esc(description)}</p><noscript><p>Activează JavaScript pentru funcțiile interactive.</p></noscript></section>`;
  await writeFile(path.join(output, 'pages', pathname.slice(1) + '.html'), page(`${title} · RewardHub`, description, pathname,
    content));
}

const productPaths = [];
for (const reward of rewards) {
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(reward.id)) throw new Error('Catalog contains an invalid reward ID.');
  const pathname = `/produs/${encodeURIComponent(reward.id)}`;
  productPaths.push(pathname);
  const description = String(reward.fullDescription || reward.description || '').slice(0, 300);
  const image = productImage(reward);
  const schema = {
    '@context': 'https://schema.org', '@type': 'Product', name: reward.name, description,
    sku: reward.id, category: reward.category, image: SITE.origin + image, url: SITE.origin + pathname,
  };
  const content = `<article class="page stack"><a href="/magazin">Magazin</a><h1>${esc(reward.name)}</h1>
    <img src="${esc(image)}" alt="${esc(reward.name)}" width="240" height="240"><p>${esc(description)}</p></article>`;
  await writeFile(path.join(output, 'pages', `produs/${reward.id}.html`), page(`${reward.name} · RewardHub`, description, pathname, content, schema));
}
const sitemapPaths = ['/', '/magazin', ...publicPages.map(([pathname]) => pathname), ...productPaths];
const sitemap = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${sitemapPaths
  .map((pathname) => `<url><loc>${esc(SITE.origin + pathname)}</loc></url>`).join('')}</urlset>\n`;
await writeFile(path.join(output, 'sitemap.xml'), sitemap);
await writeFile(path.join(output, 'robots.txt'), `User-agent: *\nAllow: /\nDisallow: /RewardHub/\nSitemap: ${SITE.origin}/sitemap.xml\n`);
await chmod(output, 0o755);
for (const entry of await readdir(output, { recursive: true, withFileTypes: true })) {
  const file = path.join(entry.parentPath, entry.name);
  await chmod(file, entry.isDirectory() ? 0o755 : 0o644);
}
console.log(`Built target/frontend with ${rewards.length} product pages and a sitemap.`);
