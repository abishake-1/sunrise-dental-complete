package com.sunrisedental.service;

import com.sunrisedental.dao.DataAccessException;
import com.sunrisedental.dao.PatientDao;
import com.sunrisedental.dao.TransactionManager;
import com.sunrisedental.model.Patient;
import com.sunrisedental.util.ConflictException;
import com.sunrisedental.util.NotFoundException;
import com.sunrisedental.util.ValidationException;
import com.sunrisedental.util.Validator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Patient registration, maintenance and search, with input validation. */
public final class PatientService {

    private static final int SEARCH_LIMIT = 50;
    private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

    private final TransactionManager transactions;
    private final PatientDao patientDao;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param transactions connection handling
     * @param patientDao   patient access
     * @param clock        source of "today"
     */
    public PatientService(TransactionManager transactions, PatientDao patientDao, Clock clock) {
        this.transactions = transactions;
        this.patientDao = patientDao;
        this.clock = clock;
    }

    /**
     * Registers a new patient.
     *
     * @param input the patient data (id ignored)
     * @return the stored patient
     * @throws ValidationException on invalid data
     * @throws ConflictException   if the e-mail address is already registered
     */
    public Patient register(Patient input) {
        Patient patient = normalize(input);
        try {
            return transactions.inTransaction(c -> patientDao.insert(c, patient));
        } catch (DataAccessException e) {
            throw translate(e);
        }
    }

    /**
     * Updates an existing patient.
     *
     * @param input the new data (id required)
     * @return the stored patient
     * @throws NotFoundException if the patient does not exist
     */
    public Patient update(Patient input) {
        long id = Validator.requireId(input == null ? null : input.getId(), "patientId");
        Patient patient = normalize(input);
        patient.setId(id);
        try {
            return transactions.inTransaction(c -> {
                if (!patientDao.update(c, patient)) {
                    throw new NotFoundException("Patient", id);
                }
                return patientDao.findById(c, id).orElseThrow(() -> new NotFoundException("Patient", id));
            });
        } catch (DataAccessException e) {
            throw translate(e);
        }
    }

    /**
     * Loads a patient.
     *
     * @param id patient id
     * @return the patient
     * @throws NotFoundException if absent
     */
    public Patient get(long id) {
        Validator.requireId(id, "patientId");
        return transactions.read(c -> patientDao.findById(c, id))
                .orElseThrow(() -> new NotFoundException("Patient", id));
    }

    /**
     * Searches by name or phone fragment.
     *
     * @param term 1 to 50 characters
     * @return up to 50 matches ordered by name
     */
    public List<Patient> search(String term) {
        String safeTerm = Validator.requireText(term, "search term", 50);
        return transactions.read(c -> patientDao.search(c, safeTerm, SEARCH_LIMIT));
    }

    /**
     * Deletes a patient that has no appointments.
     *
     * @param id patient id
     * @throws NotFoundException if absent
     * @throws ConflictException if appointments reference the patient
     */
    public void delete(long id) {
        Validator.requireId(id, "patientId");
        try {
            boolean deleted = transactions.inTransaction(c -> patientDao.delete(c, id));
            if (!deleted) {
                throw new NotFoundException("Patient", id);
            }
        } catch (DataAccessException e) {
            if (e.isForeignKeyViolation()) {
                throw new ConflictException("The patient has appointments and cannot be deleted");
            }
            throw e;
        }
    }

    private Patient normalize(Patient in) {
        Validator.requireNonNull(in, "patient");
        Patient p = new Patient();
        p.setFirstName(Validator.requireText(in.getFirstName(), "firstName", 50));
        p.setLastName(Validator.requireText(in.getLastName(), "lastName", 50));
        LocalDate dob = Validator.requireNonNull(in.getDateOfBirth(), "dateOfBirth");
        if (dob.isAfter(LocalDate.now(clock)) || dob.isBefore(EARLIEST_BIRTH_DATE)) {
            throw new ValidationException("dateOfBirth must be between 1900-01-01 and today");
        }
        p.setDateOfBirth(dob);
        p.setGender(Validator.requireNonNull(in.getGender(), "gender"));
        p.setPhone(Validator.requirePhone(in.getPhone()));
        p.setEmail(Validator.optionalEmail(in.getEmail()));
        p.setAddress(Validator.optionalText(in.getAddress(), "address", 255));
        p.setMedicalNotes(Validator.optionalText(in.getMedicalNotes(), "medicalNotes", 5000));
        return p;
    }

    private static RuntimeException translate(DataAccessException e) {
        if (e.isDuplicateKey()) {
            return new ConflictException("A patient with this e-mail address already exists");
        }
        if (e.isCheckViolation()) {
            return new ValidationException("The patient data violates a database constraint");
        }
        return e;
    }
}
