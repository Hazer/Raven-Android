package com.joshdholtz.sentry;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Created by Hazer on 2/25/16.
 */
class SentryEventBuilder implements Serializable {

    private static final long serialVersionUID = -8589756678369463988L;

    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    static {
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    Map<String, Object> getEvent() {
        return event;
    }

    private Map<String, Object> event;

    public static enum SentryEventLevel {

        FATAL("fatal"),
        ERROR("error"),
        WARNING("warning"),
        INFO("info"),
        DEBUG("debug");

        private String value;

        SentryEventLevel(String value) {
            this.value = value;
        }

    }

    public SentryEventBuilder() {
        event = new HashMap<String, Object>();
        event.put("event_id", UUID.randomUUID().toString().replace("-", ""));
        this.setTimestamp(System.currentTimeMillis());
    }


    public SentryEventBuilder(Throwable t, SentryEventLevel level) {
        this();

        String culprit = Sentry.getCause(t, t.getMessage());

        this.setMessage(t.getMessage())
                .setCulprit(culprit)
                .setLevel(level)
                .setException(t);
    }

    public SentryEventBuilder(Throwable t, SentryEventLevel level, String release) {
        this(t, level);
        setRelease(release);
    }

    /**
     * "message": "SyntaxError: Wattttt!"
     *
     * @param message Message
     * @return SentryEventBuilder
     */
    public SentryEventBuilder setMessage(String message) {
        event.put("message", message);
        return this;
    }

    /**
     * "timestamp": "2011-05-02T17:41:36"
     *
     * @param timestamp Timestamp
     * @return SentryEventBuilder
     */
    public SentryEventBuilder setTimestamp(long timestamp) {
        event.put("timestamp", sdf.format(new Date(timestamp)));
        return this;
    }

    /**
     * "release": "some-code-sentry-needs"
     *
     * @param release Release String
     * @return SentryEventBuilder
     */
    public SentryEventBuilder setRelease(String release) {
        if (release != null) event.put("release", release);
        return this;
    }

    /**
     * "level": "warning"
     *
     * @param level Level
     * @return SentryEventBuilder
     */
    public SentryEventBuilder setLevel(SentryEventLevel level) {
        event.put("level", level.value);
        return this;
    }

    /**
     * "logger": "my.logger.name"
     *
     * @param logger Logger
     * @return SentryEventBuilder
     */
    public SentryEventBuilder setLogger(String logger) {
        event.put("logger", logger);
        return this;
    }

    /**
     * "culprit": "my.module.function_name"
     *
     * @param culprit Culprit
     * @return SentryEventBuilder
     */
    public SentryEventBuilder setCulprit(String culprit) {
        event.put("culprit", culprit);
        return this;
    }

    /**
     * @param user User
     * @return SentryEventBuilder
     */
    public SentryEventBuilder setUser(Map<String, String> user) {
        setUser(new JSONObject(user));
        return this;
    }

    public SentryEventBuilder setUser(JSONObject user) {
        event.put("user", user);
        return this;
    }

    public JSONObject getUser() {
        if (!event.containsKey("user")) {
            setTags(new HashMap<String, String>());
        }

        return (JSONObject) event.get("user");
    }

    /**
     * @param tags Tags
     * @return SentryEventBuilder
     */
    public SentryEventBuilder setTags(Map<String, String> tags) {
        setTags(new JSONObject(tags));
        return this;
    }

    public SentryEventBuilder setTags(JSONObject tags) {
        event.put("tags", tags);
        return this;
    }

    public JSONObject getTags() {
        if (!event.containsKey("tags")) {
            setTags(new HashMap<String, String>());
        }

        return (JSONObject) event.get("tags");
    }

    /**
     * @param serverName Server name
     * @return SentryEventBuilder
     */
    public SentryEventBuilder setServerName(String serverName) {
        event.put("server_name", serverName);
        return this;
    }

    /**
     * @param name    Name
     * @param version Version
     * @return SentryEventBuilder
     */
    public SentryEventBuilder addModule(String name, String version) {
        JSONArray modules;
        if (!event.containsKey("modules")) {
            modules = new JSONArray();
            event.put("modules", modules);
        } else {
            modules = (JSONArray) event.get("modules");
        }

        if (name != null && version != null) {
            String[] module = {name, version};
            modules.put(new JSONArray(Arrays.asList(module)));
        }

        return this;
    }

    /**
     * @param extra Extra
     * @return SentryEventBuilder
     */
    public SentryEventBuilder setExtra(Map<String, String> extra) {
        setExtra(new JSONObject(extra));
        return this;
    }

    public SentryEventBuilder setExtra(JSONObject extra) {
        event.put("extra", extra);
        return this;
    }

    public JSONObject getExtra() {
        if (!event.containsKey("extra")) {
            setExtra(new HashMap<String, String>());
        }

        return (JSONObject) event.get("extra");
    }

    /**
     * @param t Throwable
     * @return SentryEventBuilder
     */
    public SentryEventBuilder setException(Throwable t) {
        JSONArray values = new JSONArray();

        while (t != null) {
            JSONObject exception = new JSONObject();

            try {
                exception.put("type", t.getClass().getSimpleName());
                exception.put("value", t.getMessage());
                exception.put("module", t.getClass().getPackage().getName());
                exception.put("stacktrace", getStackTrace(t));

                values.put(exception);
            } catch (JSONException e) {
                Log.e(Sentry.TAG, "Failed to build sentry report for " + t, e);
            }

            t = t.getCause();
        }

        JSONObject exceptionReport = new JSONObject();

        try {
            exceptionReport.put("values", values);
            event.put("exception", exceptionReport);
        } catch (JSONException e) {
            Log.e(Sentry.TAG, "Unable to attach exception to event " + values, e);
        }

        return this;
    }

    public static JSONObject getStackTrace(Throwable t) throws JSONException {
        JSONArray frameList = new JSONArray();

        for (StackTraceElement ste : t.getStackTrace()) {
            JSONObject frame = new JSONObject();

            String method = ste.getMethodName();
            if (method.length() != 0) {
                frame.put("function", method);
            }

            int lineno = ste.getLineNumber();
            if (!ste.isNativeMethod() && lineno >= 0) {
                frame.put("lineno", lineno);
            }

            boolean inApp = true;

            String className = ste.getClassName();
            frame.put("module", className);

            // Take out some of the system packages to improve the exception folding on the sentry server
            if (className.startsWith("android.")
                    || className.startsWith("java.")
                    || className.startsWith("dalvik.")
                    || className.startsWith("com.android.")) {

                inApp = false;
            }

            frame.put("in_app", inApp);

            frameList.put(frame);
        }

        JSONObject frameHash = new JSONObject();
        frameHash.put("frames", frameList);

        return frameHash;
    }
}
