package com.sunrisedental.service;

import com.sunrisedental.dao.AppointmentDao;
import com.sunrisedental.dao.AppointmentDaoImpl;
import com.sunrisedental.dao.AuditDao;
import com.sunrisedental.dao.AuditDaoImpl;
import com.sunrisedental.dao.BillDao;
import com.sunrisedental.dao.BillDaoImpl;
import com.sunrisedental.dao.DatabaseConnectionManager;
import com.sunrisedental.dao.DentistDao;
import com.sunrisedental.dao.DentistDaoImpl;
import com.sunrisedental.dao.NotificationDao;
import com.sunrisedental.dao.NotificationDaoImpl;
import com.sunrisedental.dao.PatientDao;
import com.sunrisedental.dao.PatientDaoImpl;
import com.sunrisedental.dao.ReportDaoImpl;
import com.sunrisedental.dao.TransactionManager;
import com.sunrisedental.dao.TreatmentDao;
import com.sunrisedental.dao.TreatmentDaoImpl;
import com.sunrisedental.dao.UserDao;
import com.sunrisedental.dao.UserDaoImpl;
import com.sunrisedental.dto.BookAppointmentRequest;
import com.sunrisedental.dto.DailyReport;
import com.sunrisedental.dto.GenerateBillRequest;
import com.sunrisedental.dto.Receipt;
import com.sunrisedental.dto.ReminderResult;
import com.sunrisedental.dto.ReportTable;
import com.sunrisedental.dto.RevenueSummary;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.Dentist;
import com.sunrisedental.model.Patient;
import com.sunrisedental.model.PaymentMethod;
import com.sunrisedental.model.Treatment;
import com.sunrisedental.model.User;
import com.sunrisedental.pattern.AppointmentCreatedEvent;
import com.sunrisedental.pattern.AuditLogListener;
import com.sunrisedental.pattern.BillingStrategyFactory;
import com.sunrisedental.pattern.EventPublisher;
import com.sunrisedental.pattern.NotificationGateways;
import com.sunrisedental.pattern.NotificationListener;
import com.sunrisedental.util.AppConfig;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Facade: the single entry point of the API layer. It hides the services, DAOs, transactions and event
 * wiring behind one coarse-grained interface, so HTTP handlers never touch the lower layers directly.
 * The class is not final so tests can substitute a double without a database.
 */
public class ClinicFacade {

    private final AuthService authService;
    private final PatientService patientService;
    private final AppointmentService appointmentService;
    private final BillingService billingService;
    private final ReportService reportService;
    private final ReminderService reminderService;

    /**
     * Creates a facade over ready-made services.
     *
     * @param authService        credential checks
     * @param patientService     patient use cases
     * @param appointmentService booking use cases
     * @param billingService     billing use cases
     * @param reportService      report use cases
     * @param reminderService    reminder use cases
     */
    public ClinicFacade(AuthService authService, PatientService patientService,
                        AppointmentService appointmentService, BillingService billingService,
                        ReportService reportService, ReminderService reminderService) {
        this.authService = Objects.requireNonNull(authService);
        this.patientService = Objects.requireNonNull(patientService);
        this.appointmentService = Objects.requireNonNull(appointmentService);
        this.billingService = Objects.requireNonNull(billingService);
        this.reportService = Objects.requireNonNull(reportService);
        this.reminderService = Objects.requireNonNull(reminderService);
    }

    /**
     * Composition root: wires the production object graph (config file, environment-based database
     * settings, JDBC DAOs, services, event listeners and the notification gateway).
     *
     * @return a ready-to-use facade
     */
    public static ClinicFacade createDefault() {
        return createDefault(AppConfig.load());
    }

    /**
     * Composition root with an already loaded configuration.
     *
     * @param config the business settings
     * @return a ready-to-use facade
     */
    public static ClinicFacade createDefault(AppConfig config) {
        Clock clock = Clock.systemDefaultZone();
        TransactionManager tx = new TransactionManager(DatabaseConnectionManager.getInstance());

        PatientDao patientDao = new PatientDaoImpl();
        DentistDao dentistDao = new DentistDaoImpl();
        TreatmentDao treatmentDao = new TreatmentDaoImpl();
        AppointmentDao appointmentDao = new AppointmentDaoImpl();
        BillDao billDao = new BillDaoImpl();
        AuditDao auditDao = new AuditDaoImpl();
        NotificationDao notificationDao = new NotificationDaoImpl();
        UserDao userDao = new UserDaoImpl();

        EventPublisher publisher = new EventPublisher();
        publisher.subscribe(AppointmentCreatedEvent.class, new NotificationListener(tx, notificationDao));
        publisher.subscribe(AppointmentCreatedEvent.class, new AuditLogListener(tx, auditDao));

        return new ClinicFacade(
                new AuthService(tx, userDao),
                new PatientService(tx, patientDao, clock),
                new AppointmentService(tx, patientDao, dentistDao, treatmentDao, appointmentDao, billDao,
                        auditDao, publisher, clock),
                new BillingService(tx, appointmentDao, patientDao, dentistDao, treatmentDao, billDao, userDao,
                        new BillingStrategyFactory(config), config),
                new ReportService(tx, new ReportDaoImpl(), clock, config.workingDays()),
                new ReminderService(tx, notificationDao, NotificationGateways.fromEnvironment(clock), clock));
    }

