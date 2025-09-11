/**
 * GT Shop Frontend - JavaScript Application
 * Developed by: Dragne Alexandru Mihai
 * Purpose: Dynamic shop page with Java backend API integration
 */

// API Configuration & Constants
const CONFIG = {
    API_BASE_URL: getApiBaseUrl(),
    USE_MOCK_DATA: false, // Always use real backend - no mock data
    RETRY_ATTEMPTS: 3,
    RETRY_DELAY: 1000,
    REQUEST_TIMEOUT: 10000
};

/**
 * Detectează automat URL-ul backend-ului în funcție de mediu
 */
function getApiBaseUrl() {
    const hostname = window.location.hostname;
    
    // Pentru producție pe domeniile tale - folosește HTTP pentru că backend-ul rulează pe HTTP
    if (hostname === 'rewardhub.micutu.com') {
        return 'https://api.micutu.com/RewardHub/api';
    }
    
    if (hostname === 'api.micutu.com') {
        return 'https://api.micutu.com/RewardHub/api';
    }
    
    // Pentru dezvoltare locală folosește backend-ul Java direct
    return 'http://localhost:8080/RewardHub/api';
}

// Application State Management
const AppState = {
    user: null,
    products: [],
    cart: [],
    filteredProducts: [],
    currentCategory: '',
    isLoading: false,
    error: null
};

// DOM Element References
const DOM = {
    // Navigation
    navLinks: document.querySelectorAll('.nav-link'),
    sections: document.querySelectorAll('.section'),
    
    // User Interface
    userAvatar: document.getElementById('user-avatar'),
    userName: document.getElementById('user-name'),
    userPoints: document.getElementById('user-points'),
    cartCount: document.getElementById('cart-count'),
    
    // Shop Elements
    productsGrid: document.getElementById('products-grid'),
    coursesGrid: document.getElementById('courses-grid'),
    digitalGrid: document.getElementById('digital-grid'),
    categoryBtns: document.querySelectorAll('.category-btn'),
    searchInput: document.getElementById('search-input'),
    loading: document.getElementById('loading'),
    
    // Cart Elements
    cartContent: document.getElementById('cart-content'),
    cartTotal: document.getElementById('cart-total'),
    checkoutBtn: document.getElementById('checkout-btn'),
    
    // Other Sections
    profileContent: document.getElementById('profile-content'),
    historyContent: document.getElementById('history-content'),
    
    // Modal & Notifications
    productModal: document.getElementById('product-modal'),
    modalBody: document.getElementById('modal-body'),
    closeModal: document.querySelector('.close'),
    notification: document.getElementById('notification'),
    notificationMessage: document.getElementById('notification-message'),
    
    // How It Works Modal
    howItWorksModal: document.getElementById('how-it-works-modal'),
    
    // Set Points Modal
    setPointsModal: document.getElementById('set-points-modal'),
    setPointsBtn: document.getElementById('set-points-btn'),
    
    // Storage Management
    storageStatus: document.getElementById('storage-status'),
    clearStorageBtn: document.getElementById('clear-storage-btn')
};

// === UTILITY FUNCTIONS ===

/**
 * LocalStorage management utilities
 */
const Storage = {
    KEYS: {
        CART: 'gt_shop_cart',
        USER_PREFERENCES: 'gt_shop_preferences',
        LAST_CATEGORY: 'gt_shop_last_category',
        API_MODE: 'gt_shop_api_mode'
    },

    /**
     * Save data to localStorage with error handling
     * @param {string} key - Storage key
     * @param {any} data - Data to store
     */
    save(key, data) {
        try {
            localStorage.setItem(key, JSON.stringify({
                data: data,
                timestamp: Date.now()
            }));
        } catch (error) {
            console.warn('localStorage save failed:', error);
        }
    },

    /**
     * Load data from localStorage with error handling
     * @param {string} key - Storage key
     * @returns {any} Stored data or null
     */
    load(key) {
        try {
            const stored = localStorage.getItem(key);
            if (!stored) return null;
            
            const parsed = JSON.parse(stored);
            // Optional: Add expiration check here if needed
            return parsed.data;
        } catch (error) {
            console.warn('localStorage load failed:', error);
            return null;
        }
    },

    /**
     * Remove data from localStorage
     * @param {string} key - Storage key
     */
    remove(key) {
        try {
            localStorage.removeItem(key);
        } catch (error) {
            console.warn('localStorage remove failed:', error);
        }
    },

    /**
     * Clear all app data from localStorage
     */
    clearAll() {
        Object.values(this.KEYS).forEach(key => this.remove(key));
        showStorageStatus('Toate datele locale au fost șterse', 'error');
    }
};

/**
 * Show storage status indicator
 * @param {string} message - Status message
 * @param {string} type - Status type ('success', 'error')
 */
function showStorageStatus(message, type = 'success') {
    if (!DOM.storageStatus) return;
    
    // Clear any existing timeout
    if (DOM.storageStatus.hideTimeout) {
        clearTimeout(DOM.storageStatus.hideTimeout);
    }
    
    // Reset classes completely
    DOM.storageStatus.className = 'storage-status';
    
    DOM.storageStatus.textContent = message;
    DOM.storageStatus.className = `storage-status ${type} show`;
    
    // Auto-hide after 2 seconds with proper cleanup
    DOM.storageStatus.hideTimeout = setTimeout(() => {
        DOM.storageStatus.classList.remove('show');
        // Complete cleanup after animation
        setTimeout(() => {
            DOM.storageStatus.className = 'storage-status';
            DOM.storageStatus.textContent = '';
        }, 300); // Wait for CSS transition to complete
    }, 2000);
}

/**
 * Display user-friendly notifications
 * @param {string} message - The message to display
 * @param {string} type - 'success', 'error', 'warning', 'info'
 */
function showNotification(message, type = 'success') {
    const notification = DOM.notification;
    const messageEl = DOM.notificationMessage;
    
    // Clear any existing timeout
    if (notification.hideTimeout) {
        clearTimeout(notification.hideTimeout);
    }
    
    // Reset classes completely
    notification.className = 'notification';
    
    // Set message and show with new type
    messageEl.textContent = message;
    notification.className = `notification ${type} show`;
    
    // Auto-hide after 4 seconds with proper cleanup
    notification.hideTimeout = setTimeout(() => {
        notification.classList.remove('show');
        // Complete cleanup after animation
        setTimeout(() => {
            notification.className = 'notification';
            messageEl.textContent = '';
        }, 300); // Wait for CSS transition to complete
    }, 4000);
}

