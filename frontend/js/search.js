/** Search page: find an appointment by number and show every detail (patient, dentist, treatment). */
import * as api from './api.js';
import {
  badgeHtml, byId, clearAlerts, clearFieldErrors, applyFieldErrors, formatDateTime, formatMoney, formatTime,
  html, initPage, setHtml, showAlert, showError, showLoading, withLoading,
} from './ui.js';
import { validateAppointmentNumber } from './validators.js';

const alerts = byId('alerts');
const form = byId('search-form');
const result = byId('result');
let catalogs = null;

async function loadCatalogs() {
  if (!catalogs) {
    const [dentists, treatments] = await Promise.all([api.getDentists(), api.getTreatments()]);
    catalogs = { dentists, treatments };
  }
  return catalogs;
}

function dash(value) {
  return value === undefined || value === null || value === '' ? '-' : value;
}

function endTime(start, minutes) {
  const match = /^(\d{2}):(\d{2})/.exec(String(start));
  if (!match || !Number.isFinite(minutes)) {
    return '';
  }
  const total = Number(match[1]) * 60 + Number(match[2]) + minutes;
  return `${String(Math.floor(total / 60) % 24).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
}

function render(appt, patient, dentist, treatment) {
  const end = treatment ? endTime(appt.time, treatment.durationMinutes) : '';
  const actions = [];
  const open = appt.status === 'SCHEDULED' || appt.status === 'CONFIRMED';
  if (appt.status === 'SCHEDULED') {
    actions.push(['CONFIRMED', 'Confirm', 'btn btn-secondary']);
  }
  if (open) {
    actions.push(['COMPLETED', 'Mark completed', 'btn btn-secondary']);
    actions.push(['NO_SHOW', 'Mark no-show', 'btn btn-secondary']);
    actions.push(['CANCEL', 'Cancel appointment', 'btn btn-danger']);
  }
  const billingLink = appt.status === 'COMPLETED'
    ? html`<a class="btn" href="billing.html?number=${encodeURIComponent(appt.appointmentNumber)}">Go to billing</a>`
    : '';
  const patientName = patient ? `${patient.firstName} ${patient.lastName}` : `Patient #${appt.patientId}`;
  setHtml(result, html`
    <section class="card" aria-labelledby="result-title">
      <h2 id="result-title">${appt.appointmentNumber} ${badgeHtml(appt.status)}</h2>
      <div class="grid-2">
        <dl class="details">
          <dt>Date</dt><dd>${dash(appt.date)}</dd>
          <dt>Time</dt><dd>${formatTime(appt.time)}${end ? html` - ${end}` : ''}</dd>
          <dt>Status</dt><dd>${dash(appt.status)}</dd>
          <dt>Notes</dt><dd>${dash(appt.notes)}</dd>
          <dt>Booked on</dt><dd>${formatDateTime(appt.createdAt)}</dd>
        </dl>
        <dl class="details">
          <dt>Patient</dt><dd>${patientName}</dd>
          <dt>Phone</dt><dd>${dash(patient && patient.phone)}</dd>
          <dt>Date of birth</dt><dd>${dash(patient && patient.dateOfBirth)}</dd>
          <dt>Dentist</dt><dd>${dentist ? dentist.fullName : `Dentist #${appt.dentistId}`}</dd>
          <dt>Treatment</dt><dd>${treatment ? treatment.name : `Treatment #${appt.treatmentId}`}</dd>
          <dt>Duration</dt><dd>${treatment ? `${treatment.durationMinutes} minutes` : '-'}</dd>
          <dt>Price</dt><dd>${treatment ? formatMoney(treatment.price) : '-'}</dd>
        </dl>
      </div>
      <div class="actions" id="result-actions">
        ${actions.map(([code, label, cls]) => html`<button type="button" class="${cls}" data-action="${code}">${label}</button>`)}
        ${billingLink}
      </div>
    </section>`);
  byId('result-actions').querySelectorAll('button[data-action]').forEach((button) => {
    button.addEventListener('click', () => act(appt, button));
  });
}

async function act(appt, button) {
  const action = button.dataset.action;
  if (action === 'CANCEL' && !window.confirm(`Cancel appointment ${appt.appointmentNumber}?`)) {
    return;
  }
  clearAlerts(alerts);
  try {
    await withLoading(button, 'Working...', () => (action === 'CANCEL'
      ? api.cancelAppointment(appt.id)
      : api.updateAppointmentStatus(appt.id, action)));
    await lookup(appt.appointmentNumber);
    showAlert(alerts, action === 'CANCEL' ? 'Appointment cancelled.' : 'Status updated.', 'success');
  } catch (err) {
    showError(alerts, err);
  }
}

async function lookup(rawNumber) {
  const number = rawNumber.trim().toUpperCase();
  showLoading(result, 'Searching...');
  try {
    const appt = await api.getAppointmentByNumber(number);
    const [{ dentists, treatments }, patient] = await Promise.all([
      loadCatalogs(), api.getPatient(appt.patientId).catch(() => null)]);
    render(appt,
      patient,
      dentists.find((d) => d.id === appt.dentistId),
      treatments.find((t) => t.id === appt.treatmentId));
  } catch (err) {
    result.replaceChildren();
    showError(alerts, err);
    throw err;
  }
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  clearFieldErrors(form);
  clearAlerts(alerts);
  const value = form.elements.number.value;
  const problem = validateAppointmentNumber(value);
  if (problem) {
    applyFieldErrors(form, { number: problem });
    return;
  }
  try {
    await withLoading(byId('search-button'), 'Searching...', () => lookup(value));
  } catch {
    // lookup() has already shown the error
  }
});

async function main() {
  const user = await initPage('search');
  if (!user) {
    return;
  }
  const preset = new URLSearchParams(window.location.search).get('number');
  if (preset && !validateAppointmentNumber(preset)) {
    form.elements.number.value = preset.toUpperCase();
    form.requestSubmit();
  }
}

main();
