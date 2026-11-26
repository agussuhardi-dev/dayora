package app.dayora.calendar.adapter.out.persistence;

import app.dayora.calendar.domain.CalendarEvent;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="calendar_event") class CalendarJpaEntity {
  @Column(name="workspace_id",nullable=false) UUID workspaceId;
  @Id UUID id; @Column(name="owner_id",nullable=false) UUID ownerId; @Column(name="task_id") UUID taskId;
  @Column(nullable=false,length=240) String title; @Column(name="starts_at",nullable=false) Instant startsAt; @Column(name="ends_at",nullable=false) Instant endsAt;
  @Column(nullable=false,length=64) String timezone; @Column(length=64) String category; @Column(length=32) String color;
  @Version @Column(nullable=false) long revision; @Column(name="created_at",nullable=false) Instant createdAt; @Column(name="updated_at",nullable=false) Instant updatedAt; @Column(name="deleted_at") Instant deletedAt;
  protected CalendarJpaEntity() {} CalendarJpaEntity(CalendarEvent e) { id=e.id();ownerId=e.ownerId();workspaceId=e.workspaceId();apply(e);revision=e.revision();createdAt=e.createdAt(); }
  void apply(CalendarEvent e){taskId=e.taskId();title=e.title();startsAt=e.startsAt();endsAt=e.endsAt();timezone=e.timezone();category=e.category();color=e.color();updatedAt=e.updatedAt();deletedAt=e.deletedAt();}
  CalendarEvent domain(){return new CalendarEvent(id,ownerId,taskId,title,startsAt,endsAt,timezone,category,color,revision,createdAt,updatedAt,deletedAt,workspaceId);}
}
