USE sunrise_dental;

-- Passwords are stored as plain text (simple demo system). Log in with admin / admin123 or reception / reception123.
INSERT INTO users (username, password, full_name, email, role) VALUES
('admin',     'admin123',     'Clinic Administrator', 'admin@sunrisedental.example',     'ADMIN'),
('reception', 'reception123', 'Front Desk',           'reception@sunrisedental.example', 'RECEPTIONIST');

INSERT INTO dentists (full_name, specialization, license_number, phone, email, work_start, work_end) VALUES
('Dr. Maria Santos',  'General Dentistry',  'DEN-1001', '+1 555 0101', 'maria.santos@sunrisedental.example',  '09:00:00', '17:00:00'),
('Dr. James Carter',  'Orthodontics',       'DEN-1002', '+1 555 0102', 'james.carter@sunrisedental.example',  '09:00:00', '17:00:00'),
('Dr. Aisha Rahman',  'Pediatric Dentistry','DEN-1003', '+1 555 0103', 'aisha.rahman@sunrisedental.example',  '10:00:00', '18:00:00'),
('Dr. Daniel Kim',    'Endodontics',        'DEN-1004', '+1 555 0104', 'daniel.kim@sunrisedental.example',    '08:00:00', '16:00:00');

INSERT INTO treatments (code, name, description, duration_minutes, price) VALUES
('CHK', 'Check-up & Consultation',    'Examination and treatment advice',           30,  25.00),
('CLN', 'Scaling & Polishing',        'Professional teeth cleaning',                45,  60.00),
('FIL', 'Dental Filling',             'Composite filling for a cavity',             45,  90.00),
('EXT', 'Tooth Extraction',           'Simple extraction under local anaesthetic',  30,  80.00),
('RCT', 'Root Canal Treatment',       'Single-canal root canal therapy',            90, 350.00),
('CRN', 'Dental Crown',               'Porcelain crown fitting',                    60, 500.00),
('WHT', 'Teeth Whitening',            'In-clinic whitening session',                60, 200.00),
('ORT', 'Orthodontic Adjustment',     'Braces check and wire adjustment',           30,  75.00);
