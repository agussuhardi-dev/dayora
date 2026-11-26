package app.dayora.realtime;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

@Component
public class RealtimeHandler extends TextWebSocketHandler {
  private final Map<String,WebSocketSession> sessions=new ConcurrentHashMap<>();
  @Override public synchronized void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String owner=session.getPrincipal().getName();
    if(sessions.size()>=1000 || sessions.values().stream().filter(s->s.getPrincipal().getName().equals(owner)).count()>=5){session.close(CloseStatus.POLICY_VIOLATION);return;}
    session.setTextMessageSizeLimit(4096);
    sessions.put(session.getId(),new ConcurrentWebSocketSessionDecorator(session,1000,8192));
  }
  @Override protected void handleTextMessage(WebSocketSession s,TextMessage m) throws Exception {s.close(CloseStatus.POLICY_VIOLATION);}
  @Override public void afterConnectionClosed(WebSocketSession s,CloseStatus c){sessions.remove(s.getId());}
  @Override public void handleTransportError(WebSocketSession s,Throwable e) throws Exception {sessions.remove(s.getId());s.close(CloseStatus.SERVER_ERROR);}
  @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
  public void committed(AccountEvents.Committed event){
    var message=new TextMessage("{\"type\":\"INVALIDATE\",\"cursor\":"+event.cursor()+"}");
    sessions.values().stream().filter(s->s.getPrincipal().getName().equals(event.owner().toString())).forEach(s->{
      try{s.sendMessage(message);}catch(Exception e){sessions.remove(s.getId());try{s.close(CloseStatus.SERVER_ERROR);}catch(IOException ignored){}}
    });
  }
}
