package com.sunrisedental.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.sunrisedental.model.Dentist;
import com.sunrisedental.model.Gender;
import com.sunrisedental.model.Patient;
import com.sunrisedental.model.PaymentMethod;
import com.sunrisedental.model.PaymentStatus;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.Treatment;
import com.sunrisedental.model.User;
import com.sunrisedental.pattern.BillingStrategyFactory;
import com.sunrisedental.util.AppConfig;
import com.sunrisedental.util.ConflictException;
import com.sunrisedental.util.NotFoundException;
import com.sunrisedental.util.ValidationException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class BillingServiceTest {

    private final List<Bill> inserted = new ArrayList<>();
    private Optional<Bill> existing = Optional.empty();
    private RuntimeException insertFailure;
    private boolean markPaidResult = true;

    private static AppConfig config() {
        Properties p = new Properties();
        p.setProperty("clinic.consultation.fee", "25.00");
        p.setProperty("clinic.consultation.treatment.code", "CHK");
        p.setProperty("billing.tax.percent", "0");
        p.setProperty("billing.senior.discount.percent", "15");
        p.setProperty("billing.senior.min.age", "60");
        p.setProperty("billing.insurance.default.coverage.percent", "70");
        return new AppConfig(p);
    }

    private static Appointment appointment(AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setId(10L);
        a.setAppointmentNumber("APT-0010");
        a.setPatientId(5L);
        a.setDentistId(1L);
        a.setTreatmentId(2L);
        a.setAppointmentDate(LocalDate.of(2026, 10, 12));
        a.setAppointmentTime(LocalTime.of(10, 0));
        a.setStatus(status);
        return a;
    }

    private static Patient patient(LocalDate dob) {
        Patient p = new Patient();
        p.setId(5L);
        p.setFirstName("Nimal");
        p.setLastName("Perera");
        p.setDateOfBirth(dob);
        p.setGender(Gender.MALE);
        p.setPhone("0771234567");
        return p;
    }

    private static Treatment treatment(String code, String price) {
        Treatment t = new Treatment();
        t.setId(2L);
        t.setCode(code);
        t.setName("Treatment " + code);
        t.setPrice(new BigDecimal(price));
        return t;
    }

    private static User staff() {
        User u = new User();
        u.setId(1L);
        u.setUsername("reception");
        u.setFullName("Front Desk");
        u.setRole(Role.RECEPTIONIST);
        return u;
    }

    private BillingService service(Appointment a, Patient p, Treatment t) {
        AppointmentDao appointments = Stubs.of(AppointmentDao.class)
                .on("findByIdForUpdate", args -> Optional.of(a)).on("findById", args -> Optional.of(a)).make();
        PatientDao patients = Stubs.of(PatientDao.class).on("findById", args -> Optional.of(p)).make();
        Dentist dentist = new Dentist();
        dentist.setFullName("Dr. Maria Santos");
        DentistDao dentists = Stubs.of(DentistDao.class).on("findById", args -> Optional.of(dentist)).make();
        TreatmentDao treatments = Stubs.of(TreatmentDao.class).on("findById", args -> Optional.of(t)).make();
        UserDao users = Stubs.of(UserDao.class).on("findById", args -> Optional.of(staff())).make();
        BillDao bills = Stubs.of(BillDao.class)
                .on("findByAppointmentId", args -> existing)
                .on("insert", args -> {
                    if (insertFailure != null) {
                        throw insertFailure;
                    }
                    Bill b = (Bill) args[1];
                    b.setId(7L);
                    b.setCreatedAt(LocalDateTime.of(2026, 10, 12, 14, 5));
                    inserted.add(b);
                    return b;
                })
                .on("findById", args -> Optional.of(inserted.isEmpty() ? existing.orElseThrow() : inserted.get(0)))
                .on("markPaid", args -> markPaidResult)
                .make();
        return new BillingService(new TransactionManager(Stubs.fakeConnections()), appointments, patients, dentists,
                treatments, bills, users, new BillingStrategyFactory(config()), config());
    }

    private static GenerateBillRequest request(String type, BigDecimal coverage) {
        return new GenerateBillRequest(10, type, coverage);
    }

    @Test
    void seniorBillAddsConsultationFeeAndAppliesTheDiscountToTheWholeSubtotal() {
        BillingService service = service(appointment(AppointmentStatus.COMPLETED),
                patient(LocalDate.of(1950, 3, 1)), treatment("FIL", "90.00"));
        Receipt r = service.generateBill(request("SENIOR", null), staff());
        assertEquals("90.00", r.treatmentPrice().toPlainString());
        assertEquals("25.00", r.consultationFee().toPlainString());
        assertEquals("115.00", r.subtotal().toPlainString());
        assertEquals("17.25", r.discountAmount().toPlainString());
        assertEquals("97.75", r.total().toPlainString());
        assertEquals("SENIOR", r.billingType());
        assertEquals("RCP-000007", r.receiptNumber());
        assertEquals("Front Desk", r.issuedBy());
        assertEquals("UNPAID", r.paymentStatus());
        assertEquals(1, inserted.size());
        assertEquals(Long.valueOf(1), inserted.get(0).getIssuedBy());
    }

    @Test
    void consultationFeeIsWaivedForTheConsultationTreatmentItself() {
        BillingService service = service(appointment(AppointmentStatus.COMPLETED),
                patient(LocalDate.of(1990, 1, 1)), treatment("CHK", "25.00"));
        Receipt r = service.generateBill(request("STANDARD", null), staff());
        assertEquals("0.00", r.consultationFee().toPlainString());
        assertEquals("25.00", r.total().toPlainString());
    }

    @Test
    void insuranceUsesTheRequestedCoverage() {
        BillingService service = service(appointment(AppointmentStatus.COMPLETED),
                patient(LocalDate.of(1990, 1, 1)), treatment("FIL", "90.00"));
        Receipt r = service.generateBill(request("INSURANCE", new BigDecimal("50")), staff());
        assertEquals("57.50", r.discountAmount().toPlainString());
        assertEquals("57.50", r.total().toPlainString());
    }

    @Test
    void onlyCompletedAppointmentsCanBeBilled() {
        for (AppointmentStatus status : List.of(AppointmentStatus.SCHEDULED, AppointmentStatus.CONFIRMED,
                AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW)) {
            BillingService service = service(appointment(status), patient(LocalDate.of(1990, 1, 1)),
                    treatment("FIL", "90.00"));
            ConflictException e = assertThrows(ConflictException.class,
                    () -> service.generateBill(request("STANDARD", null), staff()));
            assertTrue(e.getMessage().contains("COMPLETED"));
        }
        assertEquals(0, inserted.size());
    }

    @Test
    void aSecondBillForTheSameAppointmentIsRefused() {
        Bill present = new Bill();
        present.setId(3L);
        present.setPaymentStatus(PaymentStatus.PAID);
        existing = Optional.of(present);
        BillingService service = service(appointment(AppointmentStatus.COMPLETED),
                patient(LocalDate.of(1990, 1, 1)), treatment("FIL", "90.00"));
        ConflictException e = assertThrows(ConflictException.class,
                () -> service.generateBill(request("STANDARD", null), staff()));
        assertTrue(e.getMessage().contains("already has bill 3"));
        assertEquals(0, inserted.size());
    }

    @Test
    void theUniqueConstraintIsTheSafetyNetForRaces() {
        insertFailure = new DataAccessException("Insert failed", new SQLException("Duplicate", "23000", 1062));
        BillingService service = service(appointment(AppointmentStatus.COMPLETED),
                patient(LocalDate.of(1990, 1, 1)), treatment("FIL", "90.00"));
        assertThrows(ConflictException.class, () -> service.generateBill(request("STANDARD", null), staff()));
    }

    @Test
    void seniorDiscountNeedsTheMinimumAge() {
        BillingService service = service(appointment(AppointmentStatus.COMPLETED),
                patient(LocalDate.of(1990, 1, 1)), treatment("FIL", "90.00"));
        assertThrows(ValidationException.class, () -> service.generateBill(request("SENIOR", null), staff()));
        assertEquals(0, inserted.size());
    }

    @Test
    void unknownAppointmentIs404() {
        AppointmentDao none = Stubs.of(AppointmentDao.class).on("findByIdForUpdate", args -> Optional.empty()).make();
        BillingService service = new BillingService(new TransactionManager(Stubs.fakeConnections()), none, null, null,
                null, null, null, new BillingStrategyFactory(config()), config());
        assertThrows(NotFoundException.class, () -> service.generateBill(request("STANDARD", null), staff()));
    }

    @Test
    void payingAnAlreadyPaidBillIsAConflict() {
        Bill paid = new Bill();
        paid.setId(7L);
        paid.setPaymentStatus(PaymentStatus.PAID);
        existing = Optional.of(paid);
        markPaidResult = false;
        BillingService service = service(appointment(AppointmentStatus.COMPLETED),
                patient(LocalDate.of(1990, 1, 1)), treatment("FIL", "90.00"));
        assertThrows(ConflictException.class, () -> service.pay(7, PaymentMethod.CASH));
    }
}
