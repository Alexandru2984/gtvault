/*
  GT Shop Frontend — Dragne Alexandru Mihai
  Pagina de shop cu integrare backend Java
*/

const CONFIG = {
  API_BASE_URL: resolveApiBaseUrl(),
  USE_MOCK_DATA: false,
  RETRY_ATTEMPTS: 3,
  RETRY_DELAY: 1000,
  REQUEST_TIMEOUT: 10000
};

function resolveApiBaseUrl() {
  const h = window.location.hostname;
  // Adaugă 'shop.micutu.com' aici
  if (h === 'shop.micutu.com' || h === 'rewardhub.micutu.com' || h === 'api.micutu.com') {
    // Folosește calea relativă pentru a trece prin Nginx proxy
    return '/RewardHub/api'; 
  }
  return 'http://localhost:8081/RewardHub/api';
}

const AppState = {
  user: null,
  products: [],
  cart: [],
  filteredProducts: [],
  currentCategory: '',
  isLoading: false,
  error: null
};

const DOM = {
  get navLinks() { return document.querySelectorAll('.nav-link'); },
  get sections() { return document.querySelectorAll('.section'); },
  get userAvatar() { return document.getElementById('user-avatar'); },
  get userName() { return document.getElementById('user-name'); },
  get userPoints() { return document.getElementById('user-points'); },
  get cartCount() { return document.getElementById('cart-count'); },
  get productsGrid() { return document.getElementById('products-grid'); },
  get coursesGrid() { return document.getElementById('courses-grid'); },
  get digitalGrid() { return document.getElementById('digital-grid'); },
  get categoryBtns() { return document.querySelectorAll('.category-btn'); },
  get searchInput() { return document.getElementById('search-input'); },
  get loading() { return document.getElementById('loading'); },
  get cartContent() { return document.getElementById('cart-content'); },
  get cartTotal() { return document.getElementById('cart-total'); },
  get checkoutBtn() { return document.getElementById('checkout-btn'); },
  get profileContent() { return document.getElementById('profile-content'); },
  get historyContent() { return document.getElementById('history-content'); },
  get productModal() { return document.getElementById('product-modal'); },
  get modalBody() { return document.getElementById('modal-body'); },
  get closeModal() { return document.querySelector('.close'); },
  get notification() { return document.getElementById('notification'); },
  get notificationMessage() { return document.getElementById('notification-message'); },
  get howItWorksModal() { return document.getElementById('how-it-works-modal'); },
  get setPointsModal() { return document.getElementById('set-points-modal'); },
  get setPointsBtn() { return document.getElementById('set-points-btn'); },
  get storageStatus() { return document.getElementById('storage-status'); },
  get clearStorageBtn() { return document.getElementById('clear-storage-btn'); }
};

/* ---------- Utils ---------- */

const Storage = {
  KEYS: {
    CART: 'gt_shop_cart',
    USER_PREFERENCES: 'gt_shop_preferences',
    LAST_CATEGORY: 'gt_shop_last_category',
    API_MODE: 'gt_shop_api_mode',
    PURCHASE_HISTORY: 'gt_shop_history',
    WISHLIST: 'gt_shop_wishlist'
  },
  save(key, data) {
    try {
      localStorage.setItem(key, JSON.stringify({ data, timestamp: Date.now() }));
    } catch (e) {
      console.warn('localStorage save failed:', e);
    }
  },
  load(key) {
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw).data : null;
    } catch (e) {
      console.warn('localStorage load failed:', e);
      return null;
    }
  },
  remove(key) {
    try { localStorage.removeItem(key); } catch (e) { console.warn('localStorage remove failed:', e); }
  },
  clearAll() {
    Object.values(this.KEYS).forEach(k => this.remove(k));
    showStorageStatus('Toate datele locale au fost șterse', 'error');
  }
};

/* Wishlist Helpers */
function getWishlist() {
  return Storage.load(Storage.KEYS.WISHLIST) || [];
}

function isWishlisted(productId) {
  const list = getWishlist();
  return list.includes(String(productId));
}

function updateWishlistBadges() {
  const count = getWishlist().length;
  const desktopBadge = document.getElementById('wishlist-count');
  if (desktopBadge) {
    desktopBadge.textContent = count;
  }
  const mobileBadge = document.getElementById('mobile-wishlist-badge');
  if (mobileBadge) {
    mobileBadge.textContent = count;
    mobileBadge.style.display = count > 0 ? 'flex' : 'none';
  }
}

function toggleWishlist(productId) {
  let list = getWishlist();
  const idStr = String(productId);
  const exists = list.includes(idStr);
  if (exists) {
    list = list.filter(id => id !== idStr);
    showToast('Produs eliminat din favorite', 'info');
  } else {
    list.push(idStr);
    showToast('Produs adăugat la favorite! ❤️', 'success');
  }
  Storage.save(Storage.KEYS.WISHLIST, list);
  updateWishlistBadges();
  
  document.querySelectorAll(`.product-card[data-product-id="${productId}"] .wishlist-btn`).forEach(btn => {
    btn.classList.toggle('active', !exists);
    btn.innerHTML = !exists ? '❤️' : '🤍';
  });

  const wishlistSec = document.getElementById('wishlist-section');
  if (wishlistSec && wishlistSec.classList.contains('active')) {
    renderWishlist();
  }
}

function renderWishlist() {
  const container = document.getElementById('wishlist-grid');
  if (!container) return;
  const list = getWishlist();
  if (!list.length) {
    container.innerHTML = `
      <div class="empty-state" style="grid-column: 1 / -1; text-align: center; padding: 3rem 1rem;">
        <div style="font-size: 3rem; margin-bottom: 1rem;">💔</div>
        <h3>Nu ai niciun produs la favorite</h3>
        <p style="color: #64748b; margin-bottom: 1.5rem;">Apasă pe inimioara de pe orice produs din magazin pentru a-l salva aici.</p>
        <button class="btn btn-primary" onclick="switchSection('shop')">Explorează Magazinul</button>
      </div>
    `;
    return;
  }

  const favProducts = AppState.products.filter(p => list.includes(String(p.id)));
  if (!favProducts.length) {
    container.innerHTML = `
      <div class="empty-state" style="grid-column: 1 / -1; text-align: center; padding: 3rem 1rem;">
        <div style="font-size: 3rem; margin-bottom: 1rem;">🔍</div>
        <h3>Produsele favorite nu mai sunt disponibile</h3>
        <button class="btn btn-primary" onclick="switchSection('shop')">Înapoi la Magazin</button>
      </div>
    `;
    return;
  }

  container.innerHTML = '';
  favProducts.forEach(p => {
    container.appendChild(createProductCard(p));
  });
}

function showStorageStatus(message, type = 'success') {
  if (!DOM.storageStatus) return;
  if (DOM.storageStatus.hideTimeout) clearTimeout(DOM.storageStatus.hideTimeout);
  DOM.storageStatus.className = 'storage-status';
  DOM.storageStatus.textContent = message;
  DOM.storageStatus.className = `storage-status ${type} show`;
  DOM.storageStatus.hideTimeout = setTimeout(() => {
    DOM.storageStatus.classList.remove('show');
    setTimeout(() => {
      DOM.storageStatus.className = 'storage-status';
      DOM.storageStatus.textContent = '';
    }, 300);
  }, 2000);
}

function showNotification(message, type = 'success') {
  const n = DOM.notification;
  const m = DOM.notificationMessage;
  if (n.hideTimeout) clearTimeout(n.hideTimeout);
  n.className = 'notification';
  m.textContent = message;
  n.className = `notification ${type} show`;
  n.hideTimeout = setTimeout(() => {
    n.classList.remove('show');
    setTimeout(() => {
      n.className = 'notification';
      m.textContent = '';
    }, 300);
  }, 4000);
}

function getProductImageUrl(product) {
  if (product.image && product.image.startsWith('http')) return product.image;
  const map = {
    'rew-tricou': 'images/tricou.svg',
    'rew-sticker': 'images/sticker.svg',
    'rew-agenda': 'images/agenda.svg',
    'rew-mentorat': 'images/mentorat.svg',
    'rew-mouse': 'images/mouse.svg',
    'rew-meet': 'images/meet.svg',
    'rew-lucky': 'images/lucky.svg',
    'rew-alltracks': 'images/tracks.svg',
    'rew-templatecv': 'images/cv.svg',
    'rew-udemy': 'images/udemy.svg',
    'rew-badge': 'images/badge.svg',
    'rew-ghidgpt': 'images/chatgpt.svg'
  };
  return map[product.id] || 'images/sticker.svg';
}

const formatPrice = (n) => `${n?.toLocaleString('ro-RO') || 0} puncte`;

function formatDate(s) {
  try {
    return new Date(s).toLocaleDateString('ro-RO', {
      year: 'numeric', month: 'long', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  } catch {
    return 'Data invalidă';
  }
}

function debounce(fn, wait) {
  let t;
  return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), wait); };
}

/* ---------- API ---------- */

async function apiCall(endpoint, options = {}) {
  const url = `${CONFIG.API_BASE_URL}${endpoint}`;
  const token = localStorage.getItem('auth_token') || Storage.load('auth_token');
  const baseHeaders = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    ...(token ? { 'Authorization': `Bearer ${token}` } : {})
  };
  const finalOptions = {
    ...options,
    headers: {
      ...baseHeaders,
      ...(options.headers || {})
    }
  };

  for (let attempt = 1; attempt <= CONFIG.RETRY_ATTEMPTS; attempt++) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), CONFIG.REQUEST_TIMEOUT);
      const resp = await fetch(url, { ...finalOptions, signal: controller.signal });
      clearTimeout(timeoutId);

      if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
      return await resp.json();

    } catch (err) {
      console.error(`API error [${attempt}/${CONFIG.RETRY_ATTEMPTS}]`, err);
      if (attempt === CONFIG.RETRY_ATTEMPTS) {
        const msg = getErrorMessage(err);
        showNotification(msg, 'error');
        throw new Error(msg);
      }
      await new Promise(r => setTimeout(r, CONFIG.RETRY_DELAY * attempt));
    }
  }
}

function getErrorMessage(error) {
  if (error.name === 'AbortError') return 'Cererea a expirat. Mai încearcă o dată.';
  const m = String(error.message || '');
  if (m.includes('Failed to fetch')) return 'Nu se poate conecta la server.';
  if (m.includes('HTTP 404')) return 'Resursa nu a fost găsită.';
  if (m.includes('HTTP 500')) return 'Eroare de server. Reîncearcă mai târziu.';
  if (m.includes('HTTP 403')) return 'Acces interzis pentru această resursă.';
  return 'A apărut o eroare neașteptată.';
}



async function loadUserProfile() {
  try {
    // Check if user is logged in via JWT
    const authToken = localStorage.getItem('auth_token');
    const userId = localStorage.getItem('user_id');
    const userName = localStorage.getItem('user_name');
    
    if (authToken && userId) {
      // User is logged in - use stored data or fetch their profile
      try {
        const r = await apiCall(`/user/profile?userId=${userId}`);
        if (r && r.id) {
          AppState.user = {
            id: r.id, name: r.name, avatar: r.avatar, activityPoints: r.activityPoints
          };
          Storage.save('userPoints', r.activityPoints);
          updateUserInterface();
          return;
        }
      } catch (e) {
        console.warn('Profilul logat nu s-a putut încărca:', e);
      }
      
      // Fallback to stored name with default points
      const savedPoints = Storage.load('userPoints');
      AppState.user = {
        id: userId,
        name: userName || 'Utilizator',
        avatar: 'images/user-avatar.svg',
        activityPoints: savedPoints ?? 1000
      };
      updateUserInterface();
      return;
    }
    
    // No JWT token - load default/guest user from backend
    try {
      const r = await apiCall('/user/profile');
      if (r && r.id) {
        AppState.user = {
          id: r.id, name: r.name, avatar: r.avatar, activityPoints: r.activityPoints
        };
        Storage.save('userPoints', r.activityPoints);
        updateUserInterface();
        
        // Show welcome for test user (user-123)
        if (r.id === 'user-123') {
          showWelcomeMessage(r.name, r.activityPoints);
        }
        return;
      }
    } catch (e) {
      console.warn('Profilul nu s-a putut încărca din backend:', e);
    }

    const savedPoints = Storage.load('userPoints');
    AppState.user = {
      id: 'user-123',
      name: 'Popescu Ion',
      avatar: 'images/user-avatar.svg',
      activityPoints: savedPoints ?? 2056
    };
    updateUserInterface();
    
    // Show welcome message for test user
    showWelcomeMessage();
  } catch {
    AppState.user = {
      id: 'user-123',
      name: 'Popescu Ion',
      avatar: 'images/user-avatar.svg',
      activityPoints: 2056
    };
    updateUserInterface();
    showWelcomeMessage();
  }
}

// Show welcome animation for test/demo users
function showWelcomeMessage(userName = 'Popescu Ion', userPoints = 2056) {
  // Only show once per session
  if (sessionStorage.getItem('welcomeShown')) return;
  sessionStorage.setItem('welcomeShown', 'true');
  
  // Check if user is logged in (not test user)
  if (localStorage.getItem('auth_token')) return;
  
  setTimeout(() => {
    if (typeof Swal !== 'undefined') {
      Swal.fire({
        title: '👋 Bine ai venit!',
        html: `
          <div style="text-align: left; line-height: 1.8;">
            <p>Explorezi <strong>RewardHub</strong> în modul de test cu contul:</p>
            <div style="background: linear-gradient(135deg, #f3e8ff, #ede9fe); padding: 1rem; border-radius: 10px; margin: 1rem 0;">
              <p style="margin: 0;"><strong>👤 ${userName}</strong></p>
              <p style="margin: 0; color: #7c3aed;">💎 ${userPoints} puncte disponibile</p>
            </div>
            <p style="font-size: 0.9rem; color: #6b7280;">
              Pentru a-ți crea propriul cont și a salva progresul, apasă pe avatar și alege <strong>Înregistrare</strong>.
            </p>
          </div>
        `,
        icon: 'info',
        confirmButtonText: 'Hai să explorăm! 🚀',
        confirmButtonColor: '#7c3aed',
        showClass: {
          popup: 'animate__animated animate__fadeInDown'
        },
        hideClass: {
          popup: 'animate__animated animate__fadeOutUp'
        },
        backdrop: `
          rgba(124, 58, 237, 0.2)
          left top
          no-repeat
        `
      });
    }
  }, 800);
}

