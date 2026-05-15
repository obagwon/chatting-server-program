package test;

import protocol.*;
import server.ServerConfig;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Simple load-test driver supporting four assignment scenarios. */
public class LoadTestClient {
    private static final JsonMessageConverter CONVERTER = new JsonMessageConverter();
    private static final String HOST = "127.0.0.1";

    public static void main(String[] args) throws Exception {
        String scenario = args.length == 0 ? "NORMAL_LOAD" : args[0];
        switch (scenario) {
            case "NORMAL_LOAD" -> normalLoad();
            case "RATE_LIMIT_TEST" -> rateLimitTest();
            case "SAME_IP_LIMIT_TEST" -> sameIpLimitTest();
            case "THREAD_POOL_SATURATION_TEST" -> threadPoolSaturationTest();
            default -> System.out.println("Usage: NORMAL_LOAD | RATE_LIMIT_TEST | SAME_IP_LIMIT_TEST | THREAD_POOL_SATURATION_TEST");
        }
    }

    private static void normalLoad() throws InterruptedException {
        long start = System.currentTimeMillis();
        int virtualClients = 30;
        AtomicInteger loginSuccess = new AtomicInteger();
        AtomicInteger joinSuccess = new AtomicInteger();
        AtomicInteger messagesSent = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(virtualClients);
        CountDownLatch latch = new CountDownLatch(virtualClients);
        for (int i = 0; i < virtualClients; i++) {
            int id = i;
            pool.submit(() -> {
                try (SimpleClient c = new SimpleClient()) {
                    if (!c.login("load" + id)) { failures.incrementAndGet(); return; }
                    loginSuccess.incrementAndGet();
                    Message room = c.authed(id == 0 ? MessageType.CREATE_ROOM : MessageType.JOIN_ROOM);
                    room.setRoomName("load-test-room");
                    Message r = c.sendAndRead(room);
                    if (r != null && (r.getType() == MessageType.CREATE_ROOM_SUCCESS || r.getType() == MessageType.JOIN_ROOM_SUCCESS)) joinSuccess.incrementAndGet();
                    for (int j = 0; j < 10; j++) { Message chat = c.authed(MessageType.CHAT); chat.setMessage("msg-" + j); c.send(chat); messagesSent.incrementAndGet(); }
                    c.send(c.authed(MessageType.LOGOUT));
                } catch (Exception e) { failures.incrementAndGet(); }
                finally { latch.countDown(); }
            });
        }
        latch.await(); pool.shutdownNow();
        System.out.printf("Virtual Clients: %d%nLogin Success: %d%nJoin Room Success: %d%nMessages Sent: %d%nFailures: %d%nElapsed Time: %d ms%n",
                virtualClients, loginSuccess.get(), joinSuccess.get(), messagesSent.get(), failures.get(), System.currentTimeMillis() - start);
    }

    private static void rateLimitTest() throws Exception {
        try (SimpleClient c = new SimpleClient()) {
            if (!c.login("ratelimit")) { System.out.println("login failed"); return; }
            int exceeded = 0;
            for (int i = 0; i < 30; i++) {
                Message m = c.authed(MessageType.USER_LIST);
                Message r = c.sendAndRead(m);
                if (r != null && r.getType() == MessageType.RATE_LIMIT_EXCEEDED) exceeded++;
            }
            System.out.println("RATE_LIMIT_EXCEEDED count: " + exceeded);
        }
    }

    private static void sameIpLimitTest() throws Exception {
        List<Socket> sockets = new ArrayList<>(); int rejected = 0;
        for (int i = 0; i < 5; i++) {
            Socket s = new Socket(HOST, ServerConfig.PORT); sockets.add(s);
            s.setSoTimeout(1000);
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String line = br.readLine();
                if (line != null && line.contains("CONNECTION_LIMIT_EXCEEDED")) rejected++;
            } catch (IOException ignored) { }
        }
        for (Socket s : sockets) try { s.close(); } catch (IOException ignored) { }
        System.out.println("Connections attempted: 5");
        System.out.println("Expected allowed up to: " + ServerConfig.MAX_CONNECTIONS_PER_IP);
        System.out.println("Rejected responses observed: " + rejected);
    }

    private static void threadPoolSaturationTest() throws InterruptedException {
        int attempts = 120;
        AtomicInteger busy = new AtomicInteger(); AtomicInteger connected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(60); CountDownLatch latch = new CountDownLatch(attempts);
        for (int i = 0; i < attempts; i++) pool.submit(() -> {
            try (Socket s = new Socket(HOST, ServerConfig.PORT)) {
                connected.incrementAndGet(); s.setSoTimeout(1500);
                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                String line = br.readLine(); if (line != null && line.contains("SERVER_BUSY")) busy.incrementAndGet();
                Thread.sleep(ServerConfig.LOGIN_TIMEOUT_MILLIS + 100L);
            } catch (Exception ignored) { } finally { latch.countDown(); }
        });
        latch.await(); pool.shutdownNow();
        System.out.println("Connection attempts: " + attempts);
        System.out.println("Connected sockets: " + connected.get());
        System.out.println("SERVER_BUSY observed: " + busy.get() + " (also check logs/chat-server.log for THREAD_POOL_REJECT)");
    }

    private static class SimpleClient implements Closeable {
        private final Socket socket = new Socket(HOST, ServerConfig.PORT);
        private final PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        private final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        private String token;
        SimpleClient() throws IOException { socket.setSoTimeout(1500); }
        boolean login(String nick) throws IOException { Message m = new Message(); m.setType(MessageType.LOGIN); m.setNickname(nick); Message r = sendAndRead(m); if (r != null && r.getType() == MessageType.LOGIN_SUCCESS) { token = r.getToken(); return true; } return false; }
        Message authed(MessageType type) { Message m = new Message(); m.setType(type); m.setToken(token); return m; }
        void send(Message m) { out.println(CONVERTER.toJson(m)); }
        Message sendAndRead(Message m) throws IOException { send(m); try { String line = in.readLine(); return line == null ? null : CONVERTER.fromJson(line); } catch (java.net.SocketTimeoutException e) { return null; } }
        @Override public void close() throws IOException { socket.close(); }
    }
}
