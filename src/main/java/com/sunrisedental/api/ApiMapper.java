package com.sunrisedental.api;

import com.sunrisedental.dto.ReportTable;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.Dentist;
import com.sunrisedental.model.Patient;
import com.sunrisedental.model.Treatment;
import com.sunrisedental.model.User;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts model objects to the JSON shapes of the API. Sensitive data (password hashes) never appears. */
public final class ApiMapper {

    private ApiMapper() {
    }

    /** @param user the user @return its public representation */
    public static Map<String, Object> user(User user) {
        return Json.object("id", user.getId(), "username", user.getUsername(),
                "fullName", user.getFullName(), "role", user.getRole());
    }

    /** @param a the appointment @return its JSON representation */
    public static Map<String, Object> appointment(Appointment a) {
        return Json.object("id", a.getId(), "appointmentNumber", a.getAppointmentNumber(),
                "patientId", a.getPatientId(), "dentistId", a.getDentistId(), "treatmentId", a.getTreatmentId(),
                "date", a.getAppointmentDate(), "time", a.getAppointmentTime(), "status", a.getStatus(),
                "notes", a.getNotes(), "createdBy", a.getCreatedBy(),
                "createdAt", a.getCreatedAt(), "updatedAt", a.getUpdatedAt());
    }

    /** @param d the dentist @return its JSON representation */
    public static Map<String, Object> dentist(Dentist d) {
        return Json.object("id", d.getId(), "fullName", d.getFullName(), "specialization", d.getSpecialization(),
                "workStart", d.getWorkStart(), "workEnd", d.getWorkEnd());
    }

    /** @param t the treatment @return its JSON representation */
    public static Map<String, Object> treatment(Treatment t) {
        return Json.object("id", t.getId(), "code", t.getCode(), "name", t.getName(),
                "description", t.getDescription(), "durationMinutes", t.getDurationMinutes(), "price", t.getPrice());
    }

    /** @param p the patient @return its JSON representation */
    public static Map<String, Object> patient(Patient p) {
        return Json.object("id", p.getId(), "firstName", p.getFirstName(), "lastName", p.getLastName(),
                "dateOfBirth", p.getDateOfBirth(), "gender", p.getGender(), "phone", p.getPhone(),
                "email", p.getEmail(), "address", p.getAddress(), "medicalNotes", p.getMedicalNotes(),
                "createdAt", p.getCreatedAt());
    }

    /**
     * Converts a report to JSON: metadata plus one object per row, keyed by column name.
     *
     * @param table the report result
     * @return the JSON structure
     */
    public static Map<String, Object> reportTable(ReportTable table) {
        List<Object> rows = new ArrayList<>();
        for (List<Object> row : table.rows()) {
            Map<String, Object> object = new LinkedHashMap<>();
            for (int i = 0; i < table.columns().size(); i++) {
                object.put(table.columns().get(i), row.get(i));
            }
            rows.add(object);
        }
        return Json.object("report", table.report(), "title", table.title(), "parameters", table.parameters(),
                "columns", table.columns(), "rowCount", rows.size(), "rows", rows);
    }

    /**
     * Wraps mapped items in a list envelope.
     *
     * @param items the mapped items
     * @return {@code {"count":n,"items":[...]}}
     */
    public static Map<String, Object> list(List<Map<String, Object>> items) {
        return Json.object("count", items.size(), "items", items);
    }
}