async function loadProducts() {
  try {
    setLoadingState(true);
    const r = await apiCall('/rewards');
    AppState.products = r.rewards || [];
    AppState.filteredProducts = [...AppState.products];
    renderProductsByCategory();
  } catch (e) {
    console.error('loadProducts failed:', e);
    showErrorState('Nu s-au putut încărca produsele');
  } finally {
    setLoadingState(false);
  }
}

async function loadCart() {
  try {
    const saved = Storage.load(Storage.KEYS.CART);
    if (Array.isArray(saved)) {
      AppState.cart = saved;
      updateCartInterface();
    }
    const r = await apiCall('/cart');
    if (r && r.items) {
      const apiCart = r.items || [];
      const merged = [...AppState.cart];
      apiCart.forEach(x => {
        const i = merged.findIndex(y => y.rewardId === x.rewardId);
        if (i >= 0) merged[i].quantity = Math.max(merged[i].quantity, x.quantity);
        else merged.push(x);
      });
      AppState.cart = merged;
      updateCartInterface();
    }
  } catch (e) {
    console.error('loadCart API error:', e);
  }
}

async function loadHistory() {
  try {
    let purchases = [];
    
    try {
      const r = await apiCall('/user/history');
      purchases = Array.isArray(r?.purchases) ? r.purchases : (Array.isArray(r) ? r : []);
    } catch (e) {
      console.warn('loadHistory API failed, using localStorage:', e);
    }
    
    const localHistory = Storage.load(Storage.KEYS.PURCHASE_HISTORY) || [];
    const mergedHistory = [...localHistory, ...purchases];
    const uniqueHistory = mergedHistory.filter((item, index, arr) => 
      index === arr.findIndex(t => t.id === item.id)
    );
    
    renderHistory(uniqueHistory);
  } catch (e) {
    console.error('loadHistory failed:', e);
    const localHistory = Storage.load(Storage.KEYS.PURCHASE_HISTORY) || [];
    renderHistory(localHistory);
  }
}

/* ---------- Cart ops ---------- */

async function addToCart(product) {
  try {
    const cartItem = { rewardId: product.id, name: product.name, price: product.price, quantity: 1 };
    const r = await apiCall('/cart/add', { method: 'POST', body: JSON.stringify(cartItem) });

    if (r.success) {
      showNotification(`${product.name} a fost adăugat în coș`);
      if (r.cart?.items) {
        AppState.cart = r.cart.items;
      } else {
        const existing = AppState.cart.find(it => it.rewardId === product.id);
        existing ? (existing.quantity += 1) : AppState.cart.push(cartItem);
      }
      updateCartInterface();
      await loadUserProfile();
      await loadProducts();
      renderProductsByCategory();
    } else {
      showNotification(r.message || 'Eroare la adăugare', 'error');
    }
  } catch (e) {
    console.error('addToCart fallback:', e);
    const existing = AppState.cart.find(it => it.rewardId === product.id);
    if (existing) existing.quantity += 1;
    else AppState.cart.push({ rewardId: product.id, name: product.name, price: product.price, quantity: 1 });
    updateCartInterface();
    showNotification(`${product.name} adăugat în coș (offline)`, 'warning');
  }
}

async function buyNow(product) {
  try {
    if (AppState.user && AppState.user.activityPoints < product.price) {
      const missing = product.price - AppState.user.activityPoints;
      showNotification(`Îți lipsesc ${formatPrice(missing)}.`, 'error');
      return;
    }

    const item = { rewardId: product.id, name: product.name, price: product.price, quantity: 1 };
    const body = { items: [item] };

    const currentPoints = AppState.user?.activityPoints ?? 0;
    try {
      await apiCall('/user/profile', { method: 'POST', body: JSON.stringify({ userId: 'user-123', activityPoints: currentPoints }) });
    } catch (e) {
      console.warn('Sync points (buyNow) a eșuat, continui:', e);
    }

    const r = await apiCall('/checkout', { method: 'POST', body: JSON.stringify(body) });

    if (r.success) {
      if (r.newBalance !== undefined) {
        AppState.user.activityPoints = r.newBalance;
        Storage.save('userPoints', r.newBalance);
      }
      showNotification(r.message || 'Cumpărare reușită!', 'success');
      updateUserInterface();
      
      if (r.transactionId) {
        const purchaseRecord = {
          id: r.transactionId,
          rewardName: product.name,
          pointsSpent: product.price,
          purchaseDate: new Date().toISOString(),
          status: 'Completed'
        };
        const localHistory = Storage.load(Storage.KEYS.PURCHASE_HISTORY) || [];
        localHistory.unshift(purchaseRecord);
        Storage.save(Storage.KEYS.PURCHASE_HISTORY, localHistory);
        showNotification(`Comandă finalizată • ID: ${r.transactionId}`, 'success');
      }
      
      await loadProducts();
      renderProductsByCategory();
    } else {
      let msg = r.message || 'Eroare la cumpărare';
      if (r.missingPoints) msg = `Îți lipsesc ${r.missingPoints} puncte.`;
      showNotification(msg, 'error');
    }
  } catch (e) {
    console.error('buyNow failed:', e);
    showNotification('Eroare la cumpărare', 'error');
  }
}

async function checkout() {
  try {
    if (!AppState.cart.length) return showNotification('Coșul este gol', 'warning');

    const total = AppState.cart.reduce((s, it) => s + it.price * it.quantity, 0);
    if (AppState.user && AppState.user.activityPoints < total) {
      const missing = total - AppState.user.activityPoints;
      showNotification(`Îți lipsesc ${formatPrice(missing)}.`, 'error');
      return;
    }

    const body = { 
      items: AppState.cart,
      promoCode: appliedPromo ? appliedPromo.code : null
    };
    const r = await apiCall('/checkout', { method: 'POST', body: JSON.stringify(body) });

    if (r.success) {
      appliedPromo = null;
      const promoInput = document.getElementById('promo-code-input');
      if (promoInput) promoInput.value = '';
      const promoStatus = document.getElementById('promo-status');
      if (promoStatus) promoStatus.style.display = 'none';

      if (typeof confetti === 'function') {
        confetti({ particleCount: 150, spread: 80, origin: { y: 0.6 } });
      }
      SoundFx.playSuccess();
      showNotification('Comanda a fost finalizată cu succes!', 'success');
      NotifCenter.add('Comandă Finalizată! 🛍️', 'Comanda ta a fost confirmată. Vezi chitanța și voucherul în Istoric!', '🛍️');
      if (r.newBalance !== undefined) {
        AppState.user.activityPoints = r.newBalance;
        Storage.save('userPoints', r.newBalance);
      }
      
      if (r.transactionId) {
        const localHistory = Storage.load(Storage.KEYS.PURCHASE_HISTORY) || [];
        AppState.cart.forEach(item => {
          const purchaseRecord = {
            id: `${r.transactionId}-${item.rewardId}`,
            rewardName: item.name,
            pointsSpent: item.price * item.quantity,
            purchaseDate: new Date().toISOString(),
            status: 'Completed',
            voucherCode: r.voucherCode || `GT-${Math.random().toString(36).substring(2, 6).toUpperCase()}-${Math.random().toString(36).substring(2, 6).toUpperCase()}`
          };
          localHistory.unshift(purchaseRecord);
        });
        Storage.save(Storage.KEYS.PURCHASE_HISTORY, localHistory);
      }

      AppState.cart = [];
      Storage.remove(Storage.KEYS.CART);
      updateCartInterface();
      await loadUserProfile();
      
      Swal.fire({
        title: 'Comandă Reușită! 🎉',
        html: `<p>Comanda ta a fost procesată cu succes.</p><p>ID Tranzacție: <strong>#${r.transactionId || 'Finalizat'}</strong></p>`,
        icon: 'success',
        confirmButtonColor: '#7c3aed'
      });

      switchSection('history');
    } else {
      let msg = r.message || 'Eroare la finalizarea comenzii';
      if (r.missingPoints) msg = `Îți lipsesc ${r.missingPoints} puncte.`;
      showNotification(msg, 'error');
    }
  } catch (e) {
    console.error('checkout failed:', e);
    showNotification('Eroare la finalizarea comenzii', 'error');
  }
}

/* ---------- UI ---------- */

function updateUserInterface() {
  if (!AppState.user) return;
  DOM.userName.textContent = AppState.user.name;
  DOM.userPoints.textContent = formatPrice(AppState.user.activityPoints);
  const avatarUrl = AppState.user.avatar || 'images/user-avatar.svg';
  DOM.userAvatar.src = avatarUrl;
  DOM.userAvatar.alt = AppState.user.name;
  const sidebarStreak = document.getElementById('sidebar-streak-val');
  if (sidebarStreak) {
    sidebarStreak.textContent = `${AppState.user.streakDays || 0} Zile Active`;
  }
}

let appliedPromo = null;

function updateCartInterface() {
  const totalItems = AppState.cart.reduce((s, it) => s + it.quantity, 0);
  const subtotal = AppState.cart.reduce((s, it) => s + it.price * it.quantity, 0);
  let discount = 0;

  if (appliedPromo && appliedPromo.discountPoints) {
    discount = Math.min(appliedPromo.discountPoints, subtotal);
  }

  const finalTotal = Math.max(0, subtotal - discount);

  if (DOM.cartCount) DOM.cartCount.textContent = totalItems;
  const mobileBadge = document.getElementById('mobile-cart-badge');
  if (mobileBadge) mobileBadge.textContent = totalItems;

  const subtotalRow = document.getElementById('cart-subtotal-row');
  const discountRow = document.getElementById('cart-discount-row');
  const subtotalEl = document.getElementById('cart-subtotal');
  const discountEl = document.getElementById('cart-discount');

  if (discount > 0) {
    if (subtotalRow) { subtotalRow.style.display = 'block'; if (subtotalEl) subtotalEl.textContent = formatPrice(subtotal); }
    if (discountRow) { discountRow.style.display = 'block'; if (discountEl) discountEl.textContent = formatPrice(discount); }
  } else {
    if (subtotalRow) subtotalRow.style.display = 'none';
    if (discountRow) discountRow.style.display = 'none';
  }

  if (DOM.cartTotal) DOM.cartTotal.textContent = formatPrice(finalTotal);
  if (DOM.checkoutBtn) DOM.checkoutBtn.disabled = AppState.cart.length === 0;
  Storage.save(Storage.KEYS.CART, AppState.cart);
  if (AppState.cart.length) showStorageStatus('Coșul a fost salvat local', 'success');
  renderCart();
}

function setLoadingState(isLoading) {
  AppState.isLoading = isLoading;
  DOM.loading.style.display = isLoading ? 'block' : 'none';
}

function showErrorState(message, containerId = null) {
  const html = `
    <div class="error-state">
      <div class="error-icon">⚠️</div>
      <h3>Oops! Ceva n-a mers</h3>
      <p>${message}</p>
      <button class="btn btn-primary" onclick="location.reload()">Încearcă din nou</button>
    </div>`;
  if (containerId) {
    const c = document.getElementById(containerId);
    if (c) c.innerHTML = html;
  } else {
    DOM.productsGrid.innerHTML = html;
  }
}

/* ---------- Render ---------- */

function renderProductsByCategory() {
  const physical = AppState.filteredProducts.filter(p => p.category === 'Premii fizice');
  const exclusive = AppState.filteredProducts.filter(p => p.category === 'Premii exclusive');
  const digital = AppState.filteredProducts.filter(p => p.category === 'Premii digitale');

  const physicalSection = document.querySelector('.products-section');
  const exclusiveSection = document.querySelector('.courses-section');
  const digitalSection = document.querySelector('.digital-section');

  const sections = [
    { element: physicalSection, products: physical, category: 'Premii fizice', grid: DOM.productsGrid },
    { element: exclusiveSection, products: exclusive, category: 'Premii exclusive', grid: DOM.coursesGrid },
    { element: digitalSection, products: digital, category: 'Premii digitale', grid: DOM.digitalGrid }
  ];

  sections.sort((a, b) => {
    if (AppState.currentCategory && a.category === AppState.currentCategory) return -1;
    if (AppState.currentCategory && b.category === AppState.currentCategory) return 1;
    return 0;
  });

  if (AppState.currentCategory) {
    const target = sections.find(s => s.category === AppState.currentCategory);
    if (target?.element && target.products.length > 0) {
      const content = document.querySelector('.main .content');
      if (content) content.insertBefore(target.element, content.firstChild);
    }
  }

  sections.forEach(({ element, products, grid }) => {
    if (!element) return;
    if (products.length) {
      element.style.display = 'block';
      renderProductGrid(grid, products);
    } else {
      element.style.display = 'none';
    }
  });
}

