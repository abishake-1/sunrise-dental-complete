package com.sunrisedental.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class AppConfigStep5Test {

    private static AppConfig of(String... keyValues) {
        Properties p = new Properties();
        for (int i = 0; i < keyValues.length; i += 2) {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return new AppConfig(p);
    }

    @Test
    void defaultsApplyWhenKeysAreMissing() {
        AppConfig c = of();
        assertEquals("Sunrise Dental Clinic", c.clinicName());
        assertEquals("LKR", c.currency());
        assertEquals("", c.consultationTreatmentCode());
        assertEquals(LocalTime.of(18, 0), c.reminderRunTime());
        assertTrue(c.remindersEnabled());
        assertEquals(List.of("txt", "html"), c.receiptFormats());
        assertEquals(6, c.workingDays().size());
        assertTrue(!c.workingDays().contains(DayOfWeek.SUNDAY));
    }

    @Test
    void configuredValuesAreParsed() {
        AppConfig c = of("clinic.working.days", "mon, Wed ,FRIDAY", "reminders.run.time", "07:30",
                "reminders.enabled", "false", "clinic.currency", "usd", "receipts.formats", "HTML");
        assertEquals(java.util.EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), c.workingDays());
        assertEquals(LocalTime.of(7, 30), c.reminderRunTime());
        assertEquals(false, c.remindersEnabled());
        assertEquals("USD", c.currency());
        assertEquals(List.of("html"), c.receiptFormats());
    }

    @Test
    void invalidValuesFailFast() {
        assertThrows(IllegalStateException.class, () -> of("clinic.working.days", "FUNDAY").workingDays());
        assertThrows(IllegalStateException.class, () -> of("clinic.working.days", " , ").workingDays());
        assertThrows(IllegalStateException.class, () -> of("reminders.run.time", "6pm").reminderRunTime());
        assertThrows(IllegalStateException.class, () -> of("clinic.currency", "rupees").currency());
    }
}
