/**
 * Session handling for the browser client.
 *
 * The Bearer token lives in sessionStorage only: it disappears when the tab is closed. The server remains the
 * authority for permissions; the role stored here only decides which menu items are shown.
 */

const TOKEN_KEY = 'sunrise.token';
const USER_KEY = 'sunrise.user';

/** The login page (relative URL, so the app works from any folder). */
export const LOGIN_PAGE = 'login.html';

/**
 * Every menu entry. {@code roles: null} means every signed-in user; Exit is an action, not a page.
 */
export const MENU = [
  { key: 'register', label: 'Register Appointment', href: 'register-appointment.html', roles: null,
    description: 'Register a patient and book a time with a dentist.' },
  { key: 'search', label: 'Search', href: 'search.html', roles: null,
    description: 'Find an appointment by its number and see every detail.' },
  { key: 'billing', label: 'Bill', href: 'billing.html', roles: null,
    description: 'Calculate a bill, print the receipt and record payment.' },
  { key: 'reports', label: 'Reports', href: 'reports.html', roles: ['ADMIN'],
    description: 'Tables, charts and CSV exports (administrators only).' },
  { key: 'help', label: 'Help', href: 'help.html', roles: null,
    description: 'Step-by-step guide for new staff.' },
  { key: 'exit', label: 'Exit', action: 'logout', roles: null,
    description: 'Sign out and return to the login page.' },
];

/**
 * Menu entries a role may see (Reports is hidden unless the role is ADMIN).
 *
 * @param {string} role ADMIN or RECEPTIONIST
 * @returns {Array<object>} the visible entries
 */
export function menuFor(role) {
  return MENU.filter((item) => !item.roles || item.roles.includes(role));
}

/**
 * Remembers a successful login.
 *
 * @param {{token: string, user: object}} session the login response data
 */
export function saveSession({ token, user }) {
  sessionStorage.setItem(TOKEN_KEY, token);
  sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}

/**
 * Replaces the stored user (for example after asking the server who is logged in).
 *
 * @param {object} user the user object
 */
export function saveUser(user) {
  sessionStorage.setItem(USER_KEY, JSON.stringify(user));
}

/** Forgets the token and the user. */
export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(USER_KEY);
}

/**
 * The token, or null when nobody is logged in.
 *
 * @returns {string|null} the Bearer token
 */
export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY) || null;
}

/**
 * The signed-in user.
 *
 * @returns {{id: number, username: string, fullName: string, role: string}|null} the user or null
 */
export function getUser() {
  try {
    const raw = sessionStorage.getItem(USER_KEY);
    const user = raw ? JSON.parse(raw) : null;
    return user && typeof user === 'object' ? user : null;
  } catch {
    return null;
  }
}

/**
 * Sends the browser to the login page.
 *
 * @param {string|null} [reason] shown on the login page: "expired" or "logout"
 */
export function redirectToLogin(reason = null) {
  const target = reason ? `${LOGIN_PAGE}?reason=${encodeURIComponent(reason)}` : LOGIN_PAGE;
  window.location.replace(target);
}

/**
 * Called when the server answers 401 (for example after the server was restarted): clears the session and
 * goes to the login page.
 */
export function handleUnauthorized() {
  clearSession();
  redirectToLogin('expired');
}

/**
 * Guard for every page except the login page.
 *
 * @returns {object|null} the user, or null after redirecting to the login page
 */
export function requireLogin() {
  const user = getUser();
  if (!getToken() || !user) {
    clearSession();
    redirectToLogin();
    return null;
  }
  return user;
}
