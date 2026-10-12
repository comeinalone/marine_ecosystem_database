package com.marine.ui;

import com.marine.model.Field;
import com.marine.model.LookupChoice;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class Validation {
    private Validation() { }

    public static Object parse(Field field, Object input) {
        if (input instanceof LookupChoice choice) return choice.id();
        String text = input == null ? "" : input.toString().trim();
        if (text.isEmpty()) {
            if (field.required()) throw new IllegalArgumentException(field.label() + " is required.");
            return null;
        }
        if (field.maxLength() > 0 && text.codePointCount(0, text.length()) > field.maxLength())
            throw new IllegalArgumentException(field.label() + " must be at most " + field.maxLength() + " characters.");
        if (field.kind() == Field.Kind.REFERENCE)
            throw new IllegalArgumentException("Select a valid " + field.label().toLowerCase() + ".");
        if (field.kind() == Field.Kind.INTEGER) {
            try {
                int value = Integer.parseInt(text);
                if (field.minimum() != null && value < field.minimum())
                    throw new IllegalArgumentException(field.label() + " must be at least " + field.minimum() + ".");
                return value;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(field.label() + " must be a whole number within the INT range.");
            }
        }
        if (field.kind() == Field.Kind.DATE) {
            try {
                LocalDate date = LocalDate.parse(text);
                if (date.isBefore(LocalDate.of(1900, 1, 1)) || date.getYear() > 9999)
                    throw new IllegalArgumentException(field.label() + " must be between 1900-01-01 and 9999-12-31.");
                if (field.name().equals("date_of_birth") && date.isAfter(LocalDate.now()))
                    throw new IllegalArgumentException("Date of birth cannot be in the future.");
                return date;
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException(field.label() + " must be a valid date in YYYY-MM-DD format.");
            }
        }
        if (field.kind() == Field.Kind.CHOICE && !field.options().contains(text))
            throw new IllegalArgumentException("Select a valid " + field.label().toLowerCase() + ".");
        if (field.name().equals("email_address") && !text.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Enter a valid email address.");
        return text;
    }
}
