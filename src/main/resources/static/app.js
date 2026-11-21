'use strict';

const $ = (selector) => document.querySelector(selector);
const money = (value) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
const escapeHtml = (value) => String(value).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
const normalize = (value) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
const CART_KEY = 'aura-cart-v1';
let products = [];
let category = 'Todos';
let cart = readCart();
let toastTimer;
let ordering = false;
let catalogReady = false;
let hashRestored = false;
// Usuário logado (null = visitante). Vem do session.js; ausente nos testes de Node.
let session = null;
const auth = () => window.AuraSession;

function readCart() {
  try {
    const saved = JSON.parse(localStorage.getItem(CART_KEY) || '{}');
    if (!saved || typeof saved !== 'object' || Array.isArray(saved)) return {};
    return Object.fromEntries(Object.entries(saved).filter(([id, count]) => /^\d+$/.test(id) && Number.isSafeInteger(count) && count > 0 && count <= 99));
  } catch { return {}; }
}

function saveCart() {
  try { localStorage.setItem(CART_KEY, JSON.stringify(cart)); } catch { /* Browsers may disable storage; the current session still works. */ }
  updateCount();
}

function updateCount() {
  const count = Object.values(cart).reduce((sum, quantity) => sum + quantity, 0);
  $('#cart-count').textContent = count;
  $('#open-cart').setAttribute('aria-label', `Abrir carrinho, ${count} ${count === 1 ? 'item' : 'itens'}`);
}

function reconcileCart() {
  let changed = false;
  for (const [id, quantity] of Object.entries(cart)) {
    const product = products.find((item) => item.id === Number(id));
    if (!product || product.stock === 0) { delete cart[id]; changed = true; }
    else if (quantity > product.stock) { cart[id] = Math.min(product.stock, 99); changed = true; }
  }
  saveCart();
  return changed;
}

async function loadProducts() {
  $('#products').setAttribute('aria-busy', 'true');
  $('#products').innerHTML = '<div class="skeleton"></div>'.repeat(4);
  $('#catalog-state').hidden = true;
  try {
    const response = await fetch('/api/products', { headers: { Accept: 'application/json' } });
    if (!response.ok) throw new Error('Catálogo indisponível');
    products = await response.json();
    catalogReady = true;
    const changed = reconcileCart();
    if (changed) toast('Sua sacola foi atualizada conforme o estoque disponível.');
    $('[data-category="Todos"] span').textContent = String(products.length).padStart(2, '0');
    renderProducts();
    restoreHashTarget();
  } catch {
    catalogReady = false;
    $('#products').innerHTML = '';
    $('#result-count').textContent = 'Não foi possível carregar os produtos';
    showState('Uma pequena pausa na conexão.', 'Não conseguimos abrir o catálogo. Tente novamente em instantes.', true);
  } finally {
    $('#products').setAttribute('aria-busy', 'false');
    if ($('#cart-dialog').open) renderCart();
  }
}

// Links diretos como /#sobre: o catálogo chega depois do primeiro scroll do navegador e
// empurra as seções para baixo. Após a primeira renderização, volta à seção pedida.
function restoreHashTarget() {
  if (hashRestored) return;
  hashRestored = true;
  if (location.hash === '#sacola') { renderCart(); openDialog('#cart-dialog'); return; }
  if (location.hash.length < 2) return;
  try { document.getElementById(decodeURIComponent(location.hash.slice(1)))?.scrollIntoView(); } catch { /* hash inválido: mantém a posição */ }
}

function showState(title, message, retry = false) {
  $('#catalog-state').hidden = false;
  $('#state-title').textContent = title;
  $('#state-message').textContent = message;
  $('#retry').hidden = !retry;
  $('#clear-filters').hidden = retry || products.length === 0;
}

