package server;

import log.ChatLogService;
import protocol.*;
import room.RoomManager;
import security.*;
import session.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** TCP socket chat server with bounded ThreadPoolExecutor and safe shutdown. */
public class ChatServer {
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final JsonMessageConverter converter = new JsonMessageConverter();
    private final SessionManager sessionManager = new SessionManager();
    private final ConnectionLimitService connectionLimitService = new ConnectionLimitService();
    private final RateLimitService rateLimitService = new RateLimitService();
    private final TokenService tokenService = new TokenService();
    private final ChatLogService logService;
    private final RoomManager roomManager;
    private final ThreadPoolExecutor clientExecutor;
    private ServerSocket serverSocket;

    public ChatServer() throws IOException {
        this.logService = new ChatLogService(ServerConfig.LOG_FILE);
        this.roomManager = new RoomManager(logService);
        this.clientExecutor = new ThreadPoolExecutor(
                ServerConfig.CORE_POOL_SIZE,
                ServerConfig.MAX_POOL_SIZE,
                ServerConfig.THREAD_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(ServerConfig.TASK_QUEUE_CAPACITY),
                new ThreadPoolExecutor.AbortPolicy());
    }

    public static void main(String[] args) throws Exception { new ChatServer().start(); }

    public void start() throws IOException {
        running.set(true);
        serverSocket = new ServerSocket(ServerConfig.PORT);
        logService.log("SERVER_START", "port=" + ServerConfig.PORT);
        new Thread(new ServerConsole(this), "server-console").start();
        System.out.println("ChatServer started on port " + ServerConfig.PORT + " (/stop to shutdown)");
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                handleAcceptedSocket(socket);
            } catch (SocketException e) {
                if (running.get()) logService.log("ERROR", "accept=" + e.getMessage());
            }
        }
    }

    private void handleAcceptedSocket(Socket socket) throws IOException {
        String ip = socket.getInetAddress().getHostAddress();
        if (!connectionLimitService.tryAcquire(ip)) {
            sendOneShot(socket, Message.of(MessageType.CONNECTION_LIMIT_EXCEEDED, false, "동일 IP에서 허용된 최대 연결 수를 초과했습니다."));
            socket.close(); logService.log("IP_CONNECTION_REJECT", "ip=" + ip); return;
        }
        logService.log("CONNECT", "ip=" + ip + " count=" + connectionLimitService.getCount(ip));
        try {
            clientExecutor.execute(new ClientHandler(socket, ip, sessionManager, roomManager,
                    connectionLimitService, rateLimitService, tokenService, logService));
        } catch (RejectedExecutionException e) {
            sendOneShot(socket, Message.of(MessageType.SERVER_BUSY, false, "서버가 혼잡하여 현재 접속을 처리할 수 없습니다."));
            socket.close(); connectionLimitService.release(ip);
            logService.log("THREAD_POOL_REJECT", "ip=" + ip);
        }
    }

    private void sendOneShot(Socket socket, Message message) {
        try {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            writer.println(converter.toJson(message));
        } catch (IOException ignored) { }
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) return;
        logService.log("SERVER_SHUTDOWN", "requested=true");
        Message shutdown = Message.of(MessageType.SERVER_SHUTDOWN, false, "서버가 종료됩니다. 연결이 해제됩니다.");
        for (ClientSession session : sessionManager.sessions()) {
            session.send(shutdown); session.closeSocket();
        }
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { }
        clientExecutor.shutdown();
        try {
            if (!clientExecutor.awaitTermination(5, TimeUnit.SECONDS)) clientExecutor.shutdownNow();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); clientExecutor.shutdownNow(); }
        try { logService.close(); } catch (IOException ignored) { }
    }
}
