package log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Thread-safe append-only server event logger. */
public class ChatLogService implements Closeable {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final BufferedWriter writer;

    public ChatLogService(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public synchronized void log(String event, String detail) {
        try {
            writer.write(String.format("[%s] %s%s%n", LocalDateTime.now().format(FORMATTER), event,
                    detail == null || detail.isBlank() ? "" : " " + detail));
            writer.flush();
        } catch (IOException e) {
            System.err.println("로그 기록 실패: " + e.getMessage());
        }
    }
    public void log(String event) { log(event, ""); }
    @Override public synchronized void close() throws IOException { writer.close(); }
}