function renderProducts() {
  if (!catalogReady) return;
  const query = normalize($('#search').value.trim());
  const filtered = products.filter((product) => (category === 'Todos' || product.category === category)
    && normalize(`${product.name} ${product.category} ${product.description}`).includes(query));
  const sort = $('#sort').value;
  if (sort === 'price-asc') filtered.sort((a, b) => a.price - b.price);
  if (sort === 'price-desc') filtered.sort((a, b) => b.price - a.price);
  if (sort === 'name') filtered.sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'));
  $('#result-count').textContent = `${filtered.length} ${filtered.length === 1 ? 'essencial para o seu dia' : 'essenciais para o seu dia'}`;
  $('#catalog-state').hidden = true;
  $('#products').innerHTML = filtered.map((product) => {
    const name = escapeHtml(product.name);
    const colors = { Areia: '#c7bba3', 'Verde sálvia': '#87977b', Preto: '#363c38', 'Off-white': '#d9d6c7', Branco: '#fff' };
    return `<article class="product-card">
      <button class="product-image" data-detail="${product.id}" aria-label="Ver detalhes de ${name}">
        ${product.badge ? `<span class="badge ${product.badge === 'Oferta' ? 'offer' : ''}">${escapeHtml(product.badge)}</span>` : ''}
        <img src="${escapeHtml(product.image)}" alt="${name} — ${escapeHtml(product.color)}" width="300" height="300" loading="lazy">
      </button>
      <div class="product-info"><div class="product-meta"><span>${escapeHtml(product.category)}</span><span class="color"><i style="background:${colors[product.color] || '#aaa'}"></i>${escapeHtml(product.color)}</span></div>
        <button class="product-name" data-detail="${product.id}">${name}</button>
        <div class="product-bottom"><div><span class="price">${money(product.price)}</span>${product.originalPrice ? `<span class="old-price">${money(product.originalPrice)}</span>` : ''}</div><button class="add-button" data-add="${product.id}" aria-label="Adicionar ${name} à sacola" ${product.stock === 0 ? 'disabled' : ''}>+</button></div>
        <p class="installments">${product.stock > 0 ? 'Design para acompanhar você' : 'Temporariamente esgotado'}</p>
      </div></article>`;
  }).join('');
  if (!filtered.length) showState('Seu próximo favorito está por aqui.', 'Tente outra busca ou explore todas as categorias.');
}

function setCategory(value) {
  category = value;
  document.querySelectorAll('[data-category]').forEach((button) => {
    const active = button.dataset.category === value;
    button.classList.toggle('active', active);
    button.setAttribute('aria-pressed', String(active));
  });
  renderProducts();
}

function toast(message) {
  clearTimeout(toastTimer);
  $('#toast').textContent = message;
  $('#toast').classList.add('visible');
  toastTimer = setTimeout(() => $('#toast').classList.remove('visible'), 3200);
}

function addToCart(id) {
  const product = products.find((item) => item.id === id);
  if (!product) return;
  const count = cart[id] || 0;
  if (count >= Math.min(product.stock, 99)) return toast('Você já adicionou o limite disponível deste produto.');
  cart[id] = count + 1;
  saveCart();
  toast(`${product.name} adicionado à sacola.`);
  if ($('#cart-dialog').open) renderCart();
}

function cartLines() {
  return Object.entries(cart).map(([id, quantity]) => ({ product: products.find((item) => item.id === Number(id)), quantity })).filter((line) => line.product);
}

function totals() {
  // Work in integer cents. The backend remains authoritative at checkout.
  const subtotal = cartLines().reduce((sum, line) => sum + Math.round(line.product.price * 100) * line.quantity, 0);
  const shipping = subtotal === 0 || subtotal >= 39900 ? 0 : 1990;
  return { subtotal: subtotal / 100, shipping: shipping / 100, total: (subtotal + shipping) / 100 };
}

function summaryHtml(values) {
  return `<div class="summary-line"><span>Subtotal</span><span>${money(values.subtotal)}</span></div>
    <div class="summary-line"><span>Entrega</span><span>${values.shipping ? money(values.shipping) : 'Grátis'}</span></div>
    <div class="summary-line total"><span>Total</span><span>${money(values.total)}</span></div>`;
}

