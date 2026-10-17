/**
 * Register Appointment: pick or create the patient, choose dentist, treatment, date and time, then book.
 * Validation runs in the browser first (validators.js); the server's field errors are shown under the inputs.
 */
import * as api from './api.js';
import {
  applyFieldErrors, byId, clearAlerts, clearFieldErrors, el, formatMoney, formatTime, initPage, renderTable,
  clearFieldError, showAlert, showError, showLoading, todayIso, withLoading,
} from './ui.js';
import { validateBookingForm, validatePatientForm, validateSearchTerm } from './validators.js';

const form = byId('booking-form');
const alerts = byId('alerts');
const NEW_PATIENT_FIELDS = ['firstName', 'lastName', 'dateOfBirth', 'gender', 'phone', 'email', 'address', 'medicalNotes'];

let selectedPatient = null;
let dentists = [];
let treatments = [];
let slotRequest = 0;

const mode = () => form.elements.patientMode.value;
const value = (id) => byId(id).value;

function setMode(newMode) {
  byId('existing-panel').hidden = newMode !== 'existing';
  byId('new-panel').hidden = newMode !== 'new';
  clearFieldErrors(form);
}

function patientLabel(p) {
  return `${p.firstName} ${p.lastName} (${p.phone})`;
}

function selectPatient(patient) {
  selectedPatient = patient;
  const box = byId('selected-patient');
  box.textContent = `Selected patient: ${patientLabel(patient)}`;
  box.hidden = false;
  byId('err-patientId').textContent = '';
}

// ---- dropdowns ----------------------------------------------------------------------------------------------

function fillSelect(select, items, label) {
  select.replaceChildren(el('option', { value: '', text: 'Choose...' }),
    ...items.map((item) => el('option', { value: item.id, text: label(item) })));
}

function showDentistInfo() {
  const dentist = dentists.find((d) => String(d.id) === value('dentistId'));
  byId('dentist-info').textContent = dentist
    ? `${dentist.fullName}${dentist.specialization ? ` - ${dentist.specialization}` : ''}. Works ${formatTime(dentist.workStart)} to ${formatTime(dentist.workEnd)}.`
    : '';
}

function showTreatmentInfo() {
  const t = treatments.find((x) => String(x.id) === value('treatmentId'));
  byId('treatment-info').textContent = t
    ? `${t.name}: takes ${t.durationMinutes} minutes, price ${formatMoney(t.price)}.`
    : '';
}

async function showBookedSlots() {
  const box = byId('booked-slots');
  const dentistId = value('dentistId');
  const date = value('date');
  const ticket = ++slotRequest;
  if (!dentistId || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    box.textContent = '';
    return;
  }
  try {
    const items = (await api.listAppointments(date, dentistId)).filter((a) => a.status !== 'CANCELLED');
    if (ticket !== slotRequest) {
      return;
    }
    items.sort((a, b) => String(a.time).localeCompare(String(b.time)));
    box.textContent = items.length
      ? `Already booked for this dentist on ${date}: ${items.map((a) => `${formatTime(a.time)} (${a.appointmentNumber})`).join(', ')}.`
      : `No appointments yet for this dentist on ${date}.`;
  } catch {
    box.textContent = '';
  }
}

// ---- existing patient search -------------------------------------------------------------------------------

async function searchPatients() {
  const results = byId('patient-results');
  const term = value('patient-search');
  const problem = validateSearchTerm(term);
  if (problem) {
    showAlert(alerts, problem, 'warning');
    return;
  }
  clearAlerts(alerts);
  showLoading(results, 'Searching...');
  try {
    const patients = await api.searchPatients(term);
    renderTable(results, [
      { key: 'firstName', label: 'Name', format: (v, p) => `${p.firstName} ${p.lastName}` },
      { key: 'phone', label: 'Phone' },
      { key: 'dateOfBirth', label: 'Date of birth' },
      { key: 'id', label: '', format: (v, p) => el('button', {
        type: 'button', class: 'btn btn-small', onclick: () => selectPatient(p) }, 'Select') },
    ], patients, { emptyText: 'No patient matches. Choose "New patient" to register one.' });
  } catch (err) {
    results.replaceChildren();
    showError(alerts, err);
  }
}

