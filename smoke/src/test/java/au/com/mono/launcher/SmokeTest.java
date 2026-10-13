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
@Config(sdk = 35)
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

    @Test
    public void droppingAppOnAppMakesFolder() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        ShadowLooper.idleMainLooper();
        String dragged = "test.pkg/.Main";
        a.apps.add(a.new App(dragged, "Test", android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)));
        java.util.Set<String> home = a.homeIds();
        home.add("target.app");
        a.prefs.edit().putStringSet("homeApps", home).putInt("homePos:target.app", 15).commit();
        assertTrue(a.putHome(a.new AppDrag(dragged, null), 15));
        assertTrue(java.util.Arrays.asList(a.folders).contains("Folder"));
        assertEquals(15, a.position("Folder"));
        java.util.Set<String> inside = a.prefs.getStringSet("folder:Folder", null);
        assertTrue(inside.contains(dragged) && inside.contains("target.app"));
        assertFalse(a.homeIds().contains("target.app"));
        ShadowLooper.idleMainLooper();
    }

    @Test
    public void blockFieldRipplesAndSettles() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        BlockField f = a.blockField;
        assertNotNull(f);
        f.layout(0, 0, 1080, 2400);
        assertTrue(f.cols > 0 && f.rows > 0);
        f.poke(540, 1200, 1f);
        assertTrue(f.moving);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(1080, 2400, android.graphics.Bitmap.Config.ARGB_8888);
        f.draw(new android.graphics.Canvas(b));
        boolean still = true;
        for (int k = 0; k < 2000 && still; k++) still = f.step(1f / 60f);
        assertFalse("field comes to rest", still);
    }
}
