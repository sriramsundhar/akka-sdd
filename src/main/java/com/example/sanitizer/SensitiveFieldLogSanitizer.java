package com.example.sanitizer;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

import akka.javasdk.LogSanitizer;
import akka.javasdk.SanitizerContext;

/**
 * Masks the values of configured sensitive fields in structured log lines, keeping the field
 * names visible: {@code "ssn":"123-45-6789"} becomes {@code "ssn":"***********"} and {@code
 * password=hunter2} becomes {@code password=*******}. Field names come from the {@code fields}
 * list of the sanitizer's config entry and match case-insensitively, on whole names only.
 *
 * <p>The runtime calls this for every log line of the service, including its own, so it must stay
 * fast, never log and never throw. Patterns are compiled once, and a line without a configured
 * field is returned as the same instance.
 */
public final class SensitiveFieldLogSanitizer implements LogSanitizer {

  // Matches up to and including the opening quote of a JSON string value: "name" : "
  private final Pattern jsonValueStart;
  // Group 1 is the value of name=value, ending at whitespace, '&', ',' or ';'
  private final Pattern keyValue;

  public SensitiveFieldLogSanitizer(SanitizerContext context) {
    var fields = context.config().getStringList("fields");
    if (fields.isEmpty()) {
      jsonValueStart = null;
      keyValue = null;
    } else {
      var names = fields.stream().map(Pattern::quote).collect(Collectors.joining("|"));
      jsonValueStart = Pattern.compile("\"(?:" + names + ")\"\\s*:\\s*\"", CASE_INSENSITIVE);
      keyValue = Pattern.compile("(?<![A-Za-z0-9_])(?:" + names + ")=([^\\s&,;]+)", CASE_INSENSITIVE);
    }
  }

  @Override
  public String sanitize(String message) {
    if (message == null || keyValue == null) return message;
    return maskKeyValues(maskJsonValues(message));
  }

  private String maskJsonValues(String message) {
    var matcher = jsonValueStart.matcher(message);
    StringBuilder masked = null;
    int copied = 0;
    int from = 0;
    while (from < message.length() && matcher.find(from)) {
      int valueStart = matcher.end();
      int valueEnd = closingQuote(message, valueStart);
      if (valueEnd < 0) break;
      if (valueEnd > valueStart) {
        if (masked == null) masked = new StringBuilder(message.length());
        masked.append(message, copied, valueStart).repeat('*', valueEnd - valueStart);
        copied = valueEnd;
      }
      from = valueEnd + 1;
    }
    return masked == null ? message : masked.append(message, copied, message.length()).toString();
  }

  /** Index of the quote closing a JSON string that starts at {@code start}, or -1. */
  private static int closingQuote(String message, int start) {
    for (int i = start; i < message.length(); i++) {
      char c = message.charAt(i);
      if (c == '\\') i++;
      else if (c == '"') return i;
    }
    return -1;
  }

  private String maskKeyValues(String message) {
    var matcher = keyValue.matcher(message);
    StringBuilder masked = null;
    int copied = 0;
    while (matcher.find()) {
      if (masked == null) masked = new StringBuilder(message.length());
      masked.append(message, copied, matcher.start(1)).repeat('*', matcher.end(1) - matcher.start(1));
      copied = matcher.end(1);
    }
    return masked == null ? message : masked.append(message, copied, message.length()).toString();
  }
}
