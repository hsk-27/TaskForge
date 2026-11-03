package com.taskforge.test;

import java.util.Objects;

/**
 * Lightweight, zero-dependency assertion utility matching standard Java library constraints.
 */
public final class Assert {
    private Assert() {}

    @FunctionalInterface
    public interface Executable {
        void execute() throws Throwable;
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion Failed: " + message + " (expected true, got false)");
        }
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError("Assertion Failed: " + message + " (expected false, got true)");
        }
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Assertion Failed: " + message + " (expected: [" + expected + "], actual: [" + actual + "])");
        }
    }

    public static void assertNotEquals(Object unexpected, Object actual, String message) {
        if (Objects.equals(unexpected, actual)) {
            throw new AssertionError("Assertion Failed: " + message + " (expected NOT to equal: [" + unexpected + "])");
        }
    }

    public static void assertNotNull(Object actual, String message) {
        if (actual == null) {
            throw new AssertionError("Assertion Failed: " + message + " (expected non-null object)");
        }
    }

    public static void assertNull(Object actual, String message) {
        if (actual != null) {
            throw new AssertionError("Assertion Failed: " + message + " (expected null, but was: [" + actual + "])");
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable) {
        try {
            executable.execute();
        } catch (Throwable actual) {
            if (expectedType.isInstance(actual)) {
                return (T) actual;
            }
            throw new AssertionError("Expected exception of type " + expectedType.getName() + " but got " + actual.getClass().getName(), actual);
        }
        throw new AssertionError("Expected exception of type " + expectedType.getName() + " but no exception was thrown");
    }

    public static void fail(String message) {
        throw new AssertionError("Test Failed: " + message);
    }
}
