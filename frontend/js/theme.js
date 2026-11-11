// Ruleaza sincron in <head> ca tema sa fie aplicata inainte de prima randare (fara "flash").
(function () {
  var pref = null;
  try { pref = JSON.parse(localStorage.getItem('gt.pref.theme') || 'null'); } catch (e) { pref = null; }
  var dark = pref === 'dark' || (pref !== 'light' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
})();
