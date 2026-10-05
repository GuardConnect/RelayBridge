package ru.eyeone.relaybridge;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * User settings. The whole object is stored as one encrypted JSON value;
 * a reduced "route" copy with only the delivery credentials travels with each queued event.
 */
final class Config {
    private static final String PREFS = "relay";
    private static final String KEY_CONFIG = "config";
    private static final Set<String> AUTH_MODES = Set.of("AUTO", "LOGIN", "PLAIN", "NONE");
    private static final String ADDRESS_PART = "[^\\s\\p{Cntrl}<>@,;]+";
    private static final String ADDRESS = ADDRESS_PART + "@" + ADDRESS_PART + "\\." + ADDRESS_PART;

    // Sources. A fresh install selects nothing.
    boolean enabled, sms, calls, pushes, ongoing;
    boolean smsAllSims = true, callAllSims = true;
    final Set<Integer> smsSubscriptions = new HashSet<>();
    final Set<Integer> callSubscriptions = new HashSet<>();
    final Set<String> apps = new HashSet<>();

    // Telegram.
    boolean telegram;
    String token = "", chat = "";

    // Email / SMTP.
    boolean email;
    String host = "", user = "", password = "", from = "", to = "";
    String tls = "STARTTLS", auth = "AUTO";
    int port = 587;

    String messageTemplate = MessageTemplate.DEFAULT;

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Read the saved settings. Forwarding counts as enabled only while consent is valid
     * and the status notification can be shown.
     */
    static Config load(Context context) throws Exception {
        Config config = new Config();
        String sealed = prefs(context).getString(KEY_CONFIG, "");
        if (sealed.isEmpty()) {
            forgetOpened();
            return config;
        }
        JSONObject json = new JSONObject(opened(sealed));

        config.enabled = json.optBoolean("enabled") && Consent.accepted(context) && Indicator.allowed(context);
        // Settings written before a source could be switched off treated a missing flag as "on".
        config.sms = json.optBoolean("sms", true);
        config.calls = json.optBoolean("calls", true);
        config.pushes = json.optBoolean("pushes", true);
        config.ongoing = json.optBoolean("ongoing");
        config.telegram = json.optBoolean("telegram");
        config.email = json.optBoolean("email");

        config.token = json.optString("token");
        config.chat = json.optString("chat");
        config.host = json.optString("host", "");
        config.port = json.optInt("port", 587);
        config.user = json.optString("user");
        config.password = json.optString("password");
        config.from = json.optString("from");
        config.to = json.optString("to");
        config.tls = json.optString("tls", "STARTTLS");
        config.auth = json.optString("auth", "AUTO");
        if (config.auth.equals("XOAUTH2")) {
            // Google sign-in was removed; such a configuration must be completed again by hand.
            config.auth = "AUTO";
            config.password = "";
            config.email = false;
        }
        config.messageTemplate = json.optString("messageTemplate", MessageTemplate.DEFAULT);

        config.smsAllSims = json.optBoolean("smsAllSims", true);
        readSubscriptions(json.optJSONArray("smsSubscriptions"), config.smsSubscriptions);
        config.callAllSims = json.optBoolean("callAllSims", true);
        readSubscriptions(json.optJSONArray("callSubscriptions"), config.callSubscriptions);
        JSONArray apps = json.optJSONArray("apps");
        if (apps != null) {
            for (int index = 0; index < apps.length(); index++) config.apps.add(apps.getString(index));
        }
        return config;
    }

    // The settings are read for every incoming event, including every notification of every application.
    // Decryption goes through Android Keystore, which is slow, so the last opened value is kept in memory
    // for as long as the stored value does not change.
    private static String openedSealed, openedPlain;

    private static synchronized String opened(String sealed) throws Exception {
        if (!sealed.equals(openedSealed)) {
            openedPlain = Crypto.open(sealed);
            openedSealed = sealed;
        }
        return openedPlain;
    }

    /** Settings were erased: do not keep their decrypted copy either. */
    private static synchronized void forgetOpened() {
        openedSealed = null;
        openedPlain = null;
    }

    private static void readSubscriptions(JSONArray source, Set<Integer> target) {
        if (source == null) return;
        for (int index = 0; index < source.length(); index++) {
            int id = source.optInt(index, -1);
            if (id >= 0) target.add(id);
        }
    }

