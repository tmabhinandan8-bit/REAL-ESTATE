package com.realestate.exceptions;

public class AuthenticationException extends RealEstateException {
    private static final long serialVersionUID = 1L;

    public AuthenticationException(String message) {
        super(message, 401);
    }
}
