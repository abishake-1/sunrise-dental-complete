// Run: node --test frontend/tests   (Node 18+, no npm needed)
import test from 'node:test';
import assert from 'node:assert/strict';
import {
  validateName, validatePhone, validateEmail, validateAddress, validateAppointmentTime, validateSchedule,
  validateAppointmentNumber, validateDateOfBirth, validateDateRange, validatePatientForm, validateBookingForm,
  validatePercent, validateId, isRealDate, localDate,
} from '../js/validators.js';

const NOW = new Date(2026, 9, 14, 10, 30); // 14 Oct 2026, 10:30 local time

test('phone must be 07XXXXXXXX', () => {
  assert.equal(validatePhone('0771234567'), null);
  for (const bad of ['', '0671234567', '077123456', '07712345678', '+94771234567', '07712 34567', '07a1234567']) {
    assert.notEqual(validatePhone(bad), null, bad);
  }
});

test('names accept letters, spaces, hyphens, apostrophes and accents only', () => {
  for (const good of ['Nimal', "O'Brien", 'Mary-Jane', 'De Silva', 'Chaminda', 'José', 'நிமால்']) {
    assert.equal(validateName(good, 'Name'), null, good);
  }
  for (const bad of ['', ' ', 'R2D2', 'John3', 'A  B', '-Anna', "Anna'", 'a@b', '<script>', 'x'.repeat(51)]) {
    assert.notEqual(validateName(bad, 'Name'), null, bad);
  }
});

test('address is optional but 5..255 characters when given', () => {
  assert.equal(validateAddress(''), null);
  assert.equal(validateAddress('12 Main St'), null);
  assert.notEqual(validateAddress('Abc'), null);
  assert.notEqual(validateAddress('x'.repeat(256)), null);
  assert.notEqual(validateAddress('', { required: true }), null);
});

test('email optional, otherwise shaped like an address', () => {
  assert.equal(validateEmail(''), null);
  assert.equal(validateEmail('a@b.lk'), null);
  assert.notEqual(validateEmail('nope'), null);
  assert.notEqual(validateEmail('a@b'), null);
});

test('time must be 08:00-18:00 on a 15-minute boundary', () => {
  for (const good of ['08:00', '09:15', '12:30', '17:45', '18:00']) {
    assert.equal(validateAppointmentTime(good), null, good);
  }
  for (const bad of ['', '07:45', '18:15', '09:10', '9:00', '25:00', '09:60']) {
    assert.notEqual(validateAppointmentTime(bad), null, bad);
  }
});

test('date must be today or later; today needs a later time', () => {
  assert.deepEqual(validateSchedule('2026-10-15', '09:00', NOW), {});
  assert.deepEqual(validateSchedule('2026-10-14', '11:00', NOW), {});
  assert.ok(validateSchedule('2026-10-13', '09:00', NOW).date);
  assert.ok(validateSchedule('2026-10-14', '10:30', NOW).time);
  assert.ok(validateSchedule('2026-10-14', '09:00', NOW).time);
  assert.ok(validateSchedule('2026-02-30', '09:00', NOW).date);
  assert.ok(validateSchedule('', '', NOW).date);
  assert.ok(validateSchedule('', '', NOW).time);
});

test('appointment number', () => {
  assert.equal(validateAppointmentNumber('APT-0001'), null);
  assert.equal(validateAppointmentNumber('apt-12345'), null);
  assert.notEqual(validateAppointmentNumber('APT-1'), null);
  assert.notEqual(validateAppointmentNumber('0001'), null);
});

test('date helpers', () => {
  assert.equal(localDate(NOW), '2026-10-14');
  assert.equal(isRealDate('2024-02-29'), true);
  assert.equal(isRealDate('2026-02-29'), false);
  assert.equal(isRealDate('2026-13-01'), false);
  assert.equal(validateDateOfBirth('1990-05-20', NOW), null);
  assert.notEqual(validateDateOfBirth('2027-01-01', NOW), null);
  assert.deepEqual(validateDateRange('2026-10-01', '2026-10-14'), {});
  assert.ok(validateDateRange('2026-10-15', '2026-10-14').to);
});

test('forms collect every error at once', () => {
  const errors = validatePatientForm({
    firstName: '', lastName: 'R2', dateOfBirth: '', gender: '', phone: '123', email: 'x', address: 'ab', medicalNotes: '',
  }, NOW);
  assert.deepEqual(Object.keys(errors).sort(),
    ['address', 'dateOfBirth', 'email', 'firstName', 'gender', 'lastName', 'phone']);
  assert.deepEqual(validatePatientForm({
    firstName: 'Nimal', lastName: 'Perera', dateOfBirth: '1990-05-20', gender: 'MALE', phone: '0771234567',
    email: '', address: '', medicalNotes: '',
  }, NOW), {});
  const booking = validateBookingForm({ dentistId: '', treatmentId: '2', date: '2020-01-01', time: '03:00', notes: '' }, NOW);
  assert.deepEqual(Object.keys(booking).sort(), ['date', 'dentistId', 'time']);
});

test('small helpers', () => {
  assert.equal(validatePercent(''), null);
  assert.equal(validatePercent('50'), null);
  assert.notEqual(validatePercent('101'), null);
  assert.notEqual(validatePercent('abc'), null);
  assert.equal(validateId('12', 'Dentist'), null);
  assert.notEqual(validateId('0', 'Dentist'), null);
});
