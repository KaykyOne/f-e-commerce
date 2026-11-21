'use strict';

// Sessão compartilhada por todas as páginas: quem está logado, chamadas à API com CSRF,
// link de conta no cabeçalho, sair e avisos (toast).
(() => {
  const csrfToken = () => decodeURIComponent((document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/) || [])[1] || '');

  async function api(path, { method = 'GET', body } = {}) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (method !== 'GET') headers['X-XSRF-TOKEN'] = csrfToken();
    const response = await fetch(path, { method, headers, credentials: 'same-origin', body: body === undefined ? undefined : JSON.stringify(body) });
    const data = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) {
      const error = new Error((data && data.message) || 'Não foi possível concluir a operação.');
      error.status = response.status;
      throw error;
    }
    return data;
  }

  let current;
  function me(refresh = false) {
    if (!current || refresh) current = api('/api/auth/me').then((session) => (session.authenticated ? session.user : null)).catch(() => null);
    return current;
  }

  async function logout(destination = '/') {
    try { await api('/api/auth/logout', { method: 'POST' }); } catch { /* a sessão pode já ter expirado */ }
    location.assign(destination);
  }

  // Só aceita destinos internos ("/conta"), nunca "//site.com" ou "https://...".
  function safeNext(value, fallback) {
    return typeof value === 'string' && /^\/(?![/\\])/.test(value) ? value : fallback;
  }

  const home = (user) => (user && user.role === 'ADMIN' ? '/admin' : '/conta');

  async function renderAccountLink() {
    const link = document.getElementById('account-link');
    if (!link) return;
    const user = await me();
    const [label, href] = !user ? ['Entrar', '/entrar'] : user.role === 'ADMIN' ? ['Painel admin', '/admin'] : ['Minha conta', '/conta'];
    link.querySelector('.account-label').textContent = label;
    link.setAttribute('aria-label', label);
    link.href = href;
    link.dataset.state = user ? user.role.toLowerCase() : 'visitante';
  }

  let toastTimer;
  function toast(message) {
    const element = document.getElementById('toast');
    if (!element) return;
    clearTimeout(toastTimer);
    element.textContent = message;
    element.classList.add('visible');
    toastTimer = setTimeout(() => element.classList.remove('visible'), 3200);
  }

  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
  const money = (value) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);

  window.AuraSession = { api, me, logout, safeNext, home, renderAccountLink, toast, escapeHtml, money };
  document.addEventListener('DOMContentLoaded', () => {
    renderAccountLink();
    document.querySelectorAll('[data-logout]').forEach((button) => button.addEventListener('click', () => logout(button.dataset.logout || '/')));
  });
})();
