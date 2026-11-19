package com.realestate.services;

import java.sql.SQLException;
import java.util.List;
import com.realestate.dao.AppointmentDAO;
import com.realestate.exceptions.InvalidPropertyDataException;
import com.realestate.model.appointment.PropertyVisit;

public class AppointmentService {

    private final AppointmentDAO appointmentDAO;
    private final AsyncNotificationService notificationService;

    public AppointmentService() {
        this.appointmentDAO = new AppointmentDAO();
        this.notificationService = AsyncNotificationService.getInstance();
    }

    public PropertyVisit scheduleVisit(PropertyVisit visit) throws SQLException, InvalidPropertyDataException {
        if (visit.getVisitDate() == null) {
            throw new InvalidPropertyDataException("Please choose a visit date.");
        }
        if (visit.getContactNumber() == null || visit.getContactNumber().trim().isEmpty()) {
            throw new InvalidPropertyDataException("Contact phone number is required.");
        }

        PropertyVisit scheduled = appointmentDAO.scheduleVisit(visit);

        notificationService.dispatchNotification(
            visit.getUserEmail() != null ? visit.getUserEmail() : "buyer@realestate.com",
            "Property Visit Scheduled",
            "Your appointment for property #" + visit.getPropertyId() + " on " + visit.getVisitDate() + " at " + visit.getVisitTime() + " is pending confirmation."
        );

        return scheduled;
    }

    public List<PropertyVisit> getVisitsByUser(int userId) throws SQLException {
        return appointmentDAO.getVisitsByUser(userId);
    }

    public List<PropertyVisit> getVisitsByOwner(int ownerId) throws SQLException {
        return appointmentDAO.getVisitsByOwner(ownerId);
    }

    public List<PropertyVisit> getAllVisits() throws SQLException {
        return appointmentDAO.getAllVisits();
    }

    public boolean updateVisitStatus(int visitId, String status) throws SQLException {
        return appointmentDAO.updateVisitStatus(visitId, status);
    }
}
