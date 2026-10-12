package com.marine.ui;

import com.marine.model.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {
    @Test void requiredNullableAndLengthRules() {
        assertThrows(IllegalArgumentException.class, () -> Validation.parse(Field.text("name", "Name", 3, true), "  "));
        assertNull(Validation.parse(Field.text("name", "Name", 3, false), "  "));
        assertEquals("abc", Validation.parse(Field.text("name", "Name", 3, true), " abc "));
        assertThrows(IllegalArgumentException.class, () -> Validation.parse(Field.text("name", "Name", 3, true), "abcd"));
    }
    @Test void numericBoundsAndOverflow() {
        Field capacity = Field.number("vessel_capacity", "Capacity", 1);
        assertEquals(1, Validation.parse(capacity, "1"));
        assertThrows(IllegalArgumentException.class, () -> Validation.parse(capacity, "0"));
        assertThrows(IllegalArgumentException.class, () -> Validation.parse(capacity, "2147483648"));
        assertThrows(IllegalArgumentException.class, () -> Validation.parse(capacity, "1.5"));
        assertNull(Validation.parse(capacity, ""));
    }
    @Test void validDatesAndSchemaLowerBound() {
        Field date = Field.date("collection_date", "Collection date");
        assertEquals(LocalDate.of(2024, 2, 29), Validation.parse(date, "2024-02-29"));
        for (String invalid : List.of("2025-02-29", "1899-12-31", "12/01/2026", "+10000-01-01"))
            assertThrows(IllegalArgumentException.class, () -> Validation.parse(date, invalid));
        assertThrows(IllegalArgumentException.class, () -> Validation.parse(Field.date("date_of_birth", "DOB"), LocalDate.now().plusDays(1)));
    }
    @Test void referencesRequireAnActualChoiceAndStoreTheId() {
        Field species = Field.ref("speciesID", "Species", true, "SPECIES");
        assertEquals(301, Validation.parse(species, new LookupChoice(301, "Whale Shark")));
        assertThrows(IllegalArgumentException.class, () -> Validation.parse(species, "301"));
        assertThrows(IllegalArgumentException.class, () -> Validation.parse(species, null));
    }
    @Test void nullableChoicesAndEmailValidation() {
        Field quality = Field.choice("DNA_quality", "Quality", 20, "High", "Medium", "Low");
        assertNull(Validation.parse(quality, ""));
        assertEquals("High", Validation.parse(quality, "High"));
        assertThrows(IllegalArgumentException.class, () -> Validation.parse(quality, "Invalid"));
        Field email = Field.text("email_address", "Email", 100, true);
        assertEquals("test@example.invalid", Validation.parse(email, "test@example.invalid"));
        assertThrows(IllegalArgumentException.class, () -> Validation.parse(email, "bad-email"));
    }
}
