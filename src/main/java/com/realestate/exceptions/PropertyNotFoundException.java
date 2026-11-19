package com.realestate.exceptions;

public class PropertyNotFoundException extends RealEstateException {
    private static final long serialVersionUID = 1L;

    public PropertyNotFoundException(int propertyId) {
        super("Property with ID " + propertyId + " not found or has been removed.", 404);
    }

    public PropertyNotFoundException(String message) {
        super(message, 404);
    }
}
