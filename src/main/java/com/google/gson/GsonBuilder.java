package com.google.gson;

/** Minimal GsonBuilder-compatible facade for this console assignment. */
public class GsonBuilder {
    public GsonBuilder disableHtmlEscaping() { return this; }
    public Gson create() { return new Gson(); }
}
