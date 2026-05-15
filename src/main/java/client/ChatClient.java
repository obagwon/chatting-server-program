package client;

import protocol.*;
import server.ServerConfig;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** Interactive console client for the chat JSON protocol. */
public class ChatClient {
    private final JsonMessageConverter converter = new JsonMessageConverter();
    private PrintWriter writer;
    private BufferedReader reader;
    private String token;
    private boolean inRoom;

    public static void main(String[] args) throws Exception { new ChatClient().start(args.length > 0 ? args[0] : "127.0.0.1"); }

    public void start(String host) throws IOException {
        try (Socket socket = new Socket(host, ServerConfig.PORT); Scanner scanner = new Scanner(System.in)) {
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            System.out.print("닉네임: ");
            Message login = new Message(); login.setType(MessageType.LOGIN); login.setNickname(scanner.nextLine().trim());
            send(login);
            Message response = converter.fromJson(reader.readLine());
            System.out.println(response.getMessage());
            if (response.getType() != MessageType.LOGIN_SUCCESS) return;
            token = response.getToken(); printHelp();
            ServerMessageListener listener = new ServerMessageListener(reader);
            new Thread(listener, "server-message-listener").start();
            while (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                if (input.isBlank()) continue;
                if (input.startsWith("/")) {
                    if (!handleCommand(input)) break;
                } else {
                    if (!inRoom) { System.out.println("먼저 /join 또는 /create로 방에 입장하세요."); continue; }
                    Message chat = authed(MessageType.CHAT); chat.setMessage(input); send(chat);
                }
            }
            listener.stop();
        }
    }

    private boolean handleCommand(String input) {
        String[] p = input.split(" ", 3);
        switch (p[0]) {
            case "/help" -> printHelp();
            case "/rooms" -> send(authed(MessageType.ROOM_LIST));
            case "/create" -> { if (p.length < 2) usage("/create [방이름]"); else { Message m = authed(MessageType.CREATE_ROOM); m.setRoomName(p[1]); send(m); inRoom = true; } }
            case "/join" -> { if (p.length < 2) usage("/join [방이름]"); else { Message m = authed(MessageType.JOIN_ROOM); m.setRoomName(p[1]); send(m); inRoom = true; } }
            case "/leave" -> { send(authed(MessageType.LEAVE_ROOM)); inRoom = false; }
            case "/users" -> send(authed(MessageType.USER_LIST));
            case "/w" -> { if (p.length < 3) usage("/w [닉네임] [메시지]"); else { Message m = authed(MessageType.WHISPER); m.setTargetNickname(p[1]); m.setMessage(p[2]); send(m); } }
            case "/quit" -> { send(authed(MessageType.LOGOUT)); return false; }
            default -> System.out.println("알 수 없는 명령입니다. /help");
        }
        return true;
    }
    private Message authed(MessageType type) { Message m = new Message(); m.setType(type); m.setToken(token); return m; }
    private void send(Message m) { writer.println(converter.toJson(m)); }
    private void usage(String u) { System.out.println("사용법: " + u); }
    private void printHelp() { System.out.println("명령어: /help /rooms /create [방이름] /join [방이름] /leave /users /w [닉네임] [메시지] /quit"); }
}
