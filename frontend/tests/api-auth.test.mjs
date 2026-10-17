// Tests api.js + auth.js with a fake fetch, sessionStorage and window.location (no browser needed).
import test from 'node:test';
import assert from 'node:assert/strict';

const store = new Map();
globalThis.sessionStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k),
};
const redirects = [];
globalThis.window = { location: { replace: (url) => redirects.push(url) } };

const auth = await import('../js/auth.js');
const api = await import('../js/api.js');

function reply(status, body, headers = {}) {
  const text = body === undefined ? '' : (typeof body === 'string' ? body : JSON.stringify(body));
  return { ok: status < 400, status, text: async () => text, blob: async () => text, headers: { get: (n) => headers[n] || null } };
}

test('Bearer token is sent and the query string is encoded', async () => {
  auth.saveSession({ token: 'T0K', user: { role: 'ADMIN' } });
  let seen;
  globalThis.fetch = async (url, init) => { seen = { url, init }; return reply(200, { count: 0, items: [] }); };
  await api.listAppointments('2026-10-14', 3);
  assert.equal(seen.url, '/api/appointments?date=2026-10-14&dentistId=3');
  assert.equal(seen.init.headers.Authorization, 'Bearer T0K');
  await api.searchPatients('a b&c');
  assert.equal(seen.url, '/api/patients?search=a%20b%26c');
});

test('JSON errors become ApiError with field details and friendly text', async () => {
  globalThis.fetch = async () => reply(400, { error: { status: 400, code: 'VALIDATION_FAILED', message: 'Validation failed',
    details: [{ field: 'phone', message: 'bad phone' }] } });
  await assert.rejects(api.registerPatient({}), (e) => {
    assert.ok(e instanceof api.ApiError);
    assert.equal(e.status, 400);
    assert.deepEqual(e.details, [{ field: 'phone', message: 'bad phone' }]);
    assert.equal(e.userMessage(), 'Please correct the problems below.');
    return true;
  });
});

test('401 clears the session and redirects to the login page', async () => {
  redirects.length = 0;
  globalThis.fetch = async () => reply(401, { error: { status: 401, code: 'UNAUTHORIZED', message: 'Invalid or expired session' } });
  await assert.rejects(api.getDentists(), (e) => e.status === 401);
  assert.equal(auth.getToken(), null);
  assert.deepEqual(redirects, ['login.html?reason=expired']);
});

test('a 401 on login is just wrong credentials: no redirect', async () => {
  redirects.length = 0;
  globalThis.fetch = async () => reply(401, { error: { status: 401, code: 'INVALID_CREDENTIALS', message: 'Invalid username or password' } });
  await assert.rejects(api.login('a', 'b'), (e) => e.userMessage() === 'Wrong username or password.');
  assert.deepEqual(redirects, []);
});

test('network failure is status 0 with a friendly message', async () => {
  auth.saveSession({ token: 'T', user: { role: 'ADMIN' } });
  globalThis.fetch = async () => { throw new TypeError('failed'); };
  await assert.rejects(api.getTreatments(), (e) => e.status === 0 && /Cannot reach the server/.test(e.userMessage()));
});

test('with no token no request is made and the user goes to the login page', async () => {
  auth.clearSession();
  redirects.length = 0;
  let called = false;
  globalThis.fetch = async () => { called = true; return reply(200, {}); };
  await assert.rejects(api.getDentists(), (e) => e.status === 401);
  assert.equal(called, false);
  assert.deepEqual(redirects, ['login.html?reason=expired']);
});

test('requireLogin redirects when there is no session', () => {
  auth.clearSession();
  redirects.length = 0;
  assert.equal(auth.requireLogin(), null);
  assert.deepEqual(redirects, ['login.html']);
});

test('logout ends the session, clears storage and returns to login.html', async () => {
  auth.saveSession({ token: 'T', user: { role: 'ADMIN' } });
  redirects.length = 0;
  let call;
  globalThis.fetch = async (url, init) => { call = [init.method, url]; return reply(204); };
  await api.logout();
  assert.deepEqual(call, ['POST', '/api/auth/logout']);
  assert.equal(auth.getToken(), null);
  assert.equal(auth.getUser(), null);
  assert.deepEqual(redirects, ['login.html?reason=logout']);
});

test('CSV download returns the blob and the server file name', async () => {
  auth.saveSession({ token: 'T', user: { role: 'ADMIN' } });
  globalThis.fetch = async (url) => { assert.match(url, /format=csv/); return reply(200, 'a,b', { 'Content-Disposition': 'attachment; filename="top.csv"' }); };
  const file = await api.downloadReportCsv('top-treatments', { from: '2026-10-01', to: '2026-10-14' });
  assert.equal(file.filename, 'top.csv');
});
