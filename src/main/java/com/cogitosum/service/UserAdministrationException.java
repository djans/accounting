package com.cogitosum.service;

public final class UserAdministrationException extends RuntimeException {

    public UserAdministrationException(String messageCode) {
        super(messageCode);
    }
}
