/**
 * The only module that calls fetch(). Every REST endpoint of the server has one function here.
 *
 * Responsibilities: add the Bearer token, send/parse JSON, turn the server's error format
 * {"error":{"status","code","message","details":[{"field","message"}]}} into an {@link ApiError}, and send the
 * user back to the login page when the server answers 401.
 */
import { getToken, clearSession, handleUnauthorized, redirectToLogin } from './auth.js';

/** An error answered by the server, or a network failure (status 0). */
export class ApiError extends Error {
  /**
   * @param {number} status HTTP status, 0 when the server could not be reached
   * @param {string} code machine-readable code such as VALIDATION_FAILED
   * @param {string} message the server's message
   * @param {Array<{field: string, message: string}>} [details] field errors
   */
  constructor(status, code, message, details = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }

  /** @returns {string} a sentence that is safe and friendly to show to staff */
  userMessage() {
    if (this.status === 0) {
      return 'Cannot reach the server. Check that it is running and that you are connected, then try again.';
    }
    if (this.status === 401) {
      return this.code === 'INVALID_CREDENTIALS'
        ? 'Wrong username or password.'
        : 'Your session has ended. Please log in again.';
    }
    if (this.status === 403) {
      return 'You do not have permission to do this.';
    }
    if (this.status === 400 && this.details.length > 0) {
      return 'Please correct the problems below.';
    }
    if (this.status === 413) {
      return 'That request is too large.';
    }
    if (this.status >= 500) {
      return 'The server had a problem completing the request. Please try again; if it keeps happening, tell the administrator.';
    }
    return this.message || 'The request could not be completed.';
  }
}

