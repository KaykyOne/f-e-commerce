// No external dependencies. Exercises the actual browser script with a minimal DOM adapter.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const vm = require('node:vm');

function app(saved = '{}') {
  const elements = new Map();
  const storage = new Map([['aura-cart-v1', saved]]);
  const node = (selector) => {
    if (!elements.has(selector)) elements.set(selector, {
      textContent: '', innerHTML: '', value: '', hidden: false, open: false,
      setAttribute() {}, addEventListener() {}, classList: { add() {}, remove() {}, toggle() {} },
    });
    return elements.get(selector);
  };
  const context = vm.createContext({
    document: { querySelector: node, querySelectorAll: () => [], addEventListener() {} },
    window: { addEventListener() {} },
    localStorage: { getItem: (key) => storage.get(key), setItem: (key, value) => storage.set(key, value) },
    fetch: () => new Promise(() => {}),
    setTimeout: () => 0, clearTimeout() {}, Intl, console,
  });
  vm.runInContext(readFileSync(resolve(__dirname, '../src/main/resources/static/app.js'), 'utf8'), context);
  const run = (code) => vm.runInContext(code, context);
  run(`products = [
    {id:1,name:'Fone',category:'Áudio',description:'Som',color:'Areia',image:'/images/headphone.svg',price:349.90,stock:3},
    {id:2,name:'Mouse',category:'Setup',description:'Mouse',color:'Preto',image:'/images/mouse.svg',price:129.90,stock:10}
  ]; catalogReady = true;`);
  return { run, node, storage };
}

test('empty cart does not charge shipping', () => {
  const { run } = app();
  assert.equal(run('totals().total'), 0);
  assert.equal(run('totals().shipping'), 0);
});

test('cart totals use cents and apply the shipping threshold', () => {
  const { run } = app();
  run('addToCart(1)');
  assert.equal(run('totals().subtotal'), 349.9);
  assert.equal(run('totals().total'), 369.8);
  run('addToCart(2)');
  assert.equal(run('totals().shipping'), 0);
  assert.equal(run('totals().total'), 479.8);
  run('products[0].price = 399; cart = {1:1}');
  assert.equal(run('totals().shipping'), 0);
  run('products[0].price = 398.99');
  assert.equal(run('totals().shipping'), 19.9);
});

test('adding an item respects current stock and saves the cart', () => {
  const { run, storage, node } = app();
  run('for(let i=0;i<5;i++) addToCart(1)');
  assert.equal(run('cart[1]'), 3);
  assert.deepEqual(JSON.parse(storage.get('aura-cart-v1')), { 1: 3 });
  assert.equal(node('#cart-count').textContent, 3);
});

test('invalid local storage cannot create negative or fractional quantities', () => {
  for (const stored of ['null', '[]', 'invalid json', '{"1":-2,"2":1.5,"3":100,"nope":2}']) {
    const { run } = app(stored);
    assert.equal(run('Object.keys(cart).length'), 0);
  }
});

test('reconciliation removes deleted products and clamps to current stock', () => {
  const { run } = app('{"1":9,"2":2,"999":1}');
  assert.equal(run('reconcileCart()'), true);
  assert.equal(run('cart[1]'), 3);
  assert.equal(run('cart[2]'), 2);
  assert.equal(run('cart[999]'), undefined);
});

test('search ignores accents and escaping protects catalog markup', () => {
  const { run, node } = app();
  assert.equal(run("normalize('ÁUDIO')"), 'audio');
  assert.equal(run("escapeHtml('<script>')"), '&lt;script&gt;');
  node('#search').value = 'audio';
  run('renderProducts()');
  assert.match(node('#products').innerHTML, /Fone/);
  assert.doesNotMatch(node('#products').innerHTML, /Mouse/);
  node('#search').value = '';
  run("products[0].name = '<img onerror=alert(1)>'; renderProducts()");
  assert.match(node('#products').innerHTML, /&lt;img onerror=alert\(1\)&gt;/);
  assert.doesNotMatch(node('#products').innerHTML, /<img onerror/);
});

test('a category without matches displays the empty state', () => {
  const { run, node } = app();
  run("setCategory('Lifestyle')");
  assert.equal(node('#products').innerHTML, '');
  assert.equal(node('#catalog-state').hidden, false);
});
