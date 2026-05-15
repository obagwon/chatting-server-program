package client;

import protocol.*;
import java.io.BufferedReader;
import java.io.IOException;

/** Background reader that prints server pushed broadcasts and shutdown notices. */
public class ServerMessageListener implements Runnable {
    private final BufferedReader reader;
    private final JsonMessageConverter converter = new JsonMessageConverter();
    private volatile boolean running = true;
    public ServerMessageListener(BufferedReader reader) { this.reader = reader; }
    public void stop() { running = false; }
    @Override public void run() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                Message m = converter.fromJson(line);
                print(m);
                if (m.getType() == MessageType.SERVER_SHUTDOWN) break;
            }
        } catch (IOException ignored) { }
    }
    private void print(Message m) {
        if (m == null) return;
        switch (m.getType()) {
            case CHAT_BROADCAST -> System.out.printf("[%s][%s] %s: %s%n", m.getTimestamp(), m.getRoomName(), m.getSender(), m.getMessage());
            case WHISPER_RECEIVED -> System.out.printf("[귓속말 from %s] %s%n", m.getSender(), m.getMessage());
            default -> System.out.println("[SERVER] " + m.getType() + " - " + (m.getMessage() == null ? "" : m.getMessage()));
        }
    }
}
