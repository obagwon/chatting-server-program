package server;

/** Central place for server resource-control and protocol constants. */
public final class ServerConfig {
    public static final int PORT = 5000;
    public static final int MAX_LOGGED_IN_CLIENTS = 50;
    public static final int MAX_CONNECTIONS_PER_IP = 3;
    public static final int CORE_POOL_SIZE = 10;
    public static final int MAX_POOL_SIZE = 20;
    public static final int TASK_QUEUE_CAPACITY = 50;
    public static final int LOGIN_TIMEOUT_MILLIS = 30_000;
    public static final int IDLE_TIMEOUT_MILLIS = 300_000;
    public static final int RATE_LIMIT_WINDOW_MILLIS = 10_000;
    public static final int RATE_LIMIT_MAX_REQUESTS = 20;
    public static final int THREAD_KEEP_ALIVE_SECONDS = 60;
    public static final String LOG_FILE = "logs/chat-server.log";
    private ServerConfig() {}
}