function buildQuery(query) {
  if (!query) {
    return '';
  }
  const parts = [];
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || String(value).trim() === '') {
      continue;
    }
    parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value).trim())}`);
  }
  return parts.length ? `?${parts.join('&')}` : '';
}

async function toApiError(response, redirectOn401) {
  let code = '';
  let message = '';
  let details = [];
  try {
    const parsed = JSON.parse(await response.text());
    const error = parsed && parsed.error ? parsed.error : null;
    if (error) {
      code = String(error.code || '');
      message = String(error.message || '');
      details = Array.isArray(error.details)
        ? error.details.map((d) => ({ field: String(d.field || ''), message: String(d.message || '') }))
        : [];
    }
  } catch {
    // not JSON: fall back to the status text below
  }
  if (response.status === 401 && redirectOn401) {
    handleUnauthorized();
  }
  return new ApiError(response.status, code, message || `Server returned HTTP ${response.status}`, details);
}

function filenameFrom(disposition, fallback) {
  const match = /filename="?([^";]+)"?/i.exec(disposition || '');
  return match ? match[1] : fallback;
}

/**
 * Sends one request.
 *
 * @param {string} method HTTP method
 * @param {string} path path starting with /api/
 * @param {object} [options] query, body, auth (default true), as ('json'|'text'|'blob'), redirectOn401
 * @returns {Promise<any>} the parsed JSON, text, {blob, filename}, or null for 204
 */
async function request(method, path, options = {}) {
  const { query, body, auth = true, as = 'json', redirectOn401 = true, fallbackName = 'download' } = options;
  const headers = { Accept: as === 'json' ? 'application/json' : '*/*' };
  if (auth) {
    const token = getToken();
    if (!token) {
      if (redirectOn401) {
        redirectToLogin('expired');
      }
      throw new ApiError(401, 'NO_SESSION', 'You are not logged in.');
    }
    headers.Authorization = `Bearer ${token}`;
  }
  const init = { method, headers, credentials: 'same-origin' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  let response;
  try {
    response = await fetch(path + buildQuery(query), init);
  } catch {
    throw new ApiError(0, 'NETWORK', 'The server could not be reached.');
  }
  if (!response.ok) {
    throw await toApiError(response, auth && redirectOn401);
  }
  if (response.status === 204) {
    return null;
  }
  if (as === 'text') {
    return response.text();
  }
  if (as === 'blob') {
    return {
      blob: await response.blob(),
      filename: filenameFrom(response.headers.get('Content-Disposition'), fallbackName),
    };
  }
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

// ---- authentication -------------------------------------------------------------------------------------

/**
 * POST /api/auth/login (the only call without a token; a 401 here means wrong credentials).
 *
 * @param {string} username login name
 * @param {string} password clear-text password
 * @returns {Promise<{token: string, user: object}>} the session data
 */
export function login(username, password) {
  return request('POST', '/api/auth/login', { body: { username, password }, auth: false });
}

/**
 * Exit: tells the server to end the session, clears the stored session and returns to the login page.
 * Errors are ignored on purpose - the local session is dropped either way.
 *
 * @returns {Promise<void>} resolves just before the redirect
 */
export async function logout() {
  try {
    await request('POST', '/api/auth/logout', { redirectOn401: false });
  } catch {
    // the session may already be gone on the server
  }
  clearSession();
  redirectToLogin('logout');
}

/** @returns {Promise<object>} GET /api/auth/me - the signed-in user as the server sees it */
export function me() {
  return request('GET', '/api/auth/me');
}

// ---- catalogues -----------------------------------------------------------------------------------------

/** @returns {Promise<Array<object>>} GET /api/dentists */
export async function getDentists() {
  return (await request('GET', '/api/dentists')).items;
}

/** @returns {Promise<Array<object>>} GET /api/treatments */
export async function getTreatments() {
  return (await request('GET', '/api/treatments')).items;
}

// ---- patients -------------------------------------------------------------------------------------------

/**
 * @param {string} term name or phone fragment (1 to 50 characters)
 * @returns {Promise<Array<object>>} GET /api/patients?search=
 */
export async function searchPatients(term) {
  return (await request('GET', '/api/patients', { query: { search: term } })).items;
}

/**
 * @param {number} id patient id
 * @returns {Promise<object>} GET /api/patients/{id}
 */
export function getPatient(id) {
  return request('GET', `/api/patients/${encodeURIComponent(id)}`);
}

/**
 * @param {object} patient firstName, lastName, dateOfBirth, gender, phone, optional email, address, medicalNotes
 * @returns {Promise<object>} POST /api/patients - the saved patient
 */
export function registerPatient(patient) {
  return request('POST', '/api/patients', { body: patient });
}

// ---- appointments ---------------------------------------------------------------------------------------

/**
 * @param {object} booking patientId, dentistId, treatmentId, date, time, optional notes
 * @returns {Promise<object>} POST /api/appointments
 */
export function bookAppointment(booking) {
  return request('POST', '/api/appointments', { body: booking });
}

/**
 * @param {string} number appointment number such as APT-0001
 * @returns {Promise<object>} GET /api/appointments/{number}
 */
export function getAppointmentByNumber(number) {
  return request('GET', `/api/appointments/${encodeURIComponent(number)}`);
}

/**
 * @param {string} date yyyy-MM-dd
 * @param {number|string} [dentistId] optional dentist filter
 * @returns {Promise<Array<object>>} GET /api/appointments?date=&dentistId=
 */
export async function listAppointments(date, dentistId) {
  return (await request('GET', '/api/appointments', { query: { date, dentistId } })).items;
}

/**
 * @param {number} id appointment id
 * @param {string} status CONFIRMED, COMPLETED or NO_SHOW
 * @returns {Promise<object>} PUT /api/appointments/{id}
 */
export function updateAppointmentStatus(id, status) {
  return request('PUT', `/api/appointments/${encodeURIComponent(id)}`, { body: { status } });
}

/**
 * @param {number} id appointment id
 * @param {{date: string, time: string, notes?: string}} schedule the new slot
 * @returns {Promise<object>} PUT /api/appointments/{id}
 */
export function rescheduleAppointment(id, schedule) {
  return request('PUT', `/api/appointments/${encodeURIComponent(id)}`, { body: schedule });
}

/**
 * @param {number} id appointment id
 * @returns {Promise<object>} DELETE /api/appointments/{id} - the cancelled appointment
 */
export function cancelAppointment(id) {
  return request('DELETE', `/api/appointments/${encodeURIComponent(id)}`);
}

// ---- billing --------------------------------------------------------------------------------------------

/**
 * @param {number} appointmentId id of a COMPLETED appointment
 * @param {{billingType?: string, insuranceCoveragePercent?: number}} [options] bill options
 * @returns {Promise<object>} POST /api/appointments/{id}/bill - the receipt as JSON
 */
export function createBill(appointmentId, options = {}) {
  return request('POST', `/api/appointments/${encodeURIComponent(appointmentId)}/bill`, { body: options });
}

/**
 * @param {number} billId bill id
 * @returns {Promise<object>} GET /api/bills/{id} - the receipt as JSON
 */
export function getBill(billId) {
  return request('GET', `/api/bills/${encodeURIComponent(billId)}`);
}

/**
 * @param {number} billId bill id
 * @param {string} method CASH, CARD, ONLINE or INSURANCE
 * @returns {Promise<object>} POST /api/bills/{id}/pay - the updated receipt
 */
export function payBill(billId, method) {
  return request('POST', `/api/bills/${encodeURIComponent(billId)}/pay`, { body: { paymentMethod: method } });
}

// ---- reports (ADMIN) ------------------------------------------------------------------------------------

/**
 * @param {string} slug daily-appointments, monthly-revenue, top-treatments, dentist-utilization or
 *     cancellation-rate
 * @param {object} params from/to (or year/month) and limit
 * @returns {Promise<object>} GET /api/reports/{slug} - {columns, rows, ...}
 */
export function getReport(slug, params) {
  return request('GET', `/api/reports/${encodeURIComponent(slug)}`, { query: params });
}

/**
 * @param {string} slug report name
 * @param {object} params report parameters
 * @returns {Promise<{blob: Blob, filename: string}>} the CSV file
 */
export function downloadReportCsv(slug, params) {
  return request('GET', `/api/reports/${encodeURIComponent(slug)}`, {
    query: { ...params, format: 'csv' }, as: 'blob', fallbackName: `${slug}.csv`,
  });
}
