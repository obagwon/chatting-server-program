package room;

import log.ChatLogService;
import protocol.Message;
import session.ClientSession;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Owns room lifecycle and keeps ClientSession.currentRoomName in sync. */
public class RoomManager {
    private final ConcurrentHashMap<String, ChatRoom> rooms = new ConcurrentHashMap<>();
    private final ChatLogService logService;
    public RoomManager(ChatLogService logService) { this.logService = logService; }

    public synchronized boolean createRoomAndJoin(String roomName, ClientSession session) {
        if (rooms.containsKey(roomName) || session.getCurrentRoomName() != null) return false;
        ChatRoom room = new ChatRoom(roomName);
        room.addMember(session); rooms.put(roomName, room); session.setCurrentRoomName(roomName);
        logService.log("ROOM_CREATE", "room=" + roomName + " creator=" + session.getNickname());
        return true;
    }
    public synchronized boolean joinRoom(String roomName, ClientSession session) {
        if (roomName == null) return false;
        String normalizedRoomName = roomName.trim();
        if (normalizedRoomName.isEmpty()) return false;
        ChatRoom room = rooms.get(normalizedRoomName);
        if (room == null || session.getCurrentRoomName() != null) return false;
        room.addMember(session); session.setCurrentRoomName(normalizedRoomName);
        logService.log("ROOM_JOIN", "room=" + normalizedRoomName + " nickname=" + session.getNickname());
        room.broadcastExcept(Message.system(session.getNickname() + "님이 입장했습니다."), session.getNickname());
        return true;
    }
    public synchronized boolean leaveRoom(ClientSession session) {
        String roomName = session.getCurrentRoomName();
        if (roomName == null) return false;
        ChatRoom room = rooms.get(roomName);
        if (room != null) {
            room.removeMember(session.getNickname());
            session.setCurrentRoomName(null);
            logService.log("ROOM_LEAVE", "room=" + roomName + " nickname=" + session.getNickname());
            room.broadcast(Message.system(session.getNickname() + "님이 퇴장했습니다."));
            if (room.memberCount() == 0) { rooms.remove(roomName, room); logService.log("ROOM_DELETE", "room=" + roomName); }
        } else session.setCurrentRoomName(null);
        return true;
    }
    public ChatRoom getRoom(String roomName) { return rooms.get(roomName); }
    public List<RoomInfo> listRooms() {
        List<RoomInfo> list = new ArrayList<>();
        for (ChatRoom room : rooms.values()) list.add(new RoomInfo(room.getRoomName(), room.memberCount()));
        list.sort(Comparator.comparing(RoomInfo::getRoomName));
        return list;
    }
}
