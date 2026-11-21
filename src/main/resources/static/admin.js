'use strict';

// Painel do administrador: resumo, produtos (criar, editar, desativar/reativar) e pedidos.
(() => {
  const { api, me, toast, escapeHtml, money } = window.AuraSession;
  const dialog = document.getElementById('product-dialog');
  const form = document.getElementById('product-form');
  const error = form.querySelector('.form-error');
  const dateFormat = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
  let products = [];
  let editing = null;

  async function loadSummary() {
    const s = await api('/api/admin/summary');
    const stats = document.getElementById('stats');
    stats.innerHTML = [
      [s.activeProducts, 'produtos na loja'], [s.lowStock, 'com estoque baixo (≤ 5)'],
      [s.orders, 'pedidos'], [money(s.revenue), 'em pedidos (demo)'], [s.customers, 'clientes cadastrados'],
    ].map(([value, label]) => `<div class="stat"><strong>${escapeHtml(value)}</strong><span>${label}</span></div>`).join('');
    stats.setAttribute('aria-busy', 'false');
  }

  async function loadProducts() {
    products = await api('/api/admin/products');
    document.getElementById('product-rows').innerHTML = products.map((p) => `
      <tr data-product="${p.id}">
        <td><img src="${escapeHtml(p.image)}" alt="" width="44" height="44"></td>
        <td><strong>${escapeHtml(p.name)}</strong><br><small>${escapeHtml(p.color)}${p.badge ? ` · ${escapeHtml(p.badge)}` : ''}</small></td>
        <td>${escapeHtml(p.category)}</td>
        <td>${money(p.price)}</td>
        <td>${p.stock}${p.stock <= 5 ? ' <span class="tag off">baixo</span>' : ''}</td>
        <td>${p.active ? '<span class="tag">Na loja</span>' : '<span class="tag off">Desativado</span>'}</td>
        <td><div class="row-actions">
          <button class="small-button" type="button" data-edit="${p.id}" aria-label="Editar ${escapeHtml(p.name)}">Editar</button>
          ${p.active
            ? `<button class="small-button danger" type="button" data-deactivate="${p.id}" aria-label="Desativar ${escapeHtml(p.name)}">Desativar</button>`
            : `<button class="small-button" type="button" data-reactivate="${p.id}" aria-label="Reativar ${escapeHtml(p.name)}">Reativar</button>`}
        </div></td>
      </tr>`).join('');
  }

  async function loadOrders() {
    const orders = await api('/api/admin/orders');
    document.getElementById('order-rows').innerHTML = orders.length ? orders.map((o) => `
      <tr><td>${dateFormat.format(new Date(o.createdAt))}</td><td><code>${escapeHtml(o.id.slice(0, 8))}</code></td>
      <td>${escapeHtml(o.customerName)}<br><small>${escapeHtml(o.email)}</small></td><td>${o.units}</td><td>${money(o.total)}</td></tr>`).join('')
      : '<tr><td colspan="5" class="empty-cell">Nenhum pedido ainda.</td></tr>';
  }

  const refresh = () => Promise.all([loadSummary(), loadProducts(), loadOrders()]).catch((failure) => toast(failure.message));

  function openForm(product) {
    editing = product;
    form.reset();
    error.hidden = true;
    document.getElementById('product-form-title').innerHTML = `${product ? 'Editar produto' : 'Novo produto'}<span>.</span>`;
    if (product) {
      for (const field of ['name', 'category', 'color', 'description', 'price', 'originalPrice', 'stock', 'badge', 'image']) {
        form.elements[field].value = product[field] ?? '';
      }
      form.elements.active.checked = product.active;
    }
    dialog.showModal();
    form.elements.name.focus();
  }

  // Só os campos que a API aceita (ela rejeita campos desconhecidos, como "id").
  const payload = (p, active) => ({
    name: p.name, category: p.category, color: p.color, description: p.description, price: p.price,
    originalPrice: p.originalPrice, stock: p.stock, badge: p.badge || '', image: p.image, active,
  });

  async function setActive(id, active) {
    const product = products.find((item) => item.id === id);
    await api(`/api/admin/products/${id}`, active ? { method: 'PUT', body: payload(product, true) } : { method: 'DELETE' });
    toast(active ? `${product.name} voltou para a loja.` : `${product.name} foi desativado. Você pode reativá-lo depois.`);
    refresh();
  }

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    error.hidden = true;
    if (!form.reportValidity()) return;
    const data = Object.fromEntries(new FormData(form));
    const body = {
      name: data.name, category: data.category, color: data.color, description: data.description,
      price: Number(data.price), originalPrice: data.originalPrice ? Number(data.originalPrice) : null,
      stock: Number(data.stock), badge: data.badge, image: data.image, active: form.elements.active.checked,
    };
    try {
      const saved = await api(editing ? `/api/admin/products/${editing.id}` : '/api/admin/products', { method: editing ? 'PUT' : 'POST', body });
      dialog.close();
      toast(editing ? `${saved.name} atualizado.` : `${saved.name} cadastrado.`);
      refresh();
    } catch (failure) {
      error.textContent = failure.message;
      error.hidden = false;
    }
  });

  document.getElementById('new-product').addEventListener('click', () => openForm(null));
  document.addEventListener('click', (event) => {
    const button = event.target.closest('button');
    if (!button) return;
    if (button.hasAttribute('data-close')) button.closest('dialog')?.close();
    if (button.dataset.edit) openForm(products.find((item) => item.id === Number(button.dataset.edit)));
    if (button.dataset.deactivate) setActive(Number(button.dataset.deactivate), false).catch((failure) => toast(failure.message));
    if (button.dataset.reactivate) setActive(Number(button.dataset.reactivate), true).catch((failure) => toast(failure.message));
  });

  me().then((user) => {
    if (!user || user.role !== 'ADMIN') return location.replace('/entrar?next=%2Fadmin');
    refresh().then(() => { if (location.hash) document.getElementById(location.hash.slice(1))?.scrollIntoView(); });
  });
})();
