package app.dayora.push;

import app.dayora.realtime.AccountEvents;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.WebpushConfig;
import com.google.api.client.http.javanet.NetHttpTransport;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class FcmPushListener {
  private static final Logger log=LoggerFactory.getLogger(FcmPushListener.class);
  private record Token(String key,String value,String platform) {}
  private final JdbcTemplate jdbc;
  private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(256),r->{var t=new Thread(r,"dayora-fcm");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
  private final FirebaseMessaging messaging;
  private final PushTokenController.Config webConfig;
  private String reason="ready";
  public boolean enabled(){return messaging!=null;}
  public String reason(){return reason;}
  public PushTokenController.Config webConfig(){return webConfig;}
  public FcmPushListener(JdbcTemplate jdbc,@Value("${dayora.fcm.project-id:}") String project,@Value("${dayora.fcm.web.api-key:}") String apiKey,@Value("${dayora.fcm.web.auth-domain:}") String authDomain,@Value("${dayora.fcm.web.messaging-sender-id:}") String sender,@Value("${dayora.fcm.web.app-id:}") String appId,@Value("${dayora.fcm.web.vapid-key:}") String vapid) { this.jdbc=jdbc; webConfig=new PushTokenController.Config(apiKey,authDomain,project,sender,appId,vapid); messaging=initialize(project); }
  private FirebaseMessaging initialize(String project) {
    if(project.isBlank()){reason="project-id-missing";log.info("FCM disabled: FIREBASE_PROJECT_ID is not configured");return null;}
    try { var app=FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(GoogleCredentials.getApplicationDefault()).setProjectId(project).setHttpTransport(new NetHttpTransport()).build(),"dayora"); log.info("FCM enabled for project {}",project); return FirebaseMessaging.getInstance(app); }
    catch(Exception e){reason="credentials-or-initialization-failed";log.warn("FCM disabled: credentials or initialization failed ({})",e.getClass().getSimpleName());return null;}
  }
  @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
  public void changed(AccountEvents.Committed event) { if(messaging==null)return; try{executor.execute(()->send(event));}catch(RejectedExecutionException e){log.warn("FCM queue full; invalidation dropped for owner {}",event.owner());} }
  private void send(AccountEvents.Committed event) {
    List<Token> tokens=jdbc.query("select token_key,token,platform from push_device_token where owner_id=?",(rs,n)->new Token(rs.getString(1),rs.getString(2),rs.getString(3)),event.owner());
    String type=event.phase()!=null?"pomodoro_phase_changed":event.focus()?"focus_invalidated":"sync_invalidated";
    log.info("FCM dispatch: type={}, cursor={}, targets={}, adminSdk={}, java={}",type,event.cursor(),tokens.size(),FirebaseMessaging.class.getPackage().getImplementationVersion(),Runtime.version());
    for(var token:tokens)try{
      var builder=Message.builder().setToken(token.value()).putData("type",type).putData("cursor",Long.toString(event.cursor()));
      if(event.phase()!=null)builder.putData("sessionId",event.sessionId().toString()).putData("phase",event.phase()).putData("startedAt",event.startedAt().toString()).putData("durationSeconds",Long.toString(event.durationSeconds())).putData("completedFocusCount",Integer.toString(event.completedFocusCount()));
      if("WEB".equals(token.platform())) builder.setWebpushConfig(WebpushConfig.builder().putData("title","Dayora").putData("body","Your Dayora data changed. Open to refresh.").putData("tag","dayora-sync").putData("url","/").build());
      else builder.setAndroidConfig(AndroidConfig.builder().setPriority(event.focus()?AndroidConfig.Priority.HIGH:AndroidConfig.Priority.NORMAL).build());
      String messageId=messaging.send(builder.build());
      log.info("FCM accepted: type={}, platform={}, messageId={}",type,token.platform(),messageId);
    }catch(FirebaseMessagingException e){if(e.getMessagingErrorCode()==MessagingErrorCode.UNREGISTERED)jdbc.update("delete from push_device_token where token_key=? and owner_id=?",token.key(),event.owner());else log.warn("FCM send failed for owner {}: {}",event.owner(),e.getMessagingErrorCode());}
    catch(RuntimeException e){log.warn("FCM send failed for owner {}: {}",event.owner(),e.getClass().getSimpleName());}
  }
  @PreDestroy void stop(){executor.shutdown();}
}
