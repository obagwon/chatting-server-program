package com.google.gson;

/** Minimal local fallback used when external Maven artifacts are unavailable. */
public class JsonSyntaxException extends RuntimeException {
    public JsonSyntaxException(String message) { super(message); }
    public JsonSyntaxException(String message, Throwable cause) { super(message, cause); }
}
