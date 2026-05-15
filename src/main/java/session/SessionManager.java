package session;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Concurrent nickname/token indexes for authenticated sessions. */
public class SessionManager {
    private final ConcurrentHashMap<String, ClientSession> nicknameSessionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ClientSession> tokenSessionMap = new ConcurrentHashMap<>();

    public boolean register(ClientSession session) {
        ClientSession existing = nicknameSessionMap.putIfAbsent(session.getNickname(), session);
        if (existing != null) return false;
        tokenSessionMap.put(session.getToken(), session);
        return true;
    }
    public void remove(ClientSession session) {
        if (session == null) return;
        nicknameSessionMap.remove(session.getNickname(), session);
        tokenSessionMap.remove(session.getToken(), session);
    }
    public ClientSession getByToken(String token) { return token == null ? null : tokenSessionMap.get(token); }
    public ClientSession getByNickname(String nickname) { return nickname == null ? null : nicknameSessionMap.get(nickname); }
    public boolean containsNickname(String nickname) { return nicknameSessionMap.containsKey(nickname); }
    public int count() { return nicknameSessionMap.size(); }
    public List<String> nicknames() { List<String> list = new ArrayList<>(nicknameSessionMap.keySet()); Collections.sort(list); return list; }
    public Collection<ClientSession> sessions() { return new ArrayList<>(nicknameSessionMap.values()); }
}
