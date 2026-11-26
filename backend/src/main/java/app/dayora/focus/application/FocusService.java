package app.dayora.focus.application;

import app.dayora.focus.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Transactional
public class FocusService {
  private static final Logger log=LoggerFactory.getLogger(FocusService.class);
  private final FocusRepository repository;
  private final app.dayora.realtime.AccountEvents events;
  private final app.dayora.workspace.WorkspaceService workspaces;
  public FocusService(FocusRepository repository, app.dayora.realtime.AccountEvents events, app.dayora.workspace.WorkspaceService workspaces) { this.repository = repository; this.events=events; this.workspaces=workspaces; }
  private FocusSession save(FocusSession session) {
    var saved=repository.save(session);
    events.invalidate(saved.ownerId(), true, saved.mode()==FocusSession.Mode.POMODORO?saved:null);
    return saved;
  }

  public FocusSession start(UUID owner, UUID task, long duration) { return start(owner, task, null, duration, null, null, null); }

  public FocusSession start(UUID owner, UUID task, FocusSession.Mode mode, Long duration, Integer focus, Integer shortBreak, Integer longBreak) {
    return start(owner,null,task,mode,duration,focus,shortBreak,longBreak,null);
  }

  public FocusSession start(UUID owner, UUID workspace, UUID task, FocusSession.Mode mode, Long duration, Integer focus, Integer shortBreak, Integer longBreak) {
    return start(owner,workspace,task,mode,duration,focus,shortBreak,longBreak,null);
  }

  public FocusSession start(UUID owner, UUID workspace, UUID task, FocusSession.Mode mode, Long duration, Integer focus, Integer shortBreak, Integer longBreak, Integer cycles) {
    events.lock(owner); workspace=workspaces.resolve(owner,workspace); if(task!=null && !repository.ownsTask(owner,task)) throw new NotFound();
    if (task != null && !repository.ownsTask(owner, task)) throw new NotFound();
    if (task != null && !repository.taskInWorkspace(owner,task,workspace)) throw new IllegalArgumentException("task unavailable in workspace");
    if (duration != null && (duration < 1 || duration > 86400)) throw new IllegalArgumentException("Duration must be 1–86400 seconds");
    int focusMinutes = focus == null ? 25 : focus;
    int shortBreakMinutes = shortBreak == null ? 5 : shortBreak;
    int longBreakMinutes = longBreak == null ? 15 : longBreak;
    int cycleCount = cycles == null ? 4 : cycles;
    if (cycleCount < 1 || cycleCount > 12) throw new IllegalArgumentException("Cycles must be 1–12");
    if (focusMinutes < 1 || focusMinutes > 1440 || shortBreakMinutes < 1 || shortBreakMinutes > 1440 || longBreakMinutes < 1 || longBreakMinutes > 1440) throw new IllegalArgumentException("Pomodoro durations must be 1–1440 minutes");
    if (current(owner).isPresent()) throw new FocusSession.Conflict();
    long seconds = mode == FocusSession.Mode.POMODORO ? focusMinutes * 60L : duration == null ? 86400 : duration;
    return save(FocusSession.start(owner, workspace, task, mode == null ? FocusSession.Mode.NORMAL : mode, seconds, focusMinutes, shortBreakMinutes, longBreakMinutes, cycleCount, Instant.now()));
  }

  public FocusSession get(UUID owner, UUID id) { return settle(repository.lock(owner, id).orElseThrow(NotFound::new)); }

  public Optional<FocusSession> current(UUID owner) {
    return repository.findActive(owner).map(s -> get(owner, s.id()));
  }

  public List<FocusSession> history(UUID owner) { return history(owner,null); }
  public List<FocusSession> history(UUID owner, UUID workspace) {
    current(owner); if(workspace!=null)workspaces.require(owner,workspace);
    return repository.findHistory(owner).stream().filter(s->workspace==null||workspace.equals(s.workspaceId())).toList();
  }

  public List<FocusSession> historySince(UUID owner, UUID workspace, Instant since) {
    current(owner); if (workspace != null) workspaces.require(owner, workspace);
    return repository.findHistorySince(owner, since).stream().filter(s -> workspace == null || workspace.equals(s.workspaceId())).toList();
  }

  private FocusSession settle(FocusSession session) {
    Instant now = Instant.now();
    if (!session.expired(now)) return session;
    Instant end = session.runningSince().plusSeconds(Math.max(0, session.durationSeconds() - session.activeDurationSeconds()));
    var completed = saveCompletion(session.autoComplete(end));
    return completed.mode() == FocusSession.Mode.POMODORO ? advance(completed, end) : completed;
  }

  @Scheduled(fixedDelay = 1000)
  public void advanceExpiredPomodoros() {
    for (var session : repository.findRunningPomodoros()) {
      try {
        var settled=settle(get(session.ownerId(), session.id()));
        log.info("Pomodoro scheduler checked owner={} session={} phase={} count={} state={}",session.ownerId(),session.id(),settled.phase(),settled.completedFocusCount(),settled.state());
      } catch (FocusSession.Conflict e) {
        log.debug("Pomodoro scheduler skipped conflicting session owner={} session={}",session.ownerId(),session.id());
      } catch (RuntimeException e) {
        log.error("Pomodoro scheduler failed owner={} session={} phase={}",session.ownerId(),session.id(),session.phase(),e);
      }
    }
  }

  private FocusSession advance(FocusSession previous, Instant now) {
    events.lock(previous.ownerId());
    var active = repository.findActive(previous.ownerId());
    if (active.isPresent()) return active.get();
    var phase = previous.phase() == FocusSession.Phase.FOCUS
        ? (previous.completedFocusCount() % previous.cyclesBeforeLongBreak() == 0 ? FocusSession.Phase.LONG_BREAK : FocusSession.Phase.SHORT_BREAK)
        : FocusSession.Phase.FOCUS;
    long duration = 60L * (phase == FocusSession.Phase.FOCUS ? previous.focusDurationMinutes() : phase == FocusSession.Phase.SHORT_BREAK ? previous.shortBreakDurationMinutes() : previous.longBreakDurationMinutes());
    return save(new FocusSession(UUID.randomUUID(), previous.ownerId(), previous.workspaceId(), previous.taskId(), FocusSession.State.RUNNING,
        previous.mode(), phase, previous.completedFocusCount(), duration, 0, previous.focusDurationMinutes(), previous.shortBreakDurationMinutes(), previous.longBreakDurationMinutes(), previous.cyclesBeforeLongBreak(), now, now, null));
  }

  private FocusSession saveCompletion(FocusSession session) {
    save(session);
    if (session.phase() == FocusSession.Phase.FOCUS) repository.createTimeEntry(TimeEntry.completed(session));
    return session;
  }

  public FocusSession transition(UUID owner, UUID id, FocusSession.Action action) {
    var current = get(owner, id);
    var next = current.transition(action, Instant.now());
    if (next == current) return current;
    if (next.state() == FocusSession.State.COMPLETED) return saveCompletion(next);
    return save(next);
  }

  public FocusSession next(UUID owner, UUID id) {
    var previous = get(owner, id);
    if (previous.mode() != FocusSession.Mode.POMODORO) throw new FocusSession.Conflict();
    var active = repository.findActive(owner);
    if (active.isPresent()) return active.get();
    if (previous.state() != FocusSession.State.COMPLETED || !repository.findHistory(owner).getFirst().id().equals(id)) throw new FocusSession.Conflict();
    return advance(previous, Instant.now());
  }

  public static class NotFound extends RuntimeException {}
}
