package app.dayora.push;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushTokenService {
  public enum Platform { ANDROID, WEB }
  private final JdbcTemplate jdbc;
  public PushTokenService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
  private String key(String token) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }
  @Transactional public void register(UUID owner, String token, Platform platform) {
    jdbc.update("insert into push_device_token(token_key,token,owner_id,platform) values (?,?,?,?) on conflict (token_key) do update set token=excluded.token, owner_id=excluded.owner_id, platform=excluded.platform", key(token), token, owner, platform.name());
  }
  public long count(UUID owner) { return jdbc.queryForObject("select count(*) from push_device_token where owner_id=?", Long.class, owner); }
  @Transactional public void unregister(UUID owner, String token) { jdbc.update("delete from push_device_token where token_key=? and owner_id=?", key(token), owner); }
}
