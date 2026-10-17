package com.sunrisedental.service;

import com.sunrisedental.dao.AppointmentDao;
import com.sunrisedental.dao.BillDao;
import com.sunrisedental.dao.DataAccessException;
import com.sunrisedental.dao.DentistDao;
import com.sunrisedental.dao.PatientDao;
import com.sunrisedental.dao.TransactionManager;
import com.sunrisedental.dao.TreatmentDao;
import com.sunrisedental.dao.UserDao;
import com.sunrisedental.dto.GenerateBillRequest;
import com.sunrisedental.dto.Receipt;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.Bill;
import com.sunrisedental.model.BillingType;
import com.sunrisedental.model.Dentist;
import com.sunrisedental.model.Patient;
import com.sunrisedental.model.PaymentMethod;
import com.sunrisedental.model.Treatment;
import com.sunrisedental.model.User;
import com.sunrisedental.pattern.BillingContext;
import com.sunrisedental.pattern.BillingStrategy;
import com.sunrisedental.pattern.BillingStrategyFactory;
import com.sunrisedental.pattern.BillingTerms;
import com.sunrisedental.util.AppConfig;
import com.sunrisedental.util.ConflictException;
import com.sunrisedental.util.NotFoundException;
import com.sunrisedental.util.ValidationException;
import com.sunrisedental.util.Validator;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Optional;

/**
 * Creates and settles bills.
 *
 * <p>Rules: only COMPLETED appointments are billed; {@code total = treatment price + consultation fee -
 * discount (+ tax, 0 by default)}; the discount percentage comes from a {@link BillingStrategy}; the arithmetic
 * is exact ({@link BillCalculator}). The consultation fee comes from {@code config.properties}; it is not
 * charged a second time for the treatment that already is the consultation.
 *
 * <p>Duplicate bills are prevented in two layers: the appointment row is locked while the bill is created and an
 * existing bill produces a 409, and {@code UNIQUE(appointment_id)} stops anything that slips through.
 */
public final class BillingService {

    private final TransactionManager transactions;
    private final AppointmentDao appointmentDao;
    private final PatientDao patientDao;
    private final DentistDao dentistDao;
    private final TreatmentDao treatmentDao;
    private final BillDao billDao;
    private final UserDao userDao;
    private final BillingStrategyFactory strategyFactory;
    private final AppConfig config;

    /**
     * Creates the service.
     *
     * @param transactions    connection handling
     * @param appointmentDao  appointment access
     * @param patientDao      patient access
     * @param dentistDao      dentist access
     * @param treatmentDao    treatment access
     * @param billDao         bill access
     * @param userDao         user access (receipt shows who issued the bill)
     * @param strategyFactory creates billing strategies
     * @param config          business settings
     */
    public BillingService(TransactionManager transactions, AppointmentDao appointmentDao, PatientDao patientDao,
                          DentistDao dentistDao, TreatmentDao treatmentDao, BillDao billDao, UserDao userDao,
                          BillingStrategyFactory strategyFactory, AppConfig config) {
        this.transactions = transactions;
        this.appointmentDao = appointmentDao;
        this.patientDao = patientDao;
        this.dentistDao = dentistDao;
        this.treatmentDao = treatmentDao;
        this.billDao = billDao;
        this.userDao = userDao;
        this.strategyFactory = strategyFactory;
        this.config = config;
    }

