package app.dayora.realtime;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeHandlerTest {
  @Test void invalidatesOnlyOwnerAfterCommit() throws Exception {
    var handler=new RealtimeHandler();
    UUID owner=UUID.randomUUID();
    var own=session(owner);
    var other=session(UUID.randomUUID());
    handler.afterConnectionEstablished(own);
    handler.afterConnectionEstablished(other);
    handler.committed(new AccountEvents.Committed(owner,42));
    verify(own).sendMessage(new TextMessage("{\"type\":\"INVALIDATE\",\"cursor\":42}"));
    verify(other,never()).sendMessage(any());
    assertEquals(TransactionPhase.AFTER_COMMIT,RealtimeHandler.class.getMethod("committed",AccountEvents.Committed.class).getAnnotation(TransactionalEventListener.class).phase());
  }
  private WebSocketSession session(UUID owner) {
    var session=mock(WebSocketSession.class);
    when(session.getId()).thenReturn(UUID.randomUUID().toString());
    when(session.getPrincipal()).thenReturn(()->owner.toString());
    when(session.isOpen()).thenReturn(true);
    return session;
  }
}
