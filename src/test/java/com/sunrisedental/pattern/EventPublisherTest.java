package com.sunrisedental.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventPublisherTest {

    private static AppointmentCreatedEvent event() {
        return new AppointmentCreatedEvent(1, "APT-0001", 2, "Jane Doe", null, "Dr. A", "Check-up",
                LocalDate.of(2026, 10, 12), LocalTime.of(9, 0), "reception");
    }

    @Test
    void deliversToAllListenersEvenWhenOneFails() {
        EventPublisher publisher = new EventPublisher();
        List<String> received = new ArrayList<>();
        publisher.subscribe(AppointmentCreatedEvent.class, e -> {
            throw new IllegalStateException("boom");
        });
        publisher.subscribe(AppointmentCreatedEvent.class, e -> received.add(e.appointmentNumber()));
        publisher.publish(event());
        assertEquals(List.of("APT-0001"), received);
    }
}
