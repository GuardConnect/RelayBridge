package ru.eyeone.relaybridge;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** User-defined message layout with {{variable}} placeholders. Substitution is literal, never recursive. */
final class MessageTemplate {
    static final String DEFAULT = "RelayBridge · {{type}}\n{{time}}\n\n{{data}}";
    static final Set<String> VARIABLES = Set.of(
            "time", "date", "type", "data", "title", "message", "number", "sim", "app", "package", "sender");

    private static final int MAX_TEMPLATE = 4000;
    private static final int MAX_OUTPUT = 32000;
    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\}\\}");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX", Locale.ROOT);

    private MessageTemplate() { }

    /** @throws IllegalArgumentException with a user-readable reason when the template is unusable */
    static void validate(String template) {
        if (template == null || template.isBlank())
            throw new IllegalArgumentException("Шаблон сообщения не может быть пустым");
        if (template.length() > MAX_TEMPLATE)
            throw new IllegalArgumentException("Шаблон: не более 4000 символов");
        Matcher matcher = TOKEN.matcher(template);
        while (matcher.find()) {
            if (!VARIABLES.contains(name(matcher)))
                throw new IllegalArgumentException("Неизвестная переменная: {{" + matcher.group(1) + "}}");
        }
        // Whatever braces remain outside valid tokens are unbalanced or empty placeholders.
        String literals = matcher.replaceAll("");
        if (literals.contains("{{") || literals.contains("}}"))
            throw new IllegalArgumentException("Переменные записываются как {{time}} — проверьте скобки");
    }

    static Map<String, String> values(String type, String title, String message, String data, String number,
                                      String sim, String app, String pkg, long millis) {
        return values(type, title, message, data, number, sim, app, pkg, "", millis);
    }

    static Map<String, String> values(String type, String title, String message, String data, String number,
                                      String sim, String app, String pkg, String sender, long millis) {
        ZonedDateTime moment = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault());
        Map<String, String> values = new HashMap<>();
        values.put("time", moment.format(TIME));
        values.put("date", moment.format(DateTimeFormatter.ISO_LOCAL_DATE));
        put(values, "type", type);
        put(values, "title", title);
        put(values, "message", message);
        put(values, "data", data);
        put(values, "number", number);
        put(values, "sim", sim);
        put(values, "app", app);
        put(values, "package", pkg);
        put(values, "sender", sender);
        return values;
    }

    private static void put(Map<String, String> values, String name, String content) {
        values.put(name, content == null ? "" : content);
    }

    /** Fill the template; unavailable fields become empty strings and the result is bounded. */
    static String render(String template, Map<String, String> values) {
        validate(template);
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder out = new StringBuilder();
        int position = 0;
        while (matcher.find()) {
            append(out, template.substring(position, matcher.start()));
            String value = values.get(name(matcher));
            append(out, value == null ? "" : value);
            position = matcher.end();
        }
        append(out, template.substring(position));
        String result = out.toString();
        if (result.isBlank())
            throw new IllegalArgumentException("Шаблон дал пустое сообщение — добавьте {{data}} или {{message}}");
        return result;
    }

    private static String name(Matcher token) {
        return token.group(1).toLowerCase(Locale.ROOT);
    }

    private static void append(StringBuilder out, String value) {
        int available = MAX_OUTPUT - out.length();
        if (available > 0) out.append(value, 0, TextTools.chunkEnd(value, 0, available));
    }
}