    /**
     * Creates the bill of a completed appointment.
     *
     * @param request appointment, billing type and optional insurance coverage
     * @param actor   the staff member issuing the bill
     * @return the receipt of the new bill
     * @throws NotFoundException   if the appointment does not exist
     * @throws ConflictException   if the appointment is not COMPLETED or already has a bill
     * @throws ValidationException if the billing scheme does not apply (e.g. patient too young for SENIOR)
     */
    public Receipt generateBill(GenerateBillRequest request, User actor) {
        Validator.requireNonNull(request, "request");
        Validator.requireNonNull(actor, "actor");
        long appointmentId = Validator.requireId(request.appointmentId(), "appointmentId");
        BillingType type = BillingType.parse(request.billingType());
        BillingStrategy strategy = strategyFactory.create(type);
        if (request.insuranceCoveragePercent() != null) {
            Validator.requirePercent(request.insuranceCoveragePercent(), "insuranceCoveragePercent");
        }

        return transactions.inTransaction(c -> {
            // Locking the appointment serialises concurrent attempts to bill it.
            Appointment appointment = appointmentDao.findByIdForUpdate(c, appointmentId)
                    .orElseThrow(() -> new NotFoundException("Appointment", appointmentId));
            if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
                throw new ConflictException("Only COMPLETED appointments can be billed; "
                        + appointment.getAppointmentNumber() + " is " + appointment.getStatus());
            }
            Optional<Bill> existing = billDao.findByAppointmentId(c, appointmentId);
            if (existing.isPresent()) {
                throw new ConflictException(appointment.getAppointmentNumber() + " already has bill "
                        + existing.get().getId() + " (" + existing.get().getPaymentStatus() + ")");
            }
            Patient patient = patientDao.findById(c, appointment.getPatientId())
                    .orElseThrow(() -> new NotFoundException("Patient", appointment.getPatientId()));
            Treatment treatment = treatmentDao.findById(c, appointment.getTreatmentId())
                    .orElseThrow(() -> new NotFoundException("Treatment", appointment.getTreatmentId()));

            BillingTerms terms = strategy.calculateTerms(new BillingContext(
                    patient.getAgeOn(appointment.getAppointmentDate()), request.insuranceCoveragePercent()));
            BillAmounts amounts = BillCalculator.calculate(treatment.getPrice(), consultationFeeFor(treatment), terms);

            Bill bill = new Bill();
            bill.setAppointmentId(appointmentId);
            bill.setBillingType(type);
            bill.setTreatmentPrice(amounts.treatmentPrice());
            bill.setConsultationFee(amounts.consultationFee());
            bill.setSubtotal(amounts.subtotal());
            bill.setDiscountPercent(amounts.discountPercent());
            bill.setDiscountAmount(amounts.discountAmount());
            bill.setTaxPercent(amounts.taxPercent());
            bill.setTaxAmount(amounts.taxAmount());
            bill.setTotalAmount(amounts.total());
            bill.setIssuedBy(actor.getId());
            try {
                return receiptFor(c, billDao.insert(c, bill));
            } catch (DataAccessException e) {
                if (e.isDuplicateKey()) {
                    throw new ConflictException(appointment.getAppointmentNumber() + " already has a bill");
                }
                if (e.isCheckViolation()) {
                    throw new ValidationException("The bill violates a database constraint");
                }
                throw e;
            }
        });
    }

    /**
     * Loads a receipt.
     *
     * @param billId bill id
     * @return the receipt
     * @throws NotFoundException if the bill does not exist
     */
    public Receipt getReceipt(long billId) {
        Validator.requireId(billId, "billId");
        return transactions.read(c -> receiptFor(c, requireBill(c, billId)));
    }

    /**
     * Records the payment of an unpaid bill.
     *
     * @param billId bill id
     * @param method how it was paid
     * @return the updated receipt
     * @throws ConflictException if the bill is not unpaid
     */
    public Receipt pay(long billId, PaymentMethod method) {
        Validator.requireId(billId, "billId");
        Validator.requireNonNull(method, "paymentMethod");
        return transactions.inTransaction(c -> {
            if (!billDao.markPaid(c, billId, method)) {
                throw notPayable(requireBill(c, billId), "paid");
            }
            return receiptFor(c, requireBill(c, billId));
        });
    }

    /**
     * Voids an unpaid bill.
     *
     * @param billId bill id
     * @return the updated receipt
     * @throws ConflictException if the bill is not unpaid
     */
    public Receipt voidBill(long billId) {
        Validator.requireId(billId, "billId");
        return transactions.inTransaction(c -> {
            if (!billDao.voidBill(c, billId)) {
                throw notPayable(requireBill(c, billId), "voided");
            }
            return receiptFor(c, requireBill(c, billId));
        });
    }

    /**
     * The consultation fee of a bill: the configured fee, or zero for the treatment that already is the
     * consultation.
     *
     * @param treatment the billed treatment
     * @return the fee with two decimals
     */
    BigDecimal consultationFeeFor(Treatment treatment) {
        String waivedCode = config.consultationTreatmentCode();
        if (!waivedCode.isEmpty() && waivedCode.equalsIgnoreCase(treatment.getCode())) {
            return BigDecimal.ZERO.setScale(2);
        }
        return config.consultationFee();
    }

    private static ConflictException notPayable(Bill bill, String action) {
        return new ConflictException("Only an unpaid bill can be " + action + "; bill " + bill.getId()
                + " is " + bill.getPaymentStatus());
    }

    private Bill requireBill(Connection c, long billId) {
        return billDao.findById(c, billId).orElseThrow(() -> new NotFoundException("Bill", billId));
    }

    private Receipt receiptFor(Connection c, Bill bill) {
        Appointment appointment = appointmentDao.findById(c, bill.getAppointmentId())
                .orElseThrow(() -> new NotFoundException("Appointment", bill.getAppointmentId()));
        Patient patient = patientDao.findById(c, appointment.getPatientId())
                .orElseThrow(() -> new NotFoundException("Patient", appointment.getPatientId()));
        Dentist dentist = dentistDao.findById(c, appointment.getDentistId())
                .orElseThrow(() -> new NotFoundException("Dentist", appointment.getDentistId()));
        Treatment treatment = treatmentDao.findById(c, appointment.getTreatmentId())
                .orElseThrow(() -> new NotFoundException("Treatment", appointment.getTreatmentId()));
        String issuedBy = bill.getIssuedBy() == null ? "-"
                : userDao.findById(c, bill.getIssuedBy()).map(User::getFullName).orElse("-");
        return new Receipt(Receipt.numberFor(bill.getId()), bill.getId(), bill.getCreatedAt(), issuedBy,
                appointment.getAppointmentNumber(), appointment.getAppointmentDate(),
                appointment.getAppointmentTime(), patient.getFullName(), patient.getPhone(),
                dentist.getFullName(), treatment.getCode(), treatment.getName(), bill.getTreatmentPrice(),
                bill.getConsultationFee(), bill.getSubtotal(), bill.getBillingType().name(),
                bill.getDiscountPercent(), bill.getDiscountAmount(), bill.getTaxPercent(), bill.getTaxAmount(),
                bill.getTotalAmount(), bill.getPaymentStatus().name(),
                bill.getPaymentMethod() == null ? null : bill.getPaymentMethod().name(), bill.getPaidAt());
    }
}
