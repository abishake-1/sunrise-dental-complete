package com.sunrisedental.model;

import java.time.LocalDate;
import java.time.Period;

/** A patient of the clinic ({@code patients} table). */
public final class Patient extends BaseEntity {

    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String phone;
    private String email;
    private String address;
    private String medicalNotes;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getMedicalNotes() { return medicalNotes; }
    public void setMedicalNotes(String medicalNotes) { this.medicalNotes = medicalNotes; }

    /** @return first and last name separated by a space */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    /**
     * Computes the patient's age on a given day.
     *
     * @param date the reference day
     * @return completed years
     */
    public int getAgeOn(LocalDate date) {
        return Period.between(dateOfBirth, date).getYears();
    }

    @Override
    public String toString() {
        return "Patient{id=" + getId() + ", name='" + getFullName() + "'}";
    }
}
