-- =====================================================================
-- Migration 002: itemised bills, report procedures, reminder logging.
-- Run ONCE after schema.sql + data.sql:
--     mysql -u root -p < database/migration_002_billing_reports_reminders.sql
-- =====================================================================
USE sunrise_dental;

-- ---------------------------------------------------------------------
-- 1. Bills keep the components of the price, so a receipt can always be
--    reproduced even after the price list changes.
--    subtotal = treatment_price + consultation_fee   (enforced below)
-- ---------------------------------------------------------------------
ALTER TABLE bills
    ADD COLUMN treatment_price  DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER appointment_id,
    ADD COLUMN consultation_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00 AFTER treatment_price,
    ADD COLUMN billing_type     VARCHAR(10)   NOT NULL DEFAULT 'STANDARD' AFTER consultation_fee,
    ADD COLUMN issued_by        INT UNSIGNED  NULL AFTER paid_at;

-- Bills created before this migration had no consultation fee.
UPDATE bills SET treatment_price = subtotal WHERE treatment_price = 0 AND consultation_fee = 0;

ALTER TABLE bills
    ADD CONSTRAINT chk_bills_parts CHECK (treatment_price >= 0 AND consultation_fee >= 0
                                          AND subtotal = treatment_price + consultation_fee),
    ADD CONSTRAINT chk_bills_type  CHECK (billing_type IN ('STANDARD', 'SENIOR', 'INSURANCE')),
    ADD CONSTRAINT fk_bills_issued_by FOREIGN KEY (issued_by) REFERENCES users (user_id)
        ON DELETE RESTRICT ON UPDATE RESTRICT;

-- The bill arithmetic now lives in Java (BillCalculator, BigDecimal), where it is unit-tested;
-- the CHECK constraints above and in schema.sql still guard every invariant in the database.
DROP PROCEDURE IF EXISTS sp_calculate_bill;

-- ---------------------------------------------------------------------
-- 2. Notifications: remember why a delivery failed, and make the
--    "has this appointment been reminded?" lookup cheap.
-- ---------------------------------------------------------------------
ALTER TABLE notifications ADD COLUMN failure_reason VARCHAR(255) NULL AFTER sent_at;
CREATE INDEX idx_notifications_reminder ON notifications (appointment_id, type, status);

-- ---------------------------------------------------------------------
-- 3. Report procedures (all ranges are inclusive)
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_report_daily_by_dentist;
DROP PROCEDURE IF EXISTS sp_report_monthly_revenue;
DROP PROCEDURE IF EXISTS sp_report_top_treatments;
DROP PROCEDURE IF EXISTS sp_report_dentist_utilization;
DROP PROCEDURE IF EXISTS sp_report_cancellation_rate;

DELIMITER $$

-- Appointments per dentist per day, split by status.
CREATE PROCEDURE sp_report_daily_by_dentist(IN p_from DATE, IN p_to DATE)
BEGIN
    IF p_from IS NULL OR p_to IS NULL OR p_from > p_to THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid date range';
    END IF;

    SELECT a.appointment_date                                   AS appointment_date,
           d.dentist_id                                         AS dentist_id,
           d.full_name                                          AS dentist_name,
           COUNT(*)                                             AS total,
           CAST(SUM(a.status = 'SCHEDULED') AS SIGNED)          AS scheduled,
           CAST(SUM(a.status = 'CONFIRMED') AS SIGNED)          AS confirmed,
           CAST(SUM(a.status = 'COMPLETED') AS SIGNED)          AS completed,
           CAST(SUM(a.status = 'CANCELLED') AS SIGNED)          AS cancelled,
           CAST(SUM(a.status = 'NO_SHOW')   AS SIGNED)          AS no_show
      FROM appointments a
      JOIN dentists d ON d.dentist_id = a.dentist_id
     WHERE a.appointment_date BETWEEN p_from AND p_to
     GROUP BY a.appointment_date, d.dentist_id, d.full_name
     ORDER BY a.appointment_date, d.full_name;
END$$

-- Revenue of one month by treatment. The month is the appointment (service) month; void bills are ignored.
-- "billed" = what was invoiced, "collected" = the part already paid.
CREATE PROCEDURE sp_report_monthly_revenue(IN p_year SMALLINT, IN p_month TINYINT)
BEGIN
    DECLARE v_from DATE;
    DECLARE v_to   DATE;

    IF p_year IS NULL OR p_year NOT BETWEEN 2000 AND 2100
       OR p_month IS NULL OR p_month NOT BETWEEN 1 AND 12 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid year or month';
    END IF;

    SET v_from = DATE_ADD(MAKEDATE(p_year, 1), INTERVAL (p_month - 1) MONTH);
    SET v_to   = DATE_SUB(DATE_ADD(v_from, INTERVAL 1 MONTH), INTERVAL 1 DAY);

    SELECT t.code                                                   AS code,
           t.name                                                   AS treatment_name,
           COUNT(b.bill_id)                                         AS bills,
           SUM(b.treatment_price)                                   AS treatment_revenue,
           SUM(b.consultation_fee)                                  AS consultation_fees,
           SUM(b.discount_amount)                                   AS discounts,
           SUM(b.tax_amount)                                        AS tax,
           SUM(b.total_amount)                                      AS billed,
           COALESCE(SUM(CASE WHEN b.payment_status = 'PAID' THEN b.total_amount END), 0) AS collected
      FROM bills b
      JOIN appointments a ON a.appointment_id = b.appointment_id
      JOIN treatments   t ON t.treatment_id   = a.treatment_id
     WHERE a.appointment_date BETWEEN v_from AND v_to
       AND b.payment_status <> 'VOID'
     GROUP BY t.treatment_id, t.code, t.name
     ORDER BY billed DESC, t.name;
