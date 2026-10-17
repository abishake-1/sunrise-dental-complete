/** Dashboard: role-based menu cards and today's appointments. */
import * as api from './api.js';
import { menuFor } from './auth.js';
import {
  badge, byId, el, formatTime, initPage, renderTable, showAlert, showError, showLoading, todayIso,
} from './ui.js';

async function main() {
  const user = await initPage('dashboard');
  if (!user) {
    return;
  }
  const alerts = byId('alerts');
  if (new URLSearchParams(window.location.search).get('denied') === '1') {
    showAlert(alerts, 'That page is only available to administrators.', 'warning');
  }
  byId('welcome').textContent = `Welcome, ${user.fullName}. Choose what you want to do.`;

  byId('menu').replaceChildren(...menuFor(user.role).map((item) => {
    const inner = [el('span', { class: 'menu-title', text: item.label }),
      el('span', { class: 'menu-desc', text: item.description })];
    const card = item.action === 'logout'
      ? el('button', { type: 'button', class: 'menu-card', onclick: () => api.logout() }, inner)
      : el('a', { class: 'menu-card', href: item.href }, inner);
    return el('li', {}, card);
  }));

  const table = byId('today-table');
  showLoading(table);
  try {
    const [appointments, dentists, treatments] = await Promise.all([
      api.listAppointments(todayIso()), api.getDentists(), api.getTreatments()]);
    const dentistName = new Map(dentists.map((d) => [d.id, d.fullName]));
    const treatmentName = new Map(treatments.map((t) => [t.id, t.name]));
    const rows = appointments.slice().sort((a, b) => String(a.time).localeCompare(String(b.time)));
    byId('today-summary').textContent = rows.length === 1 ? '1 appointment today.' : `${rows.length} appointments today.`;
    renderTable(table, [
      { key: 'time', label: 'Time', format: (v) => formatTime(v) },
      { key: 'appointmentNumber', label: 'Number',
        format: (v) => el('a', { href: `search.html?number=${encodeURIComponent(v)}`, text: v }) },
      { key: 'dentistId', label: 'Dentist', format: (v) => dentistName.get(v) || `Dentist #${v}` },
      { key: 'treatmentId', label: 'Treatment', format: (v) => treatmentName.get(v) || `Treatment #${v}` },
      { key: 'status', label: 'Status', format: (v) => badge(v) },
    ], rows, { emptyText: 'No appointments are booked for today.' });
  } catch (err) {
    table.replaceChildren();
    showError(alerts, err);
  }
}

main();
