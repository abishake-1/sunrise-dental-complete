/**
 * Shared UI helpers: HTML escaping, DOM building, alerts, loading states, tables, formatting and the page shell.
 *
 * XSS rule used everywhere in this client: data that comes from the API is only ever inserted as TEXT -
 * either through el()/textContent, or through the html`` tag, which escapes every interpolated value.
 * Nothing assigns API data to innerHTML any other way.
 */
import { menuFor, requireLogin, saveUser } from './auth.js';
import * as api from './api.js';
import { ApiError } from './api.js';
import { localDate } from './validators.js';

// ---- escaping ---------------------------------------------------------------------------------------------

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;', '`': '&#96;' };

/**
 * Escapes text for HTML element content and quoted attribute values.
 *
 * @param {any} value any value (null and undefined become an empty string)
 * @returns {string} the escaped text
 */
export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"'`]/g, (c) => ESCAPES[c]);
}

/** HTML that is already safe: produced by the html`` tag, never by hand. */
export class SafeHtml {
  /** @param {string} value markup that has been escaped */
  constructor(value) {
    this.value = value;
  }

  /** @returns {string} the markup */
  toString() {
    return this.value;
  }
}

function part(value) {
  if (value instanceof SafeHtml) {
    return value.value;
  }
  if (Array.isArray(value)) {
    return value.map(part).join('');
  }
  return escapeHtml(value);
}

/**
 * Template tag that escapes every interpolated value; nested html`` results and arrays of them pass through.
 * Always put interpolations inside quoted attributes or element text.
 *
 * @param {TemplateStringsArray} strings the literal parts (trusted: written in this source code)
 * @param {...any} values the interpolated values (escaped)
 * @returns {SafeHtml} the safe markup
 */
export function html(strings, ...values) {
  let out = '';
  strings.forEach((literal, i) => {
    out += literal;
    if (i < values.length) {
      out += part(values[i]);
    }
  });
  return new SafeHtml(out);
}

/**
 * Puts safe markup into an element.
 *
 * @param {Element} target the element to fill
 * @param {SafeHtml} safe markup produced by html``
 */
export function setHtml(target, safe) {
  if (!(safe instanceof SafeHtml)) {
    throw new TypeError('setHtml needs markup built with the html`` tag');
  }
  target.innerHTML = safe.value;
}

// ---- DOM building -----------------------------------------------------------------------------------------

/**
 * @param {string} id element id
 * @returns {HTMLElement} the element (throws a clear error when the page is missing it)
 */
export function byId(id) {
  const node = document.getElementById(id);
  if (!node) {
    throw new Error(`Missing element #${id}`);
  }
  return node;
}

/**
 * Creates an element. Text goes in as text nodes, so it cannot inject markup.
 *
 * @param {string} tag tag name
 * @param {object} [props] class, text, onclick-style handlers, or any attribute
 * @param {...any} children nodes or text (arrays are flattened one level)
 * @returns {HTMLElement} the new element
 */
