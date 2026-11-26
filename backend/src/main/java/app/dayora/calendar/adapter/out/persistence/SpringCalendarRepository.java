package app.dayora.calendar.adapter.out.persistence;

import app.dayora.calendar.application.*;
import app.dayora.calendar.domain.CalendarEvent;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository public class SpringCalendarRepository implements CalendarRepository {
  private final EntityManager em; private final JdbcTemplate jdbc;
  public SpringCalendarRepository(EntityManager em,JdbcTemplate jdbc){this.em=em;this.jdbc=jdbc;}
  public Optional<CalendarEvent> find(UUID owner,UUID id){return entity(owner,id).map(CalendarJpaEntity::domain);}
  private Optional<CalendarJpaEntity> entity(UUID owner,UUID id){return em.createQuery("from CalendarJpaEntity where ownerId=:owner and id=:id and deletedAt is null",CalendarJpaEntity.class).setParameter("owner",owner).setParameter("id",id).getResultStream().findFirst();}
  public List<CalendarEvent> findBetween(UUID owner,Instant from,Instant to){return em.createQuery("from CalendarJpaEntity where ownerId=:owner and deletedAt is null and startsAt < :to and endsAt > :from order by startsAt,id",CalendarJpaEntity.class).setParameter("owner",owner).setParameter("from",from).setParameter("to",to).getResultList().stream().map(CalendarJpaEntity::domain).toList();}
  public boolean ownsTask(UUID owner,UUID task){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from task where owner_id=? and id=? and deleted_at is null)",Boolean.class,owner,task));}
  public CalendarEvent save(CalendarEvent event,long expected){
    CalendarJpaEntity entity;
    if(expected==0){entity=new CalendarJpaEntity(event);em.persist(entity);}
    else {entity=entity(event.ownerId(),event.id()).orElseThrow(CalendarService.NotFound::new);if(entity.revision!=expected)throw new CalendarService.Stale();entity.apply(event);}
    em.flush();return entity.domain();
  }
}
