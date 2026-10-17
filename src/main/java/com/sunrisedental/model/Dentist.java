package com.sunrisedental.model;

import java.time.LocalTime;

/** A dentist who receives appointments ({@code dentists} table). */
public final class Dentist extends BaseEntity {

    private String fullName;
    private String specialization;
    private String licenseNumber;
    private String phone;
    private String email;
    private LocalTime workStart;
    private LocalTime workEnd;
    private boolean active = true;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalTime getWorkStart() { return workStart; }
    public void setWorkStart(LocalTime workStart) { this.workStart = workStart; }
    public LocalTime getWorkEnd() { return workEnd; }
    public void setWorkEnd(LocalTime workEnd) { this.workEnd = workEnd; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return "Dentist{id=" + getId() + ", name='" + fullName + "'}";
    }
}
