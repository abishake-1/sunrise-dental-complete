package com.sunrisedental.api;

import com.sunrisedental.dto.BookAppointmentRequest;
import com.sunrisedental.dto.GenerateBillRequest;
import com.sunrisedental.dto.Receipt;
import com.sunrisedental.dto.ReminderResult;
import com.sunrisedental.dto.ReportTable;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.Dentist;
import com.sunrisedental.model.Gender;
import com.sunrisedental.model.Patient;
import com.sunrisedental.model.PaymentMethod;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.Treatment;
import com.sunrisedental.model.User;
import com.sunrisedental.service.AppointmentService;
import com.sunrisedental.service.AuthService;
import com.sunrisedental.service.BillingService;
import com.sunrisedental.service.ClinicFacade;
import com.sunrisedental.service.PatientService;
import com.sunrisedental.service.ReminderService;
import com.sunrisedental.service.ReportService;
import com.sunrisedental.util.ConflictException;
import com.sunrisedental.util.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.Optional;

/** In-memory stand-in for the facade so the REST layer can be tested without MySQL. */
final class FakeFacade extends ClinicFacade {

    Long lastDentistFilter;
    LocalDate lastDateFilter;
    GenerateBillRequest lastBillRequest;
    String lastReport;
    LocalDate lastReminderDate = LocalDate.of(1970, 1, 1);

    FakeFacade(Clock clock) {
        super(new AuthService(null, null), new PatientService(null, null, clock),
                new AppointmentService(null, null, null, null, null, null, null, null, clock),
                new BillingService(null, null, null, null, null, null, null, null, null),
                new ReportService(null, null, clock, Set.of(DayOfWeek.MONDAY)),
                new ReminderService(null, null, null, clock));
    }

    private static User user(long id, String name, Role role) {
        User u = new User();
        u.setId(id);
        u.setUsername(name);
        u.setFullName(name + " full");
        u.setRole(role);
        u.setPassword("must-never-leak");
        return u;
    }

    @Override
    public Optional<User> login(String username, String pw) {
        if ("admin".equals(username) && "admin123".equals(pw)) {
            return Optional.of(user(1, "admin", Role.ADMIN));
        }
        if ("reception".equals(username) && "reception123".equals(pw)) {
            return Optional.of(user(2, "reception", Role.RECEPTIONIST));
        }
        if ("other".equals(username) && "other123".equals(pw)) {
            return Optional.of(user(3, "other", Role.RECEPTIONIST));
        }
        return Optional.empty();
    }

    private static Appointment appointment(long id, long createdBy, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setAppointmentNumber(String.format("APT-%04d", id));
        a.setPatientId(5L);
        a.setDentistId(1L);
        a.setTreatmentId(2L);
        a.setAppointmentDate(LocalDate.of(2026, 10, 12));
        a.setAppointmentTime(LocalTime.of(10, 0));
        a.setStatus(status);
        a.setCreatedBy(createdBy);
        return a;
    }

    @Override
    public Appointment bookAppointment(BookAppointmentRequest request, User actor) {
        if (request.treatmentId() == 99) {
            throw new ConflictException("The dentist is already booked from 10:00 to 10:45 on that day");
        }
        Appointment a = appointment(10, actor.getId(), AppointmentStatus.SCHEDULED);
        a.setAppointmentDate(request.date());
        a.setAppointmentTime(request.time());
        return a;
    }

    @Override
    public Appointment getAppointment(long id) {
        if (id == 404) {
            throw new NotFoundException("Appointment", id);
        }
        return appointment(id, 2, AppointmentStatus.SCHEDULED);
    }

    @Override
    public Appointment getAppointmentByNumber(String number) {
        if (!"APT-0010".equals(number)) {
            throw new NotFoundException("Appointment", number);
        }
        return appointment(10, 2, AppointmentStatus.SCHEDULED);
    }

    @Override
    public Appointment cancelAppointment(long id, User actor) {
        return appointment(id, 2, AppointmentStatus.CANCELLED);
    }

    @Override
    public Appointment changeAppointmentStatus(long id, AppointmentStatus status, User actor) {
        return appointment(id, 2, status);
    }

    @Override
    public Appointment rescheduleAppointment(long id, LocalDate date, LocalTime time, String notes, User actor) {
        Appointment a = appointment(id, 2, AppointmentStatus.SCHEDULED);
        a.setAppointmentDate(date);
        a.setAppointmentTime(time);
        a.setNotes(notes);
        return a;
    }

