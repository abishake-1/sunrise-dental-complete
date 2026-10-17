/**
 * Reports (ADMIN only): tables, simple canvas bar charts and CSV download.
 */
import * as api from './api.js';
import { aggregateRows, drawBarChart } from './charts.js';
import {
  applyFieldErrors, byId, clearAlerts, clearFieldErrors, el, formatMoney, humanizeKey, initPage, renderTable,
  showError, showLoading, todayIso, withLoading,
} from './ui.js';
import { validateDateRange } from './validators.js';

const MONEY_KEYS = new Set(['billed', 'collected', 'treatmentRevenue', 'consultationFees', 'discounts', 'tax']);
const HIDDEN_KEYS = new Set(['dentistId']);
const MAX_BARS = 30;

/** What each report needs and how it is charted. */
const REPORTS = {
  'daily-appointments': { range: true, label: 'dentistName', aggregate: true,
    series: [{ key: 'total', name: 'Appointments' }] },
  'monthly-revenue': { month: true, label: 'treatmentName', decimals: 2,
    series: [{ key: 'billed', name: 'Billed' }, { key: 'collected', name: 'Collected' }] },
  'top-treatments': { range: true, limit: true, label: 'treatmentName',
    series: [{ key: 'appointments', name: 'Appointments' }] },
  'dentist-utilization': { range: true, label: 'dentistName', suffix: '%', decimals: 1, max: 100,
    series: [{ key: 'utilizationPercent', name: 'Utilization' }] },
  'cancellation-rate': { range: true, label: 'dentistName', suffix: '%', decimals: 1,
    series: [{ key: 'cancellationRatePercent', name: 'Cancelled' }, { key: 'noShowRatePercent', name: 'No-show' }] },
};

const alerts = byId('alerts');
const form = byId('report-form');
let lastChart = null;

const slug = () => byId('report').value;

function updateFields() {
  const def = REPORTS[slug()];
  byId('range-fields').hidden = !def.range;
  byId('month-fields').hidden = !def.month;
  byId('limit-field').hidden = !def.limit;
}

function readParams() {
  const def = REPORTS[slug()];
  const errors = {};
  const params = {};
  if (def.month) {
    const year = Number(byId('year').value);
    if (!Number.isInteger(year) || year < 2000 || year > 2100) {
      errors.year = 'Enter a year between 2000 and 2100.';
    }
    params.year = byId('year').value.trim();
    params.month = byId('month').value;
  } else {
    Object.assign(errors, validateDateRange(byId('from').value, byId('to').value));
    params.from = byId('from').value.trim();
    params.to = byId('to').value.trim();
  }
  if (def.limit) {
    const limit = Number(byId('limit').value);
    if (!Number.isInteger(limit) || limit < 1 || limit > 50) {
      errors.limit = 'Enter a whole number from 1 to 50.';
    }
    params.limit = byId('limit').value.trim();
  }
  return { params, errors };
}

function formatCell(key) {
  if (MONEY_KEYS.has(key)) {
    return (v) => formatMoney(v);
  }
  if (key.endsWith('Percent')) {
    return (v) => (v === null || v === undefined ? '-' : `${v}%`);
  }
  return undefined;
}

function renderSummary(slugName, rows) {
  const box = byId('summary');
  const items = [];
  if (slugName === 'monthly-revenue') {
    const sum = (key) => rows.reduce((total, r) => total + (Number(r[key]) || 0), 0);
    items.push(['Bills', String(sum('bills'))], ['Billed', formatMoney(sum('billed'))],
      ['Collected', formatMoney(sum('collected'))], ['Discounts', formatMoney(sum('discounts'))]);
  } else {
    items.push(['Rows', String(rows.length)]);
  }
  box.replaceChildren(...items.map(([label, value]) =>
    el('div', {}, el('span', { class: 'muted', text: label }), el('strong', { text: value }))));
}

function renderChart(def, rows, title) {
  const chartBox = byId('chart-box');
  const canvas = byId('chart');
  let data = def.aggregate ? aggregateRows(rows, def.label, def.series.map((s) => s.key)) : rows;
  data = data.slice(0, MAX_BARS);
  if (!data.length) {
    chartBox.hidden = true;
    lastChart = null;
    return;
  }
  chartBox.hidden = false;
  lastChart = {
    canvas,
    spec: {
      labels: data.map((r) => String(r[def.label])),
      series: def.series.map((s) => ({ name: s.name, values: data.map((r) => Number(r[s.key]) || 0) })),
      suffix: def.suffix || '', decimals: def.decimals || 0, max: def.max ?? null,
    },
  };
  canvas.setAttribute('aria-label', `Bar chart: ${title}`);
  drawBarChart(canvas, lastChart.spec);
}

function render(report, def) {
  byId('output').hidden = false;
  byId('report-title').textContent = report.title || humanizeKey(report.report);
  const params = Object.entries(report.parameters || {}).map(([k, v]) => `${humanizeKey(k)}: ${v}`).join(' | ');
  byId('report-range').textContent = params;
  const rows = report.rows || [];
  renderSummary(slug(), rows);
  renderChart(def, rows, byId('report-title').textContent);
  const columns = report.columns.filter((key) => !HIDDEN_KEYS.has(key)).map((key) => ({
    key,
    label: humanizeKey(key),
    align: rows.some((r) => typeof r[key] === 'number') ? 'right' : 'left',
    format: formatCell(key),
  }));
  renderTable(byId('table'), columns, rows, { emptyText: 'No data for this period.' });
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  clearFieldErrors(form);
  clearAlerts(alerts);
  const { params, errors } = readParams();
  if (applyFieldErrors(form, errors)) {
    return;
  }
  const def = REPORTS[slug()];
  byId('output').hidden = false;
  showLoading(byId('table'));
  try {
    const report = await withLoading(byId('run-button'), 'Loading...', () => api.getReport(slug(), params));
    render(report, def);
  } catch (err) {
    byId('output').hidden = true;
    showError(alerts, err, form);
  }
});

byId('csv-button').addEventListener('click', async (event) => {
  clearFieldErrors(form);
  clearAlerts(alerts);
  const { params, errors } = readParams();
  if (applyFieldErrors(form, errors)) {
    return;
  }
  try {
    const file = await withLoading(event.currentTarget, 'Preparing...', () => api.downloadReportCsv(slug(), params));
    const url = URL.createObjectURL(file.blob);
    const link = el('a', { href: url, download: file.filename });
    document.body.append(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (err) {
    showError(alerts, err, form);
  }
});

byId('print-button').addEventListener('click', () => window.print());
byId('report').addEventListener('change', updateFields);

let resizeTimer = null;
window.addEventListener('resize', () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    if (lastChart && !byId('chart-box').hidden) {
      drawBarChart(lastChart.canvas, lastChart.spec);
    }
  }, 150);
});

async function main() {
  const user = await initPage('reports', { roles: ['ADMIN'] });
  if (!user) {
    return;
  }
  const today = new Date();
  byId('from').value = `${todayIso(today).slice(0, 8)}01`;
  byId('to').value = todayIso(today);
  byId('year').value = String(today.getFullYear());
  byId('month').value = String(today.getMonth() + 1);
  updateFields();
}

main();
