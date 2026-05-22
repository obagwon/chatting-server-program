package room;

import protocol.Message;
import session.ClientSession;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/** One chat room; members are stored in a ConcurrentHashMap for safe broadcasting. */
public class ChatRoom {
    private final String roomName;
    private final ConcurrentHashMap<String, ClientSession> members = new ConcurrentHashMap<>();
    public ChatRoom(String roomName) { this.roomName = roomName; }
    public boolean addMember(ClientSession session) { return members.putIfAbsent(session.getNickname(), session) == null; }
    public void removeMember(String nickname) { members.remove(nickname); }
    public int memberCount() { return members.size(); }
    public Collection<ClientSession> members() { return members.values(); }
    public String getRoomName() { return roomName; }
    public void broadcast(Message message) {
        for (ClientSession member : members.values()) {
            try { member.send(message); } catch (RuntimeException ignored) { }
        }
    }
    public void broadcastExcept(Message message, String excludedNickname) {
        for (ClientSession member : members.values()) {
            if (member.getNickname().equals(excludedNickname)) continue;
            try { member.send(message); } catch (RuntimeException ignored) { }
        }
    }
}
