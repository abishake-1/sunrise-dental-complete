-- =====================================================================
-- Sunrise Dental Clinic - schema            Requires MySQL 8.0.19+
-- Run with the mysql CLI (it understands DELIMITER):
--     mysql -u root -p < database/schema.sql
-- WARNING: drops and recreates all tables (development convenience).
-- =====================================================================

CREATE DATABASE IF NOT EXISTS sunrise_dental
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE sunrise_dental;

DROP PROCEDURE IF EXISTS sp_calculate_bill;
DROP PROCEDURE IF EXISTS sp_daily_appointments;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS bills;
DROP TABLE IF EXISTS appointments;
DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS treatments;
DROP TABLE IF EXISTS dentists;
DROP TABLE IF EXISTS patients;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS sequence_counters;

CREATE TABLE sequence_counters (
    sequence_name VARCHAR(30)  NOT NULL,
    next_value    INT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (sequence_name),
    CONSTRAINT chk_seq_next CHECK (next_value >= 1)
) ENGINE=InnoDB;

INSERT INTO sequence_counters (sequence_name, next_value) VALUES ('APPOINTMENT', 1);

CREATE TABLE users (
    user_id       INT UNSIGNED NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password      VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    is_active     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email    UNIQUE (email),
    CONSTRAINT chk_users_username CHECK (username REGEXP '^[A-Za-z0-9_.]{3,50}$'),
    CONSTRAINT chk_users_email    CHECK (email LIKE '%_@_%.__%'),
    CONSTRAINT chk_users_role     CHECK (role IN ('ADMIN', 'RECEPTIONIST')),
    CONSTRAINT chk_users_active   CHECK (is_active IN (0, 1)),
    CONSTRAINT chk_users_password CHECK (CHAR_LENGTH(password) >= 4)
) ENGINE=InnoDB;

CREATE TABLE patients (
    patient_id    INT UNSIGNED NOT NULL AUTO_INCREMENT,
    first_name    VARCHAR(50)  NOT NULL,
    last_name     VARCHAR(50)  NOT NULL,
    date_of_birth DATE         NOT NULL,
    gender        VARCHAR(10)  NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    email         VARCHAR(120) NULL,
    address       VARCHAR(255) NULL,
    medical_notes TEXT         NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (patient_id),
    CONSTRAINT uq_patients_email UNIQUE (email),
    CONSTRAINT chk_patients_names  CHECK (CHAR_LENGTH(TRIM(first_name)) > 0
                                          AND CHAR_LENGTH(TRIM(last_name)) > 0),
    CONSTRAINT chk_patients_dob    CHECK (date_of_birth >= '1900-01-01'),
    CONSTRAINT chk_patients_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT chk_patients_phone  CHECK (phone REGEXP '^[0-9+() -]{7,20}$'),
    CONSTRAINT chk_patients_email  CHECK (email IS NULL OR email LIKE '%_@_%.__%'),
    INDEX idx_patients_name  (last_name, first_name),
    INDEX idx_patients_phone (phone)
) ENGINE=InnoDB;

CREATE TABLE dentists (
    dentist_id     INT UNSIGNED NOT NULL AUTO_INCREMENT,
    full_name      VARCHAR(100) NOT NULL,
    specialization VARCHAR(80)  NOT NULL,
    license_number VARCHAR(30)  NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    email          VARCHAR(120) NOT NULL,
    work_start     TIME         NOT NULL DEFAULT '09:00:00',
    work_end       TIME         NOT NULL DEFAULT '17:00:00',
    is_active      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (dentist_id),
    CONSTRAINT uq_dentists_license UNIQUE (license_number),
    CONSTRAINT uq_dentists_email   UNIQUE (email),
    CONSTRAINT chk_dentists_phone  CHECK (phone REGEXP '^[0-9+() -]{7,20}$'),
    CONSTRAINT chk_dentists_email  CHECK (email LIKE '%_@_%.__%'),
    CONSTRAINT chk_dentists_hours  CHECK (work_start < work_end),
    CONSTRAINT chk_dentists_active CHECK (is_active IN (0, 1)),
    INDEX idx_dentists_active (is_active, full_name)
) ENGINE=InnoDB;

