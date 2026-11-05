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
    PURCHASE_HISTORY: 'gt_shop_history'
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
  const base = {
    headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' }
  };
  const finalOptions = { ...base, ...options };

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

    const body = { items: AppState.cart };
    const currentPoints = AppState.user?.activityPoints ?? 0;

    try {
      await apiCall('/user/profile', { method: 'POST', body: JSON.stringify({ userId: 'user-123', activityPoints: currentPoints }) });
    } catch (e) {
      console.warn('Sync points (checkout) a eșuat:', e);
    }

    const r = await apiCall('/checkout', { method: 'POST', body: JSON.stringify(body) });

    if (r.success) {
      showNotification('Comanda a fost finalizată cu succes!');
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
            status: 'Completed'
          };
          localHistory.unshift(purchaseRecord);
        });
        Storage.save(Storage.KEYS.PURCHASE_HISTORY, localHistory);
        showNotification(`Comandă finalizată • ID: ${r.transactionId}`, 'success');
      }

      AppState.cart = [];
      Storage.remove(Storage.KEYS.CART);
      updateCartInterface();
      await loadUserProfile();
      switchSection('profile');
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
  const avatarUrl = 'images/user-avatar.svg';
  DOM.userAvatar.src = avatarUrl;
  DOM.userAvatar.alt = AppState.user.name;
}

function updateCartInterface() {
  const totalItems = AppState.cart.reduce((s, it) => s + it.quantity, 0);
  const totalPrice = AppState.cart.reduce((s, it) => s + it.price * it.quantity, 0);
  DOM.cartCount.textContent = totalItems;
  DOM.cartTotal.textContent = formatPrice(totalPrice);
  DOM.checkoutBtn.disabled = AppState.cart.length === 0;
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
  el.innerHTML = `
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
  const avatarUrl = 'images/user-avatar.svg';
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
          <div class="stat-value">${AppState.cart.length}</div>
          <div class="stat-label">Produse în coș</div>
        </div>
      </div>
    </div>`;
}

function renderHistory(purchases) {
  if (!purchases.length) {
    DOM.historyContent.innerHTML = `
      <div class="empty-state">
        <div class="empty-icon">📋</div>
        <h3>Nu ai încă cumpărături</h3>
        <p>Istoricul va apărea după prima achiziție</p>
        <button class="btn btn-primary" onclick="goToShop()">Începe cumpărăturile</button>
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
          <span class="detail-value status-${p.status?.toLowerCase() || 'unknown'}">${p.status || 'Necunoscut'}</span>
        </div>
        ${p.id ? `
        <div class="history-info">
          <span class="detail-label">ID comandă:</span>
          <span class="detail-value">${p.id}</span>
        </div>` : ''}
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
    </div>`;
  DOM.productModal.style.display = 'block';
  document.body.style.overflow = 'hidden';
}

/* ---------- Nav / filters ---------- */

function switchSection(name) {
  const navLinks = document.querySelectorAll('.nav-link');
  const sections = document.querySelectorAll('.section');
  navLinks.forEach(l => l.classList.toggle('active', l.dataset.section === name));
  sections.forEach(s => s.classList.toggle('active', s.id === `${name}-section`));
  switch (name) {
    case 'profile': renderProfile(); break;
    case 'history': loadHistory(); break;
    case 'cart': break;
    case 'shop': if (!AppState.products.length) loadProducts(); break;
  }
}

function filterProducts() {
  const q = (DOM.searchInput.value || '').toLowerCase().trim();
  AppState.filteredProducts = AppState.products.filter(p => {
    const matchQ = !q || p.name.toLowerCase().includes(q) || p.description.toLowerCase().includes(q) || p.category.toLowerCase().includes(q);
    const matchC = !AppState.currentCategory || p.category === AppState.currentCategory;
    return matchQ && matchC;
  });
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
    setupApiToggle();
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
  const navLinks = document.querySelectorAll('.nav-link');
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
