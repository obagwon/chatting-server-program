package client;

import protocol.*;
import room.RoomInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Background reader that prints server pushed broadcasts and shutdown notices. */
public class ServerMessageListener implements Runnable {
    private final BufferedReader reader;
    private final AtomicBoolean inRoom;
    private final JsonMessageConverter converter = new JsonMessageConverter();
    private volatile boolean running = true;

    public ServerMessageListener(BufferedReader reader, AtomicBoolean inRoom) {
        this.reader = reader;
        this.inRoom = inRoom;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                Message m = converter.fromJson(line);
                applyState(m);
                print(m);
                if (m.getType() == MessageType.SERVER_SHUTDOWN) break;
            }
        } catch (IOException ignored) {
        }
    }

    private void applyState(Message m) {
        if (m == null || m.getType() == null) return;
        switch (m.getType()) {
            case CREATE_ROOM_SUCCESS, JOIN_ROOM_SUCCESS -> inRoom.set(true);
            case LEAVE_ROOM_SUCCESS, LOGOUT, IDLE_TIMEOUT, SERVER_SHUTDOWN -> inRoom.set(false);
            default -> { }
        }
    }

    private void print(Message m) {
        if (m == null) return;
        switch (m.getType()) {
            case CHAT_BROADCAST ->
                    System.out.printf("[%s][%s] %s: %s%n", m.getTimestamp(), m.getRoomName(), m.getSender(), m.getMessage());
            case WHISPER_RECEIVED ->
                    System.out.printf("[귓속말 from %s] %s%n", m.getSender(), m.getMessage());
            case ROOM_LIST_RESULT -> printRooms(m.getRooms());
            case USER_LIST_RESULT -> printUsers(m.getUsers());
            default ->
                    System.out.println("[SERVER] " + m.getType() + " - " + (m.getMessage() == null ? "" : m.getMessage()));
        }
    }

    private void printRooms(List<RoomInfo> rooms) {
        if (rooms == null || rooms.isEmpty()) {
            System.out.println("[방 목록] 방이 없습니다.");
            return;
        }
        System.out.println("[방 목록]");
        for (RoomInfo room : rooms) {
            System.out.printf("- %s (%d명)%n", room.getRoomName(), room.getMemberCount());
        }
    }

    private void printUsers(List<String> users) {
        if (users == null || users.isEmpty()) {
            System.out.println("[접속자 목록] 접속자가 없습니다.");
            return;
        }
        System.out.println("[접속자 목록] " + String.join(", ", users));
    }
}
