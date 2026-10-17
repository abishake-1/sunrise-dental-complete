/**
 * Billing: load a COMPLETED appointment, issue the bill (the server calculates it), show the receipt, print it
 * with window.print() (the print stylesheet hides everything but the receipt) and record the payment.
 */
import * as api from './api.js';
import { ApiError } from './api.js';
import {
  applyFieldErrors, badgeHtml, byId, clearAlerts, clearFieldErrors, formatDateTime, formatMoney, formatTime,
  html, initPage, setHtml, showAlert, showError, showLoading, withLoading,
} from './ui.js';
import { validateAppointmentNumber, validateId, validatePercent } from './validators.js';

const alerts = byId('alerts');
const lookupForm = byId('lookup-form');
const billIdForm = byId('bill-id-form');
const billForm = byId('bill-form');
const summary = byId('appointment-summary');
let appointment = null;
let receipt = null;

function hideReceipt() {
  receipt = null;
  byId('receipt-card').hidden = true;
  byId('receipt').replaceChildren();
}

function dash(value) {
  return value === undefined || value === null || value === '' ? '-' : value;
}

function receiptHtml(r) {
  const currency = r.clinic.currency;
  const taxRow = Number(r.tax.amount) > 0
    ? html`<tr><td>Tax (${r.tax.percent}%)</td><td class="num">${formatMoney(r.tax.amount)}</td></tr>` : '';
  const paid = r.payment.status === 'PAID'
    ? html`Paid by ${dash(r.payment.method)} on ${formatDateTime(r.payment.paidAt)}` : html`Not paid yet`;
  return html`
    <article class="receipt" aria-label="Receipt ${r.receiptNumber}">
      <header class="receipt-head">
        <h2>${r.clinic.name}</h2>
        <p>${r.clinic.address}</p>
        <p>Tel: ${r.clinic.phone}</p>
      </header>
      <dl class="receipt-meta">
        <dt>Receipt</dt><dd>${r.receiptNumber}</dd>
        <dt>Issued</dt><dd>${formatDateTime(r.issuedAt)}</dd>
        <dt>Appointment</dt><dd>${r.appointment.number}</dd>
        <dt>Date</dt><dd>${r.appointment.date} ${formatTime(r.appointment.time)}</dd>
        <dt>Patient</dt><dd>${r.patient.name}</dd>
        <dt>Dentist</dt><dd>${r.dentist.name}</dd>
        <dt>Billing</dt><dd>${r.billingType}</dd>
      </dl>
      <table class="receipt-table">
        <thead><tr><th scope="col">Description</th><th scope="col" class="num">Amount (${currency})</th></tr></thead>
        <tbody>
          ${r.lineItems.map((item) => html`<tr><td>${item.description}</td><td class="num">${formatMoney(item.amount)}</td></tr>`)}
          <tr><td>Subtotal</td><td class="num">${formatMoney(r.subtotal)}</td></tr>
          <tr><td>Discount (${r.discount.percent}%)</td><td class="num">- ${formatMoney(r.discount.amount)}</td></tr>
          ${taxRow}
        </tbody>
        <tfoot><tr class="total"><th scope="row">TOTAL</th><td class="num">${currency} ${formatMoney(r.total)}</td></tr></tfoot>
      </table>
      <p class="receipt-pay">Payment: <strong>${r.payment.status}</strong><br>${paid}</p>
      <p class="receipt-thanks">Thank you for choosing ${r.clinic.name}.<br>Issued by ${dash(r.issuedBy)}</p>
    </article>`;
}

function showReceipt(r) {
  receipt = r;
  setHtml(byId('receipt'), receiptHtml(r));
  byId('payment-box').hidden = r.payment.status !== 'UNPAID';
  byId('receipt-card').hidden = false;
  byId('receipt-card').scrollIntoView({ block: 'nearest' });
}

// ---- step 1: appointment --------------------------------------------------------------------------------

