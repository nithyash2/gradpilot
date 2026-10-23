package com.gradpilot.jobs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns pasted emails and HTML bodies into the plain parts the parser reads. */
public final class EmailText {

    private static final Pattern HEADER = Pattern.compile(
            "^\\s*(from|subject|sent|date|to|cc)\\s*:\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern NAME_AND_ADDRESS = Pattern.compile(
            "^\\s*\"?([^\"<]*?)\"?\\s*<\\s*([^>\\s]+@[^>\\s]+)\\s*>\\s*$");

    private static final Pattern ADDRESS = Pattern.compile("[^\\s<>\"]+@[^\\s<>\"]+\\.[a-z]{2,}",
            Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> ENTITIES = Map.ofEntries(
            Map.entry("&nbsp;", " "), Map.entry("&amp;", "&"), Map.entry("&lt;", "<"), Map.entry("&gt;", ">"),
            Map.entry("&quot;", "\""), Map.entry("&#39;", "'"), Map.entry("&apos;", "'"),
            Map.entry("&rsquo;", "'"), Map.entry("&lsquo;", "'"), Map.entry("&ldquo;", "\""),
            Map.entry("&rdquo;", "\""), Map.entry("&ndash;", "–"), Map.entry("&mdash;", "—"),
            Map.entry("&middot;", "·"), Map.entry("&hellip;", "…"));

    private EmailText() {
    }

    /** A sender written as {@code Name <address>}, or just an address. */
    public record Sender(String name, String address) {
    }

    public static Sender sender(String value) {
        if (value == null || value.isBlank()) {
            return new Sender(null, null);
        }
        Matcher both = NAME_AND_ADDRESS.matcher(value);
        if (both.matches()) {
            String name = both.group(1).trim();
            return new Sender(name.isEmpty() ? null : name, both.group(2).trim());
        }
        Matcher address = ADDRESS.matcher(value);
        if (address.find()) {
            String name = (value.substring(0, address.start()) + value.substring(address.end()))
                    .replaceAll("[<>\"()\\[\\]]", "").trim();
            return new Sender(name.isEmpty() ? null : name, address.group());
        }
        return new Sender(value.trim(), null);
    }

    /**
     * Reads an email someone copied from their mail app. Header lines such as
     * "From:" and "Subject:" are used when present and removed from the text.
     */
    public static JobEmail pasted(String from, String subject, String text, Instant receivedAt) {
        String body = text == null ? "" : text.replace("\r\n", "\n");
        String headerFrom = null;
        String headerSubject = null;

        Matcher m = HEADER.matcher(body);
        StringBuilder remaining = new StringBuilder();
        int last = 0;
        int lineLimit = headerZoneEnd(body);
        while (m.find() && m.start() <= lineLimit) {
            String key = m.group(1).toLowerCase();
            String value = m.group(2).trim();
            if (key.equals("from") && headerFrom == null) {
                headerFrom = value;
            } else if (key.equals("subject") && headerSubject == null) {
                headerSubject = value;
            }
            remaining.append(body, last, m.start());
            last = m.end();
        }
        remaining.append(body.substring(last));

        Sender sender = sender(isBlank(from) ? headerFrom : from);
        String finalSubject = isBlank(subject) ? headerSubject : subject;
        String finalText = remaining.toString().trim();
        if (isBlank(finalSubject)) {
            // No subject given: treat the first line as one, as mail apps do when copying
            int newline = finalText.indexOf('\n');
            finalSubject = newline > 0 ? finalText.substring(0, newline).trim() : "";
        }
        String id = "pasted:" + fingerprint(sender.address() + "\n" + finalSubject + "\n" + finalText);
        return new JobEmail(id, sender.name(), sender.address(), finalSubject, finalText, receivedAt);
    }

    /** Plain text from an HTML email body. */
    public static String fromHtml(String html) {
        if (html == null) {
            return "";
        }
        String text = html
                .replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|li|h[1-6]|table|section)>", "\n")
                .replaceAll("(?i)</?(b|i|u|em|strong|span|a|font|small)\\b[^>]*>", "")
                .replaceAll("(?s)<[^>]+>", " ");
        for (Map.Entry<String, String> entity : ENTITIES.entrySet()) {
            text = text.replace(entity.getKey(), entity.getValue());
        }
        text = decodeNumericEntities(text);
        return text.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" *\\n[ \\n]*", "\n").trim();
    }

    /** A short, stable id for some text, so the same email is never imported twice. */
    public static String fingerprint(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /** Header lines only count near the top of the pasted text. */
    private static int headerZoneEnd(String body) {
        int index = 0;
        for (int line = 0; line < 12 && index >= 0; line++) {
            int next = body.indexOf('\n', index);
            if (next < 0) {
                return body.length();
            }
            index = next + 1;
        }
        return index;
    }

    private static String decodeNumericEntities(String text) {
        Matcher m = Pattern.compile("&#(x?)([0-9a-fA-F]{1,6});").matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int code;
            try {
                code = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            } catch (NumberFormatException e) {
                code = ' ';
            }
            String replacement = Character.isValidCodePoint(code) ? new String(Character.toChars(code)) : " ";
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
