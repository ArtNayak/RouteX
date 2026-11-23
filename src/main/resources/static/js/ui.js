/**
 * ui.js — DOM utilities: page routing, toasts, modal, loading states.
 * Keeps all "infrastructure" UI logic separate from feature logic.
 */

const UI = (() => {

  // ----------------------------------------------------------------
  //  Page Router
  // ----------------------------------------------------------------

  function showPage(name) {
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    const target = document.getElementById('page-' + name);
    if (target) target.classList.add('active');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  // ----------------------------------------------------------------
  //  Toast Notifications
  // ----------------------------------------------------------------

  const ICONS = { success: '✓', error: '✕', info: 'ℹ' };

  function toast(message, type = 'info', duration = 3500) {
    const container = document.getElementById('toast-container');
    const el = document.createElement('div');
    el.className = `toast ${type}`;
    el.innerHTML = `<span>${ICONS[type]}</span><span>${message}</span>`;
    container.appendChild(el);
    setTimeout(() => {
      el.style.animation = 'none';
      el.style.opacity = '0';
      el.style.transform = 'translateX(100px)';
      el.style.transition = 'all 0.3s ease';
      setTimeout(() => el.remove(), 300);
    }, duration);
  }

  // ----------------------------------------------------------------
  //  Auth Modal
  // ----------------------------------------------------------------

  function openAuthModal(tab = 'login') {
    document.getElementById('auth-modal').classList.add('open');
    switchAuthTab(tab);
  }

  function closeAuthModal() {
    document.getElementById('auth-modal').classList.remove('open');
  }

  function switchAuthTab(tab) {
    ['login', 'register'].forEach(t => {
      document.getElementById('tab-' + t).classList.toggle('active', t === tab);
      document.getElementById('form-' + t).style.display = t === tab ? '' : 'none';
    });
  }

  // ----------------------------------------------------------------
  //  Loading States
  // ----------------------------------------------------------------

  function loadingHTML() {
    return `<div class="loading-dots">
      <div class="loading-dot"></div>
      <div class="loading-dot"></div>
      <div class="loading-dot"></div>
    </div>`;
  }

  function emptyState(icon, title, subtitle = '', actionHTML = '') {
    return `<div class="empty-state">
      <div class="empty-icon">${icon}</div>
      <div class="empty-title">${title}</div>
      ${subtitle ? `<p>${subtitle}</p>` : ''}
      ${actionHTML}
    </div>`;
  }

  // ----------------------------------------------------------------
  //  Navbar Update
  // ----------------------------------------------------------------

  function updateNav(user) {
    const loginBtn  = document.getElementById('nav-login');
    const logoutBtn = document.getElementById('nav-logout');
    const userPill  = document.getElementById('nav-user');
    const adminBtn  = document.getElementById('nav-admin');

    if (user) {
      loginBtn.style.display  = 'none';
      logoutBtn.style.display = '';
      userPill.style.display  = '';
      userPill.textContent    = user.name;
      adminBtn.style.display  = user.role === 'ADMIN' ? '' : 'none';
    } else {
      loginBtn.style.display  = '';
      logoutBtn.style.display = 'none';
      userPill.style.display  = 'none';
      adminBtn.style.display  = 'none';
    }
  }

  // ----------------------------------------------------------------
  //  Utility: Format Date
  // ----------------------------------------------------------------

  function formatDate(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString('en-IN', {
      weekday: 'short', day: 'numeric', month: 'short', year: 'numeric'
    });
  }

  // ----------------------------------------------------------------
  //  Utility: Barcode visual
  // ----------------------------------------------------------------

  function barcodeHTML(seed) {
    const rng = (s) => { let x = Math.sin(s) * 10000; return x - Math.floor(x); };
    return Array.from({ length: 28 }, (_, i) =>
      `<div class="barcode-bar" style="height:${Math.floor(14 + rng(seed + i) * 16)}px"></div>`
    ).join('');
  }

  // ----------------------------------------------------------------
  //  Public API
  // ----------------------------------------------------------------

  return {
    showPage, toast, openAuthModal, closeAuthModal, switchAuthTab,
    loadingHTML, emptyState, updateNav, formatDate, barcodeHTML
  };

})();
