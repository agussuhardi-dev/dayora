package app.dayora.calendar.domain;

import java.time.*;
import java.util.UUID;

public record CalendarEvent(UUID id, UUID ownerId, UUID taskId, String title, Instant startsAt,
                            Instant endsAt, String timezone, String category, String color,
                            long revision, Instant createdAt, Instant updatedAt, Instant deletedAt, UUID workspaceId) {
  public CalendarEvent(UUID id,UUID ownerId,UUID taskId,String title,Instant startsAt,Instant endsAt,String timezone,String category,String color,long revision,Instant createdAt,Instant updatedAt,Instant deletedAt){this(id,ownerId,taskId,title,startsAt,endsAt,timezone,category,color,revision,createdAt,updatedAt,deletedAt,null);}
  public CalendarEvent {
    if (id == null || ownerId == null) throw new IllegalArgumentException("id and owner required");
    if (title == null || title.isBlank() || title.length() > 240) throw new IllegalArgumentException("title must be 1-240 characters");
    if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) throw new IllegalArgumentException("endsAt must be after startsAt");
    if (timezone == null || !ZoneId.getAvailableZoneIds().contains(timezone)) throw new IllegalArgumentException("IANA timezone required");
    if (category != null && category.length() > 64 || color != null && color.length() > 32) throw new IllegalArgumentException("category or color too long");
    try { ZoneId.of(timezone); } catch (DateTimeException e) { throw new IllegalArgumentException("invalid timezone", e); }
    if (revision < 1) throw new IllegalArgumentException("revision must be positive");
  }
  public static CalendarEvent create(UUID owner, UUID task, String title, Instant starts, Instant ends, String timezone, String category, String color) {
    var now = Instant.now();
    return new CalendarEvent(UUID.randomUUID(), owner, task, title, starts, ends, timezone, category, color, 1, now, now, null);
  }
}