function renderCart() {
  const lines = cartLines();
  const values = totals();
  if (!catalogReady) {
    $('#shipping-progress').innerHTML = '';
    $('#cart-items').innerHTML = '<div class="empty-state"><h3>Aguardando o catálogo.</h3><p>Carregue os produtos para consultar sua sacola.</p></div>';
    $('#cart-summary').innerHTML = '';
    return;
  }
  if (!lines.length) {
    $('#shipping-progress').innerHTML = '';
    $('#cart-items').innerHTML = '<div class="empty-state"><span aria-hidden="true">✳</span><h3>Espaço para boas escolhas.</h3><p>Sua sacola ainda está vazia.<br>Que tal encontrar seu próximo favorito?</p><button class="button secondary" data-close>Explorar a coleção ↗</button></div>';
    $('#cart-summary').innerHTML = '';
    return;
  }
  $('#shipping-progress').innerHTML = `${values.subtotal >= 399 ? 'Boa escolha! Seu pedido ganhou <strong>frete grátis.</strong>' : `Faltam <strong>${money(399 - values.subtotal)}</strong> para ganhar frete grátis.`}<progress max="399" value="${Math.min(values.subtotal, 399)}" aria-label="Progresso para frete grátis"></progress>`;
  $('#cart-items').innerHTML = lines.map(({ product, quantity }) => `<div class="cart-item">
    <img src="${escapeHtml(product.image)}" alt="" width="76" height="85"><div><h3>${escapeHtml(product.name)}</h3><p>${money(product.price)}</p>
    <div class="quantity"><button data-quantity="${product.id}" data-change="-1" aria-label="Diminuir quantidade de ${escapeHtml(product.name)}">−</button><span aria-label="Quantidade">${quantity}</span><button data-quantity="${product.id}" data-change="1" aria-label="Aumentar quantidade de ${escapeHtml(product.name)}" ${quantity >= Math.min(product.stock, 99) ? 'disabled' : ''}>+</button></div></div>
    <button class="remove-button" data-remove="${product.id}" aria-label="Remover ${escapeHtml(product.name)}">×</button></div>`).join('');
  $('#cart-summary').innerHTML = summaryHtml(values) + checkoutAction();
}

// Visitante precisa entrar; administrador não compra; cliente segue para o checkout.
function checkoutAction() {
  if (!session) return `<a id="login-to-checkout" class="button primary full-width" href="/entrar?next=${encodeURIComponent('/#sacola')}">Entrar para finalizar <span aria-hidden="true">↗</span></a><p class="summary-note">Entre ou crie sua conta. Sua sacola fica guardada.</p>`;
  if (session.role !== 'CUSTOMER') return '<p class="summary-note admin-note">Você está como administrador. Entre com uma conta de cliente para comprar.</p>';
  return '<button id="go-checkout" class="button primary full-width">Ir para o checkout <span aria-hidden="true">↗</span></button><p class="summary-note">Checkout demonstrativo · Sem cobrança</p>';
}

function prefillCheckout() {
  const form = $('#checkout-form');
  $('#checkout-email').textContent = session ? session.email : '';
  for (const [field, value] of [['name', session?.name], ['address', session?.address], ['city', session?.city], ['zipCode', session?.zipCode]]) {
    if (value && !form.elements[field].value) form.elements[field].value = value;
  }
}

function openDialog(selector) {
  document.querySelectorAll('dialog[open]').forEach((dialog) => dialog.close());
  $(selector).showModal();
}

function showDetail(id) {
  const product = products.find((item) => item.id === id);
  if (!product) return;
  $('#product-detail').innerHTML = `<div class="detail-layout"><img src="${escapeHtml(product.image)}" alt="${escapeHtml(product.name)}" width="300" height="300"><div><p class="eyebrow">${escapeHtml(product.category)} · ${escapeHtml(product.color)}</p><h2 id="detail-name">${escapeHtml(product.name)}</h2><p>${escapeHtml(product.description)}</p><span class="price">${money(product.price)}</span>${product.originalPrice ? `<span class="old-price">${money(product.originalPrice)}</span>` : ''}<small>${product.stock ? `${product.stock} unidades disponíveis` : 'Temporariamente esgotado'}</small><button class="button primary full-width" data-add="${id}" ${product.stock === 0 ? 'disabled' : ''}>Adicionar à sacola <span aria-hidden="true">+</span></button></div></div>`;
  openDialog('#product-dialog');
}

