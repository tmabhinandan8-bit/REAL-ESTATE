package com.realestate.exceptions;

public class PaymentFailedException extends RealEstateException {
    private static final long serialVersionUID = 1L;

    public PaymentFailedException(String message) {
        super(message, 402);
    }
}
