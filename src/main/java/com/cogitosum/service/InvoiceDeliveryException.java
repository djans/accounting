package com.cogitosum.service;

public class InvoiceDeliveryException extends RuntimeException {

    public InvoiceDeliveryException(String message) {
        super(message);
    }

    public InvoiceDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