// ---- submit -------------------------------------------------------------------------------------------------

function readNewPatient() {
  const raw = Object.fromEntries(NEW_PATIENT_FIELDS.map((f) => [f, value(f).trim()]));
  raw.gender = raw.gender.toUpperCase();
  return Object.fromEntries(Object.entries(raw).filter(([key, v]) => v !== '' || key === 'gender'));
}

function showResult(appointment, patientName) {
  byId('result-number').textContent = appointment.appointmentNumber;
  byId('result-details').textContent = `${patientName} - ${appointment.date} at ${formatTime(appointment.time)}`;
  byId('result-link').href = `search.html?number=${encodeURIComponent(appointment.appointmentNumber)}`;
  form.hidden = true;
  byId('result-card').hidden = false;
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  clearFieldErrors(form);
  clearAlerts(alerts);
  const isNew = mode() === 'new';
  const booking = {
    dentistId: value('dentistId'), treatmentId: value('treatmentId'),
    date: value('date'), time: value('time'), notes: value('notes').trim(),
  };
  const errors = validateBookingForm(booking);
  const patientValues = isNew ? readNewPatient() : null;
  if (isNew) {
    Object.assign(errors, validatePatientForm(patientValues));
  } else if (!selectedPatient) {
    errors.patientId = 'Search for the patient and press Select.';
  }
  if (applyFieldErrors(form, errors)) {
    return;
  }
  try {
    await withLoading(byId('submit-button'), 'Booking...', async () => {
      if (isNew) {
        selectPatient(await api.registerPatient(patientValues));
        form.elements.patientMode.value = 'existing';   // a retry must not register the patient twice
        setMode('existing');
      }
      const payload = {
        patientId: selectedPatient.id, dentistId: Number(booking.dentistId), treatmentId: Number(booking.treatmentId),
        date: booking.date, time: booking.time,
      };
      if (booking.notes) {
        payload.notes = booking.notes;
      }
      const appointment = await api.bookAppointment(payload);
      showResult(appointment, patientLabel(selectedPatient));
    });
  } catch (err) {
    showError(alerts, err, form);
    if (!isNew || selectedPatient) {
      showBookedSlots();
    }
  }
});

function resetPage() {
  form.reset();
  selectedPatient = null;
  byId('selected-patient').hidden = true;
  byId('patient-results').replaceChildren();
  byId('dentist-info').textContent = '';
  byId('treatment-info').textContent = '';
  byId('booked-slots').textContent = '';
  setMode('existing');
  clearAlerts(alerts);
  byId('result-card').hidden = true;
  form.hidden = false;
}

async function main() {
  const user = await initPage('register');
  if (!user) {
    return;
  }
  byId('date').min = todayIso();
  form.elements.patientMode.forEach((radio) => radio.addEventListener('change', () => setMode(mode())));
  byId('patient-search-button').addEventListener('click', searchPatients);
  byId('patient-search').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      searchPatients();
    }
  });
  byId('dentistId').addEventListener('change', () => { showDentistInfo(); showBookedSlots(); });
  byId('treatmentId').addEventListener('change', showTreatmentInfo);
  byId('date').addEventListener('change', showBookedSlots);
  byId('book-another').addEventListener('click', resetPage);
  NEW_PATIENT_FIELDS.concat(['dentistId', 'treatmentId', 'date', 'time', 'notes']).forEach((id) => {
    byId(id).addEventListener('input', () => clearFieldError(form, id));
  });
  try {
    [dentists, treatments] = await Promise.all([api.getDentists(), api.getTreatments()]);
    fillSelect(byId('dentistId'), dentists, (d) => d.fullName);
    fillSelect(byId('treatmentId'), treatments, (t) => `${t.name} (${t.durationMinutes} min)`);
  } catch (err) {
    showError(alerts, err);
  }
}

main();
