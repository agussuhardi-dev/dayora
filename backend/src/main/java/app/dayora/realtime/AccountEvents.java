package app.dayora.realtime;

import java.util.UUID;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountEvents {
  public record Committed(UUID owner, long cursor, boolean focus, UUID sessionId, String phase, Instant startedAt, long durationSeconds, int completedFocusCount) {
    public Committed(UUID owner, long cursor) { this(owner, cursor, false, null, null, null, 0, 0); }
    public Committed(UUID owner, long cursor, boolean focus) { this(owner, cursor, focus, null, null, null, 0, 0); }
  }
  private final JdbcTemplate jdbc;
  private final ApplicationEventPublisher publisher;
  public AccountEvents(JdbcTemplate jdbc, ApplicationEventPublisher publisher) { this.jdbc=jdbc; this.publisher=publisher; }
  @Transactional(propagation=Propagation.MANDATORY)
  public void lock(UUID owner) {
    jdbc.update("insert into account_event_counter(owner_id,next_sequence) values (?,1) on conflict (owner_id) do nothing",owner);
    jdbc.queryForObject("select next_sequence from account_event_counter where owner_id=? for update",Long.class,owner);
  }
  @Transactional(propagation=Propagation.MANDATORY)
  public void invalidate(UUID owner) { invalidate(owner, false); }
  @Transactional(propagation=Propagation.MANDATORY)
  public void invalidate(UUID owner, boolean focus) { invalidate(owner, focus, null); }
  @Transactional(propagation=Propagation.MANDATORY)
  public void invalidate(UUID owner, boolean focus, app.dayora.focus.domain.FocusSession session) {
    lock(owner);
    long seq=jdbc.queryForObject("select next_sequence from account_event_counter where owner_id=? for update",Long.class,owner);
    jdbc.update("update account_event_counter set next_sequence=? where owner_id=?",seq+1,owner);
    publisher.publishEvent(new Committed(owner,seq,focus,session==null?null:session.id(),session==null?null:session.phase().name(),session==null?null:session.startedAt(),session==null?0:session.durationSeconds(),session==null?0:session.completedFocusCount()));
  }
}