CREATE TABLE treatments (
    treatment_id     INT UNSIGNED      NOT NULL AUTO_INCREMENT,
    code             VARCHAR(10)       NOT NULL,
    name             VARCHAR(100)      NOT NULL,
    description      VARCHAR(500)      NULL,
    duration_minutes SMALLINT UNSIGNED NOT NULL,
    price            DECIMAL(10,2)     NOT NULL,
    is_active        TINYINT(1)        NOT NULL DEFAULT 1,
    created_at       DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (treatment_id),
    CONSTRAINT uq_treatments_code UNIQUE (code),
    CONSTRAINT uq_treatments_name UNIQUE (name),
    CONSTRAINT chk_treatments_duration CHECK (duration_minutes BETWEEN 5 AND 480),
    CONSTRAINT chk_treatments_price    CHECK (price >= 0),
    CONSTRAINT chk_treatments_active   CHECK (is_active IN (0, 1))
) ENGINE=InnoDB;

CREATE TABLE appointments (
    appointment_id     INT UNSIGNED NOT NULL AUTO_INCREMENT,
    appointment_number VARCHAR(12)  NOT NULL,
    patient_id         INT UNSIGNED NOT NULL,
    dentist_id         INT UNSIGNED NOT NULL,
    treatment_id       INT UNSIGNED NOT NULL,
    appointment_date   DATE         NOT NULL,
    appointment_time   TIME         NOT NULL,
    status             VARCHAR(15)  NOT NULL DEFAULT 'SCHEDULED',
    notes              VARCHAR(500) NULL,
    created_by         INT UNSIGNED NOT NULL,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (appointment_id),
    CONSTRAINT uq_appointments_number UNIQUE (appointment_number),
    CONSTRAINT uq_appointments_slot   UNIQUE (dentist_id, appointment_date, appointment_time),
    CONSTRAINT fk_appointments_patient   FOREIGN KEY (patient_id)   REFERENCES patients   (patient_id)   ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_appointments_dentist   FOREIGN KEY (dentist_id)   REFERENCES dentists   (dentist_id)   ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_appointments_treatment FOREIGN KEY (treatment_id) REFERENCES treatments (treatment_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_appointments_user      FOREIGN KEY (created_by)   REFERENCES users      (user_id)      ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_appointments_number CHECK (appointment_number REGEXP '^APT-[0-9]{4,}$'),
    CONSTRAINT chk_appointments_status CHECK (status IN ('SCHEDULED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW')),
    CONSTRAINT chk_appointments_grid   CHECK (SECOND(appointment_time) = 0 AND MINUTE(appointment_time) % 15 = 0),
    INDEX idx_appointments_date_status (appointment_date, status),
    INDEX idx_appointments_patient     (patient_id, appointment_date)
) ENGINE=InnoDB;

CREATE TABLE bills (
    bill_id          INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    appointment_id   INT UNSIGNED  NOT NULL,
    subtotal         DECIMAL(10,2) NOT NULL,
    discount_percent DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
    discount_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    tax_percent      DECIMAL(5,2)  NOT NULL DEFAULT 0.00,
    tax_amount       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_amount     DECIMAL(10,2) NOT NULL,
    payment_status   VARCHAR(10)   NOT NULL DEFAULT 'UNPAID',
    payment_method   VARCHAR(15)   NULL,
    paid_at          DATETIME      NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (bill_id),
    CONSTRAINT uq_bills_appointment UNIQUE (appointment_id),
    CONSTRAINT fk_bills_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (appointment_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_bills_subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_bills_discount CHECK (discount_percent BETWEEN 0 AND 100
                                         AND discount_amount >= 0 AND discount_amount <= subtotal),
    CONSTRAINT chk_bills_tax      CHECK (tax_percent BETWEEN 0 AND 100 AND tax_amount >= 0),
    CONSTRAINT chk_bills_total    CHECK (total_amount = subtotal - discount_amount + tax_amount),
    CONSTRAINT chk_bills_status   CHECK (payment_status IN ('UNPAID', 'PAID', 'VOID')),
    CONSTRAINT chk_bills_method   CHECK (payment_method IS NULL
                                         OR payment_method IN ('CASH','CARD','ONLINE','INSURANCE')),
    CONSTRAINT chk_bills_paid     CHECK ((payment_status = 'PAID' AND paid_at IS NOT NULL AND payment_method IS NOT NULL)
                                         OR (payment_status <> 'PAID' AND paid_at IS NULL)),
    INDEX idx_bills_status (payment_status, created_at)
) ENGINE=InnoDB;

CREATE TABLE audit_log (
    audit_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    table_name   VARCHAR(64)     NOT NULL,
    record_id    BIGINT UNSIGNED NOT NULL,
    action       VARCHAR(10)     NOT NULL,
    old_values   JSON            NULL,
    new_values   JSON            NULL,
    performed_by VARCHAR(100)    NOT NULL,
    performed_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (audit_id),
    CONSTRAINT chk_audit_action CHECK (action IN ('INSERT', 'UPDATE', 'DELETE')),
    INDEX idx_audit_record (table_name, record_id),
    INDEX idx_audit_time   (performed_at)
) ENGINE=InnoDB;

CREATE TABLE notifications (
    notification_id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    patient_id      INT UNSIGNED NOT NULL,
    appointment_id  INT UNSIGNED NULL,
    channel         VARCHAR(10)  NOT NULL,
    type            VARCHAR(25)  NOT NULL,
    message         VARCHAR(500) NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at         DATETIME     NULL,
    PRIMARY KEY (notification_id),
    CONSTRAINT fk_notifications_patient     FOREIGN KEY (patient_id)     REFERENCES patients     (patient_id)     ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_notifications_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (appointment_id) ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL', 'SMS', 'IN_APP')),
    CONSTRAINT chk_notifications_type    CHECK (type IN ('BOOKING_CONFIRMATION','REMINDER','CANCELLATION','BILL')),
    CONSTRAINT chk_notifications_status  CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT chk_notifications_message CHECK (CHAR_LENGTH(TRIM(message)) > 0),
    INDEX idx_notifications_queue   (status, created_at),
    INDEX idx_notifications_patient (patient_id, created_at)
) ENGINE=InnoDB;

DELIMITER $$

CREATE TRIGGER trg_appointments_bi
BEFORE INSERT ON appointments
FOR EACH ROW
BEGIN
    DECLARE v_next INT UNSIGNED;

    IF NEW.appointment_date < CURDATE() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Appointments cannot be scheduled in the past';
    END IF;

    SELECT next_value INTO v_next
      FROM sequence_counters WHERE sequence_name = 'APPOINTMENT' FOR UPDATE;
    UPDATE sequence_counters SET next_value = v_next + 1
     WHERE sequence_name = 'APPOINTMENT';

    SET NEW.appointment_number = CONCAT('APT-',
        IF(v_next < 10000, LPAD(v_next, 4, '0'), CAST(v_next AS CHAR)));
END$$

CREATE TRIGGER trg_appointments_ai
AFTER INSERT ON appointments
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (table_name, record_id, action, old_values, new_values, performed_by)
    VALUES ('appointments', NEW.appointment_id, 'INSERT', NULL,
            JSON_OBJECT('appointment_number', NEW.appointment_number,
                        'patient_id',   NEW.patient_id,
                        'dentist_id',   NEW.dentist_id,
                        'treatment_id', NEW.treatment_id,
                        'date',         NEW.appointment_date,
                        'time',         NEW.appointment_time,
                        'status',       NEW.status),
            COALESCE(@app_user, CURRENT_USER()));
END$$

CREATE TRIGGER trg_appointments_bu
BEFORE UPDATE ON appointments
FOR EACH ROW
BEGIN
    IF (NEW.appointment_date <> OLD.appointment_date OR NEW.appointment_time <> OLD.appointment_time)
       AND NEW.appointment_date < CURDATE() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Appointments cannot be rescheduled to a past date';
    END IF;
    SET NEW.appointment_number = OLD.appointment_number;
END$$

CREATE TRIGGER trg_appointments_au
AFTER UPDATE ON appointments
FOR EACH ROW
BEGIN
    INSERT INTO audit_log (table_name, record_id, action, old_values, new_values, performed_by)
    VALUES ('appointments', NEW.appointment_id, 'UPDATE',
            JSON_OBJECT('dentist_id', OLD.dentist_id, 'treatment_id', OLD.treatment_id,
                        'date', OLD.appointment_date, 'time', OLD.appointment_time,
                        'status', OLD.status),
            JSON_OBJECT('dentist_id', NEW.dentist_id, 'treatment_id', NEW.treatment_id,
                        'date', NEW.appointment_date, 'time', NEW.appointment_time,
                        'status', NEW.status),
            COALESCE(@app_user, CURRENT_USER()));
END$$

CREATE PROCEDURE sp_calculate_bill(
    IN p_appointment_id    INT UNSIGNED,
    IN p_discount_percent  DECIMAL(5,2),
    IN p_tax_percent       DECIMAL(5,2))
BEGIN
    DECLARE v_price           DECIMAL(10,2);
    DECLARE v_status          VARCHAR(15);
    DECLARE v_existing_status VARCHAR(10);
    DECLARE v_discount        DECIMAL(10,2);
    DECLARE v_tax             DECIMAL(10,2);
    DECLARE v_total           DECIMAL(10,2);

    IF p_discount_percent IS NULL OR p_discount_percent < 0 OR p_discount_percent > 100 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Discount percent must be between 0 and 100';
    END IF;
    IF p_tax_percent IS NULL OR p_tax_percent < 0 OR p_tax_percent > 100 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Tax percent must be between 0 and 100';
    END IF;

    SELECT t.price, a.status INTO v_price, v_status
      FROM appointments a
      JOIN treatments  t ON t.treatment_id = a.treatment_id
     WHERE a.appointment_id = p_appointment_id;

    IF v_price IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Appointment not found';
    END IF;
    IF v_status = 'CANCELLED' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Cannot bill a cancelled appointment';
    END IF;

    SELECT payment_status INTO v_existing_status
      FROM bills WHERE appointment_id = p_appointment_id FOR UPDATE;
    IF v_existing_status IS NOT NULL AND v_existing_status <> 'UNPAID' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Bill is already paid or void and cannot be recalculated';
    END IF;

    SET v_discount = ROUND(v_price * p_discount_percent / 100, 2);
    SET v_tax      = ROUND((v_price - v_discount) * p_tax_percent / 100, 2);
    SET v_total    = v_price - v_discount + v_tax;

    INSERT INTO bills (appointment_id, subtotal, discount_percent, discount_amount,
                       tax_percent, tax_amount, total_amount)
    VALUES (p_appointment_id, v_price, p_discount_percent, v_discount,
            p_tax_percent, v_tax, v_total) AS new_bill
    ON DUPLICATE KEY UPDATE
        subtotal         = new_bill.subtotal,
        discount_percent = new_bill.discount_percent,
        discount_amount  = new_bill.discount_amount,
        tax_percent      = new_bill.tax_percent,
        tax_amount       = new_bill.tax_amount,
        total_amount     = new_bill.total_amount;

    SELECT * FROM bills WHERE appointment_id = p_appointment_id;
END$$

CREATE PROCEDURE sp_daily_appointments(IN p_date DATE)
BEGIN
    DECLARE v_date DATE DEFAULT COALESCE(p_date, CURDATE());

    SELECT a.appointment_number,
           a.appointment_date,
           a.appointment_time,
           ADDTIME(a.appointment_time, SEC_TO_TIME(t.duration_minutes * 60)) AS end_time,
           a.status,
           CONCAT(p.first_name, ' ', p.last_name) AS patient_name,
           p.phone                                AS patient_phone,
           d.full_name                            AS dentist_name,
           t.name                                 AS treatment_name,
           t.duration_minutes,
           t.price
      FROM appointments a
      JOIN patients   p ON p.patient_id   = a.patient_id
      JOIN dentists   d ON d.dentist_id   = a.dentist_id
      JOIN treatments t ON t.treatment_id = a.treatment_id
     WHERE a.appointment_date = v_date
     ORDER BY a.appointment_time, d.full_name;
END$$

DELIMITER ;
