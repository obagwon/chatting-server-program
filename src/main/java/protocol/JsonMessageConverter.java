package protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/** Gson based converter. One JSON object is sent per TCP line. */
public class JsonMessageConverter {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    public String toJson(Message message) { return GSON.toJson(message); }
    public Message fromJson(String json) throws JsonSyntaxException { return GSON.fromJson(json, Message.class); }
}