END$$

-- Most booked treatments (cancelled and no-show appointments do not count).
CREATE PROCEDURE sp_report_top_treatments(IN p_from DATE, IN p_to DATE, IN p_limit INT)
BEGIN
    IF p_from IS NULL OR p_to IS NULL OR p_from > p_to THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid date range';
    END IF;
    IF p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 50 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Limit must be between 1 and 50';
    END IF;

    SELECT ROW_NUMBER() OVER (ORDER BY COUNT(*) DESC, COALESCE(SUM(b.total_amount), 0) DESC, t.name) AS ranking,
           t.code                                  AS code,
           t.name                                  AS treatment_name,
           COUNT(*)                                AS appointments,
           COALESCE(SUM(b.total_amount), 0)        AS billed
      FROM appointments a
      JOIN treatments t ON t.treatment_id = a.treatment_id
      LEFT JOIN bills b ON b.appointment_id = a.appointment_id AND b.payment_status <> 'VOID'
     WHERE a.appointment_date BETWEEN p_from AND p_to
       AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
     GROUP BY t.treatment_id, t.code, t.name
     ORDER BY ranking
     LIMIT p_limit;
END$$

-- Booked minutes against available minutes of every active dentist.
-- p_working_days = number of clinic working days inside the range (calculated by the caller from configuration).
CREATE PROCEDURE sp_report_dentist_utilization(IN p_from DATE, IN p_to DATE, IN p_working_days INT)
BEGIN
    IF p_from IS NULL OR p_to IS NULL OR p_from > p_to THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid date range';
    END IF;
    IF p_working_days IS NULL OR p_working_days NOT BETWEEN 0 AND 366 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Working days must be between 0 and 366';
    END IF;

    SELECT d.dentist_id                                              AS dentist_id,
           d.full_name                                               AS dentist_name,
           COUNT(a.appointment_id)                                   AS appointments,
           CAST(COALESCE(SUM(t.duration_minutes), 0) AS SIGNED)      AS booked_minutes,
           CAST(p_working_days * (TIME_TO_SEC(TIMEDIFF(d.work_end, d.work_start)) DIV 60) AS SIGNED)
                                                                     AS available_minutes,
           ROUND(100 * COALESCE(SUM(t.duration_minutes), 0)
                 / NULLIF(p_working_days * (TIME_TO_SEC(TIMEDIFF(d.work_end, d.work_start)) DIV 60), 0), 1)
                                                                     AS utilization_percent
      FROM dentists d
      LEFT JOIN appointments a ON a.dentist_id = d.dentist_id
                              AND a.appointment_date BETWEEN p_from AND p_to
                              AND a.status NOT IN ('CANCELLED', 'NO_SHOW')
      LEFT JOIN treatments t ON t.treatment_id = a.treatment_id
     WHERE d.is_active = 1
     GROUP BY d.dentist_id, d.full_name, d.work_start, d.work_end
     ORDER BY utilization_percent DESC, d.full_name;
END$$

-- Cancellation and no-show rates per dentist plus an overall row (dentist_id NULL) at the end.
CREATE PROCEDURE sp_report_cancellation_rate(IN p_from DATE, IN p_to DATE)
BEGIN
    IF p_from IS NULL OR p_to IS NULL OR p_from > p_to THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid date range';
    END IF;

    SELECT r.dentist_id, r.dentist_name, r.total_appointments, r.cancelled, r.no_show,
           r.cancellation_rate_percent, r.no_show_rate_percent
      FROM (
            SELECT d.dentist_id                                          AS dentist_id,
                   d.full_name                                           AS dentist_name,
                   COUNT(*)                                              AS total_appointments,
                   CAST(SUM(a.status = 'CANCELLED') AS SIGNED)           AS cancelled,
                   CAST(SUM(a.status = 'NO_SHOW')   AS SIGNED)           AS no_show,
                   ROUND(100 * SUM(a.status = 'CANCELLED') / NULLIF(COUNT(*), 0), 1) AS cancellation_rate_percent,
                   ROUND(100 * SUM(a.status = 'NO_SHOW')   / NULLIF(COUNT(*), 0), 1) AS no_show_rate_percent,
                   0                                                     AS sort_group
              FROM appointments a
              JOIN dentists d ON d.dentist_id = a.dentist_id
             WHERE a.appointment_date BETWEEN p_from AND p_to
             GROUP BY d.dentist_id, d.full_name
            UNION ALL
            SELECT NULL, 'ALL DENTISTS', COUNT(*),
                   CAST(COALESCE(SUM(a.status = 'CANCELLED'), 0) AS SIGNED),
                   CAST(COALESCE(SUM(a.status = 'NO_SHOW'), 0) AS SIGNED),
                   ROUND(100 * SUM(a.status = 'CANCELLED') / NULLIF(COUNT(*), 0), 1),
                   ROUND(100 * SUM(a.status = 'NO_SHOW')   / NULLIF(COUNT(*), 0), 1),
                   1
              FROM appointments a
             WHERE a.appointment_date BETWEEN p_from AND p_to
           ) r
     ORDER BY r.sort_group, r.dentist_name;
END$$

DELIMITER ;
