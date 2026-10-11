package com.cogitosum.entity;

public enum UserRole {
    ADMIN,
    ACCOUNTANT,
    BOOKKEEPER,
    PAYMENT_OPERATOR,
    AUDITOR,
    // Retained so JPA can load legacy accounts until their old value is migrated.
    VIEWER
}
