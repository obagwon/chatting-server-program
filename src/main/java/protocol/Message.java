package protocol;

import room.RoomInfo;
import java.util.List;

/** Flexible DTO for every request/response in the line-delimited JSON protocol. */
public class Message {
    private MessageType type;
    private Boolean success;
    private String message;
    private String nickname;
    private String token;
    private String roomName;
    private String sender;
    private String targetNickname;
    private String timestamp;
    private List<RoomInfo> rooms;
    private List<String> users;

    public static Message of(MessageType type, boolean success, String message) {
        Message m = new Message(); m.type = type; m.success = success; m.message = message; return m;
    }
    public static Message system(String text) { return of(MessageType.SYSTEM_MESSAGE, true, text); }
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getTargetNickname() { return targetNickname; }
    public void setTargetNickname(String targetNickname) { this.targetNickname = targetNickname; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public List<RoomInfo> getRooms() { return rooms; }
    public void setRooms(List<RoomInfo> rooms) { this.rooms = rooms; }
    public List<String> getUsers() { return users; }
    public void setUsers(List<String> users) { this.users = users; }
}