    // ---- authentication ----

    /**
     * Checks staff credentials.
     *
     * @param username login name
     * @param password clear-text password
     * @return the user if valid and active
     */
    public Optional<User> login(String username, String password) {
        return authService.authenticate(username, password);
    }

    // ---- patients ----

    /** @param patient new patient @return the stored patient */
    public Patient registerPatient(Patient patient) {
        return patientService.register(patient);
    }

    /** @param patient changed patient (id required) @return the stored patient */
    public Patient updatePatient(Patient patient) {
        return patientService.update(patient);
    }

    /** @param id patient id @return the patient */
    public Patient getPatient(long id) {
        return patientService.get(id);
    }

    /** @param term name or phone fragment @return matching patients */
    public List<Patient> searchPatients(String term) {
        return patientService.search(term);
    }

    /** @param id patient id */
    public void deletePatient(long id) {
        patientService.delete(id);
    }

    // ---- reference data ----

    /** @return active dentists */
    public List<Dentist> listDentists() {
        return appointmentService.listDentists();
    }

    /** @return bookable treatments */
    public List<Treatment> listTreatments() {
        return appointmentService.listTreatments();
    }

    // ---- appointments ----

    /** @param request booking data @param actor staff member @return the new appointment */
    public Appointment bookAppointment(BookAppointmentRequest request, User actor) {
        return appointmentService.book(request, actor);
    }

    /** @param id appointment id @param actor staff member @return the cancelled appointment */
    public Appointment cancelAppointment(long id, User actor) {
        return appointmentService.cancel(id, actor);
    }

    /** @param id appointment id @param status new status @param actor staff member @return the updated appointment */
    public Appointment changeAppointmentStatus(long id, AppointmentStatus status, User actor) {
        return appointmentService.updateStatus(id, status, actor);
    }

    /** @param id appointment id @return the appointment */
    public Appointment getAppointment(long id) {
        return appointmentService.get(id);
    }

    /** @param date the day @return that day's appointments */
    public List<Appointment> appointmentsOn(LocalDate date) {
        return appointmentService.findByDate(date);
    }

    /** @param date the day @param dentistId dentist filter or null @return the matching appointments */
    public List<Appointment> appointmentsOn(LocalDate date, Long dentistId) {
        return appointmentService.findByDate(date, dentistId);
    }

    /** @param number APT-nnnn @return the appointment */
    public Appointment getAppointmentByNumber(String number) {
        return appointmentService.getByNumber(number);
    }

    /**
     * @param id appointment id
     * @param date new day
     * @param time new start time
     * @param notes new notes or null to keep them
     * @param actor staff member
     * @return the rescheduled appointment
     */
    public Appointment rescheduleAppointment(long id, LocalDate date, java.time.LocalTime time, String notes,
                                             User actor) {
        return appointmentService.reschedule(id, date, time, notes, actor);
    }

    /** @param patientId patient id @return the patient's appointments */
    public List<Appointment> appointmentsOfPatient(long patientId) {
        return appointmentService.findByPatient(patientId);
    }

    // ---- billing ----

    /** @param request bill request @param actor staff member @return the receipt of the new bill */
    public Receipt generateBill(GenerateBillRequest request, User actor) {
        return billingService.generateBill(request, actor);
    }

    /** @param billId bill id @return the receipt */
    public Receipt getReceipt(long billId) {
        return billingService.getReceipt(billId);
    }

    /** @param billId bill id @param method payment method @return the updated receipt */
    public Receipt payBill(long billId, PaymentMethod method) {
        return billingService.pay(billId, method);
    }

    /** @param billId bill id @return the updated receipt */
    public Receipt voidBill(long billId) {
        return billingService.voidBill(billId);
    }

    // ---- reports ----

    /** @param date the day, null for today @return the day sheet with totals */
    public DailyReport dailyReport(LocalDate date) {
        return reportService.dailyReport(date);
    }

    /** @param from first day @param to last day @return the revenue summary */
    public RevenueSummary revenueSummary(LocalDate from, LocalDate to) {
        return reportService.revenueSummary(from, to);
    }

    /** @param from first day @param to last day @return appointments per dentist per day */
    public ReportTable dailyAppointmentsPerDentist(LocalDate from, LocalDate to) {
        return reportService.dailyAppointmentsPerDentist(from, to);
    }

    /** @param year year @param month month (1-12) @return revenue by treatment */
    public ReportTable monthlyRevenueByTreatment(int year, int month) {
        return reportService.monthlyRevenueByTreatment(year, month);
    }

    /** @param from first day @param to last day @param limit rows @return the most booked treatments */
    public ReportTable topTreatments(LocalDate from, LocalDate to, int limit) {
        return reportService.topTreatments(from, to, limit);
    }

    /** @param from first day @param to last day @return utilisation per dentist */
    public ReportTable dentistUtilization(LocalDate from, LocalDate to) {
        return reportService.dentistUtilization(from, to);
    }

    /** @param from first day @param to last day @return cancellation rates */
    public ReportTable cancellationRate(LocalDate from, LocalDate to) {
        return reportService.cancellationRate(from, to);
    }

    // ---- reminders ----

    /** @param date the appointment day, null for tomorrow @return how many reminders were due, sent and failed */
    public ReminderResult sendReminders(LocalDate date) {
        return reminderService.sendRemindersFor(date);
    }
}
