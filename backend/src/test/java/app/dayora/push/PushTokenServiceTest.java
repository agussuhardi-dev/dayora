package app.dayora.push;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PushTokenServiceTest {
  @Test void registerHashesTokenAndUsesUpsertForTransfer() throws Exception {
    var jdbc=mock(JdbcTemplate.class); var service=new PushTokenService(jdbc); var owner=UUID.randomUUID(); var token="fresh-device-token";
    service.register(owner,token,PushTokenService.Platform.WEB);
    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    verify(jdbc).update(contains("on conflict (token_key) do update"),eq(hash),eq(token),eq(owner),eq("WEB"));
    assertFalse(hash.contains(token));
  }
  @Test void unregisterScopesDeleteToAuthenticatedOwner() {
    var jdbc=mock(JdbcTemplate.class); var service=new PushTokenService(jdbc); var owner=UUID.randomUUID();
    service.unregister(owner,"token");
    verify(jdbc).update(contains("token_key=? and owner_id=?"),anyString(),eq(owner));
  }
}
