package app.dayora.realtime;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
public class CookieHandshakeInterceptor implements HandshakeInterceptor {
 public boolean beforeHandshake(ServerHttpRequest r, org.springframework.http.server.ServerHttpResponse s, WebSocketHandler h, Map<String,Object> a){
  var p=r.getPrincipal(); if(p==null) return false; a.put("principal",p); return true;
 }
 public void afterHandshake(ServerHttpRequest r, org.springframework.http.server.ServerHttpResponse s, WebSocketHandler h, Exception e){}
}
