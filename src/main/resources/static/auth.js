'use strict';

// Páginas /entrar e /cadastro. Depois de entrar, volta para ?next=... (só destinos internos)
// ou para a área do perfil: cliente → /conta, administrador → /admin.
(() => {
  const { api, me, safeNext, home } = window.AuraSession;
  const next = new URLSearchParams(location.search).get('next');
  const destination = (user) => safeNext(next, home(user));
  const form = document.querySelector('#login-form, #register-form');
  const error = document.getElementById('form-error');

  if (next) {
    const link = document.getElementById('switch-link');
    link.href = `${link.pathname}?next=${encodeURIComponent(next)}`;
    const reason = document.getElementById('login-reason');
    if (reason) reason.hidden = false;
  }

  // Quem já está logado não precisa ver o formulário.
  me().then((user) => { if (user) location.replace(destination(user)); });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    error.hidden = true;
    const data = Object.fromEntries(new FormData(form));
    if (form.id === 'register-form' && data.password !== data.confirm) {
      error.textContent = 'As senhas não conferem.';
      error.hidden = false;
      form.elements.confirm.focus();
      return;
    }
    if (!form.reportValidity()) return;
    delete data.confirm;
    const submit = form.querySelector('[type=submit]');
    submit.disabled = true;
    try {
      const session = await api(form.id === 'login-form' ? '/api/auth/login' : '/api/auth/register', { method: 'POST', body: data });
      location.assign(destination(session.user));
    } catch (failure) {
      error.textContent = failure.message;
      error.hidden = false;
      submit.disabled = false;
    }
  });
})();