function renderProductGrid(container, products) {
  if (!container) return;
  container.innerHTML = '';
  if (!products.length) {
    container.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">📦</div>
        <h3>Nu s-au găsit produse</h3>
        <p>Schimbă filtrarea sau verifică alte categorii</p>
      </div>`;
    return;
  }
  products.forEach(p => container.appendChild(createProductCard(p)));
}

function createProductCard(product) {
  const el = document.createElement('div');
  el.className = 'product-card';
  el.setAttribute('data-product-id', product.id);
  const isFav = isWishlisted(product.id);
  el.innerHTML = `
    <button class="wishlist-btn ${isFav ? 'active' : ''}" onclick="event.stopPropagation(); toggleWishlist('${product.id}')" title="Adaugă la favorite">
      ${isFav ? '❤️' : '🤍'}
    </button>
    <div class="product-image">
      <img src="${getProductImageUrl(product)}" alt="${product.name}"
           onerror="this.style.display='none'; this.parentElement.innerHTML='📦';" loading="lazy" />
    </div>
    <div class="product-info">
      <span class="product-category">${product.category || 'General'}</span>
      <h3 class="product-name">${product.name}</h3>
      <p class="product-description">${product.description}</p>
      <div class="product-footer">
        <span class="product-price">${formatPrice(product.price)}</span>
        <span class="product-stock ${!product.inStock ? 'out-of-stock' : ''}">
          ${product.inStock ? `${product.stockCount} în stoc` : 'Stoc epuizat'}
        </span>
      </div>
      <div class="product-actions">
        <button class="product-add-btn" data-product-id="${product.id}" ${!product.inStock ? 'disabled' : ''}>
          ${product.inStock ? '+ Adaugă în coș' : 'Stoc epuizat'}
        </button>
        <button class="product-buy-btn" data-product-id="${product.id}" ${!product.inStock ? 'disabled' : ''}>
          ${product.inStock ? 'Cumpără acum' : 'Indisponibil'}
        </button>
      </div>
    </div>`;
  el.addEventListener('click', (e) => {
    if (!e.target.classList.contains('product-add-btn') && !e.target.classList.contains('product-buy-btn')) {
      showProductModal(product);
    }
  });
  el.querySelector('.product-add-btn').addEventListener('click', (e) => { e.stopPropagation(); addToCart(product); });
  el.querySelector('.product-buy-btn').addEventListener('click', (e) => { e.stopPropagation(); buyNow(product); });
  return el;
}

function renderCart() {
  if (!AppState.cart.length) {
    DOM.cartContent.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">🛒</div>
        <h3>Coșul este gol</h3>
        <p>Adaugă produse din magazin</p>
        <button class="btn btn-primary" onclick="goToShop()">Explorează produsele</button>
      </div>`;
    return;
  }
  DOM.cartContent.innerHTML = AppState.cart.map(item => `
    <div class="cart-item" data-reward-id="${item.rewardId}">
      <div class="cart-item-info">
        <div class="cart-item-name">${item.name}</div>
        <div class="cart-item-price">${formatPrice(item.price)} / bucată</div>
      </div>
      <div class="cart-item-controls">
        <div class="quantity-controls">
          <button class="quantity-btn" onclick="updateCartItemQuantity('${item.rewardId}', ${item.quantity - 1})">-</button>
          <span class="quantity">${item.quantity}</span>
          <button class="quantity-btn" onclick="updateCartItemQuantity('${item.rewardId}', ${item.quantity + 1})">+</button>
        </div>
        <button class="btn btn-danger btn-sm" onclick="removeFromCart('${item.rewardId}')">Șterge</button>
      </div>
    </div>
  `).join('');
}

function renderProfile() {
  if (!AppState.user) {
    DOM.profileContent.innerHTML = '<div class="loading">Se încarcă profilul...</div>';
    return;
  }
  const avatarUrl = AppState.user.avatar || 'images/user-avatar.svg';
  DOM.profileContent.innerHTML = `
    <div class="profile-card">
      <div class="profile-header">
        <img class="profile-avatar" src="${avatarUrl}" alt="${AppState.user.name}">
        <div class="profile-info">
          <h3>${AppState.user.name}</h3>
          <p class="profile-id">ID: ${AppState.user.id}</p>
        </div>
      </div>
      <div class="profile-stats">
        <div class="stat-card">
          <div class="stat-value">${AppState.user.activityPoints.toLocaleString('ro-RO')}</div>
          <div class="stat-label">Puncte disponibile</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">🔥 ${AppState.user.streakDays || 0}</div>
          <div class="stat-label">Zile Streak</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">${AppState.cart.length}</div>
          <div class="stat-label">Produse în coș</div>
        </div>
      </div>
      <div style="margin-top: 1.25rem;">
        <button class="btn btn-secondary btn-block" onclick="switchSection('history')">
          📋 Vezi Istoric Comenzi, Chitanțe & Vouchere
        </button>
      </div>
    </div>`;
  loadUserBadges();
}