document.addEventListener('click', (event) => {
  const button = event.target.closest('button');
  if (!button || button.disabled) return;
  if (button.hasAttribute('data-close')) button.closest('dialog').close();
  if (button.dataset.add) addToCart(Number(button.dataset.add));
  if (button.dataset.detail) showDetail(Number(button.dataset.detail));
  if (button.dataset.category) setCategory(button.dataset.category);
  if (button.dataset.remove) { delete cart[button.dataset.remove]; saveCart(); renderCart(); }
  if (button.dataset.quantity) {
    const id = button.dataset.quantity;
    const product = products.find((item) => item.id === Number(id));
    const next = (cart[id] || 0) + Number(button.dataset.change);
    if (next <= 0) delete cart[id];
    else if (product && next <= Math.min(product.stock, 99)) cart[id] = next;
    saveCart(); renderCart();
  }
  if (button.id === 'go-checkout') {
    $('#checkout-total').innerHTML = summaryHtml(totals());
    $('#checkout-error').hidden = true;
    prefillCheckout();
    openDialog('#checkout-dialog');
  }
});

$('#open-cart').addEventListener('click', () => { renderCart(); openDialog('#cart-dialog'); });
$('#back-to-cart').addEventListener('click', () => { renderCart(); openDialog('#cart-dialog'); });
$('#search').addEventListener('input', renderProducts);
$('#sort').addEventListener('change', renderProducts);
$('#retry').addEventListener('click', loadProducts);
$('#clear-filters').addEventListener('click', () => { $('#search').value = ''; setCategory('Todos'); });
document.querySelectorAll('[data-nav-category]').forEach((link) => link.addEventListener('click', () => { $('#search').value = ''; setCategory(link.dataset.navCategory); }));
document.querySelectorAll('dialog').forEach((dialog) => dialog.addEventListener('click', (event) => {
  const rect = dialog.getBoundingClientRect();
  if (event.target === dialog && (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom)) {
    if (!ordering) dialog.close();
  }
}));

$('#checkout-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  if (ordering || !$('#checkout-form').reportValidity()) return;
  if (!cartLines().length) return toast('Adicione um produto à sacola para continuar.');
  ordering = true;
  const submit = $('#submit-order');
  submit.disabled = true;
  submit.textContent = 'Confirmando seu pedido…';
  $('#checkout-error').hidden = true;
  // Freeze the form and modal while the server commits the order.
  $('#checkout-dialog').querySelectorAll('input, button').forEach((element) => { element.disabled = true; });
  const form = $('#checkout-form');
  const data = Object.fromEntries(Array.from(form.elements).filter((element) => element.name).map((element) => [element.name, element.value.trim()]));
  data.items = Object.entries(cart).map(([id, quantity]) => ({ productId: Number(id), quantity }));
  try {
    const receipt = await auth().api('/api/orders', { method: 'POST', body: data });
    cart = {};
    saveCart();
    form.reset();
    $('#receipt').innerHTML = `<div class="receipt-box"><strong>Pedido registrado</strong><small>${escapeHtml(receipt.id)}</small><span>Total: <strong>${money(receipt.total)}</strong></span></div>`;
    openDialog('#success-dialog');
    await loadProducts();
  } catch (error) {
    if (error.status === 401 || error.status === 403) { location.assign(`/entrar?next=${encodeURIComponent('/#sacola')}`); return; }
    $('#checkout-error').textContent = error instanceof TypeError ? 'A conexão foi interrompida. O pedido pode ter sido registrado; confira o servidor antes de tentar novamente.' : error.message;
    $('#checkout-error').hidden = false;
    await loadProducts();
    $('#checkout-total').innerHTML = summaryHtml(totals());
  } finally {
    ordering = false;
    $('#checkout-dialog').querySelectorAll('input, button').forEach((element) => { element.disabled = false; });
    submit.innerHTML = 'Confirmar pedido demonstrativo <span aria-hidden="true">↗</span>';
  }
});
$('#checkout-dialog').addEventListener('cancel', (event) => { if (ordering) event.preventDefault(); });

window.addEventListener('storage', (event) => {
  if (event.key !== CART_KEY || ordering) return;
  cart = readCart();
  if (catalogReady) reconcileCart();
  updateCount();
  if ($('#cart-dialog').open) renderCart();
  if ($('#checkout-dialog').open) $('#checkout-total').innerHTML = summaryHtml(totals());
});

updateCount();
loadProducts();
auth()?.me().then((user) => {
  session = user;
  if ($('#cart-dialog').open) renderCart();
});
