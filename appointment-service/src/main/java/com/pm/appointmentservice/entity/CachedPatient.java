package com.pm.appointmentservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cached_patient")
public class CachedPatient {

    @Id
    private UUID id;
    private String fullName;
    private String email;
    private Instant UpdatedAT;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getUpdatedAT() {
        return UpdatedAT;
    }

    public void setUpdatedAT(Instant updatedAT) {
        UpdatedAT = updatedAT;
    }
}
