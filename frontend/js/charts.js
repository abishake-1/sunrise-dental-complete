/**
 * Simple horizontal bar charts drawn with the HTML canvas (no chart library), plus small data helpers.
 */

/**
 * Rounds a value up to a "nice" axis maximum (1, 2, 5 or 10 times a power of ten).
 *
 * @param {number} value the largest data value
 * @returns {number} the axis maximum
 */
export function niceCeil(value) {
  if (!Number.isFinite(value) || value <= 0) {
    return 1;
  }
  const exponent = 10 ** Math.floor(Math.log10(value));
  const fraction = value / exponent;
  const nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
  return nice * exponent;
}

/**
 * Sums numeric columns per label (for example appointments per dentist over many days).
 *
 * @param {Array<object>} rows report rows
 * @param {string} labelKey column used as the label
 * @param {string[]} valueKeys numeric columns to add up
 * @returns {Array<object>} one row per label, in first-seen order
 */
export function aggregateRows(rows, labelKey, valueKeys) {
  const groups = new Map();
  for (const row of rows) {
    const label = String(row[labelKey] ?? '-');
    if (!groups.has(label)) {
      groups.set(label, Object.fromEntries([[labelKey, label], ...valueKeys.map((k) => [k, 0])]));
    }
    const group = groups.get(label);
    for (const key of valueKeys) {
      group[key] += Number(row[key]) || 0;
    }
  }
  return [...groups.values()];
}

/**
 * Shortens text with an ellipsis so it fits a width.
 *
 * @param {(text: string) => number} measure returns the pixel width of a text
 * @param {string} text the label
 * @param {number} maxWidth available width in pixels
 * @returns {string} the label, possibly cut and ending with an ellipsis
 */
export function truncateText(measure, text, maxWidth) {
  if (measure(text) <= maxWidth) {
    return text;
  }
  let cut = text.length;
  while (cut > 1 && measure(`${text.slice(0, cut)}\u2026`) > maxWidth) {
    cut -= 1;
  }
  return `${text.slice(0, cut)}\u2026`;
}

function theme() {
  const css = getComputedStyle(document.documentElement);
  const read = (name, fallback) => css.getPropertyValue(name).trim() || fallback;
  return {
    colors: [read('--chart-1', '#0b7285'), read('--chart-2', '#e8590c'), read('--chart-3', '#5f3dc4')],
    text: read('--text', '#1b2733'),
    muted: read('--muted', '#5b6b7a'),
    grid: read('--border', '#d5dde5'),
  };
}

/**
 * Draws grouped horizontal bars on a canvas. The canvas is resized to its container and to the number of
 * labels, and drawn sharp on high-density screens.
 *
 * @param {HTMLCanvasElement} canvas the canvas to draw on
 * @param {{labels: string[], series: Array<{name: string, values: number[]}>, suffix?: string,
 *     decimals?: number, max?: number|null}} chart the data
 */
export function drawBarChart(canvas, { labels, series, suffix = '', decimals = 0, max = null }) {
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return;
  }
  const colors = theme();
  const ratio = window.devicePixelRatio || 1;
  const width = Math.max(300, Math.floor((canvas.parentElement ? canvas.parentElement.clientWidth : 0) || 640));
  const barHeight = 14;
  const groupHeight = barHeight * series.length + 14;
  const legendHeight = series.length > 1 ? 26 : 0;
  const pad = { top: 10 + legendHeight, right: 64, bottom: 26, left: Math.min(Math.floor(width * 0.3), 220) };
  const height = pad.top + pad.bottom + labels.length * groupHeight;

  canvas.width = Math.floor(width * ratio);
  canvas.height = Math.floor(height * ratio);
  canvas.style.width = `${width}px`;
  canvas.style.height = `${height}px`;
  ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
  ctx.clearRect(0, 0, width, height);
  ctx.font = '12px system-ui, -apple-system, "Segoe UI", sans-serif';
  ctx.textBaseline = 'middle';

  const plotWidth = width - pad.left - pad.right;
  const highest = Math.max(...series.flatMap((s) => s.values), 0);
  const axisMax = max !== null && max !== undefined ? max : niceCeil(highest);
  const format = (v) => `${Number(v).toLocaleString('en-US', {
    minimumFractionDigits: decimals, maximumFractionDigits: decimals })}${suffix}`;

  // grid lines and axis labels
  const ticks = 5;
  ctx.textAlign = 'center';
  for (let i = 0; i <= ticks; i += 1) {
    const x = pad.left + (plotWidth * i) / ticks;
    ctx.strokeStyle = colors.grid;
    ctx.beginPath();
    ctx.moveTo(x, pad.top);
    ctx.lineTo(x, height - pad.bottom);
    ctx.stroke();
    ctx.fillStyle = colors.muted;
    ctx.fillText(format((axisMax * i) / ticks), x, height - pad.bottom + 14);
  }

  // legend
  if (series.length > 1) {
    let x = pad.left;
    ctx.textAlign = 'left';
    series.forEach((s, j) => {
      ctx.fillStyle = colors.colors[j % colors.colors.length];
      ctx.fillRect(x, 8, 12, 12);
      ctx.fillStyle = colors.text;
      ctx.fillText(s.name, x + 18, 14);
      x += 40 + ctx.measureText(s.name).width;
    });
  }

  // bars
  labels.forEach((label, i) => {
    const top = pad.top + i * groupHeight + 7;
    ctx.textAlign = 'right';
    ctx.fillStyle = colors.text;
    const shown = truncateText((t) => ctx.measureText(t).width, label, pad.left - 12);
    ctx.fillText(shown, pad.left - 8, top + (barHeight * series.length) / 2);
    series.forEach((s, j) => {
      const value = Number(s.values[i]) || 0;
      const w = Math.max(0, Math.min(plotWidth, (plotWidth * value) / axisMax));
      const y = top + j * barHeight;
      ctx.fillStyle = colors.colors[j % colors.colors.length];
      ctx.fillRect(pad.left, y, w, barHeight - 2);
      ctx.textAlign = 'left';
      ctx.fillStyle = colors.text;
      ctx.fillText(format(value), pad.left + w + 5, y + (barHeight - 2) / 2);
    });
  });
}
