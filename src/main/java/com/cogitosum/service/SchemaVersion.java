package com.cogitosum.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SchemaVersion(int major, int minor, int patch) implements Comparable<SchemaVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    public static SchemaVersion parse(String value) {
        Matcher matcher = VERSION_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid schema version: " + value);
        }
        return new SchemaVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
    }

    @Override
    public int compareTo(SchemaVersion other) {
        int majorOrder = Integer.compare(major, other.major);
        if (majorOrder != 0) return majorOrder;
        int minorOrder = Integer.compare(minor, other.minor);
        return minorOrder != 0 ? minorOrder : Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
