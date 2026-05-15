package security;

import java.util.UUID;

/** Issues unpredictable UUID based session tokens. */
public class TokenService {
    public String issueToken() { return UUID.randomUUID().toString(); }
}
