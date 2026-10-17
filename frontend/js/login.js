/** Login page: validates the form, calls the API, stores the session and opens the dashboard. */
import * as api from './api.js';
import { getToken, getUser, saveSession } from './auth.js';
import {
  applyFieldErrors, byId, clearFieldErrors, showAlert, showError, withLoading,
} from './ui.js';
import { validateLogin } from './validators.js';

const form = byId('login-form');
const alerts = byId('alerts');

if (getToken() && getUser()) {
  window.location.replace('dashboard.html');
}

const reason = new URLSearchParams(window.location.search).get('reason');
if (reason === 'expired') {
  showAlert(alerts, 'Your session has ended. Please log in again.', 'info');
} else if (reason === 'logout') {
  showAlert(alerts, 'You have been signed out.', 'success');
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  clearFieldErrors(form);
  alerts.replaceChildren();
  const username = form.elements.username.value.trim();
  const password = form.elements.password.value;
  if (applyFieldErrors(form, validateLogin({ username, password }))) {
    return;
  }
  try {
    const session = await withLoading(byId('login-button'), 'Signing in...', () => api.login(username, password));
    form.elements.password.value = '';
    saveSession({ token: session.token, user: session.user });
    window.location.replace('dashboard.html');
  } catch (err) {
    form.elements.password.value = '';
    showError(alerts, err);
  }
});
