package session;

import protocol.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Represents one logged-in client and serializes writes to its socket. */
public class ClientSession {
    private final String nickname;
    private final String token;
    private final Socket socket;
    private final PrintWriter writer;
    private final String ipAddress;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile String currentRoomName;
    private final JsonMessageConverter converter = new JsonMessageConverter();

    public ClientSession(String nickname, String token, Socket socket, String ipAddress) throws IOException {
        this.nickname = nickname; this.token = token; this.socket = socket; this.ipAddress = ipAddress;
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }
    public synchronized boolean send(Message message) {
        if (closed.get() || socket.isClosed()) return false;
        writer.println(converter.toJson(message));
        return !writer.checkError();
    }
    public void closeSocket() {
        if (closed.compareAndSet(false, true)) {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
    public String getNickname() { return nickname; }
    public String getToken() { return token; }
    public Socket getSocket() { return socket; }
    public String getIpAddress() { return ipAddress; }
    public String getCurrentRoomName() { return currentRoomName; }
    public void setCurrentRoomName(String currentRoomName) { this.currentRoomName = currentRoomName; }
}
