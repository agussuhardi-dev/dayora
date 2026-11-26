package app.dayora.realtime;

import java.util.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class RealtimeConfiguration implements WebSocketConfigurer {
  private final CookieHandshakeInterceptor auth = new CookieHandshakeInterceptor();
  private final RealtimeHandler handler;
  public RealtimeConfiguration(RealtimeHandler handler){this.handler=handler;}
  @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry r) {
    r.addHandler(handler, "/ws/events").addInterceptors(auth).setAllowedOrigins(origins());
  }
  private String[] origins() { return System.getProperty("dayora.ws.origins", System.getenv().getOrDefault("DAYORA_WS_ORIGINS", "http://localhost:4200")).split(","); }
}