/**
 * Generate placeholder image URL for products
 * @param {Object} product - Product data
 * @returns {string} Image URL
 */
function getProductImageUrl(product) {
    // Check if product has a real image
    if (product.image && product.image.startsWith('http')) {
        return product.image;
    }
    
    // Use local SVG files for specific products
    const productSVGs = {
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
    
    // Check if we have a custom SVG for this product
    if (productSVGs[product.id]) {
        return productSVGs[product.id];
    }
    
    // Default fallback SVG for products without specific SVG
    return 'images/sticker.svg';
}

/**
 * Format price with proper currency
 * @param {number} price - Price in points
 * @returns {string} Formatted price string
 */
function formatPrice(price) {
    return `${price?.toLocaleString('ro-RO') || 0} puncte`;
}

/**
 * Format date to Romanian locale
 * @param {string} dateString - ISO date string
 * @returns {string} Formatted date
 */
function formatDate(dateString) {
    try {
        return new Date(dateString).toLocaleDateString('ro-RO', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (error) {
        console.warn('Invalid date format:', dateString);
        return 'Data invalidă';
    }
}

/**
 * Debounce function for search input
 * @param {Function} func - Function to debounce
 * @param {number} wait - Wait time in milliseconds
 * @returns {Function} Debounced function
 */
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// === API COMMUNICATION LAYER ===

/**
 * Enhanced API call function with mock data support
 * @param {string} endpoint - API endpoint
 * @param {Object} options - Fetch options
 * @returns {Promise} API response
 */
async function apiCall(endpoint, options = {}) {
    // Real API call logic only - no mock data
    const url = `${CONFIG.API_BASE_URL}${endpoint}`;
    const defaultOptions = {
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        },
        timeout: CONFIG.REQUEST_TIMEOUT
    };

    const finalOptions = { ...defaultOptions, ...options };

    for (let attempt = 1; attempt <= CONFIG.RETRY_ATTEMPTS; attempt++) {
        try {
            
            const controller = new AbortController();
            const timeoutId = setTimeout(() => controller.abort(), CONFIG.REQUEST_TIMEOUT);
            
            const response = await fetch(url, {
                ...finalOptions,
                signal: controller.signal
            });
            
            clearTimeout(timeoutId);
            
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            const data = await response.json();
            return data;
            
        } catch (error) {
            console.error(`❌ API Error [Attempt ${attempt}]:`, error);
            
            if (attempt === CONFIG.RETRY_ATTEMPTS) {
                const userMessage = getErrorMessage(error);
                showNotification(userMessage, 'error');
                throw new Error(userMessage);
            }
            
            // Wait before retry
            await new Promise(resolve => setTimeout(resolve, CONFIG.RETRY_DELAY * attempt));
        }
    }
}

/**
 * Mock API call handler
 * @param {string} endpoint - API endpoint
 * @param {Object} options - Request options
 * @returns {Promise} Mock response
 */
async function mockApiCall(endpoint, options = {}) {
    
    try {
        // Simulate network errors occasionally
        if (window.MockAPI.shouldSimulateError()) {
            const error = window.MockAPI.getRandomError();
            throw new Error(`HTTP ${error.status}: ${error.message}`);
        }

        switch (endpoint) {
            case '/rewards':
                return await window.MockAPI.getRewards();
                
            case '/user/profile':
                return await window.MockAPI.getUserProfile();
                
            case '/cart':
                return await window.MockAPI.getCart();
                
            case '/cart/add':
                if (options.method === 'POST') {
                    const cartItem = JSON.parse(options.body);
                    return await window.MockAPI.addToCart(cartItem);
                }
                break;
                
            case '/buy-now':
                if (options.method === 'POST') {
                    const buyRequest = JSON.parse(options.body);
                    return await window.MockAPI.buyNow(buyRequest.productId, buyRequest.quantity);
                }
                break;
                
            case '/checkout':
                if (options.method === 'POST') {
                    const checkoutRequest = JSON.parse(options.body);
                    return await window.MockAPI.checkout(checkoutRequest.items);
                }
                break;
                
            case '/user/history':
                return await window.MockAPI.getUserHistory();
                
            default:
                throw new Error(`Mock endpoint not implemented: ${endpoint}`);
        }
    } catch (error) {
        console.error('🎭 Mock API Error:', error);
        throw error;
    }
}

/**
 * Convert technical errors to user-friendly messages
 * @param {Error} error - The error object
 * @returns {string} User-friendly error message
 */
function getErrorMessage(error) {
    if (error.name === 'AbortError') {
        return 'Cererea a fost anulată din cauza unei întârzieri. Vă rugăm să încercați din nou.';
    }
    
    if (error.message.includes('Failed to fetch')) {
        return 'Nu se poate conecta la server. Verificați conexiunea la internet.';
    }
    
    if (error.message.includes('HTTP 404')) {
        return 'Resursa solicitată nu a fost găsită.';
    }
    
    if (error.message.includes('HTTP 500')) {
        return 'Eroare de server. Vă rugăm să încercați mai târziu.';
    }
    
    if (error.message.includes('HTTP 403')) {
        return 'Nu aveți permisiunea să accesați această resursă.';
    }
    
    return 'A apărut o eroare neașteptată. Vă rugăm să încercați din nou.';
}

// === DATA LOADING FUNCTIONS ===

/**
 * Load user profile from API
 */
async function loadUserProfile() {
    try {
        // Try to load from backend first
        try {
            const response = await apiCall('/user/profile');
            
            if (response && response.id) {
                AppState.user = {
                    id: response.id,
                    name: response.name,
                    avatar: response.avatar,
                    activityPoints: response.activityPoints
                };
                
                // Save to localStorage for offline use
                Storage.save('userPoints', response.activityPoints);
                
                updateUserInterface();
                return;
            }
        } catch (backendError) {
            console.warn('⚠️ Nu s-a putut încărca profilul de la backend:', backendError);
        }
        
        // Fallback to localStorage if backend fails
        const savedPoints = Storage.load('userPoints');
        const finalPoints = savedPoints !== null ? savedPoints : 2056;
        
        AppState.user = {
            id: 'user-123',
            name: 'Popescu Ion',
            avatar: 'https://example.com/avatar.png',
            activityPoints: finalPoints
        };
        updateUserInterface();
        
    } catch (error) {
        console.warn('Eroare la încărcarea profilului:', error);
        AppState.user = {
            id: 'user-123',
            name: 'Popescu Ion',
            avatar: 'https://example.com/avatar.png',
            activityPoints: 2056
        };
        updateUserInterface();
    }
}

/**
 * Load products from API and categorize them
 */
async function loadProducts() {
    try {
        setLoadingState(true);
        const response = await apiCall('/rewards');
        
        AppState.products = response.rewards || [];
        AppState.filteredProducts = [...AppState.products];
        
        renderProductsByCategory();
        
    } catch (error) {
        console.error('Failed to load products:', error);
        showErrorState('Nu s-au putut încărca produsele');
    } finally {
        setLoadingState(false);
    }
}

/**
 * Load cart contents from API and localStorage
 */
async function loadCart() {
    try {
        // First try to load from localStorage
        const savedCart = Storage.load(Storage.KEYS.CART);
        if (savedCart && Array.isArray(savedCart)) {
            AppState.cart = savedCart;
            updateCartInterface();
        }

        // Then try to sync with API (if available)
        const response = await apiCall('/cart');
        if (response && response.items) {
            // Merge localStorage cart with API cart
            const apiCart = response.items || [];
            
            // Simple merge strategy: combine and deduplicate by rewardId
            const mergedCart = [...AppState.cart];
            
            apiCart.forEach(apiItem => {
                const existingIndex = mergedCart.findIndex(item => item.rewardId === apiItem.rewardId);
                if (existingIndex >= 0) {
                    // Update quantity to the higher value
                    mergedCart[existingIndex].quantity = Math.max(
                        mergedCart[existingIndex].quantity,
                        apiItem.quantity
                    );
                } else {
                    mergedCart.push(apiItem);
                }
            });
            
            AppState.cart = mergedCart;
            updateCartInterface();
        }
        
    } catch (error) {
        console.error('Failed to load cart from API:', error);
        // If API fails, use localStorage data (already loaded above)
    }
}

/**
 * Load purchase history from API
 */
async function loadHistory() {
    try {
        const response = await apiCall('/user/history');
        
        // Handle different response formats
        let purchases = [];
        if (response.purchases && Array.isArray(response.purchases)) {
            purchases = response.purchases;
        } else if (Array.isArray(response)) {
            purchases = response;
        }
        
        renderHistory(purchases);
        
    } catch (error) {
        console.error('Failed to load history:', error);
        showErrorState('Nu s-a putut încărca istoricul cumpărăturilor', 'history-content');
    }
}

// === CART OPERATIONS ===

/**
 * Add product to cart with localStorage persistence
 * @param {Object} product - Product object
 */
async function addToCart(product) {
    try {
        const cartItem = {
            rewardId: product.id,
            name: product.name,
            price: product.price,
            quantity: 1
        };
        
        const response = await apiCall('/cart/add', {
            method: 'POST',
            body: JSON.stringify(cartItem)
        });
        
        if (response.success) {
            showNotification(`${product.name} adăugat în coș!`);
            
            // Update cart from API response
            if (response.cart && response.cart.items) {
                AppState.cart = response.cart.items;
            } else {
                // Fallback: add to local cart if no API response
                const existingItem = AppState.cart.find(item => item.rewardId === product.id);
                if (existingItem) {
                    existingItem.quantity += 1;
                } else {
                    AppState.cart.push(cartItem);
                }
            }
            
            updateCartInterface();
            await loadUserProfile(); // Refresh points
            await loadProducts(); // Refresh products to update stock
            renderProductsByCategory(); // Re-render to show updated stock
        } else {
            showNotification(response.message || 'Eroare la adăugarea în coș!', 'error');
        }
        
    } catch (error) {
        console.error('Failed to add to cart:', error);
        
        // Fallback: add to local cart if API fails
        const existingItem = AppState.cart.find(item => item.rewardId === product.id);
        if (existingItem) {
            existingItem.quantity += 1;
        } else {
            AppState.cart.push({
                rewardId: product.id,
                name: product.name,
                price: product.price,
                quantity: 1
            });
        }
        
        updateCartInterface();
        showNotification(`${product.name} adăugat în coș (offline)`, 'warning');
    }
}

/**
 * Buy product directly (skip cart) - uses checkout endpoint
 * @param {Object} product - Product object
 */
async function buyNow(product) {
    try {
        // Check if user has enough points (frontend validation)
        if (AppState.user && AppState.user.activityPoints < product.price) {
            const missingPoints = product.price - AppState.user.activityPoints;
            showNotification(`Nu deții suficiente puncte! Mai ai nevoie de ${formatPrice(missingPoints)}.`, 'error');
            return;
        }
        
        // Create a temporary cart item for direct purchase
        const cartItem = {
            rewardId: product.id,
            name: product.name,
            price: product.price,
            quantity: 1
        };
        
        const checkoutRequest = {
            items: [cartItem]
        };
        
        // Sync current points with backend before checkout
        const currentPoints = AppState.user.activityPoints;
        console.log('Trying to sync points with backend before buy now:', currentPoints);
        
        try {
            // Try to update backend points via a mock profile sync
            await apiCall('/user/profile', {
                method: 'POST',
                body: JSON.stringify({
                    userId: 'user-123',
                    activityPoints: currentPoints
                })
            });
            console.log('Points synced with backend successfully');
        } catch (syncError) {
            console.warn('Could not sync points with backend, continuing with buy now:', syncError);
        }
        
        const response = await apiCall('/checkout', {
            method: 'POST',
            body: JSON.stringify(checkoutRequest)
        });
        
        // Remove force success - use real backend response
        if (response.success) {
            // Update points from backend response
            if (response.newBalance !== undefined) {
                AppState.user.activityPoints = response.newBalance;
                Storage.save('userPoints', response.newBalance);
            }
            
            showNotification(response.message || 'Produs cumpărat cu succes!', 'success');
            updateUserInterface(); // Update UI to show new points
            await loadProducts(); // Refresh products to update stock
            renderProductsByCategory(); // Re-render to show updated stock
            
            // Show transaction details
            if (response.transactionId) {
                showNotification(`Comandă finalizată! ID: ${response.transactionId}`, 'success');
            }
        } else {
            let errorMessage = response.message || 'Eroare la cumpărare!';
            if (response.missingPoints) {
                errorMessage = `Nu deții suficiente puncte! Mai ai nevoie de ${response.missingPoints} puncte.`;
            }
            showNotification(errorMessage, 'error');
        }
        
    } catch (error) {
        console.error('Failed to buy now:', error);
        showNotification('Eroare la cumpărare!', 'error');
    }
}

/**
 * Checkout process with localStorage cleanup
 */
async function checkout() {
    try {
        if (AppState.cart.length === 0) {
            showNotification('Coșul este gol!', 'warning');
            return;
        }
        
        // Calculate total cost
        const totalCost = AppState.cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);
        
        // Check if user has enough points (frontend validation)
        if (AppState.user && AppState.user.activityPoints < totalCost) {
            const missingPoints = totalCost - AppState.user.activityPoints;
            showNotification(`Nu deții suficiente puncte! Mai ai nevoie de ${formatPrice(missingPoints)}.`, 'error');
            return;
        }
        
        const checkoutRequest = {
            items: AppState.cart
        };
        
        // Sync current points with backend before checkout
        const currentPoints = AppState.user.activityPoints;
        console.log('Cost total:', totalCost);
        console.log('Puncte user:', currentPoints);
        console.log('Are suficiente puncte?', currentPoints >= totalCost);
        
        // Try to sync points with backend before checkout
        try {
            await apiCall('/user/profile', {
                method: 'POST',
                body: JSON.stringify({
                    userId: 'user-123',
                    activityPoints: currentPoints
                })
            });
            console.log('Points synced with backend before checkout');
        } catch (syncError) {
            console.warn('Could not sync points with backend before checkout:', syncError);
        }
        
        const response = await apiCall('/checkout', {
            method: 'POST',
            body: JSON.stringify(checkoutRequest)
        });
        
        // Remove force success - use real backend response
        if (response.success) {
            showNotification('Comanda a fost finalizată cu succes!');
            
            // Update points from backend response
            if (response.newBalance !== undefined) {
                AppState.user.activityPoints = response.newBalance;
                Storage.save('userPoints', response.newBalance);
            }
            
            // Show transaction details
            if (response.transactionId) {
                showNotification(`Comandă finalizată! ID: ${response.transactionId}`, 'success');
            }
            
            // Clear cart from both state and localStorage
            AppState.cart = [];
            Storage.remove(Storage.KEYS.CART);
            
            updateCartInterface();
            await loadUserProfile(); // Refresh user points from backend
            switchSection('profile'); // Go to profile to see updated points
        } else {
            let errorMessage = response.message || 'Eroare la finalizarea comenzii';
            if (response.missingPoints) {
                errorMessage = `Nu deții suficiente puncte! Mai ai nevoie de ${response.missingPoints} puncte.`;
            }
            showNotification(errorMessage, 'error');
        }
        
    } catch (error) {
        console.error('Checkout failed:', error);
        showNotification('Eroare la finalizarea comenzii', 'error');
    }
}

// === UI UPDATE FUNCTIONS ===

/**
 * Update user interface elements
 */
function updateUserInterface() {
    if (AppState.user) {
        DOM.userName.textContent = AppState.user.name;
        DOM.userPoints.textContent = formatPrice(AppState.user.activityPoints);
        
        // Set avatar with fallback to our custom SVG
        const avatarUrl = 'images/user-avatar.svg';
        DOM.userAvatar.src = avatarUrl;
        DOM.userAvatar.alt = AppState.user.name;
    }
}

/**
 * Generate avatar placeholder
 * @param {string} name - User name
 * @returns {string} Data URL for avatar
 */
function generateAvatarPlaceholder(name) {
    const initials = name.split(' ').map(n => n[0]).join('').toUpperCase();
    return `data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="45" height="45" viewBox="0 0 45 45"><rect width="45" height="45" fill="%237c3aed"/><text x="22.5" y="28" text-anchor="middle" fill="white" font-size="16" font-weight="600">${initials}</text></svg>`;
}

/**
 * Update cart interface elements and save to localStorage
 */
function updateCartInterface() {
    const totalItems = AppState.cart.reduce((sum, item) => sum + item.quantity, 0);
    const totalPrice = AppState.cart.reduce((sum, item) => sum + (item.price * item.quantity), 0);
    
    DOM.cartCount.textContent = totalItems;
    DOM.cartTotal.textContent = formatPrice(totalPrice);
    DOM.checkoutBtn.disabled = AppState.cart.length === 0;
    
    // Save cart to localStorage
    Storage.save(Storage.KEYS.CART, AppState.cart);
    
    // Show storage status for user feedback
    if (AppState.cart.length > 0) {
        showStorageStatus('💾 Coșul a fost salvat local', 'success');
    }
    
    renderCart();
}

/**
 * Set loading state
 * @param {boolean} isLoading - Loading state
 */
function setLoadingState(isLoading) {
    AppState.isLoading = isLoading;
    DOM.loading.style.display = isLoading ? 'block' : 'none';
}

/**
 * Show error state in specific container
 * @param {string} message - Error message
 * @param {string} containerId - Container element ID
 */
function showErrorState(message, containerId = null) {
    const errorHTML = `
        <div class="error-state">
            <div class="error-icon">⚠️</div>
            <h3>Oops! Ceva nu a mers bine</h3>
            <p>${message}</p>
            <button class="btn btn-primary" onclick="location.reload()">Încearcă din nou</button>
        </div>
    `;
    
    if (containerId) {
        const container = document.getElementById(containerId);
        if (container) container.innerHTML = errorHTML;
    } else {
        // Show in main products grid
        DOM.productsGrid.innerHTML = errorHTML;
    }
}

// === RENDERING FUNCTIONS ===

/**
 * Render products organized by categories
 */
function renderProductsByCategory() {
    const physicalProducts = AppState.filteredProducts.filter(p => 
        p.category === 'Premii fizice'
    );
    const exclusiveProducts = AppState.filteredProducts.filter(p => 
        p.category === 'Premii exclusive'
    );
    const digitalProducts = AppState.filteredProducts.filter(p => 
        p.category === 'Premii digitale'
    );
    
    // Get section containers
    const physicalSection = document.querySelector('.products-section');
    const exclusiveSection = document.querySelector('.courses-section');
    const digitalSection = document.querySelector('.digital-section');
    
    // Array to track sections and their priority for ordering
    const sections = [
        { element: physicalSection, products: physicalProducts, category: 'Premii fizice', grid: DOM.productsGrid },
        { element: exclusiveSection, products: exclusiveProducts, category: 'Premii exclusive', grid: DOM.coursesGrid },
        { element: digitalSection, products: digitalProducts, category: 'Premii digitale', grid: DOM.digitalGrid }
    ];
    
    // Sort sections to show selected category first
    sections.sort((a, b) => {
        if (AppState.currentCategory && a.category === AppState.currentCategory) return -1;
        if (AppState.currentCategory && b.category === AppState.currentCategory) return 1;
        return 0;
    });
    
    // Apply order to DOM (move selected category section to top)
    if (AppState.currentCategory) {
        const targetSection = sections.find(s => s.category === AppState.currentCategory);
        if (targetSection && targetSection.element && targetSection.products.length > 0) {
            const mainContent = document.querySelector('.main .content');
            if (mainContent) {
                mainContent.insertBefore(targetSection.element, mainContent.firstChild);
            }
        }
    }
    
    // Show/hide sections based on content and render grids
    sections.forEach(({ element, products, grid }) => {
        if (!element) {
            console.warn('Section element not found', element);
            return;
        }
        
        if (products.length > 0) {
            element.style.display = 'block';
            renderProductGrid(grid, products);
        } else {
            element.style.display = 'none';
        }
    });
}

/**
 * Render product grid in specific container
 * @param {HTMLElement} container - Container element
 * @param {Array} products - Products array
 */
function renderProductGrid(container, products) {
    if (!container) return;
    
    container.innerHTML = '';
    
    if (products.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">📦</div>
                <h3>Nu s-au găsit produse</h3>
                <p>Încearcă să modifici criteriile de căutare sau să verifici alte categorii</p>
            </div>
        `;
        return;
    }
    
    products.forEach(product => {
        const productCard = createProductCard(product);
        container.appendChild(productCard);
    });
}

/**
 * Create product card element
 * @param {Object} product - Product data
 * @returns {HTMLElement} Product card element
 */
function createProductCard(product) {
    const productCard = document.createElement('div');
    productCard.className = 'product-card';
    productCard.setAttribute('data-product-id', product.id);
    
    productCard.innerHTML = `
        <div class="product-image">
            <img src="${getProductImageUrl(product)}" alt="${product.name}" 
                 onerror="this.style.display='none'; this.parentElement.innerHTML='📦';" 
                 loading="lazy" />
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
        </div>
    `;
    
    // Add event listeners
    productCard.addEventListener('click', (e) => {
        if (!e.target.classList.contains('product-add-btn') && !e.target.classList.contains('product-buy-btn')) {
            showProductModal(product);
        }
    });
    
    const addBtn = productCard.querySelector('.product-add-btn');
    addBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        addToCart(product);
    });

    const buyBtn = productCard.querySelector('.product-buy-btn');
    buyBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        buyNow(product);
    });
    
    return productCard;
}

/**
 * Render shopping cart
 */
function renderCart() {
    if (AppState.cart.length === 0) {
        DOM.cartContent.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">🛒</div>
                <h3>Coșul este gol</h3>
                <p>Adaugă produse din magazin pentru a începe cumpărăturile</p>
                <button class="btn btn-primary" onclick="goToShop()">Explorează produsele</button>
            </div>
        `;
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

/**
 * Render user profile
 */
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
        </div>
    `;
}

/**
 * Render purchase history
 * @param {Array} purchases - Purchase history array
 */
function renderHistory(purchases) {
    if (purchases.length === 0) {
        DOM.historyContent.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">📋</div>
                <h3>Nu ai încă cumpărături</h3>
                <p>Istoricul tău de comenzi va apărea aici după prima achiziție</p>
                <button class="btn btn-primary" onclick="goToShop()">Începe cumpărăturile</button>
            </div>
        `;
        return;
    }
    
    // Group purchases by date or show individual records
    DOM.historyContent.innerHTML = purchases.map(purchase => `
        <div class="history-item">
            <div class="history-header">
                <h4>${purchase.rewardName || 'Produs necunoscut'}</h4>
                <span class="history-date">${formatDate(purchase.purchaseDate)}</span>
            </div>
            <div class="history-details">
                <div class="history-info">
                    <span class="detail-label">Puncte cheltuite:</span>
                    <span class="detail-value">${formatPrice(purchase.pointsSpent)}</span>
                </div>
                <div class="history-info">
                    <span class="detail-label">Status:</span>
                    <span class="detail-value status-${purchase.status?.toLowerCase() || 'unknown'}">${purchase.status || 'Necunoscut'}</span>
                </div>
                ${purchase.id ? `
                <div class="history-info">
                    <span class="detail-label">ID comandă:</span>
                    <span class="detail-value">${purchase.id}</span>
                </div>
                ` : ''}
            </div>
        </div>
    `).join('');
    
    // Add summary at the top
    const totalPoints = purchases.reduce((sum, p) => sum + (p.pointsSpent || 0), 0);
    const summaryHTML = `
        <div class="history-summary">
            <h3>Rezumat</h3>
            <div class="summary-stats">
                <div class="summary-stat">
                    <span class="stat-label">Total cumpărături:</span>
                    <span class="stat-value">${purchases.length}</span>
                </div>
                <div class="summary-stat">
                    <span class="stat-label">Total puncte cheltuite:</span>
                    <span class="stat-value">${formatPrice(totalPoints)}</span>
                </div>
            </div>
        </div>
        <hr>
    `;
    
    DOM.historyContent.innerHTML = summaryHTML + DOM.historyContent.innerHTML;
}

/**
 * Show product details modal
 * @param {Object} product - Product object
 */
function showProductModal(product) {
    const imageUrl = getProductImageUrl(product);
    const isInStock = product.inStock && product.stockCount > 0;
    
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
                    <span class="detail-value ${!isInStock ? 'out-of-stock' : ''}">
                        ${isInStock ? `${product.stockCount} bucăți disponibile` : 'Stoc epuizat'}
                    </span>
                </div>
            </div>
            <div class="modal-actions">
                <button class="btn btn-primary" onclick="addToCartFromModal('${product.id}')" ${!isInStock ? 'disabled' : ''}>
                    ${isInStock ? '+ Adaugă în coș' : 'Stoc epuizat'}
                </button>
                <button class="btn btn-secondary" onclick="closeProductModal()">Închide</button>
            </div>
        </div>
    `;
    
    DOM.productModal.style.display = 'block';
    document.body.style.overflow = 'hidden'; // Prevent background scroll
}

// === EVENT HANDLERS & NAVIGATION ===

/**
 * Switch between different sections
 * @param {string} sectionName - Section identifier
 */
function switchSection(sectionName) {
    // Get fresh references to DOM elements
    const navLinks = document.querySelectorAll('.nav-link');
    const sections = document.querySelectorAll('.section');
    
    // Update navigation active state
    navLinks.forEach(link => {
        link.classList.toggle('active', link.dataset.section === sectionName);
    });
    
    // Update section visibility
    sections.forEach(section => {
        section.classList.toggle('active', section.id === `${sectionName}-section`);
    });
    
    // Load section-specific data
    switch (sectionName) {
        case 'profile':
            renderProfile();
            break;
        case 'history':
            loadHistory();
            break;
        case 'cart':
            // Cart is already loaded and updated
            break;
        case 'shop':
            // Ensure products are loaded
            if (AppState.products.length === 0) {
                loadProducts();
            }
            break;
    }
}

/**
 * Filter products based on search and category
 */
function filterProducts() {
    const search = DOM.searchInput.value.toLowerCase().trim();
    
    AppState.filteredProducts = AppState.products.filter(product => {
        const matchesSearch = !search || 
            product.name.toLowerCase().includes(search) ||
            product.description.toLowerCase().includes(search) ||
            product.category.toLowerCase().includes(search);
        
        const matchesCategory = !AppState.currentCategory || 
            product.category === AppState.currentCategory;
        
        return matchesSearch && matchesCategory;
    });
    
    renderProductsByCategory();
}

/**
 * Filter products by category and save preference
 * @param {string} category - Category name
 */
function filterByCategory(category) {
    AppState.currentCategory = category;
    
    // Save category preference
    Storage.save(Storage.KEYS.LAST_CATEGORY, category);
    
    // Update category buttons visual state
    DOM.categoryBtns.forEach(btn => {
        const btnCategory = btn.dataset.category || '';
        btn.classList.toggle('active', btnCategory === category);
    });
    
    // Switch to shop section if not already there
    const currentSection = document.querySelector('.section.active');
    if (!currentSection || currentSection.id !== 'shop-section') {
        switchSection('shop');
    }
    
    filterProducts();
}

/**
 * Update cart item quantity and save to localStorage
 * @param {string} rewardId - Product ID
 * @param {number} newQuantity - New quantity
 */
function updateCartItemQuantity(rewardId, newQuantity) {
    if (newQuantity <= 0) {
        removeFromCart(rewardId);
        return;
    }
    
    const item = AppState.cart.find(item => item.rewardId === rewardId);
    if (item) {
        item.quantity = newQuantity;
        updateCartInterface(); // This will also save to localStorage
        showNotification(`Cantitate actualizată pentru ${item.name}`, 'info');
    }
}

/**
 * Remove item from cart and update localStorage
 * @param {string} rewardId - Product ID to remove
 */
function removeFromCart(rewardId) {
    const item = AppState.cart.find(item => item.rewardId === rewardId);
    if (item) {
        AppState.cart = AppState.cart.filter(item => item.rewardId !== rewardId);
        updateCartInterface(); // This will also save to localStorage
        showNotification(`${item.name} a fost eliminat din coș`, 'info');
    }
}

/**
 * Add to cart from modal
 * @param {string} productId - Product ID
 */
function addToCartFromModal(productId) {
    const product = AppState.products.find(p => p.id === productId);
    if (product) {
        addToCart(product);
        closeProductModal();
    }
}

/**
 * Close product modal
 */
function closeProductModal() {
    DOM.productModal.style.display = 'none';
    document.body.style.overflow = ''; // Restore background scroll
}

/**
 * Show how it works modal
 */
function showHowItWorksModal() {
    DOM.howItWorksModal.style.display = 'block';
    document.body.style.overflow = 'hidden'; // Prevent background scroll
}

/**
 * Close how it works modal
 */
function closeHowItWorksModal() {
    DOM.howItWorksModal.style.display = 'none';
    document.body.style.overflow = ''; // Restore background scroll
}

/**
 * Show set points modal
 */
function showSetPointsModal() {
    const setPointsModal = document.getElementById('set-points-modal');
    const currentPointsDisplay = document.getElementById('current-points-display');
    const pointsInput = document.getElementById('points-input');
    
    // Update current points display
    if (AppState.user) {
        currentPointsDisplay.textContent = formatPrice(AppState.user.activityPoints);
    }
    
    // Clear input
    pointsInput.value = '';
    
    // Show modal
    setPointsModal.style.display = 'block';
    document.body.style.overflow = 'hidden'; // Prevent background scroll
    
    // Focus on input
    setTimeout(() => {
        pointsInput.focus();
        
        // Add Enter key support
        pointsInput.addEventListener('keydown', function(e) {
            if (e.key === 'Enter') {
                setUserPoints();
            }
        });
    }, 100);
}

/**
 * Close set points modal
 */
function closeSetPointsModal() {
    const setPointsModal = document.getElementById('set-points-modal');
    setPointsModal.style.display = 'none';
    document.body.style.overflow = ''; // Restore background scroll
}

/**
 * Set user points via API call
 */
async function setUserPoints() {
    const pointsInput = document.getElementById('points-input');
    const newPoints = parseInt(pointsInput.value);
    
    // Validation
    if (isNaN(newPoints) || newPoints < 0) {
        showNotification('Vă rugăm să introduceți un număr valid de puncte!', 'error');
        return;
    }
    
    if (newPoints > 999999) {
        showNotification('Numărul de puncte nu poate fi mai mare de 999,999!', 'error');
        return;
    }
    
    try {
        showNotification('Se setează punctele...', 'info');
        
        // Update backend points first
        try {
            const response = await apiCall('/user/profile', {
                method: 'POST',
                body: JSON.stringify({
                    userId: 'user-123',
                    activityPoints: newPoints
                })
            });
            
            if (response.success) {
                // Backend update successful
            } else {
                // Continue with local update anyway
            }
        } catch (backendError) {
            // Continue with local update anyway
        }
        
        // Save points to localStorage for persistence
        Storage.save('userPoints', newPoints);
        
        // Update user state immediately
        if (AppState.user) {
            AppState.user.activityPoints = newPoints;
        }
        
        // Success message
        showNotification(`Punctele au fost setate la ${formatPrice(newPoints)}!`, 'success');
        
        // Update UI
        updateUserInterface();
        
        // Close modal
        closeSetPointsModal();
        
        // If we're on profile section, re-render it
        const currentSection = document.querySelector('.section.active');
        if (currentSection && currentSection.id === 'profile-section') {
            renderProfile();
        }
        
    } catch (error) {
        console.error('Failed to set points:', error);
        showNotification('Eroare la setarea punctelor! Verificați conexiunea.', 'error');
    }
}

/**
 * Go to shop section and reset filters
 */
function goToShop() {
    // Reset category filter
    AppState.currentCategory = '';
    
    // Reset category buttons - set "Toate" as active
    DOM.categoryBtns.forEach(btn => {
        btn.classList.toggle('active', !btn.dataset.category || btn.dataset.category === '');
    });
    
    // Clear search
    if (DOM.searchInput) {
        DOM.searchInput.value = '';
    }
    
    // Switch to shop section
    switchSection('shop');
    
    // Reapply filters (will show all products)
    filterProducts();
}

// === INITIALIZATION & EVENT LISTENERS ===

/**
 * Initialize application with localStorage restoration
 */
async function initializeApp() {
    
    try {
        // Show initial loading state
        setLoadingState(true);
        
        // Restore saved preferences
        restoreUserPreferences();
        
        // Load initial data in parallel
        await Promise.all([
            loadUserProfile(),
            loadProducts(),
            loadCart()
        ]);
        
        // Setup API toggle after data is loaded
        setupApiToggle();
        
    } catch (error) {
        console.error('❌ Application initialization failed:', error);
        showNotification('Eroare la inițializarea aplicației', 'error');
    } finally {
        setLoadingState(false);
    }
}

/**
 * Restore user preferences from localStorage
 */
function restoreUserPreferences() {
    // Restore last selected category
    const lastCategory = Storage.load(Storage.KEYS.LAST_CATEGORY);
    if (lastCategory) {
        AppState.currentCategory = lastCategory;
        
        // Update category button active state
        DOM.categoryBtns.forEach(btn => {
            btn.classList.toggle('active', btn.dataset.category === lastCategory);
        });
        
    }
    
    // Restore API mode
    const savedApiMode = Storage.load(Storage.KEYS.API_MODE);
    if (savedApiMode !== null) {
        CONFIG.USE_MOCK_DATA = savedApiMode;
    }
}

/**
 * Setup API toggle functionality with localStorage persistence
 */
function setupApiToggle() {
    const mockToggle = document.getElementById('mockApiToggle');
    if (mockToggle) {
        // Pentru dev tunnels, forțează mock data și dezactivează toggle-ul
        if (window.location.hostname.includes('devtunnels.ms')) {
            CONFIG.USE_MOCK_DATA = true;
            mockToggle.checked = true;
            mockToggle.disabled = true;
            mockToggle.parentElement.title = 'Demo Mode este activat automat pentru dev tunnels';
            return;
        }
        
        // Load saved API mode preference pentru localhost
        const savedApiMode = Storage.load(Storage.KEYS.API_MODE);
        if (savedApiMode !== null) {
            CONFIG.USE_MOCK_DATA = savedApiMode;
        }
        
        mockToggle.checked = CONFIG.USE_MOCK_DATA;
        mockToggle.addEventListener('change', async (e) => {
            CONFIG.USE_MOCK_DATA = e.target.checked;
            
            // Save API mode preference
            Storage.save(Storage.KEYS.API_MODE, CONFIG.USE_MOCK_DATA);
            
            // Clear cart when switching API modes since data sources are different
            AppState.cart = [];
            Storage.remove(Storage.KEYS.CART);
            updateCartInterface();
            
            showNotification(
                CONFIG.USE_MOCK_DATA ? 'Trecut la API Mock (Demo) - Coșul a fost resetat' : 'Trecut la API Real - Coșul a fost resetat',
                'success'
            );
            
            // Reîncarcă datele cu noua setare
            try {
                setLoadingState(true);
                await Promise.all([
                    loadUserProfile(),
                    loadProducts(),
                    loadCart()
                ]);
            } catch (error) {
                console.error('Error reloading data:', error);
                showNotification('Eroare la reîncărcarea datelor', 'error');
            } finally {
                setLoadingState(false);
            }
        });
    }
}

/**
 * Setup event listeners including storage management
 */
function setupEventListeners() {
    // Get fresh references to DOM elements
    const navLinks = document.querySelectorAll('.nav-link');
    
    // Navigation
    navLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            switchSection(link.dataset.section);
        });
    });
    
    // Category buttons (with null check)
    const categoryBtns = document.querySelectorAll('.category-btn');
    categoryBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            categoryBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            filterByCategory(btn.dataset.category);
        });
    });
    
    // Search with debouncing
    const debouncedSearch = debounce(filterProducts, 300);
    DOM.searchInput.addEventListener('input', debouncedSearch);
    
    // Checkout button
    DOM.checkoutBtn.addEventListener('click', checkout);
    
    // Modal events
    DOM.closeModal.addEventListener('click', closeProductModal);
    
    window.addEventListener('click', (e) => {
        if (e.target === DOM.productModal) {
            closeProductModal();
        }
        if (e.target === DOM.howItWorksModal) {
            closeHowItWorksModal();
        }
        if (e.target === document.getElementById('set-points-modal')) {
            closeSetPointsModal();
        }
    });
    
    // Clear storage button (for debugging)
    if (DOM.clearStorageBtn) {
        DOM.clearStorageBtn.addEventListener('click', () => {
            if (confirm('Sigur vrei să ștergi toate datele locale? Coșul va fi golit.')) {
                Storage.clearAll();
                
                // Reset application state
                AppState.cart = [];
                AppState.currentCategory = '';
                CONFIG.USE_MOCK_DATA = true;
                
                // Update UI
                updateCartInterface();
                location.reload(); // Full reload for clean state
            }
        });
    }
    
    // Keyboard navigation for modal
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            if (DOM.productModal.style.display === 'block') {
                closeProductModal();
            }
            if (DOM.howItWorksModal.style.display === 'block') {
                closeHowItWorksModal();
            }
            if (document.getElementById('set-points-modal').style.display === 'block') {
                closeSetPointsModal();
            }
        }
    });
    
    // Handle network status
    window.addEventListener('online', () => {
        showNotification('Conexiunea a fost restabilită', 'success');
        // Retry failed requests if any
        if (AppState.products.length === 0) {
            loadProducts();
        }
    });
    
    window.addEventListener('offline', () => {
        showNotification('Nu există conexiune la internet', 'warning');
    });
    
    // Handle page visibility changes (for potential sync)
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden && AppState.cart.length > 0) {
            // Page became visible again, show storage status
            showStorageStatus('📦 Coșul tău este salvat local', 'success');
        }
    });
    
}

// === APPLICATION STARTUP ===

/**
 * Start application when DOM is ready
 */
document.addEventListener('DOMContentLoaded', async () => {
    setupEventListeners();
    await initializeApp();
    
    // Add some CSS for better error states
    addDynamicStyles();
});

/**
 * Add dynamic CSS styles
 */
function addDynamicStyles() {
    const style = document.createElement('style');
    style.textContent = `
        .empty-state {
            text-align: center;
            padding: 3rem;
            color: #666;
            grid-column: 1 / -1;
        }
        
        .empty-icon {
            font-size: 3rem;
            margin-bottom: 1rem;
        }
        
        .error-state {
            text-align: center;
            padding: 3rem;
            color: #666;
            grid-column: 1 / -1;
        }
        
        .error-icon {
            font-size: 3rem;
            margin-bottom: 1rem;
        }
        
        .notification {
            transition: all 0.3s ease-in-out;
            opacity: 0;
            transform: translateY(-20px);
        }
        
        .notification.show {
            opacity: 1;
            transform: translateY(0);
        }
        
        .notification.info {
            background: #3b82f6;
        }
        
        .notification.warning {
            background: #f59e0b;
        }
        
        .storage-status {
            position: fixed;
            bottom: 20px;
            right: 20px;
            background: rgba(0, 0, 0, 0.8);
            color: white;
            padding: 0.5rem 1rem;
            border-radius: 20px;
            font-size: 0.8rem;
            z-index: 1000;
            opacity: 0;
            transform: translateX(20px);
            transition: all 0.3s ease-in-out;
        }
        
        .storage-status.show {
            opacity: 1;
            transform: translateX(0);
        }
        
        .storage-status.success {
            background: rgba(34, 197, 94, 0.9);
        }
        
        .storage-status.error {
            background: rgba(239, 68, 68, 0.9);
        }
        
        .clear-storage-btn {
            position: fixed;
            bottom: 70px;
            right: 20px;
            background: #ef4444;
            color: white;
            border: none;
            padding: 0.5rem 1rem;
            border-radius: 8px;
            font-size: 0.8rem;
            cursor: pointer;
            z-index: 1000;
            opacity: 0.7;
            transition: opacity 0.3s ease;
        }
        
        .clear-storage-btn:hover {
            opacity: 1;
        }
        
        .history-summary {
            background: #f8fafc;
            padding: 1.5rem;
            border-radius: 12px;
            margin-bottom: 1.5rem;
            border: 1px solid #e2e8f0;
        }
        
        .history-summary h3 {
            color: #374151;
            margin: 0 0 1rem 0;
            font-size: 1.2rem;
        }
        
        .summary-stats {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 1rem;
        }
        
        .summary-stat {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0.75rem;
            background: white;
            border-radius: 8px;
            border: 1px solid #e5e7eb;
        }
        
        .stat-label {
            color: #6b7280;
            font-weight: 500;
        }
        
        .stat-value {
            color: #7c3aed;
            font-weight: 600;
        }
        
        .history-details {
            margin-top: 1rem;
        }
        
        .history-info {
            display: flex;
            justify-content: space-between;
            margin-bottom: 0.5rem;
            padding: 0.5rem 0;
        }
        
        .history-info:not(:last-child) {
            border-bottom: 1px solid #f3f4f6;
        }
        
        .status-completed {
            color: #10b981;
            font-weight: 600;
        }
        
        .status-pending {
            color: #f59e0b;
            font-weight: 600;
        }
        
        .status-cancelled {
            color: #ef4444;
            font-weight: 600;
        }
        
        .status-unknown {
            color: #6b7280;
            font-style: italic;
        }
        
        .how-it-works-content {
            max-width: 600px;
            margin: 0 auto;
        }
        
        .how-it-works-content h2 {
            text-align: center;
            color: #7c3aed;
            margin-bottom: 2rem;
        }
        
        .how-it-works-steps {
            margin-bottom: 2rem;
        }
        
        .step {
            display: flex;
            align-items: flex-start;
            margin-bottom: 1.5rem;
            padding: 1rem;
            background: #f8fafc;
            border-radius: 12px;
            border-left: 4px solid #7c3aed;
        }
        
        .step-icon {
            font-size: 2rem;
            margin-right: 1rem;
            flex-shrink: 0;
        }
        
        .step-content h3 {
            color: #374151;
            margin: 0 0 0.5rem 0;
            font-size: 1.1rem;
        }
        
        .step-content p {
            color: #6b7280;
            margin: 0;
            line-height: 1.6;
        }
        
        .tips-section {
            background: #fffbeb;
            padding: 1.5rem;
            border-radius: 12px;
            border: 1px solid #fbbf24;
            margin-bottom: 2rem;
        }
        
        .tips-section h3 {
            color: #92400e;
            margin: 0 0 1rem 0;
        }
        
        .tips-section ul {
            margin: 0;
            padding-left: 1.5rem;
            color: #78350f;
        }
        
        .tips-section li {
            margin-bottom: 0.5rem;
            line-height: 1.5;
        }
        
        .tips-section strong {
            color: #92400e;
        }
    `;
    document.head.appendChild(style);
}

// === GLOBAL FUNCTIONS (for inline event handlers) ===

// Make functions globally available for onclick handlers
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

// Error boundary for global error handling
window.addEventListener('error', (event) => {
    console.error('🚨 Global error:', event.error);
    showNotification('A apărut o eroare neașteptată', 'error');
});

window.addEventListener('unhandledrejection', (event) => {
    console.error('🚨 Unhandled promise rejection:', event.reason);
    showNotification('A apărut o eroare de rețea', 'error');
    event.preventDefault();
});

// Application loaded and ready