    void save(Context context) throws Exception {
        JSONObject json = new JSONObject()
                .put("enabled", enabled).put("sms", sms).put("calls", calls).put("pushes", pushes)
                .put("ongoing", ongoing).put("telegram", telegram).put("email", email)
                .put("token", token).put("chat", chat)
                .put("host", host).put("port", port).put("user", user).put("password", password)
                .put("from", from).put("to", to).put("tls", tls).put("auth", auth)
                .put("messageTemplate", messageTemplate)
                .put("smsAllSims", smsAllSims).put("smsSubscriptions", new JSONArray(smsSubscriptions))
                .put("callAllSims", callAllSims).put("callSubscriptions", new JSONArray(callSubscriptions))
                .put("apps", new JSONArray(apps));
        boolean stored = prefs(context).edit().putString(KEY_CONFIG, Crypto.seal(json.toString())).commit();
        if (!stored) throw new Exception("Не удалось сохранить настройки");
    }

    /** Delivery credentials only; stored (encrypted) next to a queued event. */
    String deliveryJson() throws JSONException {
        return new JSONObject()
                .put("token", token).put("chat", chat)
                .put("host", host).put("port", port).put("user", user).put("password", password)
                .put("from", from).put("to", to).put("auth", auth).put("tls", tls)
                .toString();
    }

    static Config deliveryConfig(String raw) throws JSONException {
        JSONObject json = new JSONObject(raw);
        Config config = new Config();
        config.token = json.getString("token");
        config.chat = json.getString("chat");
        config.host = json.getString("host");
        config.port = json.getInt("port");
        config.user = json.getString("user");
        config.password = json.getString("password");
        config.from = json.getString("from");
        config.to = json.getString("to");
        config.auth = json.optString("auth", "AUTO");
        config.tls = json.getString("tls");
        return config;
    }

    /** @throws IllegalArgumentException with a user-readable reason */
    void validate() {
        if (!telegram && !email) throw new IllegalArgumentException("Включите Telegram и/или email");
        validateSelected();
    }

    void validateSelected() {
        MessageTemplate.validate(messageTemplate);
        if (telegram) validateTelegram();
        if (email) validateEmail();
    }

    void validateTelegram() {
        if (!token.matches("[0-9]+:[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("BotToken должен иметь вид 123456789:ABC…");
        if (!chat.matches("-?[0-9]+|@[A-Za-z][A-Za-z0-9_]{3,}"))
            throw new IllegalArgumentException("ChatID: числовой ID чата или @имя канала");
    }

    void validateEmail() {
        // A colon is acceptable only inside a bare IPv6 literal.
        boolean badHost = host.isBlank() || host.contains("/") || host.matches(".*\\s.*")
                || (host.contains(":") && !host.matches("[0-9a-fA-F:]+"));
        if (badHost || port < 1 || port > 65535)
            throw new IllegalArgumentException("SMTP: укажите имя сервера и порт 1–65535");
        // Plaintext SMTP is not offered at all, with or without authentication.
        if (!tls.equals("STARTTLS") && !tls.equals("SSL"))
            throw new IllegalArgumentException("SMTP: выберите STARTTLS или SSL");
        if (!AUTH_MODES.contains(auth))
            throw new IllegalArgumentException("SMTP: неизвестный способ авторизации");
        if (!auth.equals("NONE") && (user.isBlank() || password.isEmpty()))
            throw new IllegalArgumentException("SMTP: нужны логин и пароль");
        if (!address(from) || !recipientsValid())
            throw new IllegalArgumentException("Укажите корректные email отправителя и получателя");
    }

    private boolean recipientsValid() {
        if (to.isBlank()) return false;
        for (String recipient : to.split("[,;]", -1)) {
            if (!address(recipient.trim())) return false;
        }
        return true;
    }

    /** Recipients as separate trimmed addresses; call only after {@link #validateEmail()}. */
    String[] recipients() {
        String[] parts = to.split("[,;]");
        for (int index = 0; index < parts.length; index++) parts[index] = parts[index].trim();
        return parts;
    }

    static boolean address(String value) {
        return value != null && value.length() <= 254 && value.matches(ADDRESS);
    }
}
