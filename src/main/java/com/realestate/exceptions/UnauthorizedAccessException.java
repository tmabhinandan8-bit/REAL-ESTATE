package com.realestate.exceptions;

public class UnauthorizedAccessException extends RealEstateException {
    private static final long serialVersionUID = 1L;

    public UnauthorizedAccessException(String message) {
        super(message, 403);
    }
}