    @Override
    public List<Appointment> appointmentsOn(LocalDate date, Long dentistId) {
        lastDateFilter = date;
        lastDentistFilter = dentistId;
        return List.of(appointment(10, 2, AppointmentStatus.SCHEDULED));
    }

    @Override
    public List<Dentist> listDentists() {
        Dentist d = new Dentist();
        d.setId(1L);
        d.setFullName("Dr. Test");
        d.setSpecialization("General");
        d.setWorkStart(LocalTime.of(9, 0));
        d.setWorkEnd(LocalTime.of(17, 0));
        return List.of(d);
    }

    @Override
    public List<Treatment> listTreatments() {
        Treatment t = new Treatment();
        t.setId(2L);
        t.setCode("CHK");
        t.setName("Check-up");
        t.setDurationMinutes(30);
        t.setPrice(new BigDecimal("25.00"));
        return List.of(t);
    }

    @Override
    public Patient registerPatient(Patient patient) {
        patient.setId(5L);
        return patient;
    }

    @Override
    public Patient getPatient(long id) {
        Patient p = new Patient();
        p.setId(id);
        p.setFirstName("Nimal");
        p.setLastName("Perera");
        p.setDateOfBirth(LocalDate.of(1990, 1, 1));
        p.setGender(Gender.MALE);
        p.setPhone("0771234567");
        return p;
    }

    @Override
    public Receipt generateBill(GenerateBillRequest request, User actor) {
        lastBillRequest = request;
        if (request.appointmentId() == 404) {
            throw new NotFoundException("Appointment", 404);
        }
        if (request.appointmentId() == 409) {
            throw new ConflictException("APT-0409 already has bill 1 (UNPAID)");
        }
        return SampleData.receipt(7, "Nimal Perera", "UNPAID");
    }

    @Override
    public Receipt getReceipt(long billId) {
        if (billId == 404) {
            throw new NotFoundException("Bill", 404);
        }
        return SampleData.receipt(billId, "Nimal Perera", "UNPAID");
    }

    @Override
    public Receipt payBill(long billId, PaymentMethod method) {
        return SampleData.receipt(billId, "Nimal Perera", "PAID");
    }

    private static ReportTable table(String slug) {
        return new ReportTable(slug, "Sample " + slug, java.util.Map.of(), List.of("dentistName", "total", "day"),
                List.of(java.util.Arrays.asList("Dr. A", 3L, LocalDate.of(2026, 10, 12)),
                        java.util.Arrays.asList("Dr. B, Jr.", 1L, null),
                        java.util.Arrays.asList("=cmd|' /C calc'!A0", 0L, null)));
    }

    @Override
    public ReportTable dailyAppointmentsPerDentist(LocalDate from, LocalDate to) {
        lastReport = "daily:" + from + ":" + to;
        return table("daily-appointments").withParameters(java.util.Map.of("from", from, "to", to));
    }

    @Override
    public ReportTable monthlyRevenueByTreatment(int year, int month) {
        lastReport = "monthly:" + year + ":" + month;
        return table("monthly-revenue").withParameters(java.util.Map.of("year", year, "month", month));
    }

    @Override
    public ReportTable topTreatments(LocalDate from, LocalDate to, int limit) {
        lastReport = "top:" + from + ":" + to + ":" + limit;
        java.util.Map<String, Object> parameters = new java.util.LinkedHashMap<>();
        parameters.put("from", from);
        parameters.put("to", to);
        parameters.put("limit", limit);
        return table("top-treatments").withParameters(parameters);
    }

    @Override
    public ReportTable dentistUtilization(LocalDate from, LocalDate to) {
        lastReport = "utilization:" + from + ":" + to;
        return table("dentist-utilization").withParameters(java.util.Map.of("from", from, "to", to));
    }

    @Override
    public ReportTable cancellationRate(LocalDate from, LocalDate to) {
        lastReport = "cancellation:" + from + ":" + to;
        return table("cancellation-rate").withParameters(java.util.Map.of("from", from, "to", to));
    }

    @Override
    public ReminderResult sendReminders(LocalDate date) {
        lastReminderDate = date;
        return new ReminderResult(date != null ? date : LocalDate.of(2026, 10, 12), 2, 1, 1);
    }
}
