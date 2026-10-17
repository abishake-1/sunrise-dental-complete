package com.sunrisedental.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A booking of a patient with a dentist for a treatment ({@code appointments} table).
 * New instances are created through {@code AppointmentBuilder}; the number is assigned by the database.
 */
public final class Appointment extends BaseEntity {

    private String appointmentNumber;
    private Long patientId;
    private Long dentistId;
    private Long treatmentId;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private AppointmentStatus status = AppointmentStatus.SCHEDULED;
    private String notes;
    private Long createdBy;

    public String getAppointmentNumber() { return appointmentNumber; }
    public void setAppointmentNumber(String appointmentNumber) { this.appointmentNumber = appointmentNumber; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public Long getDentistId() { return dentistId; }
    public void setDentistId(Long dentistId) { this.dentistId = dentistId; }
    public Long getTreatmentId() { return treatmentId; }
    public void setTreatmentId(Long treatmentId) { this.treatmentId = treatmentId; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public LocalTime getAppointmentTime() { return appointmentTime; }
    public void setAppointmentTime(LocalTime appointmentTime) { this.appointmentTime = appointmentTime; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    @Override
    public String toString() {
        return "Appointment{" + appointmentNumber + ", " + appointmentDate + " " + appointmentTime
                + ", dentist=" + dentistId + ", status=" + status + '}';
    }
}
