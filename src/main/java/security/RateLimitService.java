package security;

import server.ServerConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Sliding-window per-token rate limiter. */
public class RateLimitService {
    private final ConcurrentHashMap<String, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();

    public boolean allowRequest(String token) {
        long now = System.currentTimeMillis();
        Deque<Long> deque = requestTimestamps.computeIfAbsent(token, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > ServerConfig.RATE_LIMIT_WINDOW_MILLIS) {
                deque.removeFirst();
            }
            if (deque.size() >= ServerConfig.RATE_LIMIT_MAX_REQUESTS) return false;
            deque.addLast(now);
            return true;
        }
    }
    public void remove(String token) { if (token != null) requestTimestamps.remove(token); }
}