export function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props || {})) {
    if (value === undefined || value === null || value === false) {
      continue;
    }
    if (key === 'class') {
      node.className = value;
    } else if (key === 'text') {
      node.textContent = value;
    } else if (key.startsWith('on') && typeof value === 'function') {
      node.addEventListener(key.slice(2).toLowerCase(), value);
    } else {
      node.setAttribute(key, value === true ? '' : String(value));
    }
  }
  for (const child of children.flat()) {
    if (child === undefined || child === null || child === false) {
      continue;
    }
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

// ---- alerts and field errors -----------------------------------------------------------------------------

/**
 * Shows one alert box in a container (replacing the previous one).
 *
 * @param {Element} container the #alerts element
 * @param {string} message main sentence
 * @param {'error'|'success'|'info'|'warning'} [type] colour and role
 * @param {string[]} [details] extra lines shown as a list
 */
export function showAlert(container, message, type = 'error', details = []) {
  const box = el('div', { class: `alert alert-${type}`, role: type === 'error' ? 'alert' : 'status' },
    el('p', { class: 'alert-message', text: message }),
    details.length ? el('ul', {}, details.map((d) => el('li', { text: d }))) : null);
  container.replaceChildren(box);
  if (typeof box.scrollIntoView === 'function') {
    box.scrollIntoView({ block: 'nearest' });
  }
}

/** @param {Element} container the #alerts element */
export function clearAlerts(container) {
  container.replaceChildren();
}

function errorSlot(form, field) {
  return form.querySelector(`[data-error-for="${field}"]`);
}

/**
 * Shows a message under one form field.
 *
 * @param {HTMLFormElement|Element} form the form (or section) containing the field
 * @param {string} field the field name (matches data-error-for and the input id)
 * @param {string} message what is wrong
 * @returns {boolean} true when the form has a place for that field
 */
export function setFieldError(form, field, message) {
  const slot = errorSlot(form, field);
  if (!slot) {
    return false;
  }
  slot.textContent = message;
  const input = form.querySelector(`#${field}`);
  if (input) {
    input.setAttribute('aria-invalid', 'true');
    input.setAttribute('aria-describedby', slot.id || '');
  }
  return true;
}

/**
 * Removes the message under one field (used while the user is typing).
 *
 * @param {Element} form the form
 * @param {string} field the field name
 */
export function clearFieldError(form, field) {
  const slot = errorSlot(form, field);
  if (slot) {
    slot.textContent = '';
  }
  const input = form.querySelector(`#${field}`);
  if (input) {
    input.removeAttribute('aria-invalid');
    input.removeAttribute('aria-describedby');
  }
}

/**
 * Shows several field errors and moves the focus to the first invalid input.
 *
 * @param {Element} form the form
 * @param {object} errors {field: message}
 * @returns {boolean} true when there was at least one error
 */
export function applyFieldErrors(form, errors) {
  const fields = Object.keys(errors);
  fields.forEach((field) => setFieldError(form, field, errors[field]));
  if (fields.length) {
    const first = fields.map((f) => form.querySelector(`#${f}`)).find(Boolean);
    if (first && typeof first.focus === 'function') {
      first.focus();
    }
  }
  return fields.length > 0;
}

/** @param {Element} form the form whose field errors are removed */
export function clearFieldErrors(form) {
  form.querySelectorAll('[data-error-for]').forEach((slot) => {
    slot.textContent = '';
  });
  form.querySelectorAll('[aria-invalid]').forEach((input) => {
    input.removeAttribute('aria-invalid');
    input.removeAttribute('aria-describedby');
  });
}

/**
 * Shows an error from the API (or anything thrown) in a friendly way. Field errors go under their inputs when
 * a form is given; the rest are listed in the alert.
 *
 * @param {Element} container the #alerts element
 * @param {unknown} err what was thrown
 * @param {Element|null} [form] the form the request came from
 */
export function showError(container, err, form = null) {
  if (!(err instanceof ApiError)) {
    console.error(err);
    showAlert(container, 'Something unexpected went wrong. Please try again.');
    return;
  }
  const leftovers = [];
  for (const detail of err.details) {
    if (!form || !setFieldError(form, detail.field, detail.message)) {
      leftovers.push(detail.field ? `${detail.field}: ${detail.message}` : detail.message);
    }
  }
  showAlert(container, err.userMessage(), 'error', leftovers);
}

// ---- loading states ---------------------------------------------------------------------------------------

/**
 * Disables a button and changes its label while a task runs.
 *
 * @param {HTMLButtonElement} button the button that started the task
 * @param {string} busyLabel label shown meanwhile
 * @param {() => Promise<any>} task the work
 * @returns {Promise<any>} the task's result
 */
export async function withLoading(button, busyLabel, task) {
  const original = button.textContent;
  button.disabled = true;
  button.setAttribute('aria-busy', 'true');
  button.textContent = busyLabel;
  try {
    return await task();
  } finally {
    button.disabled = false;
    button.removeAttribute('aria-busy');
    button.textContent = original;
  }
}

/**
 * Replaces a container's content by a spinner line.
 *
 * @param {Element} container where to show it
 * @param {string} [text] the message
 */
export function showLoading(container, text = 'Loading...') {
  container.replaceChildren(el('p', { class: 'loading', role: 'status' },
    el('span', { class: 'spinner', 'aria-hidden': 'true' }), ' ', text));
}

// ---- tables -----------------------------------------------------------------------------------------------

/**
 * Draws a table. Cell values are inserted as text; a column's format() may return text or a DOM node.
 *
 * @param {Element} container where to draw (its content is replaced)
 * @param {Array<{key: string, label: string, align?: string, format?: Function}>} columns the columns
 * @param {Array<object>} rows the data
 * @param {{caption?: string, emptyText?: string}} [options] extras
 */
export function renderTable(container, columns, rows, { caption = '', emptyText = 'No records found.' } = {}) {
  if (!rows.length) {
    container.replaceChildren(el('p', { class: 'empty', text: emptyText }));
    return;
  }
  const head = el('thead', {}, el('tr', {}, columns.map((c) =>
    el('th', { scope: 'col', class: c.align === 'right' ? 'num' : null, text: c.label }))));
  const body = el('tbody', {}, rows.map((row) => el('tr', {}, columns.map((c) => {
    const value = row[c.key];
    const content = c.format ? c.format(value, row) : (value === undefined || value === null ? '-' : value);
    return el('td', { class: c.align === 'right' ? 'num' : null, 'data-label': c.label }, content);
  }))));
  const table = el('table', { class: 'data-table' }, caption ? el('caption', { text: caption }) : null, head, body);
  container.replaceChildren(el('div', { class: 'table-wrap' }, table));
}

// ---- formatting -------------------------------------------------------------------------------------------

/**
 * @param {number|string} value an amount
 * @returns {string} the amount with two decimals and thousands separators, or "-" when not a number
 */
export function formatMoney(value) {
  const n = Number(value);
  return Number.isFinite(n)
    ? n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : '-';
}

/**
 * @param {string} value a time such as 09:00 or 09:00:00
 * @returns {string} HH:mm
 */
export function formatTime(value) {
  return value ? String(value).slice(0, 5) : '-';
}

/**
 * @param {string} value an ISO local date-time such as 2026-10-12T14:05
 * @returns {string} yyyy-MM-dd HH:mm
 */
export function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-';
}

