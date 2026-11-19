package com.realestate.model.appointment;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

public class PropertyVisit {
    private int visitId;
    private int propertyId;
    private String propertyTitle;
    private String propertyCity;
    private int userId;
    private String userName;
    private String userEmail;
    private Date visitDate;
    private Time visitTime;
    private String contactNumber;
    private String notes;
    private String appointmentStatus; // PENDING, APPROVED, REJECTED, COMPLETED
    private Timestamp createdAt;

    public PropertyVisit() {
        this.appointmentStatus = "PENDING";
    }

    public int getVisitId() { return visitId; }
    public void setVisitId(int visitId) { this.visitId = visitId; }

    public int getPropertyId() { return propertyId; }
    public void setPropertyId(int propertyId) { this.propertyId = propertyId; }

    public String getPropertyTitle() { return propertyTitle; }
    public void setPropertyTitle(String propertyTitle) { this.propertyTitle = propertyTitle; }

    public String getPropertyCity() { return propertyCity; }
    public void setPropertyCity(String propertyCity) { this.propertyCity = propertyCity; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public Date getVisitDate() { return visitDate; }
    public void setVisitDate(Date visitDate) { this.visitDate = visitDate; }

    public Time getVisitTime() { return visitTime; }
    public void setVisitTime(Time visitTime) { this.visitTime = visitTime; }

    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getAppointmentStatus() { return appointmentStatus; }
    public void setAppointmentStatus(String appointmentStatus) { this.appointmentStatus = appointmentStatus; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
