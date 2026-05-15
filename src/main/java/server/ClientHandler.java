package server;

import com.google.gson.JsonSyntaxException;
import log.ChatLogService;
import protocol.*;
import room.ChatRoom;
import room.RoomManager;
import security.*;
import session.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Per-client request loop. All exit paths call cleanup exactly once. */
public class ClientHandler implements Runnable {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Socket socket;
    private final String ip;
    private final SessionManager sessions;
    private final RoomManager rooms;
    private final ConnectionLimitService connectionLimits;
    private final RateLimitService rateLimits;
    private final TokenService tokens;
    private final ChatLogService log;
    private final JsonMessageConverter converter = new JsonMessageConverter();
    private BufferedReader reader;
    private PrintWriter preLoginWriter;
    private volatile ClientSession session;
    private volatile boolean cleanupDone;

    public ClientHandler(Socket socket, String ip, SessionManager sessions, RoomManager rooms,
                         ConnectionLimitService connectionLimits, RateLimitService rateLimits,
                         TokenService tokens, ChatLogService log) {
        this.socket = socket; this.ip = ip; this.sessions = sessions; this.rooms = rooms;
        this.connectionLimits = connectionLimits; this.rateLimits = rateLimits; this.tokens = tokens; this.log = log;
    }

    @Override public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            preLoginWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            socket.setSoTimeout(ServerConfig.LOGIN_TIMEOUT_MILLIS);
            if (!handleLogin()) return;
            socket.setSoTimeout(ServerConfig.IDLE_TIMEOUT_MILLIS);
            String line;
            while ((line = reader.readLine()) != null) handleRequest(parse(line));
            cleanup("DISCONNECT", "eof=true");
        } catch (SocketTimeoutException e) {
            if (session == null) { sendPreLogin(Message.of(MessageType.LOGIN_TIMEOUT, false, "로그인 제한 시간이 초과되어 연결을 종료합니다.")); cleanup("LOGIN_TIMEOUT", "ip=" + ip); }
            else { safeSend(Message.of(MessageType.IDLE_TIMEOUT, false, "장시간 활동이 없어 연결이 종료됩니다.")); cleanup("IDLE_TIMEOUT", "nickname=" + session.getNickname()); }
        } catch (IOException e) {
            cleanup("DISCONNECT", "io=" + e.getMessage());
        } catch (RuntimeException e) {
            cleanup("DISCONNECT", "runtime=" + e.getMessage());
        }
    }

    private boolean handleLogin() throws IOException {
        String line = reader.readLine();
        if (line == null) { cleanup("DISCONNECT", "beforeLogin=true"); return false; }
        Message request;
        try { request = parse(line); } catch (JsonSyntaxException e) { sendPreLogin(Message.of(MessageType.ERROR, false, "JSON 파싱 실패")); cleanup("DISCONNECT", "malformedLogin=true"); return false; }
        if (request.getType() != MessageType.LOGIN) { sendPreLogin(Message.of(MessageType.LOGIN_FAIL, false, "첫 요청은 LOGIN이어야 합니다.")); cleanup("LOGIN_FAIL", "ip=" + ip); return false; }
        String nickname = request.getNickname();
        if (!isValidNickname(nickname)) { sendPreLogin(Message.of(MessageType.LOGIN_FAIL, false, "닉네임은 2~12자이며 공백을 포함할 수 없습니다.")); log.log("LOGIN_FAIL", "nickname=" + nickname); cleanup("DISCONNECT", "loginFail=true"); return false; }
        if (sessions.count() >= ServerConfig.MAX_LOGGED_IN_CLIENTS) { sendPreLogin(Message.of(MessageType.SERVER_FULL, false, "현재 로그인 가능한 최대 인원에 도달했습니다.")); log.log("SERVER_FULL", "nickname=" + nickname + " currentSessions=" + sessions.count()); cleanup("DISCONNECT", "serverFull=true"); return false; }
        if (sessions.containsNickname(nickname)) { sendPreLogin(Message.of(MessageType.LOGIN_FAIL, false, "이미 사용 중인 닉네임입니다.")); log.log("LOGIN_FAIL", "duplicate=" + nickname); cleanup("DISCONNECT", "duplicateNickname=true"); return false; }
        String token = tokens.issueToken();
        ClientSession created = new ClientSession(nickname, token, socket, ip);
        if (!sessions.register(created)) { sendPreLogin(Message.of(MessageType.LOGIN_FAIL, false, "이미 사용 중인 닉네임입니다.")); cleanup("DISCONNECT", "registerRace=true"); return false; }
        session = created;
        Message response = Message.of(MessageType.LOGIN_SUCCESS, true, "로그인 성공");
        response.setNickname(nickname); response.setToken(token); safeSend(response);
        log.log("LOGIN_SUCCESS", "nickname=" + nickname + " ip=" + ip);
        return true;
    }

    private Message parse(String line) { return converter.fromJson(line); }

    private void handleRequest(Message request) {
        if (request == null || request.getType() == null) { safeSend(Message.of(MessageType.ERROR, false, "메시지 타입이 필요합니다.")); return; }
        ClientSession auth = sessions.getByToken(request.getToken());
        if (auth == null || auth != session) { safeSend(Message.of(MessageType.AUTH_FAIL, false, "유효하지 않은 인증 토큰입니다.")); return; }
        if (!rateLimits.allowRequest(session.getToken())) {
            safeSend(Message.of(MessageType.RATE_LIMIT_EXCEEDED, false, "요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요."));
            log.log("RATE_LIMIT_EXCEEDED", "nickname=" + session.getNickname()); return;
        }
        try {
            switch (request.getType()) {
                case CREATE_ROOM -> createRoom(request);
                case ROOM_LIST -> roomList();
                case JOIN_ROOM -> joinRoom(request);
                case LEAVE_ROOM -> leaveRoom();
                case CHAT -> chat(request);
                case WHISPER -> whisper(request);
                case USER_LIST -> userList();
                case LOGOUT -> { safeSend(Message.of(MessageType.LOGOUT, true, "로그아웃합니다.")); cleanup("LOGOUT", "nickname=" + session.getNickname()); }
                default -> safeSend(Message.of(MessageType.ERROR, false, "지원하지 않는 요청입니다."));
            }
        } catch (JsonSyntaxException e) { safeSend(Message.of(MessageType.ERROR, false, "JSON 파싱 실패")); }
    }

    private void createRoom(Message request) {
        String name = request.getRoomName();
        if (name == null || name.trim().isEmpty() || name.length() > 20) { safeSend(Message.of(MessageType.CREATE_ROOM_FAIL, false, "방 이름은 1~20자여야 합니다.")); return; }
        boolean ok = rooms.createRoomAndJoin(name.trim(), session);
        safeSend(Message.of(ok ? MessageType.CREATE_ROOM_SUCCESS : MessageType.CREATE_ROOM_FAIL, ok, ok ? "채팅방 생성 및 입장 성공" : "방 생성 실패(중복 이름 또는 이미 입장 중)"));
    }
    private void roomList() { Message m = Message.of(MessageType.ROOM_LIST_RESULT, true, "방 목록"); m.setRooms(rooms.listRooms()); safeSend(m); }
    private void joinRoom(Message request) {
        boolean ok = rooms.joinRoom(request.getRoomName(), session);
        safeSend(Message.of(ok ? MessageType.JOIN_ROOM_SUCCESS : MessageType.JOIN_ROOM_FAIL, ok, ok ? "채팅방 입장 성공" : "방 입장 실패"));
    }
    private void leaveRoom() { boolean ok = rooms.leaveRoom(session); safeSend(Message.of(ok ? MessageType.LEAVE_ROOM_SUCCESS : MessageType.LEAVE_ROOM_FAIL, ok, ok ? "채팅방 퇴장 성공" : "입장한 방이 없습니다.")); }
    private void chat(Message request) {
        if (session.getCurrentRoomName() == null) { safeSend(Message.of(MessageType.ERROR, false, "먼저 방에 입장해야 합니다.")); return; }
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) { safeSend(Message.of(MessageType.ERROR, false, "빈 메시지는 보낼 수 없습니다.")); return; }
        ChatRoom room = rooms.getRoom(session.getCurrentRoomName());
        if (room == null) { session.setCurrentRoomName(null); safeSend(Message.of(MessageType.ERROR, false, "방을 찾을 수 없습니다.")); return; }
        Message out = Message.of(MessageType.CHAT_BROADCAST, true, request.getMessage());
        out.setRoomName(room.getRoomName()); out.setSender(session.getNickname()); out.setTimestamp(LocalDateTime.now().format(TS));
        room.broadcast(out);
    }
    private void whisper(Message request) {
        String msg = request.getMessage();
        ClientSession target = sessions.getByNickname(request.getTargetNickname());
        if (target == null || target == session || msg == null || msg.trim().isEmpty()) { safeSend(Message.of(MessageType.WHISPER_FAIL, false, "귓속말 대상 또는 메시지가 올바르지 않습니다.")); return; }
        Message sent = Message.of(MessageType.WHISPER_SENT, true, msg); sent.setTargetNickname(target.getNickname()); sent.setSender(session.getNickname());
        Message recv = Message.of(MessageType.WHISPER_RECEIVED, true, msg); recv.setSender(session.getNickname());
        safeSend(sent); target.send(recv);
    }
    private void userList() { Message m = Message.of(MessageType.USER_LIST_RESULT, true, "접속자 목록"); m.setUsers(sessions.nicknames()); safeSend(m); }

    private boolean isValidNickname(String n) { return n != null && n.length() >= 2 && n.length() <= 12 && !n.matches(".*\\s+.*"); }
    private void safeSend(Message m) { if (session != null) session.send(m); else sendPreLogin(m); }
    private void sendPreLogin(Message m) { if (preLoginWriter != null) preLoginWriter.println(converter.toJson(m)); }

    private synchronized void cleanup(String event, String detail) {
        if (cleanupDone) return; cleanupDone = true;
        ClientSession current = session;
        if (current != null) { rooms.leaveRoom(current); sessions.remove(current); rateLimits.remove(current.getToken()); current.closeSocket(); }
        else { try { socket.close(); } catch (IOException ignored) { } }
        connectionLimits.release(ip);
        log.log(event, detail);
    }
}