/**
 * @param {string} key a camelCase column name such as treatmentName
 * @returns {string} a heading such as "Treatment name"
 */
export function humanizeKey(key) {
  const spaced = String(key).replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

/**
 * @param {Date} [now] the moment to use
 * @returns {string} today's local date as yyyy-MM-dd
 */
export function todayIso(now = new Date()) {
  return localDate(now);
}

/**
 * @param {string} status an appointment or payment status
 * @returns {SafeHtml} a coloured badge
 */
export function badgeHtml(status) {
  const cls = String(status ?? '').toLowerCase().replace(/[^a-z_]/g, '');
  return html`<span class="badge badge-${cls}">${String(status ?? '').replace('_', ' ')}</span>`;
}

/**
 * @param {string} status an appointment or payment status
 * @returns {HTMLElement} a coloured badge element
 */
export function badge(status) {
  const cls = String(status ?? '').toLowerCase().replace(/[^a-z_]/g, '');
  return el('span', { class: `badge badge-${cls}`, text: String(status ?? '').replace('_', ' ') });
}

// ---- page shell -------------------------------------------------------------------------------------------

/**
 * Draws the top bar: brand, role-based menu and the signed-in user. Exit signs out through the API.
 *
 * @param {string} activeKey menu key of the current page
 * @param {{fullName: string, role: string}} user the signed-in user
 */
export function renderNav(activeKey, user) {
  const header = document.getElementById('site-header');
  if (!header) {
    return;
  }
  const items = menuFor(user.role).map((item) => el('li', {}, item.action === 'logout'
    ? el('button', { type: 'button', class: 'nav-link nav-exit', onclick: () => api.logout() }, item.label)
    : el('a', { class: 'nav-link', href: item.href, 'aria-current': item.key === activeKey ? 'page' : null },
      item.label)));
  header.replaceChildren(el('div', { class: 'header-inner' },
    el('a', { class: 'brand', href: 'dashboard.html' }, 'Sunrise Dental'),
    el('nav', { 'aria-label': 'Main menu' }, el('ul', { class: 'nav-list' }, items)),
    el('div', { class: 'user-box', text: `${user.fullName} (${user.role})` })));
}

/**
 * Starts every page except the login page: checks the session, asks the server who is logged in, enforces
 * the page's roles and draws the menu.
 *
 * @param {string} activeKey menu key of the page
 * @param {{roles?: string[]|null}} [options] roles allowed to open the page (null: everyone signed in)
 * @returns {Promise<object|null>} the user, or null when the browser is being redirected
 */
export async function initPage(activeKey, { roles = null } = {}) {
  const stored = requireLogin();
  if (!stored) {
    return null;
  }
  let user = stored;
  try {
    user = await api.me();
    saveUser(user);
  } catch (err) {
    if (!(err instanceof ApiError && err.status === 401)) {
      const alerts = document.getElementById('alerts');
      if (alerts) {
        showError(alerts, err);
      }
    }
    return null;
  }
  if (roles && !roles.includes(user.role)) {
    window.location.replace('dashboard.html?denied=1');
    return null;
  }
  renderNav(activeKey, user);
  return user;
}
