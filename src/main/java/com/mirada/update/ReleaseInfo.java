package com.mirada.update;

/** A published release: its version without the leading {@code v}, and its page. */
public record ReleaseInfo(String version, String url) {}
