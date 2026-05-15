package security;

import server.ServerConfig;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Enforces per-IP simultaneous TCP connection limits with atomic counters. */
public class ConnectionLimitService {
    private final ConcurrentHashMap<String, AtomicInteger> ipConnectionCountMap = new ConcurrentHashMap<>();

    public boolean tryAcquire(String ip) {
        AtomicInteger counter = ipConnectionCountMap.computeIfAbsent(ip, k -> new AtomicInteger());
        while (true) {
            int current = counter.get();
            if (current >= ServerConfig.MAX_CONNECTIONS_PER_IP) return false;
            if (counter.compareAndSet(current, current + 1)) return true;
        }
    }

    public void release(String ip) {
        if (ip == null) return;
        ipConnectionCountMap.computeIfPresent(ip, (k, counter) -> counter.decrementAndGet() <= 0 ? null : counter);
    }

    public int getCount(String ip) {
        AtomicInteger c = ipConnectionCountMap.get(ip);
        return c == null ? 0 : c.get();
    }
}
