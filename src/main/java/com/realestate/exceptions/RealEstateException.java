package com.realestate.exceptions;

/**
 * Base custom application exception for Real Estate Management System.
 */
public class RealEstateException extends Exception {
    private static final long serialVersionUID = 1L;
    private int errorCode;

    public RealEstateException(String message) {
        super(message);
        this.errorCode = 500;
    }

    public RealEstateException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public RealEstateException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 500;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
