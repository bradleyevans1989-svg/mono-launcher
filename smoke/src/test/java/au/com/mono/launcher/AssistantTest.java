package au.com.mono.launcher;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class AssistantTest {
    @org.junit.Test
    public void scriptedConversations() throws Exception {
        main(new String[0]);
    }

    static int failures = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS " : "FAIL ") + what);
        if (!ok) failures++;
    }

    static Assistant.Transport scripted(final List<String> sent, final Assistant.Response... replies) {
        return new Assistant.Transport() {
            int i = 0;
            public Assistant.Response post(String key, String body) {
                sent.add(body);
                return replies[i++];
            }
        };
    }

    static String msg(String stop, JSONArray content) throws Exception {
        return new JSONObject().put("stop_reason", stop).put("content", content).toString();
    }

    public static void main(String[] args) throws Exception {
        Runnable nop = new Runnable() { public void run() {} };
        final List<String> calls = new ArrayList<>();
        Assistant.Host host = new Assistant.Host() {
            public String runTool(String name, JSONObject input) throws Exception {
                calls.add(name + ":" + input);
                if (name.equals("open_app") && input.getString("name").equals("Nope")) throw new Exception("no installed app called \"Nope\"");
                return "Opened " + input.optString("name");
            }
        };

        // 1. Tool loop: tool_use then end_turn, history stays append-only with thinking blocks echoed.
        List<String> sent = new ArrayList<>();
        JSONArray first = new JSONArray()
            .put(new JSONObject().put("type", "thinking").put("thinking", "").put("signature", "sig1"))
            .put(new JSONObject().put("type", "tool_use").put("id", "tu1").put("name", "open_app").put("input", new JSONObject().put("name", "Spotify")));
        JSONArray second = new JSONArray().put(new JSONObject().put("type", "text").put("text", "Spotify's open."));
        Assistant a = new Assistant(scripted(sent, new Assistant.Response(200, msg("tool_use", first)), new Assistant.Response(200, msg("end_turn", second))));
        a.send("k", "open spotify", "(ctx)", host, nop);
        check(sent.size() == 2, "two requests in tool loop");
        JSONObject req2 = new JSONObject(sent.get(1));
        JSONArray m = req2.getJSONArray("messages");
        check(m.length() == 3, "history user/assistant/tool_result");
        check(m.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("signature").equals("sig1"), "thinking block echoed unchanged");
        check(m.getJSONObject(2).getJSONArray("content").getJSONObject(0).getString("tool_use_id").equals("tu1"), "tool_result references tool_use id");
        check(m.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text").equals("(ctx)"), "context line first in user turn");
        check(calls.size() == 1 && calls.get(0).startsWith("open_app"), "host ran open_app");
        List<Assistant.Line> lines = a.lines();
        check(lines.size() == 3 && lines.get(1).kind == Assistant.Line.ACTION && lines.get(2).text.equals("Spotify's open."), "transcript user/action/answer");
        check(!a.busy, "not busy after finishing");
        check(req2.getString("model").equals("claude-opus-5-5") && req2.getString("fallbacks").equals("default"), "model and fallbacks set");
        JSONArray tools = req2.getJSONArray("tools");
        check(tools.getJSONObject(tools.length() - 1).getString("type").equals("web_search_20260209"), "web search tool present");
        check(!req2.has("thinking") && !req2.has("tool_choice"), "no disabled thinking or forced tool choice");

        // 2. Tool error becomes is_error result, not a crash.
        sent.clear(); calls.clear();
        JSONArray bad = new JSONArray().put(new JSONObject().put("type", "tool_use").put("id", "tu2").put("name", "open_app").put("input", new JSONObject().put("name", "Nope")));
        Assistant b = new Assistant(scripted(sent, new Assistant.Response(200, msg("tool_use", bad)), new Assistant.Response(200, msg("end_turn", new JSONArray().put(new JSONObject().put("type", "text").put("text", "Couldn't find it."))))));
        b.send("k", "open nope", null, host, nop);
        JSONObject tr = new JSONObject(sent.get(1)).getJSONArray("messages").getJSONObject(2).getJSONArray("content").getJSONObject(0);
        check(tr.optBoolean("is_error") && tr.getString("content").contains("Nope"), "tool failure sent as is_error");

        // 3. Refusal rolls back the turn so the next request has clean history.
        sent.clear();
        Assistant c = new Assistant(scripted(sent,
            new Assistant.Response(200, msg("end_turn", new JSONArray().put(new JSONObject().put("type", "text").put("text", "hi"))))
            , new Assistant.Response(200, msg("refusal", new JSONArray()))
            , new Assistant.Response(200, msg("end_turn", new JSONArray().put(new JSONObject().put("type", "text").put("text", "ok"))))));
        c.send("k", "hello", null, host, nop);
        c.send("k", "bad", null, host, nop);
        c.send("k", "again", null, host, nop);
        JSONArray m3 = new JSONObject(sent.get(2)).getJSONArray("messages");
        check(m3.length() == 3 && m3.getJSONObject(2).getJSONArray("content").getJSONObject(0).getString("text").equals("again"), "refused turn removed from history");

        // 4. HTTP 401 shows a key error and rolls back.
        sent.clear();
        Assistant d = new Assistant(scripted(sent, new Assistant.Response(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}")));
        d.send("k", "hi", null, host, nop);
        check(d.messages.length() == 0 && d.lines().get(1).text.contains("API key"), "401 handled");

        // 5. pause_turn re-sends with the paused content appended.
        sent.clear();
        JSONArray paused = new JSONArray().put(new JSONObject().put("type", "server_tool_use").put("id", "s1").put("name", "web_search").put("input", new JSONObject().put("query", "weather")));
        Assistant e = new Assistant(scripted(sent, new Assistant.Response(200, msg("pause_turn", paused)), new Assistant.Response(200, msg("end_turn", new JSONArray().put(new JSONObject().put("type", "text").put("text", "Sunny."))))));
        e.send("k", "weather?", null, host, nop);
        JSONArray m5 = new JSONObject(sent.get(1)).getJSONArray("messages");
        check(m5.length() == 2 && m5.getJSONObject(1).getString("role").equals("assistant"), "pause_turn continues with assistant content last");

        // 6. Tool schemas are strict-compatible.
        JSONArray all = Assistant.tools();
        boolean strictOk = true;
        for (int i = 0; i < all.length() - 1; i++) {
            JSONObject schema = all.getJSONObject(i).getJSONObject("input_schema");
            JSONArray req = schema.getJSONArray("required");
            if (schema.optBoolean("additionalProperties", true) || req.length() != schema.getJSONObject("properties").length()) strictOk = false;
        }
        check(strictOk, "strict tools require every property and forbid extras");

        System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
        if (failures != 0) throw new AssertionError(failures + " assistant checks failed");
    }
}
