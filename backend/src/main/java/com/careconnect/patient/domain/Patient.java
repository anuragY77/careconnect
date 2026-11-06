package com.careconnect.patient.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A registered patient. The MRN is set once at registration and never changes (BR-14); the
 * database rejects a change too. A patient is never deleted (BR-08).
 */
@Entity
@Table(name = "patient")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patient_id")
    private Long id;

    @Column(name = "mrn", nullable = false, updatable = false, length = 20)
    private String mrn;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "dob", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 10)
    private Sex sex;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "emergency_contact_name", length = 120)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "insurance_provider", length = 120)
    private String insuranceProvider;

    @Column(name = "insurance_policy_number", length = 60)
    private String insurancePolicyNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private PatientStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /** Required by JPA. */
    protected Patient() {
    }

    /** Creates an active patient. Text values are expected to be trimmed already by the caller. */
    public static Patient register(String mrn, String firstName, String lastName, LocalDate dateOfBirth, Sex sex,
                                   String phone, String email, String address, String emergencyContactName,
                                   String emergencyContactPhone, String insuranceProvider,
                                   String insurancePolicyNumber) {
        requireText(mrn, "mrn");
        requireText(firstName, "firstName");
        requireText(lastName, "lastName");
        if (dateOfBirth == null) {
            throw new IllegalArgumentException("dateOfBirth is required");
        }
        if (sex == null) {
            throw new IllegalArgumentException("sex is required");
        }
        Patient p = new Patient();
        p.mrn = mrn;
        p.firstName = firstName;
        p.lastName = lastName;
        p.dateOfBirth = dateOfBirth;
        p.sex = sex;
        p.phone = phone;
        p.email = email;
        p.address = address;
        p.emergencyContactName = emergencyContactName;
        p.emergencyContactPhone = emergencyContactPhone;
        p.insuranceProvider = insuranceProvider;
        p.insurancePolicyNumber = insurancePolicyNumber;
        p.status = PatientStatus.ACTIVE;
        return p;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getMrn() { return mrn; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public Sex getSex() { return sex; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public String getInsuranceProvider() { return insuranceProvider; }
    public String getInsurancePolicyNumber() { return insurancePolicyNumber; }
    public PatientStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
