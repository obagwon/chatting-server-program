package com.google.gson;

import protocol.Message;
import protocol.MessageType;
import room.RoomInfo;
import java.util.*;

/**
 * Small Gson-compatible JSON codec fallback. It supports the project's Message DTO
 * shape so the assignment can compile/run in restricted environments.
 */
public class Gson {
    public String toJson(Object object) {
        if (object instanceof Message m) return messageToJson(m);
        throw new JsonSyntaxException("Unsupported serialization type: " + object.getClass());
    }

    @SuppressWarnings("unchecked")
    public <T> T fromJson(String json, Class<T> type) {
        if (type == Message.class) return (T) jsonToMessage(json);
        throw new JsonSyntaxException("Unsupported deserialization type: " + type);
    }

    private String messageToJson(Message m) {
        StringBuilder sb = new StringBuilder("{");
        add(sb, "type", m.getType() == null ? null : m.getType().name());
        add(sb, "success", m.getSuccess());
        add(sb, "message", m.getMessage());
        add(sb, "nickname", m.getNickname());
        add(sb, "token", m.getToken());
        add(sb, "roomName", m.getRoomName());
        add(sb, "sender", m.getSender());
        add(sb, "targetNickname", m.getTargetNickname());
        add(sb, "timestamp", m.getTimestamp());
        if (m.getRooms() != null) {
            comma(sb); sb.append("\"rooms\":[");
            boolean first = true;
            for (RoomInfo r : m.getRooms()) {
                if (!first) sb.append(','); first = false;
                sb.append('{'); add(sb, "roomName", r.getRoomName()); add(sb, "memberCount", r.getMemberCount()); trimComma(sb); sb.append('}');
            }
            sb.append(']');
        }
        if (m.getUsers() != null) {
            comma(sb); sb.append("\"users\":[");
            for (int i = 0; i < m.getUsers().size(); i++) { if (i > 0) sb.append(','); string(sb, m.getUsers().get(i)); }
            sb.append(']');
        }
        trimComma(sb); sb.append('}'); return sb.toString();
    }

    private Message jsonToMessage(String json) {
        try {
            Map<String, String> values = flatObject(json);
            Message m = new Message();
            String type = values.get("type"); if (type != null) m.setType(MessageType.valueOf(type));
            if (values.containsKey("success")) m.setSuccess(Boolean.parseBoolean(values.get("success")));
            m.setMessage(values.get("message")); m.setNickname(values.get("nickname")); m.setToken(values.get("token"));
            m.setRoomName(values.get("roomName")); m.setSender(values.get("sender"));
            m.setTargetNickname(values.get("targetNickname")); m.setTimestamp(values.get("timestamp"));
            if (values.containsKey("rooms")) m.setRooms(readRooms(values.get("rooms")));
            if (values.containsKey("users")) m.setUsers(readUsers(values.get("users")));
            return m;
        } catch (Exception e) { throw new JsonSyntaxException("Invalid JSON: " + json, e); }
    }

    private Map<String, String> flatObject(String json) {
        if (json == null) throw new JsonSyntaxException("null json");
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) throw new JsonSyntaxException("object expected");
        Map<String, String> map = new LinkedHashMap<>();
        int i = 1;
        while (i < json.length() - 1) {
            i = skip(json, i); if (i >= json.length() - 1) break;
            if (json.charAt(i) == ',') { i++; continue; }
            String key = readString(json, i); i = nextAfterString(json, i); i = skip(json, i);
            if (json.charAt(i++) != ':') throw new JsonSyntaxException("colon expected");
            i = skip(json, i);
            String value;
            if (json.charAt(i) == '"') { value = readString(json, i); i = nextAfterString(json, i); }
            else { int start = i; i = nextAfterValue(json, i); value = json.substring(start, i).trim(); }
            map.put(key, "null".equals(value) ? null : value);
        }
        return map;
    }

    private List<RoomInfo> readRooms(String json) {
        List<RoomInfo> rooms = new ArrayList<>();
        if (json == null || "null".equals(json)) return rooms;
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) throw new JsonSyntaxException("array expected");
        String body = json.substring(1, json.length() - 1).trim();
        if (body.isEmpty()) return rooms;
        for (String item : splitTopLevel(body)) {
            Map<String, String> room = flatObject(item);
            String roomName = room.get("roomName");
            String memberCount = room.get("memberCount");
            rooms.add(new RoomInfo(roomName, memberCount == null ? 0 : Integer.parseInt(memberCount)));
        }
        return rooms;
    }

    private List<String> readUsers(String json) {
        List<String> users = new ArrayList<>();
        if (json == null || "null".equals(json)) return users;
        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) throw new JsonSyntaxException("array expected");
        String body = json.substring(1, json.length() - 1).trim();
        if (body.isEmpty()) return users;
        for (String item : splitTopLevel(body)) {
            item = item.trim();
            users.add("null".equals(item) ? null : readString(item, 0));
        }
        return users;
    }

    private List<String> splitTopLevel(String json) {
        List<String> parts = new ArrayList<>();
        int start = 0, depth = 0;
        boolean inString = false, escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
            } else if (c == '"') inString = true;
            else if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(json.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(json.substring(start).trim());
        return parts;
    }

    private int nextAfterValue(String s, int i) {
        int depth = 0;
        boolean inString = false, escaped = false;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
            } else if (c == '"') inString = true;
            else if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') {
                if (depth == 0) return i;
                depth--;
            } else if (c == ',' && depth == 0) return i;
        }
        return i;
    }

    private int skip(String s, int i) { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; return i; }
    private String readString(String s, int i) {
        if (s.charAt(i) != '"') throw new JsonSyntaxException("string expected");
        StringBuilder out = new StringBuilder();
        for (i++; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') return out.toString();
            if (c == '\\') { char n = s.charAt(++i); out.append(switch (n) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; case '"' -> '"'; case '\\' -> '\\'; default -> n; }); }
            else out.append(c);
        }
        throw new JsonSyntaxException("unterminated string");
    }
    private int nextAfterString(String s, int i) { for (i++; i < s.length(); i++) { if (s.charAt(i) == '\\') i++; else if (s.charAt(i) == '"') return i + 1; } throw new JsonSyntaxException("unterminated string"); }
    private void add(StringBuilder sb, String key, Object value) { if (value == null) return; comma(sb); string(sb, key); sb.append(':'); if (value instanceof Number || value instanceof Boolean) sb.append(value); else string(sb, String.valueOf(value)); }
    private void comma(StringBuilder sb) { if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '{' && sb.charAt(sb.length() - 1) != '[' && sb.charAt(sb.length() - 1) != ',') sb.append(','); }
    private void trimComma(StringBuilder sb) { if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1); }
    private void string(StringBuilder sb, String s) { sb.append('"'); for (char c : s.toCharArray()) { if (c == '"' || c == '\\') sb.append('\\'); if (c == '\n') sb.append("\\n"); else if (c == '\r') sb.append("\\r"); else if (c == '\t') sb.append("\\t"); else sb.append(c); } sb.append('"'); }
}