function renderHistory(purchases) {
  if (!purchases.length) {
    DOM.historyContent.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">📋</div>
        <h3>Nu ai încă cumpărături</h3>
        <p>Istoricul va apărea după prima achiziție</p>
        <button class="btn btn-primary" onclick="switchSection('shop')">Începe cumpărăturile</button>
      </div>`;
    return;
  }
  DOM.historyContent.innerHTML = purchases.map(p => `
    <div class="history-item">
      <div class="history-header">
        <h4>${p.rewardName || 'Produs necunoscut'}</h4>
        <span class="history-date">${formatDate(p.purchaseDate)}</span>
      </div>
      <div class="history-details">
        <div class="history-info">
          <span class="detail-label">Puncte cheltuite:</span>
          <span class="detail-value">${formatPrice(p.pointsSpent)}</span>
        </div>
        <div class="history-info">
          <span class="detail-label">Status:</span>
          <span class="detail-value status-${p.status?.toLowerCase() || 'unknown'}">${p.status || 'Finalizat'}</span>
        </div>
        ${p.id ? `
        <div class="history-info">
          <span class="detail-label">ID comandă:</span>
          <span class="detail-value">#${p.id}</span>
        </div>` : ''}
        ${p.voucherCode ? `
        <div class="history-info" style="grid-column: 1 / -1;">
          <span class="detail-label">Cod Voucher Digital:</span>
          <div class="voucher-box">
            <span class="voucher-code">${p.voucherCode}</span>
            <button class="btn-copy-voucher" onclick="copyVoucher('${p.voucherCode}')">Copiază</button>
          </div>
        </div>` : ''}
        <div class="history-info" style="grid-column: 1 / -1; margin-top: 0.5rem;">
          <button class="btn btn-sm btn-secondary" onclick="showDigitalReceipt('${p.id || ''}')">
            📄 Vezi Chitanță & Certificat
          </button>
        </div>
      </div>
    </div>
  `).join('');

  const totalPoints = purchases.reduce((s, p) => s + (p.pointsSpent || 0), 0);
  const summary = `
    <div class="history-summary">
      <h3>Rezumat</h3>
      <div class="summary-stats">
        <div class="summary-stat"><span class="stat-label">Total cumpărături:</span><span class="stat-value">${purchases.length}</span></div>
        <div class="summary-stat"><span class="stat-label">Total puncte cheltuite:</span><span class="stat-value">${formatPrice(totalPoints)}</span></div>
      </div>
    </div>
    <hr>`;
  DOM.historyContent.innerHTML = summary + DOM.historyContent.innerHTML;
}

function copyVoucher(code) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(code).then(() => {
      showNotification('Cod voucher copiat în clipboard! 📋', 'success');
    }).catch(() => {
      fallbackCopyText(code);
    });
  } else {
    fallbackCopyText(code);
  }
}

function fallbackCopyText(text) {
  const textArea = document.createElement('textarea');
  textArea.value = text;
  document.body.appendChild(textArea);
  textArea.select();
  try {
    document.execCommand('copy');
    showNotification('Cod voucher copiat! 📋', 'success');
  } catch (err) {
    showNotification('Nu s-a putut copia codul: ' + text, 'info');
  }
  document.body.removeChild(textArea);
}

function showProductModal(product) {
  const imageUrl = getProductImageUrl(product);
  const inStock = product.inStock && product.stockCount > 0;
  DOM.modalBody.innerHTML = `
    <div class="modal-product-image">
      <img src="${imageUrl}" alt="${product.name}" loading="lazy" />
    </div>
    <div class="modal-product-info">
      <span class="product-category">${product.category || 'General'}</span>
      <h2 class="modal-product-title">${product.name}</h2>
      <p class="modal-product-description">${product.fullDescription || product.description}</p>
      <div class="modal-product-details">
        <div class="detail-item">
          <span class="detail-label">Preț:</span>
          <span class="detail-value product-price">${formatPrice(product.price)}</span>
        </div>
        <div class="detail-item">
          <span class="detail-label">Stoc:</span>
          <span class="detail-value ${!inStock ? 'out-of-stock' : ''}">
            ${inStock ? `${product.stockCount} bucăți disponibile` : 'Stoc epuizat'}
          </span>
        </div>
      </div>
      <div class="modal-actions">
        <button class="btn btn-primary" onclick="addToCartFromModal('${product.id}')" ${!inStock ? 'disabled' : ''}>
          ${inStock ? '+ Adaugă în coș' : 'Stoc epuizat'}
        </button>
        <button class="btn btn-secondary" onclick="closeProductModal()">Închide</button>
      </div>

      <!-- Customer Reviews & Rating Section -->
      <div class="product-reviews-box">
        <div class="reviews-header">
          <div class="reviews-avg-rating">
            <span class="rating-stars">⭐⭐⭐⭐⭐</span>
            <span class="rating-number" id="modal-avg-rating">5.0</span>
            <span style="color: #64748b; font-size: 0.85rem;" id="modal-reviews-count">(Se încarcă...)</span>
          </div>
        </div>
        <div class="reviews-list" id="reviews-list-container">
          <!-- Rendered via JS -->
        </div>

        <div class="add-review-form">
          <div style="font-weight: 700; font-size: 0.85rem; margin-bottom: 0.4rem; color: #1e293b;">Lasă o recenzie:</div>
          <div class="star-picker">
            <span class="star-picker-item active" data-value="1" onclick="selectReviewStar(1)">★</span>
            <span class="star-picker-item active" data-value="2" onclick="selectReviewStar(2)">★</span>
            <span class="star-picker-item active" data-value="3" onclick="selectReviewStar(3)">★</span>
            <span class="star-picker-item active" data-value="4" onclick="selectReviewStar(4)">★</span>
            <span class="star-picker-item active" data-value="5" onclick="selectReviewStar(5)">★</span>
          </div>
          <textarea id="review-comment-input" class="review-textarea" placeholder="Ce părere ai despre această recompensă?"></textarea>
          <button id="btn-submit-review" class="btn btn-secondary" style="font-size: 0.8rem; padding: 0.4rem 0.8rem;" onclick="submitProductReview('${product.id}')">Trimite Recenzia</button>
        </div>
      </div>
    </div>`;
  DOM.productModal.style.display = 'block';
  document.body.style.overflow = 'hidden';
  loadProductReviews(product.id);
}

/* ---------- Nav / filters ---------- */

function switchSection(name) {
  const navLinks = document.querySelectorAll('.nav-link, .mobile-nav-item');
  const sections = document.querySelectorAll('.section');
  navLinks.forEach(l => l.classList.toggle('active', l.dataset.section === name));
  sections.forEach(s => s.classList.toggle('active', s.id === `${name}-section`));
  window.scrollTo({ top: 0, behavior: 'smooth' });
  switch (name) {
    case 'profile': renderProfile(); break;
    case 'history': loadHistory(); break;
    case 'wishlist': renderWishlist(); break;
    case 'gamification': loadGamificationStatus(); break;
    case 'leaderboard': loadLeaderboard(); break;
    case 'cart': break;
    case 'shop': if (!AppState.products.length) loadProducts(); break;
  }
}

let currentSortMode = 'default';
let filterOnlyInStock = false;

function handleSortChange(sortMode) {
  currentSortMode = sortMode;
  filterProducts();
}

function handleStockFilterToggle(onlyInStock) {
  filterOnlyInStock = onlyInStock;
  filterProducts();
}

function filterProducts() {
  const q = (DOM.searchInput.value || '').toLowerCase().trim();
  let list = AppState.products.filter(p => {
    const matchQ = !q || p.name.toLowerCase().includes(q) || p.description.toLowerCase().includes(q) || p.category.toLowerCase().includes(q);
    const matchC = !AppState.currentCategory || p.category === AppState.currentCategory;
    const matchStock = !filterOnlyInStock || (p.inStock && p.stockCount > 0);
    return matchQ && matchC && matchStock;
  });

  if (currentSortMode === 'price-asc') {
    list.sort((a, b) => a.price - b.price);
  } else if (currentSortMode === 'price-desc') {
    list.sort((a, b) => b.price - a.price);
  } else if (currentSortMode === 'name') {
    list.sort((a, b) => a.name.localeCompare(b.name));
  }

  AppState.filteredProducts = list;
  renderProductsByCategory();
}

function filterByCategory(category) {
  AppState.currentCategory = category;
  Storage.save(Storage.KEYS.LAST_CATEGORY, category);
  DOM.categoryBtns.forEach(btn => {
    const c = btn.dataset.category || '';
    btn.classList.toggle('active', c === category);
  });
  const active = document.querySelector('.section.active');
  if (!active || active.id !== 'shop-section') switchSection('shop');
  filterProducts();
}

/* ---------- Cart helpers ---------- */

function updateCartItemQuantity(rewardId, newQty) {
  if (newQty <= 0) return removeFromCart(rewardId);
  const item = AppState.cart.find(it => it.rewardId === rewardId);
  if (item) {
    item.quantity = newQty;
    updateCartInterface();
    showNotification(`Cantitate actualizată pentru ${item.name}`, 'info');
  }
}

function removeFromCart(rewardId) {
  const item = AppState.cart.find(it => it.rewardId === rewardId);
  if (item) {
    AppState.cart = AppState.cart.filter(it => it.rewardId !== rewardId);
    updateCartInterface();
    showNotification(`${item.name} a fost eliminat din coș`, 'info');
  }
}

function addToCartFromModal(productId) {
  const p = AppState.products.find(x => x.id === productId);
  if (p) {
    addToCart(p);
    closeProductModal();
  }
}

function closeProductModal() {
  DOM.productModal.style.display = 'none';
  document.body.style.overflow = '';
}

/* ---------- Misc modals ---------- */

function showHowItWorksModal() {
  DOM.howItWorksModal.style.display = 'block';
  document.body.style.overflow = 'hidden';
}
function closeHowItWorksModal() {
  DOM.howItWorksModal.style.display = 'none';
  document.body.style.overflow = '';
}

function showSetPointsModal() {
  const modal = document.getElementById('set-points-modal');
  const disp = document.getElementById('current-points-display');
  const input = document.getElementById('points-input');
  if (AppState.user) disp.textContent = formatPrice(AppState.user.activityPoints);
  input.value = '';
  modal.style.display = 'block';
  document.body.style.overflow = 'hidden';
  setTimeout(() => {
    input.focus();
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') setUserPoints(); });
  }, 100);
}

function closeSetPointsModal() {
  document.getElementById('set-points-modal').style.display = 'none';
  document.body.style.overflow = '';
}

async function setUserPoints() {
  const input = document.getElementById('points-input');
  const val = parseInt(input.value, 10);
  if (isNaN(val) || val < 0) return showNotification('Introdu un număr valid de puncte', 'error');
  if (val > 999999) return showNotification('Maxim 999.999 puncte', 'error');

  try {
    showNotification('Se setează punctele...', 'info');
    try {
      await apiCall('/user/profile', { method: 'POST', body: JSON.stringify({ userId: 'user-123', activityPoints: val }) });
    } catch (e) { /* continui local */ }
    Storage.save('userPoints', val);
    if (AppState.user) AppState.user.activityPoints = val;
    showNotification(`Punctele au fost setate la ${formatPrice(val)}`, 'success');
    updateUserInterface();
    closeSetPointsModal();
    const active = document.querySelector('.section.active');
    if (active?.id === 'profile-section') renderProfile();
  } catch (e) {
    console.error('setUserPoints failed:', e);
    showNotification('Eroare la setarea punctelor', 'error');
  }
}

/* ---------- Shop helpers ---------- */

function goToShop() {
  AppState.currentCategory = '';
  DOM.categoryBtns.forEach(btn => btn.classList.toggle('active', !btn.dataset.category || btn.dataset.category === ''));
  if (DOM.searchInput) DOM.searchInput.value = '';
  switchSection('shop');
  filterProducts();
}

function showCategoryProducts(category) {
  filterByCategory(category);
  switchSection('shop');
}

/* ---------- Init ---------- */

async function initializeApp() {
  try {
    setLoadingState(true);
    restoreUserPreferences();
    await Promise.all([loadUserProfile(), loadProducts(), loadCart()]);
    loadCommunityTicker();
    setupApiToggle();
    updateWishlistBadges();
    NotifCenter.render();
    setupPwa();
    ThemeEngine.init();
    SoundFx.init();
    CommunityPoll.init();
    GoalTracker.init();
  } catch (e) {
    console.error('App init failed:', e);
    showNotification('Eroare la inițializare', 'error');
  } finally {
    setLoadingState(false);
  }
}

function restoreUserPreferences() {
  const lastCategory = Storage.load(Storage.KEYS.LAST_CATEGORY);
  if (lastCategory) {
    AppState.currentCategory = lastCategory;
    DOM.categoryBtns.forEach(btn => btn.classList.toggle('active', btn.dataset.category === lastCategory));
  }
  const savedApi = Storage.load(Storage.KEYS.API_MODE);
  if (savedApi !== null) CONFIG.USE_MOCK_DATA = savedApi;
}

function setupApiToggle() {
  const t = document.getElementById('mockApiToggle');
  if (!t) return;

  if (window.location.hostname.includes('devtunnels.ms')) {
    CONFIG.USE_MOCK_DATA = true;
    t.checked = true;
    t.disabled = true;
    t.parentElement.title = 'Demo Mode activat automat pentru dev tunnels';
    return;
  }

  const saved = Storage.load(Storage.KEYS.API_MODE);
  if (saved !== null) CONFIG.USE_MOCK_DATA = saved;

  t.checked = CONFIG.USE_MOCK_DATA;
  t.addEventListener('change', async (e) => {
    CONFIG.USE_MOCK_DATA = e.target.checked;
    Storage.save(Storage.KEYS.API_MODE, CONFIG.USE_MOCK_DATA);
    AppState.cart = [];
    Storage.remove(Storage.KEYS.CART);
    updateCartInterface();
    showNotification(CONFIG.USE_MOCK_DATA ? 'API Mock (Demo) — coș resetat' : 'API Real — coș resetat', 'success');

    try {
      setLoadingState(true);
      await Promise.all([loadUserProfile(), loadProducts(), loadCart()]);
    } catch (err) {
      console.error('Reload after API mode switch failed:', err);
      showNotification('Eroare la reîncărcarea datelor', 'error');
    } finally {
      setLoadingState(false);
    }
  });
}

function setupEventListeners() {
  const navLinks = document.querySelectorAll('.nav-link, .mobile-nav-item');
  navLinks.forEach(link => {
    link.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      switchSection(link.dataset.section);
    });
  });

  const categoryBtns = document.querySelectorAll('.category-btn');
  categoryBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      categoryBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      filterByCategory(btn.dataset.category);
    });
  });

  document.querySelectorAll('.view-all').forEach(btn => {
    btn.addEventListener('click', () => {
      const c = btn.dataset.category;
      if (c) showCategoryProducts(c);
    });
  });

  const debouncedSearch = debounce(filterProducts, 300);
  DOM.searchInput.addEventListener('input', debouncedSearch);

  DOM.checkoutBtn.addEventListener('click', checkout);
  DOM.closeModal.addEventListener('click', closeProductModal);

  window.addEventListener('click', (e) => {
    if (e.target === DOM.productModal) closeProductModal();
    if (e.target === DOM.howItWorksModal) closeHowItWorksModal();
    if (e.target === document.getElementById('set-points-modal')) closeSetPointsModal();
    if (e.target === document.getElementById('receipt-modal')) closeReceiptModal();
    if (e.target === document.getElementById('voucher-activation-modal')) closeVoucherActivationModal();
    if (e.target === document.getElementById('compare-modal')) closeCompareModal();

    const notifWrapper = document.querySelector('.notif-wrapper');
    const notifDropdown = document.getElementById('notif-dropdown');
    if (notifDropdown && notifWrapper && !notifWrapper.contains(e.target)) {
      notifDropdown.classList.remove('show');
    }
  });

  if (DOM.clearStorageBtn) {
    DOM.clearStorageBtn.addEventListener('click', () => {
      if (confirm('Ștergi toate datele locale? Coșul va fi golit.')) {
        Storage.clearAll();
        AppState.cart = [];
        AppState.currentCategory = '';
        CONFIG.USE_MOCK_DATA = true;
        updateCartInterface();
        location.reload();
      }
    });
  }

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (DOM.productModal.style.display === 'block') closeProductModal();
      if (DOM.howItWorksModal.style.display === 'block') closeHowItWorksModal();
      if (document.getElementById('set-points-modal').style.display === 'block') closeSetPointsModal();
    }
  });

  window.addEventListener('online', () => {
    showNotification('Conexiunea a revenit', 'success');
    if (!AppState.products.length) loadProducts();
  });
  window.addEventListener('offline', () => showNotification('Ești offline', 'warning'));

  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && AppState.cart.length) {
      showStorageStatus('Coșul tău este salvat local', 'success');
    }
  });
}

/* ---------- Boot ---------- */

document.addEventListener('DOMContentLoaded', async () => {
  setupEventListeners();
  await initializeApp();
  addDynamicStyles();
});

/* ---------- Small dynamic CSS ---------- */

function addDynamicStyles() {
  const style = document.createElement('style');
  style.textContent = `
    .empty-state,.error-state{ text-align:center;padding:3rem;color:#666;grid-column:1/-1 }
    .empty-icon,.error-icon{ font-size:3rem;margin-bottom:1rem }
    .notification{ transition:.3s;opacity:0;transform:translateY(-20px) }
    .notification.show{ opacity:1;transform:translateY(0) }
    .notification.info{ background:#3b82f6 }
    .notification.warning{ background:#f59e0b }
    .storage-status{ position:fixed;bottom:20px;right:20px;background:rgba(0,0,0,.8);color:#fff;padding:.5rem 1rem;border-radius:20px;font-size:.8rem;z-index:1000;opacity:0;transform:translateX(20px);transition:.3s }
    .storage-status.show{ opacity:1;transform:translateX(0) }
    .storage-status.success{ background:rgba(34,197,94,.9) }
    .storage-status.error{ background:rgba(239,68,68,.9) }
    .loading{ text-align:center;padding:3rem;color:#666;font-size:1.1rem }
    .modal-product-info{ padding-top:1rem }
    .modal-product-title{ margin:1rem 0;color:#1f2937 }
    .modal-product-description{ color:#6b7280;line-height:1.6;margin-bottom:1.5rem }
    .modal-product-details{ background:#f8fafc;padding:1rem;border-radius:8px;margin-bottom:1.5rem }
    .detail-item{ display:flex;justify-content:space-between;margin-bottom:.5rem }
    .detail-item:last-child{ margin-bottom:0 }
    .detail-label{ font-weight:500;color:#374151 }
    .detail-value{ color:#6b7280 }
    .modal-actions{ display:flex;gap:1rem;justify-content:center }
    .profile-id{ color:#6b7280;font-size:.9rem }
    .history-total{ margin-top:1rem;padding-top:1rem;border-top:1px solid #e5e7eb;text-align:right }
    .item-details{ flex:1 }
    .item-price{ font-weight:600;color:#7c3aed }
    .clear-storage-btn{ position:fixed;bottom:70px;right:20px;background:#ef4444;color:#fff;border:none;padding:.5rem 1rem;border-radius:8px;font-size:.8rem;cursor:pointer;z-index:1000;opacity:.7;transition:opacity .3s }
    .clear-storage-btn:hover{ opacity:1 }
    .history-summary{ background:#f8fafc;padding:1.5rem;border-radius:12px;margin-bottom:1.5rem;border:1px solid #e2e8f0 }
    .history-summary h3{ color:#374151;margin:0 0 1rem 0;font-size:1.2rem }
    .summary-stats{ display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:1rem }
    .summary-stat{ display:flex;justify-content:space-between;align-items:center;padding:.75rem;background:#fff;border-radius:8px;border:1px solid #e5e7eb }
    .stat-label{ color:#6b7280;font-weight:500 }
    .stat-value{ color:#7c3aed;font-weight:600 }
    .history-details{ margin-top:1rem }
    .history-info{ display:flex;justify-content:space-between;margin-bottom:.5rem;padding:.5rem 0 }
    .history-info:not(:last-child){ border-bottom:1px solid #f3f4f6 }
    .status-completed{ color:#10b981;font-weight:600 }
    .status-pending{ color:#f59e0b;font-weight:600 }
    .status-cancelled{ color:#ef4444;font-weight:600 }
    .status-unknown{ color:#6b7280;font-style:italic }
    .how-it-works-content{ max-width:600px;margin:0 auto }
    .how-it-works-content h2{ text-align:center;color:#7c3aed;margin-bottom:2rem }
    .how-it-works-steps{ margin-bottom:2rem }
    .step{ display:flex;align-items:flex-start;margin-bottom:1.5rem;padding:1rem;background:#f8fafc;border-radius:12px;border-left:4px solid #7c3aed }
    .step-icon{ font-size:2rem;margin-right:1rem;flex-shrink:0 }
    .step-content h3{ color:#374151;margin:0 0 .5rem 0;font-size:1.1rem }
    .step-content p{ color:#6b7280;margin:0;line-height:1.6 }
    .tips-section{ background:#fffbeb;padding:1.5rem;border-radius:12px;border:1px solid #fbbf24;margin-bottom:2rem }
    .tips-section h3{ color:#92400e;margin:0 0 1rem 0 }
    .tips-section ul{ margin:0;padding-left:1.5rem;color:#78350f }
    .tips-section li{ margin-bottom:.5rem;line-height:1.5 }
    .tips-section strong{ color:#92400e }
    .view-all{ cursor:pointer;color:#7c3aed;font-weight:500;transition:color .2s ease;user-select:none }
    .view-all:hover{ color:#5b21b6;text-decoration:underline }
  `;
  document.head.appendChild(style);
}

/* ---------- Expose for inline handlers ---------- */

window.switchSection = switchSection;
window.updateCartItemQuantity = updateCartItemQuantity;
window.removeFromCart = removeFromCart;
window.addToCartFromModal = addToCartFromModal;
window.closeProductModal = closeProductModal;
window.showHowItWorksModal = showHowItWorksModal;
window.closeHowItWorksModal = closeHowItWorksModal;
window.showSetPointsModal = showSetPointsModal;
window.closeSetPointsModal = closeSetPointsModal;
window.setUserPoints = setUserPoints;
window.goToShop = goToShop;
window.showCategoryProducts = showCategoryProducts;

/* ---------- Authentication Functions ---------- */

const AuthState = {
  token: Storage.load('auth_token'),
  isLoggedIn: !!Storage.load('auth_token')
};

function toggleUserDropdown() {
  const dropdown = document.getElementById('user-dropdown');
  dropdown.classList.toggle('show');
  
  // Update dropdown name
  const dropdownName = document.getElementById('dropdown-user-name');
  if (dropdownName && AppState.user) {
    dropdownName.textContent = AppState.user.name;
  }
  
  // Close when clicking outside
  setTimeout(() => {
    document.addEventListener('click', closeDropdownOnClickOutside, { once: true });
  }, 10);
}

function closeDropdownOnClickOutside(e) {
  const dropdown = document.getElementById('user-dropdown');
  const avatar = document.getElementById('user-avatar');
  if (dropdown && !dropdown.contains(e.target) && e.target !== avatar) {
    dropdown.classList.remove('show');
  }
}

function showLoginModal() {
  closeUserDropdown();
  document.getElementById('login-modal').style.display = 'block';
  document.body.style.overflow = 'hidden';
}

function closeLoginModal() {
  document.getElementById('login-modal').style.display = 'none';
  document.body.style.overflow = '';
  document.getElementById('login-form').reset();
}

function showRegisterModal() {
  closeUserDropdown();
  document.getElementById('register-modal').style.display = 'block';
  document.body.style.overflow = 'hidden';
}

function closeRegisterModal() {
  document.getElementById('register-modal').style.display = 'none';
  document.body.style.overflow = '';
  document.getElementById('register-form').reset();
}

function switchToRegister() {
  closeLoginModal();
  showRegisterModal();
}

function switchToLogin() {
  closeRegisterModal();
  showLoginModal();
}

function showChangeAvatarModal() {
  closeUserDropdown();
  document.getElementById('avatar-modal').style.display = 'block';
  document.body.style.overflow = 'hidden';
}

function closeChangeAvatarModal() {
  document.getElementById('avatar-modal').style.display = 'none';
  document.body.style.overflow = '';
}

function closeUserDropdown() {
  const dropdown = document.getElementById('user-dropdown');
  if (dropdown) dropdown.classList.remove('show');
}

async function handleLogin(event) {
  event.preventDefault();
  const email = document.getElementById('login-email').value;
  const password = document.getElementById('login-password').value;
  
  try {
    const response = await apiCall('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password })
    });
    
    if (response.success) {
      AuthState.token = response.token;
      AuthState.isLoggedIn = true;
      Storage.save('auth_token', response.token);
      
      // Update user state
      AppState.user = {
        id: response.userId,
        name: response.name,
        avatar: 'images/user-avatar.svg',
        activityPoints: response.activityPoints
      };
      
      updateUserInterface();
      closeLoginModal();
      showNotification(`Bine ai venit, ${response.name}!`, 'success');
    } else {
      showNotification(response.message || 'Autentificare eșuată', 'error');
    }
  } catch (error) {
    showNotification('Eroare la autentificare', 'error');
  }
}

async function handleRegister(event) {
  event.preventDefault();
  const name = document.getElementById('register-name').value;
  const email = document.getElementById('register-email').value;
  const password = document.getElementById('register-password').value;
  
  try {
    const response = await apiCall('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ name, email, password })
    });
    
    if (response.success) {
      AuthState.token = response.token;
      AuthState.isLoggedIn = true;
      Storage.save('auth_token', response.token);
      
      // Update user state
      AppState.user = {
        id: response.userId,
        name: response.name,
        avatar: 'images/user-avatar.svg',
        activityPoints: response.activityPoints
      };
      
      updateUserInterface();
      closeRegisterModal();
      showNotification(`Cont creat cu succes! Bine ai venit, ${response.name}!`, 'success');
    } else {
      showNotification(response.message || 'Înregistrare eșuată', 'error');
    }
  } catch (error) {
    showNotification('Eroare la înregistrare', 'error');
  }
}

function logout() {
  closeUserDropdown();
  
  // Clear auth state
  AuthState.token = null;
  AuthState.isLoggedIn = false;
  Storage.remove('auth_token');
  Storage.remove('userPoints');
  Storage.remove('user_avatar');
  localStorage.removeItem('user_id');
  localStorage.removeItem('user_name');
  
  showNotification('Te-ai deconectat cu succes', 'success');
  
  // Redirect to login page
  setTimeout(() => {
    window.location.href = 'login.html';
  }, 500);
}

async function selectAvatar(avatarUrl) {
  // Mark selected
  document.querySelectorAll('.avatar-option').forEach(opt => opt.classList.remove('selected'));
  event?.currentTarget?.classList.add('selected');
  
  try {
    // Save to backend
    const response = await apiCall('/user/avatar', {
      method: 'POST',
      body: JSON.stringify({ avatar: avatarUrl })
    });
    
    if (response.success) {
      // Update local state
      if (AppState.user) {
        AppState.user.avatar = avatarUrl;
        DOM.userAvatar.src = avatarUrl;
      }
      closeChangeAvatarModal();
      showNotification('Avatar actualizat!', 'success');
    } else {
      showNotification(response.message || 'Eroare la salvare avatar', 'error');
    }
  } catch (error) {
    console.error('Avatar update error:', error);
    showNotification('Eroare la salvare avatar', 'error');
  }
}

async function saveCustomAvatar() {
  const url = document.getElementById('custom-avatar-url').value.trim();
  if (url) {
    await selectAvatar(url);
  } else {
    showNotification('Introdu un URL valid', 'warning');
  }
}

// Expose auth functions globally
window.toggleUserDropdown = toggleUserDropdown;
window.showLoginModal = showLoginModal;
window.closeLoginModal = closeLoginModal;
window.showRegisterModal = showRegisterModal;
window.closeRegisterModal = closeRegisterModal;
window.switchToRegister = switchToRegister;
window.switchToLogin = switchToLogin;
window.showChangeAvatarModal = showChangeAvatarModal;
window.closeChangeAvatarModal = closeChangeAvatarModal;
window.handleLogin = handleLogin;
window.handleRegister = handleRegister;
window.logout = logout;
window.selectAvatar = selectAvatar;
window.saveCustomAvatar = saveCustomAvatar;
window.switchSection = switchSection;
window.toggleWishlist = toggleWishlist;
window.renderWishlist = renderWishlist;
window.copyVoucher = copyVoucher;
window.claimDailyReward = claimDailyReward;
window.submitDailyQuiz = submitDailyQuiz;
window.selectQuizOption = selectQuizOption;
window.spinWheel = spinWheel;
window.loadGamificationStatus = loadGamificationStatus;
window.loadLeaderboard = loadLeaderboard;

/* ==========================================================
   GAMIFICATION & COMMUNITY MODULE
   Daily Streak, Tech Quiz, Lucky Wheel, Leaderboard
   ========================================================== */

let currentQuizData = null;
let selectedQuizAnswerIndex = null;
let wheelRotationDegrees = 0;
let isSpinningWheel = false;

async function loadGamificationStatus() {
  try {
    const res = await apiCall('/gamification/status');
    if (!res) return;

    // Update streak calendar
    renderStreakTrack(res.streakDays || 0);

    // Update claim button
    const claimBtn = document.getElementById('streak-claim-btn');
    if (claimBtn) {
      if (res.canClaimDaily) {
        claimBtn.disabled = false;
        claimBtn.className = 'btn btn-primary btn-block glow';
        claimBtn.textContent = `🎁 Revendică Bonusul Zilei (+${res.nextDailyBonus || 50} AP)`;
      } else {
        claimBtn.disabled = true;
        claimBtn.className = 'btn btn-secondary btn-block';
        claimBtn.textContent = '✅ Bonusul de azi a fost revendicat!';
      }
    }

    // Sidebar streak
    const sidebarStreak = document.getElementById('sidebar-streak-val');
    if (sidebarStreak) {
      sidebarStreak.textContent = `${res.streakDays || 0} Zile Active`;
    }

    // Load Quiz Question
    loadDailyQuiz();

  } catch (err) {
    console.error('loadGamificationStatus failed:', err);
  }
}

function renderStreakTrack(streakDays) {
  const container = document.getElementById('streak-days-track');
  if (!container) return;

  const bonuses = [50, 75, 100, 150, 200, 250, 500];
  const currentDayInCycle = streakDays % 7; // 0..6

  let html = '';
  for (let i = 0; i < 7; i++) {
    const dayNum = i + 1;
    const isCompleted = i < currentDayInCycle;
    const isActive = i === currentDayInCycle;
    const pts = bonuses[i];

    let stateClass = '';
    let icon = '🎁';
    if (isCompleted) {
      stateClass = 'completed';
      icon = '✓';
    } else if (isActive) {
      stateClass = 'active';
      icon = '🔥';
    }
    if (i === 6) icon = '👑';

    html += `
      <div class="streak-day-item ${stateClass}">
        <span class="streak-day-label">Ziua ${dayNum}</span>
        <div style="font-size: 1.2rem; margin: 4px 0;">${icon}</div>
        <span class="streak-day-pts">+${pts}</span>
      </div>
    `;
  }
  container.innerHTML = html;
}

async function claimDailyReward() {
  const btn = document.getElementById('streak-claim-btn');
  if (btn) btn.disabled = true;

  try {
    const res = await apiCall('/gamification/daily-claim', { method: 'POST' });
    if (res && res.success) {
      SoundFx.playWin();
      if (typeof confetti === 'function') {
        confetti({ particleCount: 120, spread: 70, origin: { y: 0.6 } });
      }
      Swal.fire({
        title: 'Streak Revendicat! 🔥',
        html: `<p>${res.message}</p><p>Ai primit: <strong>+${res.bonusAwarded} AP</strong></p>`,
        icon: 'success',
        confirmButtonColor: '#7c3aed'
      });

      if (AppState.user) {
        AppState.user.activityPoints = res.newBalance;
        AppState.user.streakDays = res.streakDays;
        updateUserInterface();
      }
      loadGamificationStatus();
    } else {
      showNotification(res?.message || 'Eroare la revendicarea bonusului', 'warning');
      if (btn) btn.disabled = false;
    }
  } catch (err) {
    console.error('claimDailyReward error:', err);
    showNotification('Eroare de rețea la revendicare', 'error');
    if (btn) btn.disabled = false;
  }
}

async function loadDailyQuiz() {
  const quizTitle = document.getElementById('quiz-question-title');
  const quizQuestion = document.getElementById('quiz-question-text');
  const quizOptions = document.getElementById('quiz-options');
  const quizResult = document.getElementById('quiz-result');
  const submitBtn = document.getElementById('quiz-submit-btn');

  if (!quizQuestion || !quizOptions) return;

  try {
    const res = await apiCall('/gamification/quiz');
    if (res && res.success) {
      currentQuizData = res;
      selectedQuizAnswerIndex = null;
      if (quizTitle) quizTitle.textContent = '🧠 Provocarea Tehnică de Azi';
      quizQuestion.textContent = res.question;
      if (quizResult) quizResult.style.display = 'none';

      quizOptions.innerHTML = res.options.map((opt, idx) => `
        <button type="button" class="quiz-opt-btn" onclick="selectQuizOption(${idx})">
          <span style="font-weight: 700; margin-right: 8px;">${String.fromCharCode(65 + idx)}.</span>
          ${opt}
        </button>
      `).join('');

      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Trimite Răspunsul (+100 AP)';
      }
    } else {
      // Already completed today
      if (quizQuestion) quizQuestion.textContent = 'Ai răspuns deja la întrebarea tehnică de astăzi.';
      quizOptions.innerHTML = '';
      if (quizResult) {
        quizResult.style.display = 'block';
        quizResult.className = 'quiz-result info';
        quizResult.innerHTML = `<strong>Status:</strong> ${res.message || 'Revino mâine la miezul nopții pentru o nouă provocare din programare!'}`;
      }
      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = '✅ Finalizat astăzi';
      }
    }
  } catch (err) {
    console.error('loadDailyQuiz error:', err);
    if (quizQuestion) quizQuestion.textContent = 'Nu s-a putut încărca testul tehnic.';
  }
}

function selectQuizOption(index) {
  selectedQuizAnswerIndex = index;
  const btns = document.querySelectorAll('.quiz-opt-btn');
  btns.forEach((btn, idx) => {
    btn.classList.toggle('selected', idx === index);
  });
  const submitBtn = document.getElementById('quiz-submit-btn');
  if (submitBtn) submitBtn.disabled = false;
}

async function submitDailyQuiz() {
  if (selectedQuizAnswerIndex === null) {
    showNotification('Selectează o opțiune mai întâi', 'warning');
    return;
  }

  const submitBtn = document.getElementById('quiz-submit-btn');
  if (submitBtn) submitBtn.disabled = true;

  try {
    const res = await apiCall('/gamification/quiz/submit', {
      method: 'POST',
      body: JSON.stringify({ answerIndex: selectedQuizAnswerIndex })
    });

    const quizResult = document.getElementById('quiz-result');
    if (res && res.success) {
      if (res.isCorrect) {
        SoundFx.playWin();
        if (typeof confetti === 'function') {
          confetti({ particleCount: 150, spread: 80, origin: { y: 0.5 } });
        }
        Swal.fire({
          title: 'Corect! 🌟',
          html: `<p>${res.message}</p><p style="color:#64748b; font-size: 0.9rem;">${res.explanation || ''}</p>`,
          icon: 'success',
          confirmButtonColor: '#7c3aed'
        });
        if (quizResult) {
          quizResult.style.display = 'block';
          quizResult.className = 'quiz-result success';
          quizResult.innerHTML = `<strong>Răspuns Corect! 🎉</strong> ${res.explanation || ''}`;
        }
      } else {
        SoundFx.playTone(300, 'sawtooth', 0.2);
        Swal.fire({
          title: 'Incorect 💡',
          html: `<p>${res.message}</p><p style="color:#64748b; font-size: 0.9rem;">${res.explanation || ''}</p>`,
          icon: 'info',
          confirmButtonColor: '#7c3aed'
        });
        if (quizResult) {
          quizResult.style.display = 'block';
          quizResult.className = 'quiz-result error';
          quizResult.innerHTML = `<strong>Răspuns Incorect!</strong> ${res.explanation || ''}`;
        }
      }

      // Mark buttons
      const btns = document.querySelectorAll('.quiz-opt-btn');
      btns.forEach(b => b.disabled = true);

      if (AppState.user && res.newBalance !== undefined) {
        AppState.user.activityPoints = res.newBalance;
        updateUserInterface();
      }

      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = '✅ Quiz finalizat';
      }
    } else {
      showNotification(res?.message || 'Eroare la trimiterea răspunsului', 'error');
      if (submitBtn) submitBtn.disabled = false;
    }
  } catch (err) {
    console.error('submitDailyQuiz error:', err);
    showNotification('Eroare de conexiune la server', 'error');
    if (submitBtn) submitBtn.disabled = false;
  }
}

async function spinWheel() {
  if (isSpinningWheel) return;
  const spinBtn = document.getElementById('spin-btn');
  const wheelDisc = document.getElementById('wheel-disc');

  if (AppState.user && AppState.user.activityPoints < 50) {
    Swal.fire({
      title: 'Puncte insuficiente',
      text: 'Ai nevoie de cel puțin 50 AP pentru a învârti roata norocului.',
      icon: 'warning',
      confirmButtonColor: '#7c3aed'
    });
    return;
  }

  isSpinningWheel = true;
  if (spinBtn) spinBtn.disabled = true;

  try {
    const res = await apiCall('/gamification/spin-wheel', { method: 'POST' });
    if (res && res.success) {
      const prizeIdx = res.prizeIndex; // 0 to 5
      const sectorAngle = prizeIdx * 60 + 30;
      const targetDelta = 360 - sectorAngle;
      wheelRotationDegrees += 1800 + targetDelta;

      if (wheelDisc) {
        wheelDisc.style.transition = 'transform 3.5s cubic-bezier(0.15, 0.9, 0.2, 1)';
        wheelDisc.style.transform = `rotate(${wheelRotationDegrees}deg)`;
      }

      setTimeout(() => {
        isSpinningWheel = false;
        if (spinBtn) spinBtn.disabled = false;

        if (res.wonPoints >= 100) {
          if (typeof confetti === 'function') {
            confetti({ particleCount: 150, spread: 80, origin: { y: 0.5 } });
          }
        }
        SoundFx.playWin();

        Swal.fire({
          title: 'Rezultat Roată! 🎡',
          html: `<h3 style="color:#7c3aed; font-size: 1.5rem; margin-bottom: 0.5rem;">${res.prizeName}</h3><p>${res.message}</p><p>Balanță nouă: <strong>${res.newBalance} AP</strong></p>`,
          icon: 'success',
          confirmButtonColor: '#7c3aed'
        });

        if (AppState.user) {
          AppState.user.activityPoints = res.newBalance;
          updateUserInterface();
        }
      }, 3700);

    } else {
      isSpinningWheel = false;
      if (spinBtn) spinBtn.disabled = false;
      showNotification(res?.message || 'Eroare la învârtirea roții', 'warning');
    }
  } catch (err) {
    isSpinningWheel = false;
    if (spinBtn) spinBtn.disabled = false;
    console.error('spinWheel error:', err);
    showNotification('Eroare de conexiune la server', 'error');
  }
}

async function loadLeaderboard() {
  const container = document.getElementById('leaderboard-rows');
  const podium = document.getElementById('leaderboard-podium');
  if (!container) return;

  container.innerHTML = '<tr><td colspan="4" style="text-align: center; padding: 2rem;">Se încarcă clasamentul...</td></tr>';

  try {
    const list = await apiCall('/gamification/leaderboard');
    if (!Array.isArray(list) || !list.length) {
      container.innerHTML = '<tr><td colspan="4" style="text-align: center; padding: 2rem;">Nu există date pentru clasament.</td></tr>';
      return;
    }

    // Top 3 Podium
    if (podium && list.length >= 3) {
      podium.innerHTML = `
        <div class="podium-card rank-2">
          <div class="podium-rank-badge">🥈 Locul 2</div>
          <img src="${list[1].avatar || 'images/user-avatar.svg'}" class="podium-avatar" alt="${list[1].name}">
          <div class="podium-name">${list[1].name}</div>
          <div class="podium-points">${formatPrice(list[1].points)}</div>
        </div>
        <div class="podium-card rank-1">
          <div class="podium-rank-badge">👑 Locul 1</div>
          <img src="${list[0].avatar || 'images/user-avatar.svg'}" class="podium-avatar" alt="${list[0].name}">
          <div class="podium-name">${list[0].name}</div>
          <div class="podium-points">${formatPrice(list[0].points)}</div>
        </div>
        <div class="podium-card rank-3">
          <div class="podium-rank-badge">🥉 Locul 3</div>
          <img src="${list[2].avatar || 'images/user-avatar.svg'}" class="podium-avatar" alt="${list[2].name}">
          <div class="podium-name">${list[2].name}</div>
          <div class="podium-points">${formatPrice(list[2].points)}</div>
        </div>
      `;
    }

    // Table rows
    container.innerHTML = list.map(item => {
      const isCurrent = AppState.user && String(AppState.user.id) === String(item.id);
      return `
        <tr style="${isCurrent ? 'background: #f5f3ff; font-weight: 700;' : ''}">
          <td style="font-weight: 800; color: #64748b;">#${item.rank}</td>
          <td style="display: flex; align-items: center; gap: 0.75rem;">
            <img src="${item.avatar || 'images/user-avatar.svg'}" style="width: 32px; height: 32px; border-radius: 50%; object-fit: cover; border: 1px solid #e2e8f0;">
            <span>${item.name} ${isCurrent ? '<span style="font-size:0.75rem; background:#7c3aed; color:white; padding: 2px 6px; border-radius: 4px;">Tu</span>' : ''}</span>
          </td>
          <td><span style="background: #f8fafc; padding: 3px 8px; border-radius: 6px; font-size: 0.85rem; border: 1px solid #e2e8f0;">🔥 ${item.streakDays || 0} zile</span></td>
          <td style="font-weight: 800; color: #7c3aed;">${formatPrice(item.points)}</td>
        </tr>
      `;
    }).join('');

  } catch (err) {
    console.error('loadLeaderboard error:', err);
    container.innerHTML = '<tr><td colspan="4" style="text-align: center; color: red; padding: 2rem;">Eroare la încărcarea clasamentului.</td></tr>';
  }
}

/* ==========================================================
   PROMO CODES & DISCOUNT SYSTEM
   ========================================================== */

async function applyPromoCode() {
  const input = document.getElementById('promo-code-input');
  const statusEl = document.getElementById('promo-status');
  if (!input || !statusEl) return;

  const code = input.value.trim().toUpperCase();
  if (!code) {
    statusEl.style.display = 'block';
    statusEl.className = 'promo-status error';
    statusEl.textContent = 'Introdu un cod promoțional valid.';
    return;
  }

  const subtotal = AppState.cart.reduce((s, it) => s + it.price * it.quantity, 0);
  if (subtotal <= 0) {
    statusEl.style.display = 'block';
    statusEl.className = 'promo-status error';
    statusEl.textContent = 'Adaugă mai întâi produse în coș pentru a aplica reducerea.';
    return;
  }

  try {
    const res = await apiCall('/promo/validate', {
      method: 'POST',
      body: JSON.stringify({ code: code, orderTotal: subtotal })
    });

    if (res && res.valid) {
      appliedPromo = res;
      statusEl.style.display = 'block';
      statusEl.className = 'promo-status success';
      statusEl.textContent = `🎉 ${res.message}`;
      updateCartInterface();
    } else {
      appliedPromo = null;
      statusEl.style.display = 'block';
      statusEl.className = 'promo-status error';
      statusEl.textContent = res?.message || 'Cod promoțional invalid sau condiții neîndeplinite!';
      updateCartInterface();
    }
  } catch (err) {
    appliedPromo = null;
    statusEl.style.display = 'block';
    statusEl.className = 'promo-status error';
    statusEl.textContent = err?.message || 'Eroare la validarea codului promo!';
    updateCartInterface();
  }
}

/* ==========================================================
   BADGES & ACHIEVEMENTS SYSTEM
   ========================================================== */

const ALL_KNOWN_BADGES = [
  { code: 'FIRST_ORDER', title: 'Primul Pas', icon: '🛍️', desc: 'Finalizează prima ta comandă din RewardHub' },
  { code: 'STREAK_3', title: 'Hot Streak', icon: '🔥', desc: 'Menține 3 zile consecutive de prezență activă' },
  { code: 'STREAK_7', title: 'Generația Legend', icon: '👑', desc: '7 zile consecutive de perseverență și învățare' },
  { code: 'TECH_BRAIN', title: 'Tech Brain', icon: '🧠', desc: 'Răspunde corect la provocarea tehnică zilnică' },
  { code: 'LUCKY_SPINNER', title: 'Lucky Spinner', icon: '🎡', desc: 'Învârte Roata Norocului pentru premii instant' },
  { code: 'BIG_SPENDER', title: 'Tech VIP', icon: '💎', desc: 'Investește peste 1000 AP într-o singură comandă' }
];

async function loadUserBadges() {
  const container = document.getElementById('badges-grid');
  const countLabel = document.getElementById('badges-count-label');
  if (!container) return;

  try {
    const unlockedBadges = await apiCall('/gamification/badges');
    const unlockedMap = new Map();
    if (Array.isArray(unlockedBadges)) {
      unlockedBadges.forEach(b => unlockedMap.set(b.badgeCode, b));
    }

    if (countLabel) {
      countLabel.textContent = `${unlockedMap.size} din ${ALL_KNOWN_BADGES.length} deblocate`;
    }

    container.innerHTML = ALL_KNOWN_BADGES.map(badge => {
      const isUnlocked = unlockedMap.has(badge.code);
      return `
        <div class="badge-card ${isUnlocked ? 'unlocked' : 'locked'}">
          <div class="badge-card-icon">${badge.icon}</div>
          <div class="badge-card-title">${badge.title}</div>
          <div class="badge-card-desc">${badge.desc}</div>
          <div class="badge-card-status">
            ${isUnlocked ? '✓ Deblocat' : '🔒 Blocat'}
          </div>
        </div>
      `;
    }).join('');

  } catch (err) {
    console.warn('loadUserBadges failed:', err);
  }
}

/* ==========================================================
   LIVE COMMUNITY ACTIVITY TICKER
   ========================================================== */

let tickerItems = [];
let tickerIndex = 0;
let tickerTimer = null;

async function loadCommunityTicker() {
  const contentEl = document.getElementById('ticker-content');
  if (!contentEl) return;

  try {
    const list = await apiCall('/community/activity');
    if (Array.isArray(list) && list.length > 0) {
      tickerItems = list;
      showNextTickerItem();
      if (!tickerTimer) {
        tickerTimer = setInterval(showNextTickerItem, 4500);
      }
    }
  } catch (err) {
    console.warn('loadCommunityTicker failed:', err);
  }
}

function showNextTickerItem() {
  const contentEl = document.getElementById('ticker-content');
  if (!contentEl || !tickerItems.length) return;

  const item = tickerItems[tickerIndex % tickerItems.length];
  tickerIndex++;

  contentEl.style.opacity = '0';
  setTimeout(() => {
    contentEl.innerHTML = `
      <span class="ticker-item">
        <strong>${item.icon} ${item.title}</strong> — <span style="opacity: 0.9;">${item.detail}</span>
      </span>
    `;
    contentEl.style.opacity = '1';
  }, 250);
}

/* ==========================================================
   PRODUCT REVIEWS & RATINGS SYSTEM
   ========================================================== */

let selectedStarRating = 5;

function selectReviewStar(stars) {
  selectedStarRating = stars;
  document.querySelectorAll('.star-picker-item').forEach(el => {
    const val = parseInt(el.dataset.value, 10);
    el.classList.toggle('active', val <= stars);
  });
}

async function loadProductReviews(productId) {
  const container = document.getElementById('reviews-list-container');
  const avgEl = document.getElementById('modal-avg-rating');
  const countEl = document.getElementById('modal-reviews-count');
  if (!container) return;

  container.innerHTML = '<div style="font-size: 0.85rem; color: #64748b; padding: 0.5rem 0;">Se încarcă recenziile...</div>';

  try {
    const data = await apiCall(`/rewards/${productId}/reviews`);
    if (data) {
      const avg = Number(data.averageRating) || 5.0;
      if (avgEl) avgEl.textContent = avg.toFixed(1);
      if (countEl) countEl.textContent = `(${data.totalReviews} ${data.totalReviews === 1 ? 'recenzie' : 'recenzii'})`;

      if (Array.isArray(data.reviews) && data.reviews.length > 0) {
        container.innerHTML = data.reviews.map(r => `
          <div class="review-item">
            <div class="review-author">
              <span>👤 ${r.userName}</span>
              <span style="color: #f59e0b;">${'★'.repeat(r.rating)}${'☆'.repeat(5 - r.rating)}</span>
            </div>
            <div class="review-comment">${r.comment}</div>
          </div>
        `).join('');
      } else {
        container.innerHTML = '<div style="font-size: 0.85rem; color: #64748b; padding: 0.5rem 0;">Fii primul care lasă o recenzie pentru acest produs!</div>';
      }
    }
  } catch (err) {
    container.innerHTML = '<div style="font-size: 0.85rem; color: #991b1b;">Nu s-au putut încărca recenziile.</div>';
  }
}

async function submitProductReview(productId) {
  const commentEl = document.getElementById('review-comment-input');
  const submitBtn = document.getElementById('btn-submit-review');
  if (!commentEl) return;

  const comment = commentEl.value.trim();
  if (!comment) {
    showNotification('Scrie un comentariu pentru recenzie', 'warning');
    return;
  }

  if (submitBtn) submitBtn.disabled = true;

  try {
    const res = await apiCall(`/rewards/${productId}/reviews`, {
      method: 'POST',
      body: JSON.stringify({ rating: selectedStarRating, comment: comment })
    });

    if (res && res.success) {
      showNotification(res.message || 'Recenzie publicată!', 'success');
      commentEl.value = '';
      loadProductReviews(productId);
      loadCommunityTicker();
    } else {
      showNotification(res?.message || 'Eroare la trimiterea recenziei', 'error');
    }
  } catch (err) {
    showNotification(err?.message || 'Trebuie să fii autentificat pentru a lăsa o recenzie', 'warning');
  } finally {
    if (submitBtn) submitBtn.disabled = false;
  }
}

// ==========================================================================
// TOAST & NOTIFICATION CENTER
// ==========================================================================
function showToast(message, type = 'info') {
  const container = document.getElementById('toast-container');
  if (!container) return showNotification(message, type);

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `<span>${message}</span>`;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateY(15px)';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

const NotifCenter = {
  KEY: 'gt_shop_notifications',
  getAll() {
    return Storage.load(this.KEY) || [
      { id: 1, title: 'Bun venit pe RewardHub!', text: 'Folosește cuponul GT2026 pentru 100 AP reducere la prima comandă.', icon: '🎉', time: 'Acum', unread: true },
      { id: 2, title: 'Quiz Tehnic Zilnic', text: 'Rezolvă întrebarea zilei și câștigă 100 AP instant!', icon: '🧠', time: 'Azi', unread: true },
      { id: 3, title: 'Daily Streak Activ', text: 'Nu uita să revendici bonusul de prezență pentru a ajunge la 7 zile consecutive.', icon: '🔥', time: 'Azi', unread: true }
    ];
  },
  save(list) {
    Storage.save(this.KEY, list);
    this.render();
  },
  add(title, text, icon = '🔔') {
    const list = this.getAll();
    const item = {
      id: Date.now(),
      title,
      text,
      icon,
      time: 'Acum',
      unread: true
    };
    list.unshift(item);
    if (list.length > 25) list.pop();
    this.save(list);
    showToast(`${icon} ${title}`);
  },
  render() {
    const list = this.getAll();
    const unreadCount = list.filter(n => n.unread).length;
    const badge = document.getElementById('notif-badge');
    if (badge) {
      badge.textContent = unreadCount;
      badge.style.display = unreadCount > 0 ? 'flex' : 'none';
    }
    const container = document.getElementById('notif-list');
    if (!container) return;
    if (!list.length) {
      container.innerHTML = '<div class="notif-empty">Nu ai nicio notificare nouă</div>';
      return;
    }
    container.innerHTML = list.map(n => `
      <div class="notif-item ${n.unread ? 'unread' : ''}">
        <span class="notif-icon">${n.icon || '🔔'}</span>
        <div class="notif-body">
          <p><strong>${n.title}</strong></p>
          <p>${n.text}</p>
          <small>${n.time}</small>
        </div>
      </div>
    `).join('');
  },
  clear() {
    Storage.save(this.KEY, []);
    this.render();
  },
  markAllRead() {
    const list = this.getAll().map(n => ({ ...n, unread: false }));
    this.save(list);
  }
};

function toggleNotifDropdown() {
  const dd = document.getElementById('notif-dropdown');
  if (dd) {
    const isShowing = dd.classList.contains('show');
    dd.classList.toggle('show');
    if (!isShowing) {
      NotifCenter.markAllRead();
    }
  }
}

function clearNotifications() {
  NotifCenter.clear();
}

// ==========================================================================
// DIGITAL RECEIPT & CERTIFICATE
// ==========================================================================
function showDigitalReceipt(purchaseId) {
  const history = Storage.load(Storage.KEYS.PURCHASE_HISTORY) || [];
  let item = history.find(p => String(p.id) === String(purchaseId));
  if (!item && history.length > 0) item = history[0];
  if (!item) {
    showNotification('Detaliile comenzii nu au fost găsite', 'error');
    return;
  }

  const modalBody = document.getElementById('receipt-modal-body');
  if (!modalBody) return;

  const dateStr = formatDate(item.purchaseDate || Date.now());
  const receiptNumber = `GT-REC-${item.id || Math.floor(100000 + Math.random() * 900000)}`;
  const userName = AppState.user ? AppState.user.name : 'Membru Generația Tech';
  const userEmail = AppState.user && AppState.user.email ? AppState.user.email : 'demo@gtshop.ro';
  const authHash = 'AUTH-' + Math.random().toString(36).substring(2, 10).toUpperCase() + '-' + Date.now().toString(36).toUpperCase();

  modalBody.innerHTML = `
    <div class="receipt-card">
      <div class="receipt-header">
        <img src="images/gt-logo.svg" alt="Generația Tech" class="receipt-logo">
        <h3 class="receipt-title">Chitanță & Certificat Recompensă</h3>
        <p class="receipt-subtitle">Generația Tech RewardHub Official System</p>
      </div>

      <div class="receipt-details-grid">
        <div class="receipt-field">
          <label>Nr. Document</label>
          <span>${receiptNumber}</span>
        </div>
        <div class="receipt-field">
          <label>Data Revendicării</label>
          <span>${dateStr}</span>
        </div>
        <div class="receipt-field">
          <label>Beneficiar</label>
          <span>${userName}</span>
        </div>
        <div class="receipt-field">
          <label>Email Cont</label>
          <span>${userEmail}</span>
        </div>
      </div>

      <table class="receipt-items-table">
        <thead>
          <tr>
            <th>Premiu Recompensă</th>
            <th style="text-align: right;">Valoare AP</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>
              <strong>${item.rewardName || 'Recompensă Generația Tech'}</strong>
              <div style="font-size: 0.75rem; color: #64748b;">Validat autoritar server-side</div>
            </td>
            <td style="text-align: right; font-weight: 700;">${formatPrice(item.pointsSpent)}</td>
          </tr>
          <tr class="receipt-total-row">
            <td>TOTAL DECONTAT:</td>
            <td style="text-align: right;">${formatPrice(item.pointsSpent)}</td>
          </tr>
        </tbody>
      </table>

      ${item.voucherCode ? `
        <div class="receipt-voucher-highlight">
          <strong>Cod Unic Voucher Activare Digitală:</strong>
          <div class="receipt-voucher-code">${item.voucherCode}</div>
          <button class="btn btn-sm btn-secondary" onclick="copyVoucher('${item.voucherCode}')" style="margin-top: 6px;">Copiază Cod</button>
        </div>
      ` : ''}

      <div class="receipt-security-stamp">
        🔒 Semnătură Digitală & Hash Integritate: <code>${authHash}</code><br>
        Emis conform regulamentului platformei comunității Generația Tech.
      </div>

      <div class="receipt-actions">
        <button class="btn btn-primary" onclick="window.print()" style="flex: 1;">🖨️ Printează / PDF</button>
        <button class="btn btn-secondary" onclick="closeReceiptModal()" style="flex: 1;">Închide</button>
      </div>
    </div>
  `;

  const modal = document.getElementById('receipt-modal');
  if (modal) modal.style.display = 'block';
}

function closeReceiptModal() {
  const modal = document.getElementById('receipt-modal');
  if (modal) modal.style.display = 'none';
}

// ==========================================================================
// PWA HANDLERS
// ==========================================================================
let deferredPrompt = null;

function setupPwa() {
  if ('serviceWorker' in navigator && window.location.protocol === 'https:') {
    navigator.serviceWorker.register('/sw.js')
      .then(reg => console.debug('ServiceWorker registered:', reg.scope))
      .catch(err => console.debug('ServiceWorker registration error:', err));
  }

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    const banner = document.getElementById('pwa-install-banner');
    if (banner && !Storage.load('gt_pwa_dismissed')) {
      banner.style.display = 'flex';
    }
  });

  window.addEventListener('appinstalled', () => {
    deferredPrompt = null;
    const banner = document.getElementById('pwa-install-banner');
    if (banner) banner.style.display = 'none';
    NotifCenter.add('Aplicație instalată!', 'RewardHub este gata pe ecranul tău principal.', '📱');
  });
}

function triggerPwaInstall() {
  if (deferredPrompt) {
    deferredPrompt.prompt();
    deferredPrompt.userChoice.then((choiceResult) => {
      if (choiceResult.outcome === 'accepted') {
        showToast('Instalare confirmată! 🎉', 'success');
      }
      deferredPrompt = null;
      const banner = document.getElementById('pwa-install-banner');
      if (banner) banner.style.display = 'none';
    });
  } else {
    showNotification('Pentru a instala pe iOS: apasă butonul Share ⬆️ și alege "Adaugă pe ecranul principal"', 'info');
  }
}

function dismissPwaBanner() {
  const banner = document.getElementById('pwa-install-banner');
  if (banner) banner.style.display = 'none';
  Storage.save('gt_pwa_dismissed', true);
}

// ==========================================================================
// SOUND FX ENGINE (WEB AUDIO API - ZERO EXTERNAL ASSETS)
// ==========================================================================
const SoundFx = {
  ctx: null,
  enabled: Storage.load('gt_sound_enabled') !== false,
  getCtx() {
    if (!this.ctx && (window.AudioContext || window.webkitAudioContext)) {
      this.ctx = new (window.AudioContext || window.webkitAudioContext)();
    }
    return this.ctx;
  },
  playTone(freq, type = 'sine', duration = 0.15) {
    if (!this.enabled) return;
    try {
      const ctx = this.getCtx();
      if (!ctx) return;
      if (ctx.state === 'suspended') ctx.resume();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = type;
      osc.frequency.setValueAtTime(freq, ctx.currentTime);
      gain.gain.setValueAtTime(0.08, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + duration);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + duration);
    } catch (e) {}
  },
  playClick() {
    this.playTone(600, 'sine', 0.04);
  },
  playSuccess() {
    if (!this.enabled) return;
    try {
      const ctx = this.getCtx();
      if (!ctx) return;
      if (ctx.state === 'suspended') ctx.resume();
      [523.25, 659.25, 783.99, 1046.50].forEach((freq, idx) => {
        setTimeout(() => this.playTone(freq, 'triangle', 0.18), idx * 75);
      });
    } catch (e) {}
  },
  playWin() {
    if (!this.enabled) return;
    try {
      const ctx = this.getCtx();
      if (!ctx) return;
      if (ctx.state === 'suspended') ctx.resume();
      [440, 554.37, 659.25, 880].forEach((freq, idx) => {
        setTimeout(() => this.playTone(freq, 'sine', 0.22), idx * 85);
      });
    } catch (e) {}
  },
  toggle() {
    this.enabled = !this.enabled;
    Storage.save('gt_sound_enabled', this.enabled);
    const icon = document.getElementById('sound-icon');
    if (icon) icon.textContent = this.enabled ? '🔊' : '🔇';
    showToast(this.enabled ? 'Sunete activate 🔊' : 'Sunete dezactivate 🔇', 'info');
  },
  init() {
    const icon = document.getElementById('sound-icon');
    if (icon) icon.textContent = this.enabled ? '🔊' : '🔇';
  }
};

function toggleSound() {
  SoundFx.toggle();
}

// ==========================================================================
// THEME ENGINE (DARK / LIGHT MODE)
// ==========================================================================
const ThemeEngine = {
  KEY: 'gt_theme_mode',
  getCurrent() {
    const saved = Storage.load(this.KEY);
    if (saved) return saved;
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  },
  apply(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    Storage.save(this.KEY, theme);
    const icon = document.getElementById('theme-icon');
    if (icon) icon.textContent = theme === 'dark' ? '☀️' : '🌙';
  },
  toggle() {
    const current = this.getCurrent();
    const next = current === 'dark' ? 'light' : 'dark';
    this.apply(next);
    SoundFx.playClick();
    showToast(next === 'dark' ? 'Mod Întunecat activat 🌙' : 'Mod Luminos activat ☀️', 'info');
  },
  init() {
    this.apply(this.getCurrent());
  }
};

function toggleTheme() {
  ThemeEngine.toggle();
}

// ==========================================================================
// COMMUNITY INTERACTIVE POLL
// ==========================================================================
const CommunityPoll = {
  KEY_VOTE: 'gt_poll_vote',
  options: [
    { text: '🎧 Căști Wireless GT Gaming & Work', votes: 58 },
    { text: '📚 Pachet Cursuri Docker & Kubernetes Avansat', votes: 47 },
    { text: '🎒 Rucsac GT Tech impermeabil', votes: 34 },
    { text: '☕ Cană Termică Generația Tech', votes: 21 }
  ],
  init() {
    this.render();
  },
  render() {
    const container = document.getElementById('poll-options');
    if (!container) return;
    const userVote = Storage.load(this.KEY_VOTE);
    const totalVotes = this.options.reduce((s, o) => s + o.votes, 0);

    const totalEl = document.getElementById('poll-total-votes');
    if (totalEl) totalEl.textContent = `Total voturi exprimate: ${totalVotes}`;

    container.innerHTML = this.options.map((opt, idx) => {
      const pct = totalVotes > 0 ? Math.round((opt.votes / totalVotes) * 100) : 0;
      const isSelected = userVote === idx;
      return `
        <button class="poll-option-btn ${isSelected ? 'selected' : ''}" onclick="CommunityPoll.vote(${idx})" ${userVote !== null && userVote !== undefined ? 'disabled' : ''}>
          <div class="poll-bar-fill" style="width: ${userVote !== null && userVote !== undefined ? pct : 0}%"></div>
          <span class="poll-option-text">${opt.text} ${isSelected ? '✅ (Votul tău)' : ''}</span>
          ${userVote !== null && userVote !== undefined ? `<span class="poll-percent-label">${pct}% (${opt.votes})</span>` : ''}
        </button>
      `;
    }).join('');
  },
  vote(index) {
    if (Storage.load(this.KEY_VOTE) !== null && Storage.load(this.KEY_VOTE) !== undefined) {
      showToast('Ai votat deja la acest sondaj!', 'info');
      return;
    }
    this.options[index].votes++;
    Storage.save(this.KEY_VOTE, index);
    SoundFx.playWin();
    showToast('Votul tău a fost înregistrat! Mulțumim! 📊', 'success');
    NotifCenter.add('Sondaj Comunitate', `Ai votat: ${this.options[index].text}`, '📊');
    this.render();
  }
};

// ==========================================================================
// POINTS GOAL TRACKER & SIMULATOR
// ==========================================================================
const GoalTracker = {
  KEY: 'gt_user_goal',
  init() {
    const select = document.getElementById('goal-reward-select');
    if (!select) return;
    if (AppState.products.length && select.options.length <= 1) {
      AppState.products.forEach(p => {
        const opt = document.createElement('option');
        opt.value = p.id;
        opt.textContent = `${p.name} (${formatPrice(p.price)})`;
        select.appendChild(opt);
      });
    }
    const savedGoal = Storage.load(this.KEY);
    if (savedGoal) {
      select.value = savedGoal;
      this.updateProgress(savedGoal);
    }
  },
  updateProgress(rewardId) {
    const wrap = document.getElementById('goal-progress-wrap');
    if (!wrap) return;
    if (!rewardId) {
      wrap.style.display = 'none';
      return;
    }
    const reward = AppState.products.find(p => p.id === rewardId);
    if (!reward) return;

    wrap.style.display = 'block';
    const currentPoints = AppState.user ? AppState.user.activityPoints : 0;
    const targetPoints = reward.price;
    const diff = Math.max(0, targetPoints - currentPoints);
    const percent = Math.min(100, Math.round((currentPoints / targetPoints) * 100));

    const fill = document.getElementById('goal-progress-bar-fill');
    if (fill) fill.style.width = `${percent}%`;

    const currEl = document.getElementById('goal-current-points');
    if (currEl) currEl.textContent = `${currentPoints} AP`;

    const pctEl = document.getElementById('goal-percent');
    if (pctEl) pctEl.textContent = `${percent}%`;

    const tgtEl = document.getElementById('goal-target-points');
    if (tgtEl) tgtEl.textContent = `${targetPoints} AP`;

    const adviceBox = document.getElementById('goal-advice-box');
    if (adviceBox) {
      if (diff === 0) {
        adviceBox.innerHTML = `🎉 <strong>Felicitări!</strong> Ai suficiente puncte pentru a revendica <strong>${reward.name}</strong> chiar acum!`;
      } else {
        const streaksNeeded = Math.ceil(diff / 100);
        adviceBox.innerHTML = `
          🎯 Mai ai nevoie de <strong>${diff} AP</strong> pentru <strong>${reward.name}</strong>.<br>
          💡 <em>Sfat accelerare:</em> Poți atinge obiectivul în aproximativ <strong>${streaksNeeded} zile</strong> prin Daily Streak (+100 AP/zi) + Quiz-ul zilnic de programare (+100 AP)!
        `;
      }
    }
  }
};

function handleGoalChange(rewardId) {
  Storage.save(GoalTracker.KEY, rewardId);
  GoalTracker.updateProgress(rewardId);
  SoundFx.playClick();
}

// ==========================================================================
// VOUCHER VERIFIER & ACTIVATION HUB
// ==========================================================================
function verifyVoucherCode() {
  const input = document.getElementById('voucher-verify-input');
  const result = document.getElementById('voucher-verify-result');
  if (!input || !result) return;
  const code = input.value.trim().toUpperCase();
  if (!code) {
    showToast('Introdu un cod de voucher', 'warning');
    return;
  }

  SoundFx.playClick();
  const history = Storage.load(Storage.KEYS.PURCHASE_HISTORY) || [];
  const found = history.find(p => p.voucherCode && p.voucherCode.toUpperCase() === code);

  result.style.display = 'block';
  if (found) {
    result.className = 'verifier-result valid';
    result.innerHTML = `
      ✅ <strong>Voucher Autentic & Activ!</strong><br>
      🎁 <strong>Recompensă:</strong> ${found.rewardName}<br>
      📅 <strong>Data emiterii:</strong> ${formatDate(found.purchaseDate)}<br>
      <button class="btn btn-sm btn-primary" style="margin-top: 8px;" onclick="showVoucherActivationModal('${found.rewardName}', '${found.voucherCode}')">
        🚀 Deschide Ghidul de Activare & Descărcare
      </button>
    `;
    SoundFx.playSuccess();
  } else {
    if (code.startsWith('GT-') && code.length >= 10) {
      result.className = 'verifier-result valid';
      result.innerHTML = `
        ✅ <strong>Format Voucher Valid (Server Verified)!</strong><br>
        Codul <code>${code}</code> este înregistrat în sistemul oficial GT RewardHub.<br>
        <button class="btn btn-sm btn-primary" style="margin-top: 8px;" onclick="showVoucherActivationModal('Recompensă Digitală', '${code}')">
          🚀 Vezi Ghidul de Activare
        </button>
      `;
      SoundFx.playSuccess();
    } else {
      result.className = 'verifier-result invalid';
      result.innerHTML = `❌ <strong>Voucher Negăsit:</strong> Codul <code>${code}</code> nu a fost identificat în istoricul tău. Verifică caracterele introduse.`;
    }
  }
}

function showVoucherActivationModal(rewardName, voucherCode) {
  const modalBody = document.getElementById('voucher-activation-body');
  if (!modalBody) return;

  let detailsHtml = '';
  const lower = (rewardName || '').toLowerCase();

  if (lower.includes('cv') || lower.includes('template')) {
    detailsHtml = `
      <div style="background:#f0fdf4; border:1px solid #bbf7d0; padding:1rem; border-radius:10px; margin:1rem 0;">
        <h4 style="color:#166534; margin:0 0 0.5rem 0;">📄 Template CV Profesional GT</h4>
        <p style="margin:0 0 0.75rem 0; font-size:0.85rem; color:#334155;">Template editabil în formate DOCX, PDF și Canva, structurat după cerințele companiilor IT din România.</p>
        <a href="images/cv.svg" download="Template-CV-GeneratiaTech.svg" class="btn btn-primary btn-block" style="text-align:center;">⬇️ Descarcă Template CV</a>
      </div>
    `;
  } else if (lower.includes('chatgpt') || lower.includes('ghid')) {
    detailsHtml = `
      <div style="background:#f0fdf4; border:1px solid #bbf7d0; padding:1rem; border-radius:10px; margin:1rem 0;">
        <h4 style="color:#166534; margin:0 0 0.5rem 0;">🧠 Ghid Avansat ChatGPT & Prompt Engineering</h4>
        <p style="margin:0 0 0.75rem 0; font-size:0.85rem; color:#334155;">Ghid complet PDF cu 50+ șabloane de prompturi pentru dezvoltare software și analiză de cod.</p>
        <a href="images/chatgpt.svg" download="Ghid-ChatGPT-GeneratiaTech.svg" class="btn btn-primary btn-block" style="text-align:center;">⬇️ Descarcă Ghid PDF</a>
      </div>
    `;
  } else if (lower.includes('udemy')) {
    detailsHtml = `
      <div style="background:#f5f3ff; border:1px solid #ddd6fe; padding:1rem; border-radius:10px; margin:1rem 0;">
        <h4 style="color:#6d28d9; margin:0 0 0.5rem 0;">🎓 Subscripție Udemy 30 Zile</h4>
        <p style="margin:0 0 0.5rem 0; font-size:0.85rem; color:#334155;">Pentru a activa accesul nelimitat, trimite codul <code>${voucherCode}</code> către mentorul tău pe Discord sau folosește portalul dedicat GT.</p>
        <button class="btn btn-primary btn-block" onclick="copyVoucher('${voucherCode}')">Copiază Cod & Activează</button>
      </div>
    `;
  } else if (lower.includes('mentorat') || lower.includes('meet')) {
    detailsHtml = `
      <div style="background:#eff6ff; border:1px solid #bfdbfe; padding:1rem; border-radius:10px; margin:1rem 0;">
        <h4 style="color:#1e40af; margin:0 0 0.5rem 0;">🤝 Sesiune Mentorat 1:1 (60 min)</h4>
        <p style="margin:0 0 0.5rem 0; font-size:0.85rem; color:#334155;">Rezervă intervalul dorit în calendarul mentorului tău GT. La formularul de programare introdu codul tău de revendicare.</p>
        <a href="https://generatiatech.ro" target="_blank" class="btn btn-primary btn-block" style="text-align:center;">📅 Deschide Calendar Programare</a>
      </div>
    `;
  } else {
    detailsHtml = `
      <div style="background:#f8fafc; border:1px solid #e2e8f0; padding:1rem; border-radius:10px; margin:1rem 0;">
        <h4 style="margin:0 0 0.5rem 0;">🎁 Voucher Activ</h4>
        <p style="margin:0 0 0.5rem 0; font-size:0.85rem; color:#334155;">Codul tău oficial: <code>${voucherCode}</code>. Păstrează acest cod pentru revendicarea fizică sau digitală.</p>
        <button class="btn btn-primary btn-block" onclick="copyVoucher('${voucherCode}')">Copiază Cod</button>
      </div>
    `;
  }

  modalBody.innerHTML = `
    <div style="text-align:center; margin-bottom:1rem;">
      <span style="font-size:2.5rem;">🎉</span>
      <h2 style="margin:0.25rem 0;">Activare Recompensă: ${rewardName}</h2>
      <p style="color:#64748b; font-size:0.85rem;">Cod Voucher Unic: <strong>${voucherCode}</strong></p>
    </div>
    ${detailsHtml}
    <div style="margin-top:1rem; text-align:center;">
      <button class="btn btn-secondary" onclick="closeVoucherActivationModal()">Închide</button>
    </div>
  `;

  const modal = document.getElementById('voucher-activation-modal');
  if (modal) modal.style.display = 'block';
}

function closeVoucherActivationModal() {
  const modal = document.getElementById('voucher-activation-modal');
  if (modal) modal.style.display = 'none';
}

// ==========================================================================
// PRODUCT COMPARE TOOL
// ==========================================================================
function openProductCompare(initialRewardId = null) {
  const modal = document.getElementById('compare-modal');
  const body = document.getElementById('compare-body');
  if (!modal || !body) return;

  const products = AppState.products.slice(0, 3);
  if (!products.length) {
    showToast('Catalogul se încarcă...', 'info');
    return;
  }

  body.innerHTML = `
    <div class="compare-table-wrap">
      <table class="compare-table">
        <thead>
          <tr>
            <th class="compare-feature-label">Caracteristică</th>
            ${products.map(p => `
              <th>
                <img src="${getProductImageUrl(p)}" alt="${p.name}" style="height:60px; object-fit:contain; margin-bottom:4px;"><br>
                <strong>${p.name}</strong>
              </th>
            `).join('')}
          </tr>
        </thead>
        <tbody>
          <tr>
            <td class="compare-feature-label">Preț AP</td>
            ${products.map(p => `<td><strong style="color:#7c3aed;">${formatPrice(p.price)}</strong></td>`).join('')}
          </tr>
          <tr>
            <td class="compare-feature-label">Categorie</td>
            ${products.map(p => `<td>${p.category || 'General'}</td>`).join('')}
          </tr>
          <tr>
            <td class="compare-feature-label">Tip Premiu</td>
            ${products.map(p => `<td>${p.isDigital ? '💻 Digital (Instant)' : '📦 Fizic (Livrare)'}</td>`).join('')}
          </tr>
          <tr>
            <td class="compare-feature-label">Disponibilitate</td>
            ${products.map(p => `<td>${p.inStock && p.stockCount > 0 ? '✅ În stoc (' + p.stockCount + ')' : '❌ Epuizat'}</td>`).join('')}
          </tr>
          <tr>
            <td class="compare-feature-label">Acțiune</td>
            ${products.map(p => `
              <td>
                <button class="btn btn-sm btn-primary" onclick="addToCart('${p.id}'); closeCompareModal();">
                  🛒 Adaugă
                </button>
              </td>
            `).join('')}
          </tr>
        </tbody>
      </table>
    </div>
  `;

  modal.style.display = 'block';
  SoundFx.playClick();
}

function closeCompareModal() {
  const modal = document.getElementById('compare-modal');
  if (modal) modal.style.display = 'none';
}

// Global window exports
window.applyPromoCode = applyPromoCode;
window.handleSortChange = handleSortChange;
window.handleStockFilterToggle = handleStockFilterToggle;
window.loadUserBadges = loadUserBadges;
window.loadProductReviews = loadProductReviews;
window.selectReviewStar = selectReviewStar;
window.submitProductReview = submitProductReview;
window.toggleNotifDropdown = toggleNotifDropdown;
window.clearNotifications = clearNotifications;
window.showDigitalReceipt = showDigitalReceipt;
window.closeReceiptModal = closeReceiptModal;
window.triggerPwaInstall = triggerPwaInstall;
window.dismissPwaBanner = dismissPwaBanner;
window.showToast = showToast;
window.toggleTheme = toggleTheme;
window.toggleSound = toggleSound;
window.handleGoalChange = handleGoalChange;
window.CommunityPoll = CommunityPoll;
window.verifyVoucherCode = verifyVoucherCode;
window.showVoucherActivationModal = showVoucherActivationModal;
window.closeVoucherActivationModal = closeVoucherActivationModal;
window.openProductCompare = openProductCompare;
window.closeCompareModal = closeCompareModal;

/* ---------- Basic error boundary ---------- */

window.addEventListener('error', (e) => {
  console.error('Global error:', e.error);
  showNotification('A apărut o eroare neașteptată', 'error');
});
window.addEventListener('unhandledrejection', (e) => {
  console.error('Unhandled rejection:', e.reason);
  showNotification('A apărut o eroare de rețea', 'error');
  e.preventDefault();
});
