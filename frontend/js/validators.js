/**
 * Client-side validation that mirrors the server's rules (RequestValidators.java), so staff see problems
 * before sending anything. The server still validates everything again - this is only for convenience.
 *
 * Every validateX function returns an error message, or null when the value is fine. The validateXForm
 * functions return an object {fieldName: message} that is empty when everything is valid.
 */

export const OPENING = '08:00';
export const CLOSING = '18:00';
export const PHONE_PATTERN = /^07\d{8}$/;
/** Letters (any language) with single spaces, hyphens or apostrophes between letter groups. */
export const NAME_PATTERN = /^\p{L}[\p{L}\p{M}]*(?:[ '-]\p{L}[\p{L}\p{M}]*)*$/u;
export const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/;
export const APPOINTMENT_NUMBER_PATTERN = /^APT-\d{4,}$/i;
export const NAME_MAX = 50;
export const ADDRESS_MIN = 5;
export const ADDRESS_MAX = 255;
export const EMAIL_MAX = 120;
export const APPOINTMENT_NOTES_MAX = 500;
export const MEDICAL_NOTES_MAX = 5000;
export const SEARCH_MAX = 50;
export const GENDERS = ['MALE', 'FEMALE', 'OTHER'];

function clean(value) {
  return value === undefined || value === null ? '' : String(value).trim();
}

/**
 * @param {Date} [now] the moment to use
 * @returns {string} the local calendar date as yyyy-MM-dd
 */
export function localDate(now = new Date()) {
  const y = String(now.getFullYear()).padStart(4, '0');
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const d = String(now.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/**
 * @param {string} value text to check
 * @returns {boolean} true for a real calendar date written yyyy-MM-dd
 */
export function isRealDate(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) {
    return false;
  }
  const [y, m, d] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
}

/**
 * @param {string} value text to check
 * @returns {boolean} true for a valid 24-hour time written HH:mm
 */
export function isRealTime(value) {
  const match = /^(\d{2}):(\d{2})$/.exec(value);
  return Boolean(match) && Number(match[1]) < 24 && Number(match[2]) < 60;
}

function minutesOf(time) {
  return Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
}

/**
 * @param {string} value the name
 * @param {string} [label] field name used in the message
 * @returns {string|null} error message or null
 */
export function validateName(value, label = 'Name') {
  const v = clean(value);
  if (!v) {
    return `${label} is required.`;
  }
  if (v.length > NAME_MAX) {
    return `${label} must be at most ${NAME_MAX} characters.`;
  }
  if (!NAME_PATTERN.test(v)) {
    return `${label} must contain letters only (single spaces, hyphens and apostrophes are allowed between letters).`;
  }
  return null;
}

/**
 * @param {string} value the phone number
 * @returns {string|null} error message or null
 */
export function validatePhone(value) {
  const v = clean(value);
  if (!v) {
    return 'Phone is required.';
  }
  return PHONE_PATTERN.test(v) ? null : 'Phone must be a Sri Lankan mobile number in the format 07XXXXXXXX.';
}

/**
 * @param {string} value the e-mail (optional)
 * @returns {string|null} error message or null
 */
export function validateEmail(value) {
  const v = clean(value);
  if (!v) {
    return null;
  }
  if (v.length > EMAIL_MAX) {
    return `E-mail must be at most ${EMAIL_MAX} characters.`;
  }
  return EMAIL_PATTERN.test(v) ? null : 'E-mail must be a valid address such as name@example.com.';
}

/**
 * @param {string} value the address
 * @param {{required?: boolean}} [options] whether a blank address is an error
 * @returns {string|null} error message or null
 */
export function validateAddress(value, { required = false } = {}) {
  const v = clean(value);
  if (!v) {
    return required ? 'Address is required.' : null;
  }
  if (v.length < ADDRESS_MIN) {
    return `Address must be at least ${ADDRESS_MIN} characters when given.`;
  }
  return v.length > ADDRESS_MAX ? `Address must be at most ${ADDRESS_MAX} characters.` : null;
}

/**
 * @param {string} value yyyy-MM-dd
 * @param {Date} [now] the moment to use
 * @returns {string|null} error message or null
 */
export function validateDateOfBirth(value, now = new Date()) {
  const v = clean(value);
  if (!v) {
    return 'Date of birth is required.';
  }
  if (!isRealDate(v)) {
    return 'Date of birth must be a valid date (yyyy-mm-dd).';
  }
  if (v < '1900-01-01' || v > localDate(now)) {
    return 'Date of birth must be between 1900-01-01 and today.';
  }
  return null;
}

/**
 * @param {string} value MALE, FEMALE or OTHER
 * @returns {string|null} error message or null
 */
export function validateGender(value) {
  return GENDERS.includes(clean(value).toUpperCase()) ? null : 'Choose a gender.';
}

/**
 * @param {string} value HH:mm
 * @returns {string|null} error message or null
 */
export function validateAppointmentTime(value) {
  const v = clean(value);
  if (!v) {
    return 'Time is required.';
  }
  if (!isRealTime(v)) {
    return 'Time must be written HH:mm, for example 09:30.';
  }
  if (v < OPENING || v > CLOSING) {
    return `Time must be between ${OPENING} and ${CLOSING}.`;
  }
  if (minutesOf(v) % 15 !== 0) {
    return 'Time must be on a 15-minute boundary, for example 09:00 or 09:15.';
  }
  return null;
}

/**
 * Checks the date (today or later) and the time of a booking together.
 *
 * @param {string} date yyyy-MM-dd
 * @param {string} time HH:mm
 * @param {Date} [now] the moment to use
 * @returns {{date?: string, time?: string}} the problems found
 */
export function validateSchedule(date, time, now = new Date()) {
  const errors = {};
  const d = clean(date);
  const t = clean(time);
  const timeError = validateAppointmentTime(t);
  if (timeError) {
    errors.time = timeError;
  }
  if (!d) {
    errors.date = 'Date is required.';
  } else if (!isRealDate(d)) {
    errors.date = 'Date must be a valid date (yyyy-mm-dd).';
  } else if (d < localDate(now)) {
    errors.date = 'Date must be today or in the future.';
  } else if (d === localDate(now) && !timeError && minutesOf(t) <= now.getHours() * 60 + now.getMinutes()) {
    errors.time = 'For today, choose a time later than the current time.';
  }
  return errors;
}

/**
 * @param {string} value the appointment number
 * @returns {string|null} error message or null
 */
export function validateAppointmentNumber(value) {
  const v = clean(value);
  if (!v) {
    return 'Enter an appointment number.';
  }
  return APPOINTMENT_NUMBER_PATTERN.test(v) ? null : 'An appointment number looks like APT-0001.';
}

/**
 * @param {string} value text of a positive whole number
 * @param {string} label field name used in the message
 * @returns {string|null} error message or null
 */
export function validateId(value, label) {
  const v = clean(value);
  if (!v) {
    return `${label} is required.`;
  }
  return /^[1-9]\d{0,17}$/.test(v) ? null : `${label} must be a positive whole number.`;
}

/**
 * @param {string} value free text
 * @param {number} max maximum length
 * @param {string} label field name used in the message
 * @returns {string|null} error message or null when empty or short enough
 */
export function validateMaxLength(value, max, label) {
  return clean(value).length > max ? `${label} must be at most ${max} characters.` : null;
}

/**
 * @param {string} value percentage text (optional)
 * @returns {string|null} error message or null
 */
export function validatePercent(value) {
  const v = clean(value);
  if (!v) {
    return null;
  }
  const n = Number(v);
  return Number.isFinite(n) && n >= 0 && n <= 100 ? null : 'Enter a percentage between 0 and 100.';
}

/**
 * @param {string} term patient search text
 * @returns {string|null} error message or null
 */
export function validateSearchTerm(term) {
  const v = clean(term);
  if (!v) {
    return 'Type part of a name or a phone number.';
  }
  return v.length > SEARCH_MAX ? `Search text must be at most ${SEARCH_MAX} characters.` : null;
}

/**
 * @param {{username: string, password: string}} values the login form
 * @returns {object} field errors
 */
export function validateLogin({ username, password }) {
  const errors = {};
  if (!clean(username)) {
    errors.username = 'Enter your username.';
  }
  if (!password) {
    errors.password = 'Enter your password.';
  }
  return errors;
}

/**
 * @param {string} from first day yyyy-MM-dd
 * @param {string} to last day yyyy-MM-dd
 * @returns {object} field errors (keys "from" and "to")
 */
export function validateDateRange(from, to) {
  const errors = {};
  if (!isRealDate(clean(from))) {
    errors.from = 'Enter a valid start date.';
  }
  if (!isRealDate(clean(to))) {
    errors.to = 'Enter a valid end date.';
  }
  if (!errors.from && !errors.to && clean(from) > clean(to)) {
    errors.to = 'The end date must not be before the start date.';
  }
  return errors;
}

/**
 * Validates the new-patient form.
 *
 * @param {object} v firstName, lastName, dateOfBirth, gender, phone, email, address, medicalNotes
 * @param {Date} [now] the moment to use
 * @returns {object} field errors, empty when valid
 */
export function validatePatientForm(v, now = new Date()) {
  const checks = {
    firstName: validateName(v.firstName, 'First name'),
    lastName: validateName(v.lastName, 'Last name'),
    dateOfBirth: validateDateOfBirth(v.dateOfBirth, now),
    gender: validateGender(v.gender),
    phone: validatePhone(v.phone),
    email: validateEmail(v.email),
    address: validateAddress(v.address),
    medicalNotes: validateMaxLength(v.medicalNotes, MEDICAL_NOTES_MAX, 'Medical notes'),
  };
  return Object.fromEntries(Object.entries(checks).filter(([, message]) => message));
}

/**
 * Validates the appointment part of the booking form.
 *
 * @param {object} v dentistId, treatmentId, date, time, notes
 * @param {Date} [now] the moment to use
 * @returns {object} field errors, empty when valid
 */
export function validateBookingForm(v, now = new Date()) {
  const errors = {};
  const dentist = validateId(v.dentistId, 'Dentist');
  const treatment = validateId(v.treatmentId, 'Treatment');
  const notes = validateMaxLength(v.notes, APPOINTMENT_NOTES_MAX, 'Notes');
  if (dentist) {
    errors.dentistId = 'Choose a dentist.';
  }
  if (treatment) {
    errors.treatmentId = 'Choose a treatment.';
  }
  if (notes) {
    errors.notes = notes;
  }
  Object.assign(errors, validateSchedule(v.date, v.time, now));
  return errors;
}
