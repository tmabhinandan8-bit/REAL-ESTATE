package com.realestate.services;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import com.realestate.dao.PropertyDAO;
import com.realestate.exceptions.InvalidPropertyDataException;
import com.realestate.exceptions.PropertyNotFoundException;
import com.realestate.exceptions.UnauthorizedAccessException;
import com.realestate.model.property.Property;

public class PropertyService {

    private final PropertyDAO propertyDAO;
    private final AsyncNotificationService notificationService;

    public PropertyService() {
        this.propertyDAO = new PropertyDAO();
        this.notificationService = AsyncNotificationService.getInstance();
    }

    public Property postProperty(Property property) throws SQLException, InvalidPropertyDataException {
        if (property.getTitle() == null || property.getTitle().trim().length() < 5) {
            throw new InvalidPropertyDataException("Property title must be at least 5 characters.");
        }
        if (property.getPrice() <= 0) {
            throw new InvalidPropertyDataException("Property price must be greater than zero.");
        }
        if (property.getPropertyArea() <= 0) {
            throw new InvalidPropertyDataException("Property area must be greater than zero.");
        }
        if (property.getCity() == null || property.getCity().trim().isEmpty()) {
            throw new InvalidPropertyDataException("City is required.");
        }

        Property saved = propertyDAO.createProperty(property);

        // Async admin alert
        notificationService.dispatchNotification(
            "admin@realestate.com",
            "New Property Submitted for Review",
            "Property ID #" + saved.getPropertyId() + " (" + saved.getTitle() + ") submitted by Owner ID " + saved.getOwnerId() + " awaiting approval."
        );

        return saved;
    }

    public Property getPropertyDetails(int propertyId) throws SQLException, PropertyNotFoundException {
        return propertyDAO.getPropertyById(propertyId);
    }

    public List<Property> searchProperties(Map<String, String> filters, String sortBy) throws SQLException {
        return propertyDAO.searchProperties(filters, sortBy);
    }

    public List<Property> getFeaturedProperties(int limit) throws SQLException {
        return propertyDAO.getFeaturedProperties(limit);
    }

    public List<Property> getPendingProperties() throws SQLException {
        return propertyDAO.getPendingProperties();
    }

    public List<Property> getPropertiesByOwner(int ownerId) throws SQLException {
        return propertyDAO.getPropertiesByOwner(ownerId);
    }

    public boolean approveProperty(int propertyId) throws SQLException, PropertyNotFoundException {
        boolean ok = propertyDAO.updateApprovalStatus(propertyId, "APPROVED", "");
        if (ok) {
            Property p = propertyDAO.getPropertyById(propertyId);
            notificationService.dispatchNotification(
                p.getOwnerEmail(),
                "Property Listing Approved!",
                "Your property '" + p.getTitle() + "' has been approved and is now live on the marketplace."
            );
        }
        return ok;
    }

    public boolean rejectProperty(int propertyId, String reason) throws SQLException, PropertyNotFoundException {
        boolean ok = propertyDAO.updateApprovalStatus(propertyId, "REJECTED", reason);
        if (ok) {
            Property p = propertyDAO.getPropertyById(propertyId);
            notificationService.dispatchNotification(
                p.getOwnerEmail(),
                "Property Listing Update",
                "Your property listing '" + p.getTitle() + "' was not approved. Reason: " + reason
            );
        }
        return ok;
    }

    public boolean deleteProperty(int propertyId, int currentUserId, boolean isAdmin) 
            throws SQLException, UnauthorizedAccessException, PropertyNotFoundException {
        return propertyDAO.deleteProperty(propertyId, currentUserId, isAdmin);
    }
}
