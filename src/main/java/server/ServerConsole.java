package server;

import java.util.Scanner;

/** Reads administrative commands from standard input. */
public class ServerConsole implements Runnable {
    private final ChatServer server;
    public ServerConsole(ChatServer server) { this.server = server; }
    @Override public void run() {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            if ("/stop".equalsIgnoreCase(scanner.nextLine().trim())) {
                server.shutdown(); break;
            }
        }
    }
}
