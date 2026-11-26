package app.dayora.calendar.application;

import app.dayora.calendar.domain.CalendarEvent;
import java.time.Instant;
import java.util.*;

public interface CalendarRepository {
  CalendarEvent save(CalendarEvent event, long expectedRevision);
  Optional<CalendarEvent> find(UUID owner, UUID id);
  List<CalendarEvent> findBetween(UUID owner, Instant from, Instant to);
  boolean ownsTask(UUID owner, UUID task);
}
