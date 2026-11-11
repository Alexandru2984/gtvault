// Metadate per pagina: titlu, descriere, canonical, robots, Open Graph si JSON-LD.
export const SITE = {
  name: 'RewardHub · Generația Tech',
  origin: 'https://shop.micutu.com',
  description: 'Magazinul de recompense al comunității Generația Tech: câștigi AP din quiz-uri, Tech Wordle, misiuni și streak zilnic, apoi le transformi în tricouri, cursuri, mentorat și gadgeturi.',
};

function metaTag(attr, key) {
  let el = document.head.querySelector(`meta[${attr}="${key}"]`);
  if (!el) {
    el = document.createElement('meta');
    el.setAttribute(attr, key);
    document.head.append(el);
  }
  return el;
}

function linkTag(rel) {
  let el = document.head.querySelector(`link[rel="${rel}"]`);
  if (!el) {
    el = document.createElement('link');
    el.rel = rel;
    document.head.append(el);
  }
  return el;
}

/** Seteaza metadatele paginii curente. jsonld: obiect schema.org sau null. */
export function setMeta({ title, description, path = location.pathname, noindex = false, image = null, jsonld = null } = {}) {
  const full = title ? `${title} · RewardHub` : `${SITE.name} — premii pentru comunitatea tech`;
  document.title = full;
  const desc = (description || SITE.description).slice(0, 300);
  metaTag('name', 'description').content = desc;
  metaTag('name', 'robots').content = noindex ? 'noindex, nofollow' : 'index, follow, max-image-preview:large';
  const url = SITE.origin + path;
  linkTag('canonical').href = url;
  metaTag('property', 'og:title').content = full;
  metaTag('property', 'og:description').content = desc;
  metaTag('property', 'og:url').content = url;
  metaTag('property', 'og:image').content = image ? SITE.origin + image : `${SITE.origin}/images/og-image.png`;
  metaTag('name', 'twitter:title').content = full;
  metaTag('name', 'twitter:description').content = desc;

  document.getElementById('page-jsonld')?.remove();
  if (jsonld) {
    const s = document.createElement('script');
    s.type = 'application/ld+json';
    s.id = 'page-jsonld';
    // JSON-LD nu se executa; caracterele "<" sunt escapate ca sa nu poata inchide tag-ul
    s.textContent = JSON.stringify(jsonld).replace(/</g, '\\u003c');
    document.head.append(s);
  }
}
