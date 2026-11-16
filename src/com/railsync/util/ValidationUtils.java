package com.railsync.util;

import com.railsync.exception.InvalidLoginException;
import com.railsync.exception.InvalidPassengerException;
import com.railsync.exception.InvalidStationException;
import com.railsync.model.Station;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Helper methods for validating passenger details, phone numbers, and station inputs.
 */
public final class ValidationUtils {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9\\s.\\-]{2,50}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern PNR_PATTERN = Pattern.compile("^RS[A-Z0-9]{6,8}$");

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_.]{3,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private ValidationUtils() {
        // Prevent instantiation of utility class
    }

    public static void validatePassenger(String name, int age, String phone) throws InvalidPassengerException {
        if (name == null || !NAME_PATTERN.matcher(name.trim()).matches()) {
            throw new InvalidPassengerException("Invalid passenger name: '" + name + "'. Name must contain 2-50 letters and spaces.");
        }
        if (age < 0 || age > 125) {
            throw new InvalidPassengerException("Invalid age: " + age + ". Age must be between 0 and 125.");
        }
        if (phone != null && !phone.trim().isEmpty() && !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new InvalidPassengerException("Invalid mobile number: '" + phone + "'. Must be a 10-digit Indian phone number starting with 6-9.");
        }
    }

    public static void validateStations(Station source, Station destination) throws InvalidStationException {
        if (source == null || destination == null) {
            throw new InvalidStationException("Source and destination stations cannot be null.");
        }
        if (source.getCode().equalsIgnoreCase(destination.getCode())) {
            throw new InvalidStationException("Source and Destination stations cannot be identical (" + source.getCode() + ").");
        }
    }

    public static void validateJourneyDate(LocalDate date) throws IllegalArgumentException {
        if (date == null) {
            throw new IllegalArgumentException("Journey date cannot be null.");
        }
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            throw new IllegalArgumentException("Journey date cannot be in the past: " + date);
        }
        if (date.isAfter(today.plusDays(120))) {
            throw new IllegalArgumentException("Advance Reservation Period is maximum 120 days from today.");
        }
    }

    public static boolean isValidPnr(String pnr) {
        return pnr != null && PNR_PATTERN.matcher(pnr.trim().toUpperCase()).matches();
    }

    // ------------------------------------------------------------ login / registration

    /** Basic sanity checks on login input (blank fields, absurd lengths). */
    public static void validateLoginInput(String username, String password) throws InvalidLoginException {
        if (username == null || username.isBlank()) {
            throw new InvalidLoginException("Please enter your username.");
        }
        if (password == null || password.isEmpty()) {
            throw new InvalidLoginException("Please enter your password.");
        }
        if (username.length() > 64 || password.length() > 128) {
            throw new InvalidLoginException("Username or password is too long.");
        }
    }

    /** Full validation for a new account. Throws {@link InvalidLoginException} describing the first problem. */
    public static void validateRegistration(String username, String fullName, String email, String phone,
                                            String password) throws InvalidLoginException {
        if (username == null || !USERNAME_PATTERN.matcher(username.trim().toLowerCase()).matches()) {
            throw new InvalidLoginException("Username must be 3-30 characters: letters, digits, '_' or '.'.");
        }
        if (fullName == null || fullName.trim().length() < 2 || fullName.trim().length() > 60) {
            throw new InvalidLoginException("Please enter your full name (2-60 characters).");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new InvalidLoginException("Please enter a valid email address.");
        }
        if (phone != null && !phone.trim().isEmpty() && !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            throw new InvalidLoginException("Mobile number must be 10 digits starting with 6-9.");
        }
        if (password == null || password.length() < 6 || password.length() > 64) {
            throw new InvalidLoginException("Password must be 6-64 characters long.");
        }
        boolean letter = false;
        boolean digit = false;
        for (char ch : password.toCharArray()) {
            if (Character.isLetter(ch)) letter = true;
            if (Character.isDigit(ch)) digit = true;
        }
        if (!letter || !digit) {
            throw new InvalidLoginException("Password must contain at least one letter and one digit.");
        }
    }
}
