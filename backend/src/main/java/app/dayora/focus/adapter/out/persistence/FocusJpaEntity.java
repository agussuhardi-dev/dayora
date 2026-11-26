package app.dayora.focus.adapter.out.persistence;

import app.dayora.focus.domain.FocusSession;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "focus_session")
class FocusJpaEntity {
  @Id UUID id;
  @Column(name="owner_id", nullable=false) UUID ownerId;
  @Column(name="workspace_id", nullable=false) UUID workspaceId;
  @Column(name="task_id") UUID taskId;
  @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) FocusSession.State state;
  @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) FocusSession.Mode mode;
  @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) FocusSession.Phase phase;
  @Column(name="completed_focus_count", nullable=false) int completedFocusCount;
  @Column(name="duration_seconds", nullable=false) long durationSeconds;
  @Column(name="active_duration_seconds", nullable=false) long activeDurationSeconds;
  @Column(name="focus_duration_minutes", nullable=false) int focusDurationMinutes;
  @Column(name="short_break_duration_minutes", nullable=false) int shortBreakDurationMinutes;
  @Column(name="long_break_duration_minutes", nullable=false) int longBreakDurationMinutes;
  @Column(name="cycles_before_long_break", nullable=false) int cyclesBeforeLongBreak;
  @Column(name="started_at", nullable=false) Instant startedAt;
  @Column(name="running_since") Instant runningSince;
  @Column(name="ended_at") Instant endedAt;
  protected FocusJpaEntity() {}
  FocusJpaEntity(FocusSession s) { id=s.id(); apply(s); }
  void apply(FocusSession s) { ownerId=s.ownerId(); workspaceId=s.workspaceId(); taskId=s.taskId(); state=s.state(); mode=s.mode(); phase=s.phase(); completedFocusCount=s.completedFocusCount(); durationSeconds=s.durationSeconds(); activeDurationSeconds=s.activeDurationSeconds(); focusDurationMinutes=s.focusDurationMinutes(); shortBreakDurationMinutes=s.shortBreakDurationMinutes(); longBreakDurationMinutes=s.longBreakDurationMinutes(); cyclesBeforeLongBreak=s.cyclesBeforeLongBreak(); startedAt=s.startedAt(); runningSince=s.runningSince(); endedAt=s.endedAt(); }
  FocusSession toDomain() { return new FocusSession(id,ownerId,workspaceId,taskId,state,mode,phase,completedFocusCount,durationSeconds,activeDurationSeconds,focusDurationMinutes,shortBreakDurationMinutes,longBreakDurationMinutes,cyclesBeforeLongBreak,startedAt,runningSince,endedAt); }
}