async function loadAppointment(rawNumber) {
  const number = rawNumber.trim().toUpperCase();
  hideReceipt();
  byId('appointment-card').hidden = false;
  showLoading(summary);
  billForm.hidden = true;
  byId('complete-box').hidden = true;
  try {
    appointment = await api.getAppointmentByNumber(number);
    const [dentists, treatments, patient] = await Promise.all([
      api.getDentists(), api.getTreatments(), api.getPatient(appointment.patientId).catch(() => null)]);
    const dentist = dentists.find((d) => d.id === appointment.dentistId);
    const treatment = treatments.find((t) => t.id === appointment.treatmentId);
    setHtml(summary, html`
      <h3>${appointment.appointmentNumber} ${badgeHtml(appointment.status)}</h3>
      <dl class="details">
        <dt>Patient</dt><dd>${patient ? `${patient.firstName} ${patient.lastName}` : `Patient #${appointment.patientId}`}</dd>
        <dt>Dentist</dt><dd>${dentist ? dentist.fullName : `Dentist #${appointment.dentistId}`}</dd>
        <dt>Treatment</dt><dd>${treatment ? `${treatment.name} - ${formatMoney(treatment.price)}` : `Treatment #${appointment.treatmentId}`}</dd>
        <dt>Date</dt><dd>${appointment.date} ${formatTime(appointment.time)}</dd>
      </dl>`);
    const status = appointment.status;
    billForm.hidden = status !== 'COMPLETED';
    byId('complete-box').hidden = !(status === 'SCHEDULED' || status === 'CONFIRMED');
    if (status === 'CANCELLED' || status === 'NO_SHOW') {
      showAlert(alerts, `An appointment with status ${status.replace('_', ' ')} cannot be billed.`, 'warning');
    }
  } catch (err) {
    byId('appointment-card').hidden = true;
    appointment = null;
    throw err;
  }
}

lookupForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  clearFieldErrors(lookupForm);
  clearAlerts(alerts);
  const number = lookupForm.elements.number.value;
  const problem = validateAppointmentNumber(number);
  if (problem) {
    applyFieldErrors(lookupForm, { number: problem });
    return;
  }
  try {
    await withLoading(byId('lookup-button'), 'Loading...', () => loadAppointment(number));
  } catch (err) {
    showError(alerts, err);
  }
});

byId('complete-button').addEventListener('click', async (event) => {
  clearAlerts(alerts);
  try {
    await withLoading(event.currentTarget, 'Saving...', async () => {
      await api.updateAppointmentStatus(appointment.id, 'COMPLETED');
      await loadAppointment(appointment.appointmentNumber);
    });
    showAlert(alerts, 'Appointment marked as completed. You can now issue the bill.', 'success');
  } catch (err) {
    showError(alerts, err);
  }
});

// ---- step 2: issue the bill -------------------------------------------------------------------------------

byId('billingType').addEventListener('change', (event) => {
  byId('coverage-field').hidden = event.target.value !== 'INSURANCE';
});

billForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  clearFieldErrors(billForm);
  clearAlerts(alerts);
  const type = byId('billingType').value;
  const coverage = byId('coveragePercent').value.trim();
  const problem = type === 'INSURANCE' ? validatePercent(coverage) : null;
  if (problem) {
    applyFieldErrors(billForm, { coveragePercent: problem });
    return;
  }
  const options = { billingType: type };
  if (type === 'INSURANCE' && coverage) {
    options.insuranceCoveragePercent = Number(coverage);
  }
  try {
    const created = await withLoading(byId('bill-button'), 'Calculating...', () => api.createBill(appointment.id, options));
    showReceipt(created);
    showAlert(alerts, 'Bill issued. You can print the receipt now.', 'success');
  } catch (err) {
    const existing = err instanceof ApiError && err.status === 409 ? /already has bill (\d+)/.exec(err.message) : null;
    if (existing) {
      try {
        showReceipt(await api.getBill(existing[1]));
        showAlert(alerts, 'This appointment was already billed. The existing receipt is shown.', 'info');
        return;
      } catch (inner) {
        showError(alerts, inner);
        return;
      }
    }
    showError(alerts, err, billForm);
  }
});

// ---- open an existing bill --------------------------------------------------------------------------------

billIdForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  clearFieldErrors(billIdForm);
  clearAlerts(alerts);
  const id = billIdForm.elements.billId.value;
  const problem = validateId(id, 'Bill id');
  if (problem) {
    applyFieldErrors(billIdForm, { billId: problem });
    return;
  }
  try {
    const found = await withLoading(byId('bill-id-button'), 'Loading...', () => api.getBill(id.trim()));
    byId('appointment-card').hidden = true;
    showReceipt(found);
  } catch (err) {
    showError(alerts, err);
  }
});

// ---- step 3: print and pay --------------------------------------------------------------------------------

byId('print-button').addEventListener('click', () => window.print());

byId('pay-button').addEventListener('click', async (event) => {
  clearAlerts(alerts);
  try {
    const paid = await withLoading(event.currentTarget, 'Saving...', () => api.payBill(receipt.billId, byId('paymentMethod').value));
    showReceipt(paid);
    showAlert(alerts, 'Payment recorded.', 'success');
  } catch (err) {
    showError(alerts, err);
  }
});

async function main() {
  const user = await initPage('billing');
  if (!user) {
    return;
  }
  const preset = new URLSearchParams(window.location.search).get('number');
  if (preset && !validateAppointmentNumber(preset)) {
    lookupForm.elements.number.value = preset.toUpperCase();
    lookupForm.requestSubmit();
  }
}

main();
