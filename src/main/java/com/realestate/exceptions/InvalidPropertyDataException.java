package com.realestate.exceptions;

public class InvalidPropertyDataException extends RealEstateException {
    private static final long serialVersionUID = 1L;

    public InvalidPropertyDataException(String message) {
        super(message, 400);
    }
}
