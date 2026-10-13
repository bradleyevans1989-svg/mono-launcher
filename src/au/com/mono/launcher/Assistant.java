package au.com.mono.launcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The home screen assistant: a Claude conversation that can act on the launcher through tools.
 *
 * Calls the Messages API over plain HTTPS because this app is built without Gradle or third-party
 * libraries. {@link #send} blocks, so call it off the main thread.
 */
final class Assistant {
    static final String MODEL = "claude-opus-5-5";
    static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    static final int MAX_STEPS = 8;

    static final String SYSTEM = "You are Mono, the assistant built into the Mono Launcher home screen on the user's Android phone. "
            + "Replies appear in a small panel over the home screen, so keep them short and plain: a sentence or two, "
            + "or a short list when one is genuinely needed. No markdown headings or tables.\n\n"
            + "You can act on the phone with your tools: find and open installed apps, open websites, set timers and alarms, "
            + "and add apps to home screen folders. When the user asks you to do one of these, do it with the tool rather "
            + "than explaining how. Use find_apps when you are not sure of an app's exact name. After acting, confirm in a "
            + "few words. Use web search for current information such as news, weather, scores or opening hours.\n\n"
            + "You cannot read the user's messages, notifications, contacts, calendar or files, and you cannot change system "
            + "settings. Say so briefly if asked.";

    /** One line of the visible conversation. */
    static final class Line {
        static final int USER = 0;
        static final int ASSISTANT = 1;
        static final int ACTION = 2;
        static final int ERROR = 3;
        final int kind;
        final String text;

        Line(int kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    /** Runs launcher actions for the model. Implementations return a short result the model can read. */
    interface Host {
        String runTool(String name, JSONObject input) throws Exception;
    }

    /** Sends one request body and returns the response, so tests can stand in for the network. */
    interface Transport {
        Response post(String apiKey, String body) throws IOException;
    }

    static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    final List<Line> transcript = new ArrayList<>();
    final Transport transport;
    JSONArray messages = new JSONArray();
    volatile boolean busy;

    Assistant(Transport transport) {
        this.transport = transport;
    }

    static Assistant overHttps() {
        return new Assistant(new Transport() {
            @Override
            public Response post(String apiKey, String body) throws IOException {
                HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                try {
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(120000);
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setRequestProperty("content-type", "application/json");
                    connection.setRequestProperty("x-api-key", apiKey);
                    connection.setRequestProperty("anthropic-version", "2023-06-01");
                    connection.setRequestProperty("anthropic-beta", "server-side-fallback-2026-07-01");
                    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(payload.length);
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(payload);
                    }
                    int status = connection.getResponseCode();
                    InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                    return new Response(status, in == null ? "" : readAll(in));
                } finally {
                    connection.disconnect();
                }
            }
        });
    }

    static String readAll(InputStream in) throws IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    synchronized List<Line> lines() {
        return new ArrayList<>(this.transcript);
    }

    synchronized void clear() {
        this.transcript.clear();
        this.messages = new JSONArray();
    }

    synchronized void add(Line line) {
        this.transcript.add(line);
    }

    /**
     * Sends the user's text with a short context line and runs tool calls until Claude finishes.
     * Every visible change goes into the transcript and is reported through {@code changed}.
     */
    void send(String apiKey, String text, String context, Host host, Runnable changed) {
        this.busy = true;
        add(new Line(Line.USER, text));
        changed.run();
        int rollback = this.messages.length();
        try {
            JSONArray content = new JSONArray();
            if (context != null && !context.isEmpty()) {
                content.put(new JSONObject().put("type", "text").put("text", context));
            }
            content.put(new JSONObject().put("type", "text").put("text", text));
            this.messages.put(new JSONObject().put("role", "user").put("content", content));
            for (int step = 0; step < MAX_STEPS; step++) {
                Response response = this.transport.post(apiKey, requestBody().toString());
                if (response.status != 200) {
                    rollBack(rollback);
                    add(new Line(Line.ERROR, describeError(response)));
                    return;
                }
                JSONObject message = new JSONObject(response.body);
                String stop = message.optString("stop_reason");
                JSONArray blocks = message.optJSONArray("content");
                if (blocks == null) {
                    blocks = new JSONArray();
                }
                if ("refusal".equals(stop)) {
                    rollBack(rollback);
                    add(new Line(Line.ERROR, "Claude declined that one. Try asking another way."));
                    return;
                }
                this.messages.put(new JSONObject().put("role", "assistant").put("content", blocks));
                for (int i = 0; i < blocks.length(); i++) {
                    JSONObject block = blocks.getJSONObject(i);
                    if ("text".equals(block.optString("type"))) {
                        String said = block.optString("text").trim();
                        if (!said.isEmpty()) {
                            add(new Line(Line.ASSISTANT, said));
                        }
                    }
                }
                changed.run();
                if ("pause_turn".equals(stop)) {
                    continue;
                }
                if (!"tool_use".equals(stop)) {
                    if ("max_tokens".equals(stop)) {
                        add(new Line(Line.ERROR, "That answer ran long and was cut off."));
                    }
                    return;
                }
                JSONArray results = new JSONArray();
                for (int i = 0; i < blocks.length(); i++) {
                    JSONObject block = blocks.getJSONObject(i);
                    if (!"tool_use".equals(block.optString("type"))) {
                        continue;
                    }
                    JSONObject result = new JSONObject().put("type", "tool_result").put("tool_use_id", block.getString("id"));
                    try {
                        String outcome = host.runTool(block.getString("name"), block.optJSONObject("input") == null ? new JSONObject() : block.getJSONObject("input"));
                        result.put("content", outcome);
                        add(new Line(Line.ACTION, outcome));
                    } catch (Exception e) {
                        result.put("content", "Failed: " + e.getMessage()).put("is_error", true);
                    }
                    results.put(result);
                }
                changed.run();
                this.messages.put(new JSONObject().put("role", "user").put("content", results));
            }
            add(new Line(Line.ERROR, "That took too many steps, so I stopped."));
        } catch (IOException e) {
            rollBack(rollback);
            add(new Line(Line.ERROR, "Couldn't reach Claude. Check your connection and try again."));
        } catch (JSONException e) {
            rollBack(rollback);
            add(new Line(Line.ERROR, "Claude sent a reply the launcher couldn't read."));
        } finally {
            this.busy = false;
            changed.run();
        }
    }

    /** Drops a failed turn so the next request starts from a clean, valid history. */
    void rollBack(int length) {
        JSONArray kept = new JSONArray();
        for (int i = 0; i < length; i++) {
            kept.put(this.messages.opt(i));
        }
        this.messages = kept;
    }

    static String describeError(Response response) {
        String detail = "";
        try {
            detail = new JSONObject(response.body).getJSONObject("error").optString("message");
        } catch (Exception e) {
            // Not an API error body; fall back to the status code.
        }
        switch (response.status) {
            case 401:
            case 403:
                return "That API key didn't work. Update it in Settings, Assistant.";
            case 429:
                return "Too many requests right now. Wait a moment and try again.";
            case 529:
            case 500:
            case 502:
            case 503:
                return "Claude is busy right now. Try again shortly.";
            default:
                return "Claude couldn't answer (error " + response.status + (detail.isEmpty() ? "" : ": " + detail) + ").";
        }
    }

    JSONObject requestBody() throws JSONException {
        return new JSONObject()
                .put("model", MODEL)
                .put("max_tokens", 16000)
                .put("output_config", new JSONObject().put("effort", "low"))
                .put("fallbacks", "default")
                .put("system", new JSONArray().put(new JSONObject()
                        .put("type", "text")
                        .put("text", SYSTEM)
                        .put("cache_control", new JSONObject().put("type", "ephemeral"))))
                .put("tools", tools())
                .put("messages", this.messages);
    }

    static JSONArray tools() throws JSONException {
        return new JSONArray()
                .put(tool("find_apps", "List installed apps whose names contain the query. Pass an empty query to list every app.",
                        props().put("query", str("Part of an app name, or empty for all apps")), "query"))
                .put(tool("open_app", "Open an installed app by its name as shown on the phone.",
                        props().put("name", str("The app's name, e.g. Spotify")), "name"))
                .put(tool("open_website", "Open a web page in the user's browser.",
                        props().put("url", str("Full https address")), "url"))
                .put(tool("set_timer", "Start a countdown timer in the phone's clock app.",
                        props().put("seconds", integer("Length in seconds")).put("label", str("Short label, or empty")), "seconds", "label"))
                .put(tool("set_alarm", "Set an alarm in the phone's clock app.",
                        props().put("hour", integer("Hour, 0-23")).put("minute", integer("Minute, 0-59")).put("label", str("Short label, or empty")), "hour", "minute", "label"))
                .put(tool("add_app_to_folder", "Add an installed app to one of the home screen folders.",
                        props().put("app", str("The app's name")).put("folder", str("An existing folder name")), "app", "folder"))
                .put(new JSONObject().put("type", "web_search_20260209").put("name", "web_search").put("max_uses", 3));
    }

    static JSONObject tool(String name, String description, JSONObject properties, String... required) throws JSONException {
        JSONArray req = new JSONArray();
        for (String field : required) {
            req.put(field);
        }
        return new JSONObject()
                .put("name", name)
                .put("description", description)
                .put("strict", true)
                .put("input_schema", new JSONObject()
                        .put("type", "object")
                        .put("properties", properties)
                        .put("required", req)
                        .put("additionalProperties", false));
    }

    static JSONObject props() {
        return new JSONObject();
    }

    static JSONObject str(String description) throws JSONException {
        return new JSONObject().put("type", "string").put("description", description);
    }

    static JSONObject integer(String description) throws JSONException {
        return new JSONObject().put("type", "integer").put("description", description);
    }
}
