import test from 'node:test';
import assert from 'node:assert/strict';
import { escapeHtml, html, SafeHtml, setHtml, formatMoney, formatTime, formatDateTime, humanizeKey, todayIso } from '../js/ui.js';
import { niceCeil, aggregateRows, truncateText } from '../js/charts.js';
import { menuFor } from '../js/auth.js';

test('escapeHtml neutralises markup and quotes', () => {
  assert.equal(escapeHtml('<img src=x onerror="alert(1)">'), '&lt;img src=x onerror=&quot;alert(1)&quot;&gt;');
  assert.equal(escapeHtml("a'b&c`d"), 'a&#39;b&amp;c&#96;d');
  assert.equal(escapeHtml(null), '');
  assert.equal(escapeHtml(5), '5');
});

test('html`` escapes interpolations but keeps nested safe markup', () => {
  const evil = '<script>alert(1)</script>';
  const out = html`<p>${evil}</p>${[html`<b>${'<i>'}</b>`, 'x&y']}`;
  assert.ok(out instanceof SafeHtml);
  assert.equal(out.value, '<p>&lt;script&gt;alert(1)&lt;/script&gt;</p><b>&lt;i&gt;</b>x&amp;y');
  assert.equal(html`<td class="${'a" onclick="x'}">`.value, '<td class="a&quot; onclick=&quot;x">');
});

test('setHtml refuses plain strings', () => {
  assert.throws(() => setHtml({}, '<b>raw</b>'), TypeError);
});

test('formatting helpers', () => {
  assert.equal(formatMoney(1234.5), '1,234.50');
  assert.equal(formatMoney('abc'), '-');
  assert.equal(formatTime('09:00:00'), '09:00');
  assert.equal(formatDateTime('2026-10-14T09:05:33'), '2026-10-14 09:05');
  assert.equal(humanizeKey('treatmentName'), 'Treatment name');
  assert.equal(humanizeKey('noShowRatePercent'), 'No show rate percent');
  assert.equal(todayIso(new Date(2026, 0, 5)), '2026-01-05');
});

test('role-based menu hides Reports for non-admins', () => {
  const admin = menuFor('ADMIN').map((i) => i.key);
  const staff = menuFor('RECEPTIONIST').map((i) => i.key);
  assert.deepEqual(admin, ['register', 'search', 'billing', 'reports', 'help', 'exit']);
  assert.deepEqual(staff, ['register', 'search', 'billing', 'help', 'exit']);
});

test('chart helpers', () => {
  assert.equal(niceCeil(0), 1);
  assert.equal(niceCeil(7), 10);
  assert.equal(niceCeil(23), 50);
  assert.equal(niceCeil(120), 200);
  const rows = [
    { dentistName: 'A', total: 2 }, { dentistName: 'B', total: 1 }, { dentistName: 'A', total: 3 },
  ];
  assert.deepEqual(aggregateRows(rows, 'dentistName', ['total']),
    [{ dentistName: 'A', total: 5 }, { dentistName: 'B', total: 1 }]);
  const measure = (t) => t.length * 10;
  assert.equal(truncateText(measure, 'short', 100), 'short');
  assert.equal(truncateText(measure, 'a very long label', 80), 'a very \u2026');
});
