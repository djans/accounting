package com.cogitosum.setup;

public class DatabaseSetupException extends RuntimeException {

    private final String messageKey;

    public DatabaseSetupException(String messageKey, Throwable cause) {
        super(messageKey, cause);
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
