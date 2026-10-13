package au.com.mono.launcher;

import static org.junit.Assert.*;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class SmokeTest {
    static boolean hasText(View v, String s) {
        if (v instanceof TextView && ((TextView) v).getText().toString().contains(s)) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (hasText(g.getChildAt(i), s)) return true;
        }
        return false;
    }

    @Test
    public void homeAssistantAndSettingsWork() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        ShadowLooper.idleMainLooper();
        assertTrue("ask bar on home", hasText(a.root, "Ask Mono anything"));
        a.showAssistant(false);
        assertNotNull(a.drawer);
        assertTrue("setup shown without key", hasText(a.drawer, "Add Claude API key"));
        a.prefs.edit().putString("secret:claudeKey", "x").commit();
        a.drawerRefresh.run();
        assertTrue("suggestions with key", hasText(a.drawer, "Set a 10 minute timer"));
        a.assistant().add(new Assistant.Line(Assistant.Line.USER, "hello"));
        a.assistant().add(new Assistant.Line(Assistant.Line.ACTION, "Opened Spotify"));
        a.drawerRefresh.run();
        assertTrue(hasText(a.drawer, "✓ Opened Spotify"));
        a.onBackPressed();
        assertNull(a.drawer);
        a.settings();
        a.assistantSettings();
        a.settingsGroup("Home screen", new String[]{"x"}, new int[]{3});
        a.showApps(null);
        a.onBackPressed();
        a.showSidePage(1);
        a.showSidePage(-1);
        c.pause().resume();
        c.pause().stop().destroy();
    }

    @Test
    public void assistantToolsRunAgainstLauncher() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertTrue(a.assistantTool("find_apps", new org.json.JSONObject().put("query", "")).length() > 0);
        String t = a.assistantTool("set_timer", new org.json.JSONObject().put("seconds", 600).put("label", "tea"));
        assertEquals("Timer set for 10 min", t);
        try { a.assistantTool("open_app", new org.json.JSONObject().put("name", "zzzz")); fail(); } catch (Exception expected) {}
        try { a.assistantTool("add_app_to_folder", new org.json.JSONObject().put("app", "x").put("folder", "Nope")); fail(); } catch (Exception expected) {}
        assertTrue(a.assistantContext().contains("Work, Socials, AI"));
    }
}
