package room;

/** Lightweight room list DTO exposed in ROOM_LIST_RESULT. */
public class RoomInfo {
    private String roomName;
    private int memberCount;
    public RoomInfo(String roomName, int memberCount) { this.roomName = roomName; this.memberCount = memberCount; }
    public String getRoomName() { return roomName; }
    public int getMemberCount() { return memberCount; }
}
