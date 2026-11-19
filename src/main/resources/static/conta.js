'use strict';

// Área do cliente: perfil, histórico de pedidos e troca de senha.
(() => {
  const { api, me, toast, escapeHtml, money } = window.AuraSession;
  const profileForm = document.getElementById('profile-form');
  const passwordForm = document.getElementById('password-form');
  const dateFormat = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

  const showError = (form, message) => {
    const error = form.querySelector('.form-error');
    error.textContent = message || '';
    error.hidden = !message;
  };

  function fillProfile(user) {
    document.getElementById('greeting').innerHTML = `Olá, ${escapeHtml(user.name.split(' ')[0])}<span>.</span>`;
    for (const field of ['name', 'email', 'phone', 'address', 'city', 'zipCode']) profileForm.elements[field].value = user[field] || '';
  }

  async function loadOrders() {
    const container = document.getElementById('orders');
    try {
      const orders = await api('/api/account/orders');
      container.innerHTML = orders.length ? orders.map((order) => `
        <article class="order-card">
          <div class="order-head"><strong>Pedido ${escapeHtml(order.id.slice(0, 8))}</strong><span class="tag">Confirmado (demo)</span></div>
          <p class="order-meta">${dateFormat.format(new Date(order.createdAt))} · ${order.items.reduce((sum, item) => sum + item.quantity, 0)} item(ns)</p>
          <ul class="order-items">${order.items.map((item) => `<li><span>${item.quantity}× ${escapeHtml(item.name)}</span><span>${money(item.unitPrice * item.quantity)}</span></li>`).join('')}</ul>
          <div class="order-total"><span>Frete ${order.shipping > 0 ? money(order.shipping) : 'grátis'}</span><strong>Total ${money(order.total)}</strong></div>
        </article>`).join('')
        : '<div class="empty-state"><span aria-hidden="true">✳</span><h3>Nenhum pedido ainda.</h3><p>Que tal encontrar seu próximo favorito?</p><a class="button secondary" href="/#catalogo">Explorar a coleção ↗</a></div>';
    } catch (error) {
      container.innerHTML = `<p class="form-error">${escapeHtml(error.message)}</p>`;
    } finally {
      container.setAttribute('aria-busy', 'false');
    }
  }

  profileForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    showError(profileForm);
    if (!profileForm.reportValidity()) return;
    const data = Object.fromEntries(new FormData(profileForm));
    delete data.email;
    try {
      fillProfile(await api('/api/account', { method: 'PUT', body: data }));
      me(true);
      toast('Perfil atualizado.');
    } catch (error) { showError(profileForm, error.message); }
  });

  passwordForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    showError(passwordForm);
    if (!passwordForm.reportValidity()) return;
    const data = Object.fromEntries(new FormData(passwordForm));
    if (data.newPassword !== data.confirm) return showError(passwordForm, 'As senhas não conferem.');
    try {
      await api('/api/account/password', { method: 'PUT', body: { currentPassword: data.currentPassword, newPassword: data.newPassword } });
      passwordForm.reset();
      toast('Senha alterada.');
    } catch (error) { showError(passwordForm, error.message); }
  });

  me().then((user) => {
    // A página já é protegida pelo servidor; isto cobre uma sessão que expirou com a aba aberta.
    if (!user) return location.replace(`/entrar?next=${encodeURIComponent('/conta')}`);
    fillProfile(user);
    loadOrders();
    if (location.hash) document.getElementById(location.hash.slice(1))?.scrollIntoView();
  });
})();
