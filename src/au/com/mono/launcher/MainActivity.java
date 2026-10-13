package au.com.mono.launcher;

import android.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.WallpaperManager;
import android.app.role.RoleManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.AtomicFile;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import au.com.mono.launcher.AppSnapshot;
import au.com.mono.launcher.LauncherPet;
import au.com.mono.launcher.LearningContent;
import au.com.mono.launcher.MainActivity;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;

/* loaded from: classes.dex */
public class MainActivity extends Activity {
    static final int ACCENT = -9247761;
    static final int BLACK = -435017152;
    static final int FOLDER_BACKGROUND = -1290059688;
    boolean appsLoading;
    boolean appsReloadPending;
    TextView battery;
    View cachedApps;
    Runnable cachedRefresh;
    EditText cachedSearch;
    Clock clock;
    TextView date;
    TextView digitalClock;
    float downY;
    View dragBin;
    boolean dragging;
    View drawer;
    Runnable drawerRefresh;
    FocusClock focusClock;
    FolderBoard folderBoard;
    LinearLayout home;
    boolean homeDirty;
    long lastAppRefresh;
    LauncherApps launcher;
    LauncherPet pet;
    Assistant assistant;
    SecretStore secrets;
    Runnable assistantRefresh;
    final ExecutorService assistantWorker = Executors.newSingleThreadExecutor();
    SharedPreferences prefs;
    int refreshFailures;
    boolean resumed;
    boolean returnToRecents;
    FrameLayout root;
    Bitmap wallpaperBitmap;
    ImageView wallpaperView;
    BlockField blockField;
    ArrayList<App> apps = new ArrayList<>();
    Handler handler = new Handler();
    int batteryPercent = -1;
    final BroadcastReceiver batteryUpdates = new BroadcastReceiver() { // from class: au.com.mono.launcher.MainActivity.1
        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            int i = -1;
            int intExtra = intent.getIntExtra("level", -1);
            int intExtra2 = intent.getIntExtra("scale", 100);
            MainActivity mainActivity = MainActivity.this;
            if (intExtra >= 0 && intExtra2 > 0) {
                i = Math.round((intExtra * 100.0f) / intExtra2);
            }
            mainActivity.batteryPercent = i;
            MainActivity.this.updateBattery();
        }
    };
    String wallpaperLoaded = "";
    String wallpaperLoading = "";
    String[] folders = {"Work", "Socials", "AI"};
    String[] dock = {"Phone", "Gallery", "Messages", "Browser", "Camera"};
    final String[] upperShortcuts = {"Wallet", "Shortcut 2", "Shortcut 3", "Shortcut 4", "Shortcut 5", "Impact Hub"};
    final ExecutorService wallpaperWorker = Executors.newSingleThreadExecutor();
    final ExecutorService appWorker = Executors.newSingleThreadExecutor();
    final LauncherApps.Callback packageChanges = new LauncherApps.Callback() { // from class: au.com.mono.launcher.MainActivity.2
        @Override // android.content.pm.LauncherApps.Callback
        public void onPackageAdded(String str, UserHandle userHandle) {
            MainActivity.this.refreshApps();
        }

        @Override // android.content.pm.LauncherApps.Callback
        public void onPackageChanged(String str, UserHandle userHandle) {
            MainActivity.this.refreshApps();
        }

        @Override // android.content.pm.LauncherApps.Callback
        public void onPackageRemoved(String str, UserHandle userHandle) {
            MainActivity.this.refreshApps();
        }

        @Override // android.content.pm.LauncherApps.Callback
        public void onPackagesAvailable(String[] strArr, UserHandle userHandle, boolean z) {
            MainActivity.this.refreshApps();
        }

        @Override // android.content.pm.LauncherApps.Callback
        public void onPackagesUnavailable(String[] strArr, UserHandle userHandle, boolean z) {
            MainActivity.this.refreshApps();
        }
    };
    final Map<String, Long> usageTimes = new HashMap();
    int iconSize = 25;
    final Runnable ticker = new Runnable() { // from class: au.com.mono.launcher.MainActivity.3
        @Override // java.lang.Runnable
        public void run() {
            if (MainActivity.this.clock != null) {
                MainActivity.this.clock.invalidate();
            }
            if (MainActivity.this.focusClock != null) {
                MainActivity.this.focusClock.invalidate();
            }
            if (MainActivity.this.digitalClock != null) {
                String format = DateFormat.getTimeFormat(MainActivity.this).format(new Date());
                if (!format.contentEquals(MainActivity.this.digitalClock.getText())) {
                    MainActivity.this.digitalClock.setText(format);
                }
            }
            if (MainActivity.this.date != null) {
                String format2 = new SimpleDateFormat("EEE\ndd\nMMMM", Locale.getDefault()).format(new Date());
                if (!format2.contentEquals(MainActivity.this.date.getText())) {
                    MainActivity.this.date.setText(format2);
                }
            }
            if (MainActivity.this.resumed) {
                MainActivity.this.handler.postDelayed(this, 1000L);
            }
        }
    };
    int sidePage = 0;
    final Random learningRandom = new Random();

    interface Pick {
        void chosen(App app);
    }

    class App {
        Drawable icon;
        String id;
        String label;

        App(String str, String str2, Bitmap bitmap) {
            this.id = str;
            this.label = str2;
            this.icon = new BitmapDrawable(MainActivity.this.getResources(), bitmap);
        }

        App(LauncherActivityInfo launcherActivityInfo) {
            Drawable defaultActivityIcon;
            this.id = launcherActivityInfo.getComponentName().flattenToString();
            try {
                this.label = launcherActivityInfo.getLabel().toString();
            } catch (Exception e) {
                this.label = launcherActivityInfo.getComponentName().getPackageName();
            }
            try {
                defaultActivityIcon = launcherActivityInfo.getIcon(0);
            } catch (Exception e2) {
                defaultActivityIcon = MainActivity.this.getPackageManager().getDefaultActivityIcon();
            }
            defaultActivityIcon = defaultActivityIcon == null ? MainActivity.this.getPackageManager().getDefaultActivityIcon() : defaultActivityIcon;
            int min = Math.min(128, MainActivity.this.dp(40.0f));
            Bitmap createBitmap = Bitmap.createBitmap(min, min, Bitmap.Config.ARGB_8888);
            try {
                defaultActivityIcon.setBounds(0, 0, min, min);
                defaultActivityIcon.draw(new Canvas(createBitmap));
            } catch (Exception e3) {
                Drawable defaultActivityIcon2 = MainActivity.this.getPackageManager().getDefaultActivityIcon();
                defaultActivityIcon2.setBounds(0, 0, min, min);
                defaultActivityIcon2.draw(new Canvas(createBitmap));
            }
            this.icon = new BitmapDrawable(MainActivity.this.getResources(), createBitmap);
        }
    }

    Drawable appIcon(App app) {
        Drawable.ConstantState constantState = app.icon.getConstantState();
        return constantState == null ? app.icon : constantState.newDrawable(getResources()).mutate();
    }

    void invalidateAppDrawer() {
        this.cachedApps = null;
        this.cachedSearch = null;
        this.cachedRefresh = null;
    }

    void applyAppList(ArrayList<App> arrayList) {
        this.apps.clear();
        this.apps.addAll(arrayList);
        invalidateAppDrawer();
        this.homeDirty = true;
        if (this.drawer != null && this.drawerRefresh != null) {
            this.drawerRefresh.run();
        } else if (this.resumed && this.drawer == null && !this.dragging) {
            buildHome();
        }
    }

    /* renamed from: lambda$0$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m4lambda$0$aucommonolauncherMainActivity() {
        final ArrayList<App> cached = new ArrayList<>();
        try (FileInputStream in = new AtomicFile(new File(getFilesDir(), "apps-v2.cache")).openRead()) {
            for (AppSnapshot.Entry entry : AppSnapshot.read(in)) {
                if (ComponentName.unflattenFromString(entry.id) == null) {
                    throw new IOException("Invalid component");
                }
                Bitmap icon = BitmapFactory.decodeByteArray(entry.icon, 0, entry.icon.length);
                if (icon == null) {
                    throw new IOException("Invalid icon");
                }
                cached.add(new App(entry.id, entry.label, icon));
            }
        } catch (Exception e) {
            return;
        }
        if (cached.isEmpty()) {
            return;
        }
        this.handler.post(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.m5lambda$1$aucommonolauncherMainActivity(cached);
            }
        });
    }

    void loadAppSnapshot() {
        this.appWorker.execute(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda35
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m4lambda$0$aucommonolauncherMainActivity();
            }
        });
    }

    /* renamed from: lambda$1$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m5lambda$1$aucommonolauncherMainActivity(ArrayList arrayList) {
        if (isDestroyed() || !this.apps.isEmpty() || this.dragging) {
            return;
        }
        applyAppList(arrayList);
    }

    /* renamed from: lambda$2$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m16lambda$2$aucommonolauncherMainActivity(ArrayList arrayList) {
        FileOutputStream fileOutputStream;
        AtomicFile atomicFile = new AtomicFile(new File(getFilesDir(), "apps-v2.cache"));
        try {
            ArrayList arrayList2 = new ArrayList();
            Iterator it = arrayList.iterator();
            while (it.hasNext()) {
                App app = (App) it.next();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ((BitmapDrawable) app.icon).getBitmap().compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                arrayList2.add(new AppSnapshot.Entry(app.id, app.label, byteArrayOutputStream.toByteArray()));
            }
            fileOutputStream = atomicFile.startWrite();
            try {
                AppSnapshot.write(fileOutputStream, arrayList2);
                atomicFile.finishWrite(fileOutputStream);
            } catch (Exception e) {
                if (fileOutputStream != null) {
                    atomicFile.failWrite(fileOutputStream);
                }
            }
        } catch (Exception e2) {
            fileOutputStream = null;
        }
    }

    void saveAppSnapshot(final ArrayList<App> arrayList) {
        this.appWorker.execute(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda34
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m16lambda$2$aucommonolauncherMainActivity(arrayList);
            }
        });
    }

    /* renamed from: lambda$3$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m25lambda$3$aucommonolauncherMainActivity() {
        syncWallpaper(false);
    }

    @Override // android.app.Activity
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.prefs = getSharedPreferences("mono", 0);
        this.launcher = (LauncherApps) getSystemService(LauncherApps.class);
        loadFolders();
        migrateGrid();
        buildHome();
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(this.batteryUpdates, new IntentFilter("android.intent.action.BATTERY_CHANGED"), 4);
        } else {
            registerReceiver(this.batteryUpdates, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
        }
        this.launcher.registerCallback(this.packageChanges, this.handler);
        loadAppSnapshot();
        refreshApps();
        this.root.post(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda71
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m25lambda$3$aucommonolauncherMainActivity();
            }
        });
        handlePin(getIntent());
    }

    @Override // android.app.Activity
    public void onResume() {
        super.onResume();
        this.resumed = true;
        if (this.pet != null) {
            this.pet.setActive(true);
        }
        if (this.blockField != null) {
            this.blockField.setPaused(false);
        }
        if (!this.appsLoading && (this.apps.isEmpty() || this.refreshFailures > 0 || SystemClock.elapsedRealtime() - this.lastAppRefresh > 300000)) {
            refreshApps();
        }
        if (this.wallpaperView != null) {
            loadPhotoBackground();
        }
        if (this.homeDirty && this.drawer == null && !this.dragging) {
            buildHome();
        }
        this.handler.removeCallbacks(this.ticker);
        this.handler.post(this.ticker);
        if (this.returnToRecents) {
            this.returnToRecents = false;
            showRecents();
        }
    }

    @Override // android.app.Activity
    public void onPause() {
        super.onPause();
        this.resumed = false;
        if (this.pet != null) {
            this.pet.setActive(false);
        }
        if (this.blockField != null) {
            this.blockField.setPaused(true);
        }
        this.handler.removeCallbacks(this.ticker);
    }

    @Override // android.app.Activity
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        m57lambda$63$aucommonolauncherMainActivity();
        if (this.homeDirty) {
            buildHome();
        }
        handlePin(intent);
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        if (this.drawer != null) {
            m57lambda$63$aucommonolauncherMainActivity();
        }
        if (this.homeDirty) {
            buildHome();
        }
    }

    int dp(float f) {
        return (int) ((f * getResources().getDisplayMetrics().density) + 0.5f);
    }

    boolean sameCatalogue(ArrayList<App> arrayList, ArrayList<App> arrayList2) {
        if (arrayList.size() != arrayList2.size()) {
            return false;
        }
        for (int i = 0; i < arrayList.size(); i++) {
            App app = arrayList.get(i);
            App app2 = arrayList2.get(i);
            if (!app.id.equals(app2.id) || !app.label.equals(app2.label) || !((BitmapDrawable) app.icon).getBitmap().sameAs(((BitmapDrawable) app2.icon).getBitmap())) {
                return false;
            }
        }
        return true;
    }

    void refreshApps() {
        if (isDestroyed()) {
            return;
        }
        if (this.appsLoading) {
            this.appsReloadPending = true;
            return;
        }
        this.appsLoading = true;
        final ArrayList arrayList = new ArrayList(this.apps);
        this.appWorker.execute(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda47
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m35lambda$4$aucommonolauncherMainActivity(arrayList);
            }
        });
    }

    /* renamed from: au.com.mono.launcher.MainActivity$4, reason: invalid class name */
    class AnonymousClass4 implements Runnable {
        private final /* synthetic */ boolean val$changed;
        private final /* synthetic */ ArrayList val$fresh;
        private final /* synthetic */ boolean val$ok;

        AnonymousClass4(boolean z, boolean z2, ArrayList arrayList) {
            this.val$ok = z;
            this.val$changed = z2;
            this.val$fresh = arrayList;
        }

        @Override // java.lang.Runnable
        public void run() {
            if (MainActivity.this.isDestroyed()) {
                return;
            }
            if (MainActivity.this.dragging) {
                MainActivity.this.handler.postDelayed(this, 100L);
                return;
            }
            MainActivity.this.appsLoading = false;
            if (this.val$ok) {
                MainActivity.this.refreshFailures = 0;
                MainActivity.this.lastAppRefresh = SystemClock.elapsedRealtime();
                if (this.val$changed) {
                    MainActivity.this.applyAppList(this.val$fresh);
                    MainActivity.this.saveAppSnapshot(this.val$fresh);
                }
            } else {
                MainActivity.this.refreshFailures++;
                if (MainActivity.this.drawerRefresh != null) {
                    MainActivity.this.drawerRefresh.run();
                }
                if (MainActivity.this.refreshFailures <= 3) {
                    MainActivity.this.handler.postDelayed(new Runnable() { // from class: au.com.mono.launcher.MainActivity$4$$ExternalSyntheticLambda0
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.AnonymousClass4.this.m67lambda$0$aucommonolauncherMainActivity$4();
                        }
                    }, MainActivity.this.refreshFailures * 1500);
                } else {
                    MainActivity.this.toast("Apps could not refresh. Your saved apps are still available; try Refresh apps in settings.");
                }
            }
            if (MainActivity.this.appsReloadPending) {
                MainActivity.this.appsReloadPending = false;
                MainActivity.this.refreshApps();
            }
        }

        /* renamed from: lambda$0$au-com-mono-launcher-MainActivity$4, reason: not valid java name */
        /* synthetic */ void m67lambda$0$aucommonolauncherMainActivity$4() {
            if (MainActivity.this.isDestroyed() || MainActivity.this.appsLoading) {
                return;
            }
            MainActivity.this.refreshApps();
        }
    }

    /* renamed from: lambda$4$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m35lambda$4$aucommonolauncherMainActivity(ArrayList<App> previous) {
        ArrayList<App> fresh = new ArrayList<>();
        boolean ok;
        try {
            for (LauncherActivityInfo info : this.launcher.getActivityList(null, Process.myUserHandle())) {
                if (!info.getComponentName().getPackageName().equals(getPackageName())) {
                    fresh.add(new App(info));
                }
            }
            fresh.sort(new Comparator<App>() {
                @Override
                public int compare(App a, App b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
            ok = !fresh.isEmpty();
        } catch (Exception e) {
            ok = false;
        }
        boolean changed = ok && !sameCatalogue(previous, fresh);
        this.handler.post(new AnonymousClass4(ok, changed, fresh));
    }

    class HomeSurface extends FrameLayout {
        int direction;
        long startTime;
        float startX;
        float startY;
        boolean swiping;

        HomeSurface() {
            super(MainActivity.this);
            setClickable(true);
        }

        boolean detect(MotionEvent motionEvent) {
            if (MainActivity.this.dragging || motionEvent.getPointerCount() != 1 || (MainActivity.this.drawer != null && MainActivity.this.sidePage == 0)) {
                return false;
            }
            float x = motionEvent.getX() - this.startX;
            float y = motionEvent.getY() - this.startY;
            if (motionEvent.getActionMasked() != 2 || motionEvent.getEventTime() - this.startTime >= ViewConfiguration.getLongPressTimeout()) {
                return false;
            }
            if (Math.abs(x) > MainActivity.this.dp(40.0f) && Math.abs(x) > Math.abs(y) * 1.5f) {
                this.direction = x > 0.0f ? 1 : -1;
                this.swiping = true;
                return true;
            }
            if (MainActivity.this.sidePage != 0 || Math.abs(y) <= MainActivity.this.dp(32.0f) || Math.abs(y) <= Math.abs(x) * 1.5f) {
                return false;
            }
            this.direction = y >= 0.0f ? -2 : 2;
            this.swiping = true;
            return true;
        }

        @Override // android.view.ViewGroup
        public boolean dispatchTouchEvent(MotionEvent motionEvent) {
            BlockField field = MainActivity.this.blockField;
            if (field != null && MainActivity.this.drawer == null && MainActivity.this.wallpaperView.getDrawable() == null) {
                field.touch(motionEvent);
            }
            return super.dispatchTouchEvent(motionEvent);
        }

        @Override // android.view.ViewGroup
        public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
            if (motionEvent.getActionMasked() == 0) {
                this.startX = motionEvent.getX();
                this.startY = motionEvent.getY();
                this.startTime = motionEvent.getEventTime();
                this.swiping = false;
            }
            return detect(motionEvent);
        }

        @Override // android.view.View
        public boolean onTouchEvent(MotionEvent motionEvent) {
            if (!this.swiping) {
                detect(motionEvent);
            }
            if (!this.swiping) {
                return super.onTouchEvent(motionEvent);
            }
            if (motionEvent.getActionMasked() == 1) {
                this.swiping = false;
                if (MainActivity.this.sidePage != 0) {
                    if ((MainActivity.this.sidePage < 0 && this.direction == -1) || (MainActivity.this.sidePage > 0 && this.direction == 1)) {
                        MainActivity.this.m57lambda$63$aucommonolauncherMainActivity();
                    }
                } else if (this.direction == 1) {
                    MainActivity.this.showSidePage(-1);
                } else if (this.direction == -1) {
                    MainActivity.this.showSidePage(1);
                } else if (this.direction == 2) {
                    MainActivity.this.showApps(null);
                } else {
                    MainActivity.this.showRecents();
                }
            } else if (motionEvent.getActionMasked() == 3) {
                this.swiping = false;
            }
            return true;
        }
    }

    /** Every popup gets the launcher's rounded, softly lit panel instead of the stock grey square. */
    class MonoDialog extends AlertDialog.Builder {
        MonoDialog() {
            super(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        }

        @Override
        public AlertDialog create() {
            AlertDialog dialog = super.create();
            GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{-15521731, -16116186});
            panel.setCornerRadius(dp(28.0f));
            panel.setStroke(dp(1.0f), 1148380143);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new InsetDrawable(panel, dp(20.0f), dp(16.0f), dp(20.0f), dp(16.0f)));
                dialog.getWindow().setDimAmount(0.55f);
            }
            final AlertDialog shown = dialog;
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                    MainActivity.this.styleButtons(shown);
                }
            });
            return dialog;
        }
    }

    void styleButtons(AlertDialog dialog) {
        for (int which = -3; which <= -1; which++) {
            android.widget.Button button = dialog.getButton(which);
            if (button != null) {
                button.setTextColor(ACCENT);
                button.setAllCaps(false);
                button.setTextSize(15.0f);
            }
        }
    }

    GradientDrawable bg(int i, int i2) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        if (i == BLACK || i == FOLDER_BACKGROUND) {
            int alpha = Color.alpha(i);
            gradientDrawable.setColors(new int[]{Color.argb(alpha, 30, 73, 96), Color.argb(alpha, 16, 36, 56)});
            gradientDrawable.setOrientation(GradientDrawable.Orientation.TL_BR);
            gradientDrawable.setStroke(dp(1.0f), -2141680989);
        } else {
            gradientDrawable.setColor(i);
        }
        gradientDrawable.setCornerRadius(dp(i2));
        return gradientDrawable;
    }

    TextView text(String str, int i) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(-1);
        textView.setGravity(17);
        textView.setTypeface(Typeface.create("sans-serif", 0));
        textView.setFontFeatureSettings("tnum");
        return textView;
    }

    LinearLayout row() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(17);
        return linearLayout;
    }

    void weighted(LinearLayout linearLayout, View view, float f, int i) {
        if (i >= 0) {
            i = dp(i);
        }
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, i, f);
        layoutParams.setMargins(dp(5.0f), dp(5.0f), dp(5.0f), dp(5.0f));
        linearLayout.addView(view, layoutParams);
    }

    void buildHome() {
        if (this.pet != null) {
            this.pet.dispose();
            this.pet = null;
        }
        this.homeDirty = false;
        invalidateAppDrawer();
        m57lambda$63$aucommonolauncherMainActivity();
        this.iconSize = this.prefs.getInt("iconSize", 25);
        this.root = new HomeSurface();
        getWindow().setBackgroundDrawable(new ColorDrawable(0));
        getWindow().addFlags(1048576);
        this.blockField = new BlockField(this);
        this.blockField.setPaused(!this.resumed);
        this.root.addView(this.blockField, new FrameLayout.LayoutParams(-1, -1));
        this.wallpaperView = new ImageView(this);
        this.wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        this.root.addView(this.wallpaperView, new FrameLayout.LayoutParams(-1, -1));
        loadPhotoBackground();
        this.home = new LinearLayout(this);
        this.home.setOrientation(1);
        this.home.setPadding(dp(15.0f), dp(6.0f), dp(15.0f), dp(8.0f));
        this.root.addView(this.home, new FrameLayout.LayoutParams(-1, -1));
        setContentView(this.root);
        this.root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda59
            @Override // android.view.View.OnApplyWindowInsetsListener
            public final WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                return MainActivity.this.m54lambda$6$aucommonolauncherMainActivity(view, windowInsets);
            }
        });
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(1792);
        }
        if (Build.VERSION.SDK_INT < 35) {
            getWindow().setStatusBarColor(0);
            getWindow().setNavigationBarColor(0);
        }
        int min = Math.min(174, Math.max(110, (((int) (getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density)) - 110) / 4));
        LinearLayout row = row();
        this.clock = new Clock();
        this.clock.setContentDescription("Clock. Tap to open alarms");
        this.clock.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda60
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m63lambda$7$aucommonolauncherMainActivity(view);
            }
        });
        this.clock.setOnLongClickListener(new View.OnLongClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda61
            @Override // android.view.View.OnLongClickListener
            public final boolean onLongClick(View view) {
                return MainActivity.this.m65lambda$8$aucommonolauncherMainActivity(view);
            }
        });
        weighted(row, this.clock, 1.0f, min);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(17);
        linearLayout.setPadding(dp(8.0f), dp(10.0f), dp(8.0f), dp(10.0f));
        linearLayout.setBackground(bg(BLACK, 26));
        this.date = text("", 23);
        this.date.setTypeface(Typeface.create("sans-serif-medium", 0));
        linearLayout.addView(this.date, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        LinearLayout row2 = row();
        this.digitalClock = text(DateFormat.getTimeFormat(this).format(new Date()), 12);
        this.digitalClock.setTextColor(-4598813);
        this.digitalClock.setSingleLine();
        row2.addView(this.digitalClock, new LinearLayout.LayoutParams(-2, dp(30.0f)));
        this.battery = text("", 12);
        this.battery.setTextColor(-4598813);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, dp(30.0f));
        layoutParams.leftMargin = dp(10.0f);
        row2.addView(this.battery, layoutParams);
        linearLayout.addView(row2, new LinearLayout.LayoutParams(-1, dp(34.0f)));
        updateBattery();
        linearLayout.setContentDescription("Date, clock and battery. Tap for calendar");
        linearLayout.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda62
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m66lambda$9$aucommonolauncherMainActivity(view);
            }
        });
        weighted(row, linearLayout, 1.0f, min);
        this.home.addView(row);
        LinearLayout.LayoutParams askLp = new LinearLayout.LayoutParams(-1, dp(46.0f));
        askLp.setMargins(dp(2.0f), dp(8.0f), dp(2.0f), dp(2.0f));
        this.home.addView(askBar(), askLp);
        this.folderBoard = new FolderBoard();
        this.home.addView(this.folderBoard, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        LinearLayout row3 = row();
        for (String str : this.upperShortcuts) {
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(0, dp(100.0f), 1.0f);
            layoutParams2.setMargins(dp(2.0f), dp(5.0f), dp(2.0f), dp(5.0f));
            row3.addView(slot(str, str.equals("Wallet") ? "▱" : str.equals("Impact Hub") ? "IH" : "＋"), layoutParams2);
        }
        this.home.addView(row3);
        LinearLayout row4 = row();
        String[] strArr = {"☎", "▧", "☷", "◎", "◉"};
        for (int i = 0; i < this.dock.length; i++) {
            weighted(row4, slot(this.dock[i], strArr[i]), 1.0f, 77);
        }
        this.home.addView(row4);
        attachPet();
        this.handler.removeCallbacks(this.ticker);
        this.ticker.run();
    }

    /* renamed from: lambda$6$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ WindowInsets m54lambda$6$aucommonolauncherMainActivity(View view, WindowInsets windowInsets) {
        int top;
        int bottom;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            top = bars.top;
            bottom = bars.bottom;
        } else {
            top = windowInsets.getSystemWindowInsetTop();
            bottom = windowInsets.getSystemWindowInsetBottom();
        }
        this.home.setPadding(dp(15.0f), top + dp(6.0f), dp(15.0f), bottom + dp(8.0f));
        return windowInsets;
    }

    /* renamed from: lambda$7$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m63lambda$7$aucommonolauncherMainActivity(View view) {
        safeStart(new Intent("android.intent.action.SHOW_ALARMS"));
    }

    /* renamed from: lambda$8$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ boolean m65lambda$8$aucommonolauncherMainActivity(View view) {
        settings();
        return true;
    }

    /* renamed from: lambda$9$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m66lambda$9$aucommonolauncherMainActivity(View view) {
        safeStart(Intent.makeMainSelectorActivity("android.intent.action.MAIN", "android.intent.category.APP_CALENDAR"));
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r10v0 */
    /* JADX WARN: Type inference failed for: r10v1 */
    /* JADX WARN: Type inference failed for: r10v5, types: [boolean, int] */
    /* JADX WARN: Type inference failed for: r10v6 */
    /* JADX WARN: Type inference failed for: r10v8 */
    /* JADX WARN: Type inference failed for: r1v1, types: [android.view.View, android.widget.TextView] */
    /* JADX WARN: Type inference failed for: r2v0, types: [android.view.View, android.widget.LinearLayout] */
    /* JADX WARN: Type inference failed for: r4v4, types: [android.view.View, android.widget.LinearLayout] */
    View folderTile(String str) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(6.0f), dp(6.0f), dp(6.0f), dp(7.0f));
        tile.setBackground(bg(FOLDER_BACKGROUND, 24));
        LinearLayout icons = new LinearLayout(this);
        icons.setOrientation(LinearLayout.VERTICAL);
        icons.setGravity(Gravity.CENTER);
        Set<String> members = this.prefs.getStringSet("folder:" + str, new HashSet<String>());
        ArrayList<App> preview = new ArrayList<>();
        for (App app : this.apps) {
            if (members.contains(app.id) && preview.size() < 6) {
                preview.add(app);
            }
        }
        if (preview.isEmpty()) {
            icons.addView(text("＋", 24), new LinearLayout.LayoutParams(-1, -1));
        } else {
            for (int start = 0; start < preview.size(); start += 2) {
                LinearLayout row = row();
                for (int k = start; k < Math.min(start + 2, preview.size()); k++) {
                    ImageView imageView = new ImageView(this);
                    imageView.setImageDrawable(appIcon(preview.get(k)));
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(22.0f), -1);
                    lp.setMargins(dp(3.0f), dp(1.0f), dp(3.0f), dp(1.0f));
                    row.addView(imageView, lp);
                }
                icons.addView(row, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            }
        }
        tile.addView(icons, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        TextView label = text(str, 12);
        label.setIncludeFontPadding(false);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.topMargin = dp(4.0f);
        tile.addView(label, labelLp);
        return tile;
    }

    void loadFolders() {
        String string = this.prefs.getString("folderNames", null);
        if (string != null) {
            try {
                JSONArray jSONArray = new JSONArray(string);
                this.folders = new String[jSONArray.length()];
                for (int i = 0; i < jSONArray.length(); i++) {
                    this.folders[i] = jSONArray.getString(i);
                }
            } catch (Exception e) {
            }
        }
        SharedPreferences.Editor edit = this.prefs.edit();
        for (int i2 = 0; i2 < this.folders.length; i2++) {
            if (!this.prefs.contains("folderPos:" + this.folders[i2])) {
                edit.putInt("folderPos:" + this.folders[i2], i2);
            }
        }
        edit.apply();
    }

    void saveFolders() {
        JSONArray jSONArray = new JSONArray();
        for (String str : this.folders) {
            jSONArray.put(str);
        }
        this.prefs.edit().putString("folderNames", jSONArray.toString()).apply();
    }

    int position(String str) {
        for (int i = 0; i < this.folders.length; i++) {
            if (this.folders[i].equals(str)) {
                return this.prefs.getInt("folderPos:" + str, i);
            }
        }
        return 0;
    }

    /* renamed from: lambda$10$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m6lambda$10$aucommonolauncherMainActivity(DialogInterface dialogInterface, int i) {
        if (i == this.folders.length) {
            nameFolder(null);
        } else {
            folderOptions(this.folders[i]);
        }
    }

    void manageFolders() {
        String[] strArr = (String[]) Arrays.copyOf(this.folders, this.folders.length + 1);
        strArr[this.folders.length] = "＋ New folder";
        new MonoDialog().setTitle("Folders").setItems(strArr, new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda58
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.m6lambda$10$aucommonolauncherMainActivity(dialogInterface, i);
            }
        }).show();
    }

    void folderOptions(final String str) {
        new MonoDialog().setTitle(str).setItems(new String[]{"Choose apps", "Rename", "Move to position", "Delete folder"}, new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda37
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.m7lambda$11$aucommonolauncherMainActivity(str, dialogInterface, i);
            }
        }).show();
    }

    /* renamed from: lambda$11$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m7lambda$11$aucommonolauncherMainActivity(final String str, DialogInterface dialogInterface, int i) {
        if (i == 0) {
            editFolder(str);
        }
        if (i == 1) {
            nameFolder(str);
        }
        if (i == 2) {
            String[] strArr = new String[16];
            for (int i2 = 0; i2 < 16; i2++) {
                strArr[i2] = "Row " + ((i2 / 4) + 1) + ", column " + ((i2 % 4) + 1);
            }
            new MonoDialog().setTitle("Move " + str).setItems(strArr, new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda68
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface2, int i3) {
                    MainActivity.this.m8lambda$12$aucommonolauncherMainActivity(str, dialogInterface2, i3);
                }
            }).show();
        }
        if (i == 3) {
            new MonoDialog().setTitle("Delete " + str + "?").setMessage("Only this folder is removed. Its apps stay installed.").setPositiveButton("Delete", new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda69
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface2, int i3) {
                    MainActivity.this.m9lambda$13$aucommonolauncherMainActivity(str, dialogInterface2, i3);
                }
            }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
        }
    }

    /* renamed from: lambda$12$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m8lambda$12$aucommonolauncherMainActivity(String str, DialogInterface dialogInterface, int i) {
        moveFolder(str, i);
        buildHome();
    }

    /* renamed from: lambda$13$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m9lambda$13$aucommonolauncherMainActivity(String str, DialogInterface dialogInterface, int i) {
        ArrayList arrayList = new ArrayList(Arrays.asList(this.folders));
        arrayList.remove(str);
        this.folders = (String[]) arrayList.toArray(new String[0]);
        this.prefs.edit().remove("folder:" + str).remove("folderPos:" + str).apply();
        saveFolders();
        buildHome();
    }

    /* renamed from: lambda$14$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m10lambda$14$aucommonolauncherMainActivity(final AlertDialog alertDialog, final EditText editText, final String str, DialogInterface dialogInterface) {
        styleButtons(alertDialog);
        alertDialog.getButton(-1).setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda63
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m11lambda$15$aucommonolauncherMainActivity(editText, str, alertDialog, view);
            }
        });
    }

    /* renamed from: lambda$15$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m11lambda$15$aucommonolauncherMainActivity(EditText editText, String str, AlertDialog alertDialog, View view) {
        String trim = editText.getText().toString().trim();
        if (trim.isEmpty() || trim.length() > 24) {
            editText.setError("Use 1–24 characters");
            return;
        }
        for (String str2 : this.folders) {
            if (!str2.equals(str) && str2.equalsIgnoreCase(trim)) {
                editText.setError("That folder already exists");
                return;
            }
        }
        ArrayList arrayList = new ArrayList(Arrays.asList(this.folders));
        SharedPreferences.Editor edit = this.prefs.edit();
        if (str == null) {
            HashSet hashSet = new HashSet();
            for (String str3 : this.folders) {
                hashSet.add(Integer.valueOf(position(str3)));
            }
            Iterator<String> it = homeIds().iterator();
            while (it.hasNext()) {
                hashSet.add(Integer.valueOf(this.prefs.getInt("homePos:" + it.next(), 0)));
            }
            int i = 0;
            while (hashSet.contains(Integer.valueOf(i))) {
                i++;
            }
            if (i >= 16) {
                editText.setError("The grid is full. Remove an item first.");
                return;
            } else {
                edit.putInt("folderPos:" + trim, i);
                arrayList.add(trim);
            }
        } else {
            edit.remove("folder:" + str).remove("folderPos:" + str).putStringSet("folder:" + trim, new HashSet(this.prefs.getStringSet("folder:" + str, new HashSet()))).putInt("folderPos:" + trim, position(str));
            arrayList.set(arrayList.indexOf(str), trim);
        }
        edit.apply();
        this.folders = (String[]) arrayList.toArray(new String[0]);
        saveFolders();
        alertDialog.dismiss();
        buildHome();
    }

    void nameFolder(final String str) {
        final EditText editText = new EditText(this);
        editText.setSingleLine();
        editText.setHint("Folder name");
        if (str != null) {
            editText.setText(str);
        }
        final AlertDialog create = new MonoDialog().setTitle(str == null ? "New folder" : "Rename folder").setView(editText).setPositiveButton("Save", (DialogInterface.OnClickListener) null).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).create();
        create.setOnShowListener(new DialogInterface.OnShowListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda14
            @Override // android.content.DialogInterface.OnShowListener
            public final void onShow(DialogInterface dialogInterface) {
                MainActivity.this.m10lambda$14$aucommonolauncherMainActivity(create, editText, str, dialogInterface);
            }
        });
        create.show();
    }

    void moveFolder(String str, int i) {
        int position = position(str);
        SharedPreferences.Editor edit = this.prefs.edit();
        for (String str2 : this.folders) {
            if (!str2.equals(str) && position(str2) == i) {
                edit.putInt("folderPos:" + str2, position);
            }
        }
        for (String str3 : homeIds()) {
            if (this.prefs.getInt("homePos:" + str3, 0) == i) {
                edit.putInt("homePos:" + str3, position);
            }
        }
        edit.putInt("folderPos:" + str, i).apply();
    }

    Set<String> homeIds() {
        return new HashSet(this.prefs.getStringSet("homeApps", new HashSet()));
    }

    void migrateGrid() {
        if (this.prefs.getBoolean("grid4", false)) {
            return;
        }
        HashSet hashSet = new HashSet();
        SharedPreferences.Editor edit = this.prefs.edit();
        for (String str : this.folders) {
            int position = position(str);
            int i = ((position / 3) * 4) + (position % 3);
            do {
                if (i >= 16 || hashSet.contains(Integer.valueOf(i))) {
                    i = 0;
                    while (hashSet.contains(Integer.valueOf(i))) {
                        i++;
                    }
                }
                hashSet.add(Integer.valueOf(i));
                edit.putInt("folderPos:" + str, i);
            } while (i < 16);
            hashSet.add(Integer.valueOf(i));
            edit.putInt("folderPos:" + str, i);
        }
        edit.putBoolean("grid4", true).apply();
    }

    int itemPosition(String str) {
        return str.startsWith("app:") ? this.prefs.getInt("homePos:" + str.substring(4), 0) : position(str.substring(7));
    }

    void deleteFolder(String str) {
        ArrayList arrayList = new ArrayList(Arrays.asList(this.folders));
        arrayList.remove(str);
        this.folders = (String[]) arrayList.toArray(new String[0]);
        this.prefs.edit().remove("folder:" + str).remove("folderPos:" + str).apply();
        saveFolders();
    }

    void removeAppOrigin(AppDrag appDrag, boolean z) {
        SharedPreferences.Editor edit = this.prefs.edit();
        if (appDrag.from != null) {
            HashSet hashSet = new HashSet(this.prefs.getStringSet("folder:" + appDrag.from, new HashSet()));
            hashSet.remove(appDrag.id);
            edit.putStringSet("folder:" + appDrag.from, hashSet);
        }
        if (appDrag.home && !z) {
            Set<String> homeIds = homeIds();
            homeIds.remove(appDrag.id);
            edit.putStringSet("homeApps", homeIds).remove("homePos:" + appDrag.id);
        }
        if (appDrag.slot != null) {
            edit.putString("slot:" + appDrag.slot, "hidden");
        }
        edit.apply();
    }

    /** Dropping an app onto another home screen app puts both in a new folder where the target was. */
    void makeFolder(AppDrag appDrag, String target, int position) {
        String name = "Folder";
        for (int n = 2; Arrays.asList(this.folders).contains(name); n++) {
            name = "Folder " + n;
        }
        HashSet<String> apps = new HashSet<>();
        apps.add(target);
        apps.add(appDrag.id);
        Set<String> homeIds = homeIds();
        homeIds.remove(target);
        this.prefs.edit().putStringSet("homeApps", homeIds).remove("homePos:" + target).remove("homeLabel:" + target)
                .putStringSet("folder:" + name, apps).putInt("folderPos:" + name, position).apply();
        ArrayList<String> names = new ArrayList<>(Arrays.asList(this.folders));
        names.add(name);
        this.folders = names.toArray(new String[0]);
        saveFolders();
        removeAppOrigin(appDrag, false);
        final String created = name;
        this.handler.post(new Runnable() {
            @Override
            public void run() {
                MainActivity.this.nameFolder(created);
            }
        });
    }

    boolean putHome(AppDrag appDrag, int i) {
        int i2 = appDrag.home ? this.prefs.getInt("homePos:" + appDrag.id, -1) : -1;
        String str = null;
        String str2 = null;
        for (String str3 : this.folders) {
            if (position(str3) == i) {
                str2 = str3;
            }
        }
        for (String str4 : homeIds()) {
            if (!str4.equals(appDrag.id) && this.prefs.getInt("homePos:" + str4, 0) == i) {
                str = str4;
            }
        }
        if (str != null && find(appDrag.id) != null) {
            makeFolder(appDrag, str, i);
            return true;
        }
        if (!(str2 == null && str == null) && i2 < 0) {
            toast("Drop onto an empty space or into a folder");
            return false;
        }
        SharedPreferences.Editor edit = this.prefs.edit();
        if (str2 != null) {
            edit.putInt("folderPos:" + str2, i2);
        }
        if (str != null) {
            edit.putInt("homePos:" + str, i2);
        }
        Set<String> homeIds = homeIds();
        homeIds.add(appDrag.id);
        edit.putStringSet("homeApps", homeIds).putInt("homePos:" + appDrag.id, i).putString("homeLabel:" + appDrag.id, appDrag.label).apply();
        removeAppOrigin(appDrag, true);
        return true;
    }

    View homeApp(final String str) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(17);
        App find = find(str);
        final String string = this.prefs.getString("homeLabel:" + str, find == null ? "App" : find.label);
        if (find != null) {
            ImageView imageView = new ImageView(this);
            imageView.setImageDrawable(appIcon(find));
            linearLayout.addView(imageView, new LinearLayout.LayoutParams(dp(36.0f), dp(36.0f)));
        } else {
            linearLayout.addView(text(string.substring(0, Math.min(2, string.length())), 22));
        }
        TextView text = text(string, 11);
        text.setMaxLines(1);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setShadowLayer(dp(2.0f), 0.0f, dp(1.0f), -16777216);
        linearLayout.addView(text, new LinearLayout.LayoutParams(-1, dp(23.0f)));
        linearLayout.setContentDescription(String.valueOf(string) + ". Hold to move");
        linearLayout.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda66
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m12lambda$16$aucommonolauncherMainActivity(str, view);
            }
        });
        linearLayout.setOnLongClickListener(new View.OnLongClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda67
            @Override // android.view.View.OnLongClickListener
            public final boolean onLongClick(View view) {
                return MainActivity.this.m13lambda$17$aucommonolauncherMainActivity(str, string, view);
            }
        });
        return linearLayout;
    }

    /* renamed from: lambda$16$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m12lambda$16$aucommonolauncherMainActivity(String str, View view) {
        launchValue(str);
    }

    /* renamed from: lambda$17$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ boolean m13lambda$17$aucommonolauncherMainActivity(String str, String str2, View view) {
        AppDrag appDrag = new AppDrag(str, null);
        appDrag.home = true;
        appDrag.label = str2;
        beginDrag(view, appDrag);
        return true;
    }

    class FolderDrag {
        String name;

        FolderDrag(String str) {
            this.name = str;
        }
    }

    void launchValue(String str) {
        if (str.startsWith("url:")) {
            safeStart(new Intent("android.intent.action.VIEW", Uri.parse(str.substring(4))));
            return;
        }
        if (str.startsWith("pin:")) {
            String[] split = str.split(":", 3);
            try {
                this.launcher.startShortcut(split[1], split[2], null, null, Process.myUserHandle());
                return;
            } catch (Exception e) {
                toast("Shortcut unavailable");
                return;
            }
        }
        App find = find(str);
        if (find != null) {
            launch(find);
        } else {
            toast("App is no longer available");
        }
    }

    class FolderBoard extends FrameLayout {
        Paint gridPaint;
        int rows;
        boolean showGrid;

        FolderBoard() {
            super(MainActivity.this);
            this.rows = 4;
            this.gridPaint = new Paint(3);
            setWillNotDraw(false);
            setOnLongClickListener(new View.OnLongClickListener() { // from class: au.com.mono.launcher.MainActivity$FolderBoard$$ExternalSyntheticLambda0
                @Override // android.view.View.OnLongClickListener
                public final boolean onLongClick(View view) {
                    return MainActivity.FolderBoard.this.m69lambda$0$aucommonolauncherMainActivity$FolderBoard(view);
                }
            });
            for (final String str : MainActivity.this.folders) {
                View folderTile = MainActivity.this.folderTile(str);
                folderTile.setTag("folder:" + str);
                folderTile.setContentDescription(String.valueOf(str) + " folder. Hold and drag to move");
                folderTile.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$FolderBoard$$ExternalSyntheticLambda1
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.FolderBoard.this.m70lambda$1$aucommonolauncherMainActivity$FolderBoard(str, view);
                    }
                });
                folderTile.setOnLongClickListener(new View.OnLongClickListener() { // from class: au.com.mono.launcher.MainActivity$FolderBoard$$ExternalSyntheticLambda2
                    @Override // android.view.View.OnLongClickListener
                    public final boolean onLongClick(View view) {
                        return MainActivity.FolderBoard.this.m71lambda$2$aucommonolauncherMainActivity$FolderBoard(str, view);
                    }
                });
                MainActivity.this.installFolderDrop(folderTile, str);
                addView(folderTile, new FrameLayout.LayoutParams(1, 1));
            }
            for (String str2 : MainActivity.this.homeIds()) {
                View homeApp = MainActivity.this.homeApp(str2);
                homeApp.setTag("app:" + str2);
                addView(homeApp, new FrameLayout.LayoutParams(1, 1));
            }
            setOnDragListener(new View.OnDragListener() { // from class: au.com.mono.launcher.MainActivity$FolderBoard$$ExternalSyntheticLambda3
                @Override // android.view.View.OnDragListener
                public final boolean onDrag(View view, DragEvent dragEvent) {
                    return MainActivity.FolderBoard.this.m72lambda$3$aucommonolauncherMainActivity$FolderBoard(view, dragEvent);
                }
            });
        }

        /* renamed from: lambda$0$au-com-mono-launcher-MainActivity$FolderBoard, reason: not valid java name */
        /* synthetic */ boolean m69lambda$0$aucommonolauncherMainActivity$FolderBoard(View view) {
            MainActivity.this.settings();
            return true;
        }

        /* renamed from: lambda$1$au-com-mono-launcher-MainActivity$FolderBoard, reason: not valid java name */
        /* synthetic */ void m70lambda$1$aucommonolauncherMainActivity$FolderBoard(String str, View view) {
            MainActivity.this.showFolder(str);
        }

        /* renamed from: lambda$2$au-com-mono-launcher-MainActivity$FolderBoard, reason: not valid java name */
        /* synthetic */ boolean m71lambda$2$aucommonolauncherMainActivity$FolderBoard(String str, View view) {
            MainActivity.this.startFolderDrag(view, str);
            return true;
        }

        /* renamed from: lambda$3$au-com-mono-launcher-MainActivity$FolderBoard, reason: not valid java name */
        /* synthetic */ boolean m72lambda$3$aucommonolauncherMainActivity$FolderBoard(View view, DragEvent dragEvent) {
            if (!(dragEvent.getLocalState() instanceof FolderDrag) && !(dragEvent.getLocalState() instanceof AppDrag)) {
                return false;
            }
            if (dragEvent.getAction() == 1) {
                this.showGrid = true;
                invalidate();
            }
            if (dragEvent.getAction() != 3) {
                if (dragEvent.getAction() == 4) {
                    this.showGrid = false;
                    invalidate();
                }
                return true;
            }
            int drop = geometry(getWidth(), getHeight()).drop(dragEvent.getX(), dragEvent.getY());
            if (!(dragEvent.getLocalState() instanceof FolderDrag)) {
                return MainActivity.this.putHome((AppDrag) dragEvent.getLocalState(), drop);
            }
            MainActivity.this.moveFolder(((FolderDrag) dragEvent.getLocalState()).name, drop);
            return true;
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (this.showGrid) {
                this.gridPaint.setStyle(Paint.Style.STROKE);
                this.gridPaint.setStrokeWidth(MainActivity.this.dp(1.0f));
                this.gridPaint.setColor(-1720523793);
                float width = getWidth() / 4.0f;
                float height = getHeight() / this.rows;
                for (int i = 0; i < this.rows * 4; i++) {
                    canvas.drawRoundRect(((i % 4) * width) + MainActivity.this.dp(3.0f), ((i / 4) * height) + MainActivity.this.dp(3.0f), (((i % 4) + 1) * width) - MainActivity.this.dp(3.0f), (((i / 4) + 1) * height) - MainActivity.this.dp(3.0f), MainActivity.this.dp(15.0f), MainActivity.this.dp(15.0f), this.gridPaint);
                }
            }
        }

        FolderGrid geometry(int i, int i2) {
            return new FolderGrid(i, i2, this.rows, MainActivity.this.dp(5.0f), MainActivity.this.dp(4.0f), MainActivity.this.dp(100.0f));
        }

        @Override // android.widget.FrameLayout, android.view.View
        protected void onMeasure(int i, int i2) {
            int size = View.MeasureSpec.getSize(i);
            int size2 = View.MeasureSpec.getSize(i2);
            setMeasuredDimension(size, size2);
            FolderGrid geometry = geometry(size, size2);
            for (int i3 = 0; i3 < getChildCount(); i3++) {
                View childAt = getChildAt(i3);
                int itemPosition = MainActivity.this.itemPosition((String) childAt.getTag());
                childAt.measure(View.MeasureSpec.makeMeasureSpec(geometry.tileWidth(itemPosition), 1073741824), View.MeasureSpec.makeMeasureSpec(geometry.tileHeight(itemPosition), 1073741824));
            }
        }

        @Override // android.widget.FrameLayout, android.view.ViewGroup, android.view.View
        protected void onLayout(boolean z, int i, int i2, int i3, int i4) {
            FolderGrid geometry = geometry(i3 - i, i4 - i2);
            for (int i5 = 0; i5 < getChildCount(); i5++) {
                View childAt = getChildAt(i5);
                int itemPosition = MainActivity.this.itemPosition((String) childAt.getTag());
                int left = geometry.left(itemPosition);
                int pVar = geometry.top(itemPosition);
                childAt.layout(left, pVar, childAt.getMeasuredWidth() + left, childAt.getMeasuredHeight() + pVar);
            }
        }
    }

    void startFolderDrag(View view, String str) {
        beginDrag(view, new FolderDrag(str));
    }

    void loadPhotoBackground() {
        final String string = this.prefs.getString("wallpaper", "");
        if (string.isEmpty()) {
            this.wallpaperView.setImageDrawable(null);
            return;
        }
        if (string.equals(this.wallpaperLoaded) && this.wallpaperBitmap != null) {
            this.wallpaperView.setImageBitmap(this.wallpaperBitmap);
        } else {
            if (string.equals(this.wallpaperLoading)) {
                return;
            }
            this.wallpaperLoading = string;
            this.wallpaperWorker.execute(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda40
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m14lambda$18$aucommonolauncherMainActivity(string);
                }
            });
        }
    }

    /* renamed from: lambda$18$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m14lambda$18$aucommonolauncherMainActivity(final String str) {
        AtomicFile atomicFile = new AtomicFile(new File(getFilesDir(), "wallpaper-v2.cache"));
        Bitmap decoded;
        try {
            decoded = decodePhoto(Uri.parse(str));
        } catch (Exception e) {
            decoded = null;
        }
        if (decoded != null) {
            FileOutputStream out = null;
            try {
                out = atomicFile.startWrite();
                DataOutputStream data = new DataOutputStream(out);
                data.writeUTF(str);
                data.flush();
                if (!decoded.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw new IOException("Could not save photo");
                }
                atomicFile.finishWrite(out);
            } catch (Exception e) {
                if (out != null) {
                    atomicFile.failWrite(out);
                }
            }
        } else {
            try (DataInputStream in = new DataInputStream(atomicFile.openRead())) {
                if (str.equals(in.readUTF())) {
                    decoded = BitmapFactory.decodeStream(in);
                }
            } catch (Exception e) {
                decoded = null;
            }
        }
        final Bitmap bitmap = decoded;
        this.handler.post(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda36
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m15lambda$19$aucommonolauncherMainActivity(str, bitmap);
            }
        });
    }

    /* renamed from: lambda$19$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m15lambda$19$aucommonolauncherMainActivity(String str, Bitmap bitmap) {
        if (isDestroyed()) {
            return;
        }
        if (str.equals(this.wallpaperLoading)) {
            this.wallpaperLoading = "";
        }
        if (str.equals(this.prefs.getString("wallpaper", ""))) {
            if (bitmap == null) {
                toast("Please choose your wallpaper photo again in settings.");
                return;
            }
            this.wallpaperBitmap = bitmap;
            this.wallpaperLoaded = str;
            if (this.wallpaperView != null) {
                this.wallpaperView.setImageBitmap(bitmap);
            }
        }
    }

    static /* synthetic */ void lambda$20(ImageDecoder imageDecoder, ImageDecoder.ImageInfo imageInfo, ImageDecoder.Source source) {
        imageDecoder.setAllocator(1);
        int width = imageInfo.getSize().getWidth();
        int height = imageInfo.getSize().getHeight();
        float min = Math.min(1.0f, 1600.0f / Math.max(width, height));
        imageDecoder.setTargetSize(Math.max(1, (int) (width * min)), Math.max(1, (int) (height * min)));
    }

    Bitmap decodePhoto(Uri uri) throws IOException {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri), new ImageDecoder.OnHeaderDecodedListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda2
                @Override // android.graphics.ImageDecoder.OnHeaderDecodedListener
                public final void onHeaderDecoded(ImageDecoder imageDecoder, ImageDecoder.ImageInfo imageInfo, ImageDecoder.Source source) {
                    MainActivity.lambda$20(imageDecoder, imageInfo, source);
                }
            });
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        int i = 1;
        options.inJustDecodeBounds = true;
        try {
            InputStream openInputStream = getContentResolver().openInputStream(uri);
            try {
                BitmapFactory.decodeStream(openInputStream, null, options);
                if (openInputStream != null) {
                    openInputStream.close();
                }
                while (true) {
                    options.inSampleSize = i;
                    if (Math.max(options.outWidth, options.outHeight) / options.inSampleSize <= 1600) {
                        break;
                    }
                    i = options.inSampleSize * 2;
                }
                options.inJustDecodeBounds = false;
                try {
                    openInputStream = getContentResolver().openInputStream(uri);
                    try {
                        return BitmapFactory.decodeStream(openInputStream, null, options);
                    } finally {
                        if (openInputStream != null) {
                            openInputStream.close();
                        }
                    }
                } finally {
                }
            } finally {
                if (openInputStream != null) {
                    openInputStream.close();
                }
            }
        } finally {
        }
    }

    String folderPreview(String str) {
        int size = this.prefs.getStringSet("folder:" + str, new HashSet()).size();
        return size == 0 ? "＋" : String.valueOf(size) + " apps";
    }

    boolean isUpperShortcut(String str) {
        return Arrays.asList(this.upperShortcuts).contains(str);
    }

    String[] shortcutKeys() {
        String[] strArr = (String[]) Arrays.copyOf(this.upperShortcuts, this.upperShortcuts.length + this.dock.length);
        System.arraycopy(this.dock, 0, strArr, this.upperShortcuts.length, this.dock.length);
        return strArr;
    }

    /* renamed from: lambda$21$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m17lambda$21$aucommonolauncherMainActivity(String str, View view) {
        chooseSlot(str);
    }

    /* renamed from: lambda$22$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m18lambda$22$aucommonolauncherMainActivity(String str, View view) {
        launchSlot(str);
    }

    /* renamed from: lambda$23$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ boolean m19lambda$23$aucommonolauncherMainActivity(String str, View view) {
        String string = this.prefs.getString("slot:" + str, "");
        App guess = string.isEmpty() ? guess(str) : find(string);
        if (guess != null) {
            string = guess.id;
        }
        if (string.isEmpty()) {
            chooseSlot(str);
            return true;
        }
        AppDrag appDrag = new AppDrag(string, null);
        appDrag.slot = str;
        appDrag.label = this.prefs.getString("slotLabel:" + str, str);
        beginDrag(view, appDrag);
        return true;
    }

    /* JADX WARN: Multi-variable type inference failed */
    View slot(final String str, String str2) {
        FrameLayout.LayoutParams layoutParams;
        View textView;
        String string = this.prefs.getString("slot:" + str, "");
        if (string.equals("hidden") || (str.startsWith("Shortcut ") && string.isEmpty())) {
            TextView text = text(isUpperShortcut(str) ? "＋" : "", 24);
            text.setTextColor(-4598813);
            text.setShadowLayer(dp(2.0f), 0.0f, dp(1.0f), -16777216);
            text.setContentDescription("Empty " + str + " position. Tap to choose an app or drop an app here");
            text.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda17
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m17lambda$21$aucommonolauncherMainActivity(str, view);
                }
            });
            installDockDrop(text, str, true);
            return text;
        }
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(17);
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackground(bg(BLACK, str.equals("Impact Hub") ? 24 : 60));
        int i = isUpperShortcut(str) ? 40 : 46;
        App find = find(this.prefs.getString("slot:" + str, ""));
        if (find == null && this.prefs.getString("slot:" + str, "").isEmpty()) {
            find = guess(str);
        }
        if (find != null) {
            ImageView imageView = new ImageView(this);
            imageView.setImageDrawable(appIcon(find));
            int dp = dp((i - this.iconSize) / 2.0f);
            imageView.setPadding(dp, dp, dp, dp);
            layoutParams = new FrameLayout.LayoutParams(-1, -1);
            textView = imageView;
        } else {
            TextView text2 = text(str2, this.iconSize);
            text2.setTypeface(Typeface.DEFAULT_BOLD);
            layoutParams = new FrameLayout.LayoutParams(-1, -1);
            textView = text2;
        }
        frameLayout.addView(textView, layoutParams);
        float f = i;
        linearLayout.addView(frameLayout, new LinearLayout.LayoutParams(dp(f), dp(f)));
        TextView text3 = text(this.prefs.getString("slotLabel:" + str, str), 10);
        text3.setTextColor(-1);
        text3.setShadowLayer(dp(2.0f), 0.0f, dp(1.0f), -16777216);
        text3.setMaxLines(1);
        text3.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout.addView(text3, new LinearLayout.LayoutParams(-1, -2));
        linearLayout.setContentDescription(String.valueOf(str) + ". Long press to change");
        linearLayout.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda18
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m18lambda$22$aucommonolauncherMainActivity(str, view);
            }
        });
        linearLayout.setOnLongClickListener(new View.OnLongClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda19
            @Override // android.view.View.OnLongClickListener
            public final boolean onLongClick(View view) {
                return MainActivity.this.m19lambda$23$aucommonolauncherMainActivity(str, view);
            }
        });
        installDockDrop(linearLayout, str, false);
        return linearLayout;
    }

    String slotValue(String str) {
        String string = this.prefs.getString("slot:" + str, "");
        if (!string.isEmpty()) {
            return string;
        }
        App guess = guess(str);
        return guess == null ? "hidden" : guess.id;
    }

    void installDockDrop(View view, final String str, final boolean z) {
        view.setOnDragListener(new View.OnDragListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda13
            @Override // android.view.View.OnDragListener
            public final boolean onDrag(View view2, DragEvent dragEvent) {
                return MainActivity.this.m20lambda$24$aucommonolauncherMainActivity(z, str, view2, dragEvent);
            }
        });
    }

    /* renamed from: lambda$24$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ boolean m20lambda$24$aucommonolauncherMainActivity(boolean z, String str, View view, DragEvent dragEvent) {
        if (!(dragEvent.getLocalState() instanceof AppDrag)) {
            return false;
        }
        switch (dragEvent.getAction()) {
            case 1:
                if (z) {
                    ((TextView) view).setText("＋");
                    view.setBackground(bg(1714511736, 22));
                }
                return true;
            case 2:
            default:
                return true;
            case 3:
                AppDrag appDrag = (AppDrag) dragEvent.getLocalState();
                if (str.equals(appDrag.slot)) {
                    return true;
                }
                String slotValue = slotValue(str);
                String string = this.prefs.getString("slotLabel:" + str, str);
                int i = appDrag.home ? this.prefs.getInt("homePos:" + appDrag.id, 0) : -1;
                removeAppOrigin(appDrag, false);
                SharedPreferences.Editor putString = this.prefs.edit().putString("slot:" + str, appDrag.id).putString("slotLabel:" + str, appDrag.label);
                if (appDrag.slot != null) {
                    putString.putString("slot:" + appDrag.slot, slotValue).putString("slotLabel:" + appDrag.slot, string);
                } else if (appDrag.home && !slotValue.equals("hidden") && !slotValue.equals(appDrag.id)) {
                    Set<String> homeIds = homeIds();
                    homeIds.add(slotValue);
                    putString.putStringSet("homeApps", homeIds).putInt("homePos:" + slotValue, i).putString("homeLabel:" + slotValue, string);
                }
                putString.apply();
                return true;
            case 4:
                view.setBackground(null);
                if (z) {
                    ((TextView) view).setText(isUpperShortcut(str) ? "＋" : "");
                }
                return true;
            case 5:
                view.setBackground(bg(-1722105905, 22));
                return true;
            case 6:
                view.setBackground(z ? bg(1714511736, 22) : null);
                return true;
        }
    }

    void recordRecent(String str) {
        this.prefs.edit().putLong("recent:" + str, System.currentTimeMillis()).apply();
    }

    boolean hasUsageAccess() {
        return ((AppOpsManager) getSystemService(AppOpsManager.class)).checkOpNoThrow("android:get_usage_stats", Process.myUid(), getPackageName()) == 0;
    }

    ArrayList<App> recentApps() {
        ArrayList arrayList = new ArrayList();
        final HashMap hashMap = new HashMap();
        HashSet hashSet = new HashSet();
        Iterator<App> it = this.apps.iterator();
        while (it.hasNext()) {
            App next = it.next();
            String packageName = ComponentName.unflattenFromString(next.id).getPackageName();
            long max = Math.max(this.prefs.getLong("recent:" + next.id, 0L), this.usageTimes.containsKey(packageName) ? this.usageTimes.get(packageName).longValue() : 0L);
            if (max > 0) {
                arrayList.add(next);
                hashMap.put(next.id, Long.valueOf(max));
            }
        }
        arrayList.sort(new Comparator() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda64
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Long.compare(((Long) hashMap.get(((MainActivity.App) obj2).id)).longValue(), ((Long) hashMap.get(((MainActivity.App) obj).id)).longValue());
                return compare;
            }
        });
        ArrayList<App> arrayList2 = new ArrayList<>();
        Iterator it2 = arrayList.iterator();
        while (it2.hasNext()) {
            App app = (App) it2.next();
            if (hashSet.add(ComponentName.unflattenFromString(app.id).getPackageName())) {
                arrayList2.add(app);
            }
            if (arrayList2.size() == 24) {
                break;
            }
        }
        return arrayList2;
    }

    /* renamed from: lambda$26$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m21lambda$26$aucommonolauncherMainActivity(final View view) {
        Long l;
        final HashMap hashMap = new HashMap();
        try {
            long currentTimeMillis = System.currentTimeMillis();
            UsageEvents queryEvents = ((UsageStatsManager) getSystemService(UsageStatsManager.class)).queryEvents(currentTimeMillis - 604800000, currentTimeMillis);
            UsageEvents.Event event = new UsageEvents.Event();
            if (queryEvents != null) {
                while (queryEvents.hasNextEvent()) {
                    queryEvents.getNextEvent(event);
                    if (event.getEventType() == 1 && !getPackageName().equals(event.getPackageName()) && ((l = (Long) hashMap.get(event.getPackageName())) == null || event.getTimeStamp() > l.longValue())) {
                        hashMap.put(event.getPackageName(), Long.valueOf(event.getTimeStamp()));
                    }
                }
            }
        } catch (Exception e) {
        }
        this.handler.post(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda38
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m22lambda$27$aucommonolauncherMainActivity(hashMap, view);
            }
        });
    }

    /* renamed from: lambda$27$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m22lambda$27$aucommonolauncherMainActivity(Map map, View view) {
        if (isDestroyed()) {
            return;
        }
        this.usageTimes.clear();
        this.usageTimes.putAll(map);
        if (this.drawer != view || this.drawerRefresh == null) {
            return;
        }
        this.drawerRefresh.run();
    }

    void showRecents() {
        if (!hasUsageAccess()) {
            this.usageTimes.clear();
        }
        showAppList("Recent apps", recentApps(), null, null);
        final View view = this.drawer;
        if (hasUsageAccess()) {
            this.appWorker.execute(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda70
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m21lambda$26$aucommonolauncherMainActivity(view);
                }
            });
        }
    }

    void focusSearch(final EditText editText) {
        editText.setVisibility(0);
        editText.requestFocus();
        editText.postDelayed(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda50
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m23lambda$28$aucommonolauncherMainActivity(editText);
            }
        }, 60L);
    }

    /* renamed from: lambda$28$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m23lambda$28$aucommonolauncherMainActivity(EditText editText) {
        if (isDestroyed() || !editText.isAttachedToWindow() || !editText.hasFocus() || this.drawer == null || this.dragging || this.drawer.getVisibility() != 0) {
            return;
        }
        ((InputMethodManager) getSystemService("input_method")).showSoftInput(editText, 1);
    }

    App find(String str) {
        Iterator<App> it = this.apps.iterator();
        while (it.hasNext()) {
            App next = it.next();
            if (next.id.equals(str)) {
                return next;
            }
        }
        return null;
    }

    App guess(String str) {
        for (String str2 : str.equals("Wallet") ? new String[]{"wallet"} : str.equals("Browser") ? new String[]{"internet", "chrome", "browser"} : new String[]{str.toLowerCase(Locale.ROOT)}) {
            Iterator<App> it = this.apps.iterator();
            while (it.hasNext()) {
                App next = it.next();
                if (next.label.toLowerCase(Locale.ROOT).equals(str2)) {
                    return next;
                }
            }
        }
        return null;
    }

    void launch(App app) {
        try {
            this.launcher.startMainActivity(ComponentName.unflattenFromString(app.id), Process.myUserHandle(), null, null);
            recordRecent(app.id);
        } catch (Exception e) {
            toast("App unavailable. Long press to choose another.");
        }
    }

    void launchSlot(String str) {
        String string = this.prefs.getString("slot:" + str, "");
        if (string.startsWith("url:")) {
            safeStart(new Intent("android.intent.action.VIEW", Uri.parse(string.substring(4))));
            return;
        }
        if (string.startsWith("pin:")) {
            String[] split = string.split(":", 3);
            try {
                this.launcher.startShortcut(split[1], split[2], null, null, Process.myUserHandle());
                return;
            } catch (Exception e) {
                toast("Shortcut unavailable. Add it again from your browser.");
                return;
            }
        }
        App guess = string.isEmpty() ? guess(str) : find(string);
        if (guess != null) {
            launch(guess);
        } else {
            chooseSlot(str);
        }
    }

    void chooseSlot(final String str) {
        new MonoDialog().setTitle(String.valueOf(str) + " shortcut").setItems(new String[]{"Choose an app", "Use a website address"}, new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda20
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.m24lambda$29$aucommonolauncherMainActivity(str, dialogInterface, i);
            }
        }).show();
    }

    /* renamed from: lambda$29$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m24lambda$29$aucommonolauncherMainActivity(final String str, DialogInterface dialogInterface, int i) {
        if (i == 0) {
            showApps(new Pick() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda44
                @Override // au.com.mono.launcher.MainActivity.Pick
                public final void chosen(MainActivity.App app) {
                    MainActivity.this.m26lambda$30$aucommonolauncherMainActivity(str, app);
                }
            });
            return;
        }
        final EditText editText = new EditText(this);
        editText.setSingleLine();
        editText.setHint("https://your-website.com");
        editText.setInputType(17);
        String string = this.prefs.getString("slot:" + str, "");
        if (string.startsWith("url:")) {
            editText.setText(string.substring(4));
        }
        new MonoDialog().setTitle("Website for " + str).setView(editText).setPositiveButton("Save", new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda45
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface2, int i2) {
                MainActivity.this.m27lambda$31$aucommonolauncherMainActivity(editText, str, dialogInterface2, i2);
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    /* renamed from: lambda$30$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m26lambda$30$aucommonolauncherMainActivity(String str, App app) {
        this.prefs.edit().putString("slot:" + str, app.id).putString("slotLabel:" + str, app.label).apply();
        buildHome();
    }

    /* renamed from: lambda$31$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m27lambda$31$aucommonolauncherMainActivity(EditText editText, String str, DialogInterface dialogInterface, int i) {
        String trim = editText.getText().toString().trim();
        if (!trim.startsWith("https://") && !trim.startsWith("http://")) {
            trim = "https://" + trim;
        }
        Uri parse = Uri.parse(trim);
        if (parse.getHost() == null || parse.getHost().isEmpty()) {
            toast("Enter a valid website address");
        } else {
            this.prefs.edit().putString("slot:" + str, "url:" + trim).putString("slotLabel:" + str, str).apply();
            buildHome();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: hideDrawer, reason: merged with bridge method [inline-methods] */
    public void m57lambda$63$aucommonolauncherMainActivity() {
        if (this.pet != null) {
            this.pet.setVisibility(0);
            this.pet.setActive(this.resumed);
        }
        this.sidePage = 0;
        this.focusClock = null;
        if (this.drawer != null) {
            ((InputMethodManager) getSystemService("input_method")).hideSoftInputFromWindow(this.drawer.getWindowToken(), 0);
            if (this.root != null) {
                this.root.removeView(this.drawer);
            }
            if (this.home != null) {
                this.home.setVisibility(0);
            }
            this.drawer = null;
            this.drawerRefresh = null;
        }
        if (this.homeDirty) {
            this.handler.post(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m28lambda$32$aucommonolauncherMainActivity();
                }
            });
        }
    }

    /* renamed from: lambda$32$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m28lambda$32$aucommonolauncherMainActivity() {
        if (!isDestroyed() && this.resumed && this.drawer == null && !this.dragging && this.homeDirty) {
            buildHome();
        }
    }

    void showApps(Pick pick) {
        if (pick != null || this.cachedApps == null) {
            showAppList(pick == null ? "All apps" : "Choose an app", new ArrayList<>(this.apps), pick, null);
            return;
        }
        m57lambda$63$aucommonolauncherMainActivity();
        this.drawer = this.cachedApps;
        this.home.setVisibility(4);
        if (this.pet != null) {
            this.pet.setVisibility(4);
            this.pet.setActive(false);
        }
        this.drawerRefresh = this.cachedRefresh;
        if (this.cachedSearch != null) {
            this.cachedSearch.setText("");
            this.cachedSearch.clearFocus();
        }
        if (this.drawerRefresh != null) {
            this.drawerRefresh.run();
        }
        this.root.addView(this.drawer, new FrameLayout.LayoutParams(-1, -1));
        this.drawer.requestFocus();
        if (this.cachedSearch != null) {
            focusSearch(this.cachedSearch);
        }
    }

    void showFolder(String str) {
        Set<String> stringSet = this.prefs.getStringSet("folder:" + str, new HashSet());
        ArrayList<App> arrayList = new ArrayList<>();
        Iterator<App> it = this.apps.iterator();
        while (it.hasNext()) {
            App next = it.next();
            if (stringSet.contains(next.id)) {
                arrayList.add(next);
            }
        }
        showAppList(str, arrayList, null, str);
    }

    void showAppList(String str, final ArrayList<App> arrayList, final Pick pick, final String str2) {
        String str3;
        boolean z = str2 == null && str.equals("Recent apps");
        m57lambda$63$aucommonolauncherMainActivity();
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setFocusableInTouchMode(true);
        linearLayout.setPadding(dp(18.0f), this.home.getPaddingTop(), dp(18.0f), this.home.getPaddingBottom());
        linearLayout.setBackgroundColor(-435609549);
        LinearLayout row = row();
        TextView text = text(str, 20);
        text.setGravity(8388627);
        row.addView(text, new LinearLayout.LayoutParams(0, dp(48.0f), 1.0f));
        TextView text2 = text("⌕", 28);
        text2.setContentDescription("Search apps");
        row.addView(text2, new LinearLayout.LayoutParams(dp(48.0f), dp(48.0f)));
        linearLayout.addView(row);
        if (z && !hasUsageAccess()) {
            TextView text3 = text("Include apps opened elsewhere", 13);
            text3.setPadding(0, dp(7.0f), 0, dp(7.0f));
            text3.setTextColor(ACCENT);
            text3.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda51
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m29lambda$33$aucommonolauncherMainActivity(view);
                }
            });
            linearLayout.addView(text3);
        }
        final EditText editText = new EditText(this);
        editText.setSingleLine();
        editText.setTextColor(-1);
        editText.setHintTextColor(-4598813);
        editText.setHint("Search apps");
        editText.setVisibility(8);
        linearLayout.addView(editText);
        text2.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda52
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                MainActivity.this.m31lambda$35$aucommonolauncherMainActivity(editText, view);
            }
        });
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        final ListView listView = new ListView(this);
        listView.setDivider(null);
        listView.setVerticalScrollBarEnabled(false);
        listView.setCacheColorHint(0);
        linearLayout2.addView(listView, new LinearLayout.LayoutParams(0, -1, 1.0f));
        final ArrayList arrayList2 = new ArrayList(arrayList);
        final TextView text4 = text("#\nA\nB\nC\nD\nE\nF\nG\nH\nI\nJ\nK\nL\nM\nN\nO\nP\nQ\nR\nS\nT\nU\nV\nW\nX\nY\nZ", 10);
        text4.setTextColor(ACCENT);
        text4.setGravity(17);
        text4.setContentDescription("Alphabet quick scroll");
        if (z) {
            text4.setVisibility(8);
        }
        linearLayout2.addView(text4, new LinearLayout.LayoutParams(dp(24.0f), -1));
        text4.setOnTouchListener(new View.OnTouchListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda53
            @Override // android.view.View.OnTouchListener
            public final boolean onTouch(View view, MotionEvent motionEvent) {
                return MainActivity.lambda$36(text4, arrayList2, listView, view, motionEvent);
            }
        });
        if (str2 == null) {
            str3 = this.appsLoading ? "Loading apps…" : z ? "Apps you open will appear here" : "No matching apps";
        } else {
            str3 = "No apps here yet. Drag apps here from All apps, or tap Edit folder.";
        }
        final TextView text5 = text(str3, 16);
        text5.setPadding(dp(18.0f), dp(24.0f), dp(18.0f), dp(24.0f));
        linearLayout.addView(text5);
        listView.setEmptyView(text5);
        final AnonymousClass5 anonymousClass5 = new AnonymousClass5(arrayList2, str2);
        listView.setAdapter((ListAdapter) anonymousClass5);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda54
            @Override // android.widget.AdapterView.OnItemClickListener
            public final void onItemClick(AdapterView adapterView, View view, int i, long j) {
                MainActivity.this.m32lambda$37$aucommonolauncherMainActivity(arrayList2, pick, adapterView, view, i, j);
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda55
            @Override // android.widget.AdapterView.OnItemLongClickListener
            public final boolean onItemLongClick(AdapterView adapterView, View view, int i, long j) {
                return MainActivity.this.m33lambda$38$aucommonolauncherMainActivity(pick, arrayList2, str2, adapterView, view, i, j);
            }
        });
        editText.addTextChangedListener(new TextWatcher() { // from class: au.com.mono.launcher.MainActivity.6
            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable editable) {
            }

            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                arrayList2.clear();
                Iterator it = arrayList.iterator();
                while (it.hasNext()) {
                    App app = (App) it.next();
                    if (app.label.toLowerCase(Locale.ROOT).contains(charSequence.toString().toLowerCase(Locale.ROOT))) {
                        arrayList2.add(app);
                    }
                }
                anonymousClass5.notifyDataSetChanged();
            }
        });
        if (str2 != null) {
            Button button = new Button(this);
            button.setText("Folder options");
            button.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda56
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    MainActivity.this.m34lambda$39$aucommonolauncherMainActivity(str2, view);
                }
            });
            linearLayout.addView(button);
        }
        this.drawer = linearLayout;
        this.home.setVisibility(4);
        if (this.pet != null) {
            this.pet.setVisibility(4);
            this.pet.setActive(false);
        }
        this.root.addView(linearLayout, new FrameLayout.LayoutParams(-1, -1));
        linearLayout.requestFocus();
        if (str2 == null && pick == null && !z) {
            this.cachedApps = linearLayout;
            this.cachedSearch = editText;
            focusSearch(editText);
        }
        final boolean z2 = z;
        this.drawerRefresh = new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda57
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m36lambda$40$aucommonolauncherMainActivity(arrayList, z2, str2, arrayList2, editText, text5, anonymousClass5);
            }
        };
        if (str2 == null && pick == null && !z) {
            this.cachedRefresh = this.drawerRefresh;
        }
    }

    /* renamed from: lambda$33$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m29lambda$33$aucommonolauncherMainActivity(View view) {
        new MonoDialog().setTitle("Include all recent apps?").setMessage("Enable Usage access for Mono Launcher on the next screen. Without it, this list shows apps opened through Mono. Usage information stays on your phone.").setPositiveButton("Open settings", new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda12
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.m30lambda$34$aucommonolauncherMainActivity(dialogInterface, i);
            }
        }).setNegativeButton("Not now", (DialogInterface.OnClickListener) null).show();
    }

    /* renamed from: lambda$34$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m30lambda$34$aucommonolauncherMainActivity(DialogInterface dialogInterface, int i) {
        this.returnToRecents = true;
        safeStart(new Intent("android.settings.USAGE_ACCESS_SETTINGS", Uri.parse("package:" + getPackageName())));
    }

    /* renamed from: lambda$35$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m31lambda$35$aucommonolauncherMainActivity(EditText editText, View view) {
        editText.setVisibility(0);
        editText.requestFocus();
        ((InputMethodManager) getSystemService("input_method")).showSoftInput(editText, 1);
    }

    static /* synthetic */ boolean lambda$36(TextView textView, ArrayList arrayList, ListView listView, View view, MotionEvent motionEvent) {
        if (motionEvent.getActionMasked() != 0 && motionEvent.getActionMasked() != 2) {
            return true;
        }
        float lineHeight = textView.getLineHeight();
        int max = Math.max(0, Math.min(26, (int) ((motionEvent.getY() - ((textView.getHeight() - (27.0f * lineHeight)) / 2.0f)) / lineHeight)));
        char c = max == 0 ? '#' : (char) ((max + 65) - 1);
        for (int i = 0; i < arrayList.size(); i++) {
            String upperCase = ((App) arrayList.get(i)).label.toUpperCase(Locale.ROOT);
            if (c == '#' || (!upperCase.isEmpty() && upperCase.charAt(0) >= c)) {
                listView.setSelection(i);
                break;
            }
        }
        return true;
    }

    /* renamed from: au.com.mono.launcher.MainActivity$5, reason: invalid class name */
    class AnonymousClass5 extends BaseAdapter {
        private final /* synthetic */ ArrayList val$filtered;
        private final /* synthetic */ String val$sourceFolder;

        AnonymousClass5(ArrayList arrayList, String str) {
            this.val$filtered = arrayList;
            this.val$sourceFolder = str;
        }

        @Override // android.widget.Adapter
        public int getCount() {
            return this.val$filtered.size();
        }

        @Override // android.widget.Adapter
        public Object getItem(int i) {
            return this.val$filtered.get(i);
        }

        @Override // android.widget.Adapter
        public long getItemId(int i) {
            return i;
        }

        @Override // android.widget.Adapter
        public View getView(int i, View view, ViewGroup viewGroup) {
            LinearLayout row;
            TextView text;
            TextView text2;
            final App app = (App) this.val$filtered.get(i);
            if (view instanceof LinearLayout) {
                row = (LinearLayout) view;
                text = (TextView) row.getChildAt(0);
                text2 = (TextView) row.getChildAt(1);
            } else {
                row = MainActivity.this.row();
                text = MainActivity.this.text("", 17);
                text.setGravity(16);
                text.setPadding(MainActivity.this.dp(10.0f), MainActivity.this.dp(12.0f), MainActivity.this.dp(10.0f), MainActivity.this.dp(12.0f));
                text.setCompoundDrawablePadding(MainActivity.this.dp(16.0f));
                row.addView(text, new LinearLayout.LayoutParams(0, -2, 1.0f));
                text2 = MainActivity.this.text("⋮", 24);
                text2.setFocusable(false);
                row.addView(text2, new LinearLayout.LayoutParams(MainActivity.this.dp(48.0f), MainActivity.this.dp(56.0f)));
            }
            text.setText(app.label);
            Drawable appIcon = MainActivity.this.appIcon(app);
            appIcon.setBounds(0, 0, MainActivity.this.dp(34.0f), MainActivity.this.dp(34.0f));
            text.setCompoundDrawables(appIcon, null, null, null);
            text2.setContentDescription("Options for " + app.label);
            final String str = this.val$sourceFolder;
            text2.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$5$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view2) {
                    MainActivity.AnonymousClass5.this.m68lambda$0$aucommonolauncherMainActivity$5(app, str, view2);
                }
            });
            return row;
        }

        /* renamed from: lambda$0$au-com-mono-launcher-MainActivity$5, reason: not valid java name */
        /* synthetic */ void m68lambda$0$aucommonolauncherMainActivity$5(App app, String str, View view) {
            MainActivity.this.appOptions(app, str);
        }
    }

    /* renamed from: lambda$37$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m32lambda$37$aucommonolauncherMainActivity(ArrayList arrayList, Pick pick, AdapterView adapterView, View view, int i, long j) {
        App app = (App) arrayList.get(i);
        m57lambda$63$aucommonolauncherMainActivity();
        if (pick == null) {
            launch(app);
        } else {
            pick.chosen(app);
        }
    }

    /* renamed from: lambda$38$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ boolean m33lambda$38$aucommonolauncherMainActivity(Pick pick, ArrayList arrayList, String str, AdapterView adapterView, View view, int i, long j) {
        if (pick != null) {
            return false;
        }
        startAppDrag(view, (App) arrayList.get(i), str);
        return true;
    }

    /* renamed from: lambda$39$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m34lambda$39$aucommonolauncherMainActivity(String str, View view) {
        m57lambda$63$aucommonolauncherMainActivity();
        folderOptions(str);
    }

    /* renamed from: lambda$40$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m36lambda$40$aucommonolauncherMainActivity(ArrayList arrayList, boolean z, String str, ArrayList arrayList2, EditText editText, TextView textView, BaseAdapter baseAdapter) {
        arrayList.clear();
        if (z) {
            arrayList.addAll(recentApps());
        } else {
            Iterator<App> it = this.apps.iterator();
            while (it.hasNext()) {
                App next = it.next();
                if (str == null || this.prefs.getStringSet("folder:" + str, new HashSet()).contains(next.id)) {
                    arrayList.add(next);
                }
            }
        }
        arrayList2.clear();
        String lowerCase = editText.getText().toString().toLowerCase(Locale.ROOT);
        Iterator it2 = arrayList.iterator();
        while (it2.hasNext()) {
            App app = (App) it2.next();
            if (app.label.toLowerCase(Locale.ROOT).contains(lowerCase)) {
                arrayList2.add(app);
            }
        }
        textView.setText(str == null ? (this.appsLoading && this.apps.isEmpty()) ? "Loading apps…" : (!this.apps.isEmpty() || this.refreshFailures <= 0) ? z ? "Apps you open will appear here" : "No matching apps" : "Apps are unavailable. Retrying…" : "No apps here yet. Tap Edit folder to add apps.");
        baseAdapter.notifyDataSetChanged();
    }

    class AppDrag {
        final String from;
        boolean home;
        final String id;
        String label;
        String slot;

        AppDrag(String str, String str2) {
            this.id = str;
            this.from = str2;
            App find = MainActivity.this.find(str);
            this.label = find == null ? "App" : find.label;
        }
    }

    void startAppDrag(View view, App app, String str) {
        beginDrag(view, new AppDrag(app.id, str));
    }

    void beginDrag(final View view, Object obj) {
        if (this.dragging) {
            return;
        }
        ((InputMethodManager) getSystemService("input_method")).hideSoftInputFromWindow(view.getWindowToken(), 0);
        final View view2 = this.drawer;
        this.home.setVisibility(0);
        final TextView text = text("Remove", 16);
        Drawable drawable = getDrawable(R.drawable.ic_menu_delete);
        drawable.setBounds(0, 0, dp(24.0f), dp(24.0f));
        text.setCompoundDrawables(drawable, null, null, null);
        text.setCompoundDrawablePadding(dp(10.0f));
        text.setPadding(dp(18.0f), 0, dp(18.0f), 0);
        text.setBackground(bg(-15123116, 25));
        text.setContentDescription("Remove folder or shortcut. Does not uninstall apps.");
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(dp(168.0f), dp(54.0f), 49);
        layoutParams.topMargin = this.home.getPaddingTop() + dp(8.0f);
        this.root.addView(text, layoutParams);
        this.dragBin = text;
        text.setOnDragListener(new View.OnDragListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda42
            @Override // android.view.View.OnDragListener
            public final boolean onDrag(View view3, DragEvent dragEvent) {
                return MainActivity.this.m37lambda$41$aucommonolauncherMainActivity(text, view3, dragEvent);
            }
        });
        this.root.setOnDragListener(new View.OnDragListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda43
            @Override // android.view.View.OnDragListener
            public final boolean onDrag(View view3, DragEvent dragEvent) {
                return MainActivity.this.m38lambda$42$aucommonolauncherMainActivity(view, view2, view3, dragEvent);
            }
        });
        this.dragging = view.startDragAndDrop(ClipData.newPlainText("Mono item", "move"), new View.DragShadowBuilder(view), obj, 0);
        if (this.dragging) {
            view.performHapticFeedback(0);
            view.setAlpha(0.3f);
            if (view2 != null) {
                view2.setVisibility(4);
                return;
            }
            return;
        }
        this.root.removeView(text);
        this.dragBin = null;
        this.root.setOnDragListener(null);
        if (view2 != null) {
            this.home.setVisibility(4);
            if (this.pet != null) {
                this.pet.setVisibility(4);
                this.pet.setActive(false);
            }
        }
    }

    /* renamed from: lambda$41$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ boolean m37lambda$41$aucommonolauncherMainActivity(TextView textView, View view, DragEvent dragEvent) {
        String str;
        if (!(dragEvent.getLocalState() instanceof FolderDrag) && !(dragEvent.getLocalState() instanceof AppDrag)) {
            return false;
        }
        if (dragEvent.getAction() == 5) {
            textView.setBackground(bg(-4570281, 25));
        }
        if (dragEvent.getAction() == 6) {
            textView.setBackground(bg(-15123116, 25));
        }
        if (dragEvent.getAction() != 3) {
            return true;
        }
        if (dragEvent.getLocalState() instanceof FolderDrag) {
            deleteFolder(((FolderDrag) dragEvent.getLocalState()).name);
            str = "Folder removed";
        } else {
            AppDrag appDrag = (AppDrag) dragEvent.getLocalState();
            if (!appDrag.home && appDrag.from == null && appDrag.slot == null) {
                toast("This app is only in All apps; nothing to remove from home");
                return false;
            }
            removeAppOrigin(appDrag, false);
            str = "Shortcut removed";
        }
        toast(str);
        return true;
    }

    /* renamed from: lambda$42$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ boolean m38lambda$42$aucommonolauncherMainActivity(View view, final View view2, View view3, DragEvent dragEvent) {
        if (!(dragEvent.getLocalState() instanceof FolderDrag) && !(dragEvent.getLocalState() instanceof AppDrag)) {
            return false;
        }
        if (dragEvent.getAction() == 4) {
            this.dragging = false;
            view.setAlpha(1.0f);
            this.root.setOnDragListener(null);
            final boolean result = dragEvent.getResult();
            this.handler.post(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m39lambda$43$aucommonolauncherMainActivity(result, view2);
                }
            });
        }
        return dragEvent.getAction() != 3;
    }

    /* renamed from: lambda$43$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m39lambda$43$aucommonolauncherMainActivity(boolean z, View view) {
        if (this.dragBin != null) {
            this.root.removeView(this.dragBin);
            this.dragBin = null;
        }
        if (z) {
            buildHome();
            return;
        }
        if (view == null || this.drawer != view) {
            return;
        }
        view.setVisibility(0);
        this.home.setVisibility(4);
        if (this.pet != null) {
            this.pet.setVisibility(4);
            this.pet.setActive(false);
        }
    }

    void installFolderDrop(View view, final String str) {
        view.setOnDragListener(new View.OnDragListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda41
            @Override // android.view.View.OnDragListener
            public final boolean onDrag(View view2, DragEvent dragEvent) {
                return MainActivity.this.m40lambda$44$aucommonolauncherMainActivity(str, view2, dragEvent);
            }
        });
    }

    /* renamed from: lambda$44$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ boolean m40lambda$44$aucommonolauncherMainActivity(String str, View view, DragEvent dragEvent) {
        if (!(dragEvent.getLocalState() instanceof AppDrag)) {
            return false;
        }
        switch (dragEvent.getAction()) {
            case 1:
                return true;
            case 2:
            default:
                return true;
            case 3:
                AppDrag appDrag = (AppDrag) dragEvent.getLocalState();
                if (find(appDrag.id) == null) {
                    return false;
                }
                HashSet hashSet = new HashSet(this.prefs.getStringSet("folder:" + str, new HashSet()));
                boolean add = hashSet.add(appDrag.id);
                SharedPreferences.Editor putStringSet = this.prefs.edit().putStringSet("folder:" + str, hashSet);
                if (appDrag.from != null && !appDrag.from.equals(str)) {
                    HashSet hashSet2 = new HashSet(this.prefs.getStringSet("folder:" + appDrag.from, new HashSet()));
                    hashSet2.remove(appDrag.id);
                    putStringSet.putStringSet("folder:" + appDrag.from, hashSet2);
                }
                putStringSet.apply();
                if (appDrag.home || appDrag.slot != null) {
                    removeAppOrigin(appDrag, false);
                }
                toast(String.valueOf(add ? "Added to " : "Already in ") + str);
                return true;
            case 4:
            case 6:
                view.setBackground(bg(BLACK, 24));
                view.setScaleX(1.0f);
                view.setScaleY(1.0f);
                return true;
            case 5:
                view.setBackground(bg(-14457222, 24));
                view.setScaleX(1.04f);
                view.setScaleY(1.04f);
                return true;
        }
    }

    void appOptions(final App app, final String str) {
        new MonoDialog().setTitle(app.label).setItems(str == null ? new String[]{"App info", "Assign to home shortcut", "Add to folder"} : new String[]{"App info", "Assign to home shortcut", "Add to folder", "Remove from this folder"}, new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda39
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.m41lambda$45$aucommonolauncherMainActivity(app, str, dialogInterface, i);
            }
        }).show();
    }

    /* renamed from: lambda$45$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m41lambda$45$aucommonolauncherMainActivity(final App app, String str, DialogInterface dialogInterface, int i) {
        if (i == 0) {
            safeStart(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + ComponentName.unflattenFromString(app.id).getPackageName())));
        }
        if (i == 1) {
            final String[] shortcutKeys = shortcutKeys();
            new MonoDialog().setItems(shortcutKeys, new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda28
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface2, int i2) {
                    MainActivity.this.m42lambda$46$aucommonolauncherMainActivity(shortcutKeys, app, dialogInterface2, i2);
                }
            }).show();
        }
        if (i == 2) {
            new MonoDialog().setTitle("Add to folder").setItems(this.folders, new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda29
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface2, int i2) {
                    MainActivity.this.m43lambda$47$aucommonolauncherMainActivity(app, dialogInterface2, i2);
                }
            }).show();
        }
        if (i != 3 || str == null) {
            return;
        }
        HashSet hashSet = new HashSet(this.prefs.getStringSet("folder:" + str, new HashSet()));
        hashSet.remove(app.id);
        this.prefs.edit().putStringSet("folder:" + str, hashSet).apply();
        buildHome();
        showFolder(str);
    }

    /* renamed from: lambda$46$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m42lambda$46$aucommonolauncherMainActivity(String[] strArr, App app, DialogInterface dialogInterface, int i) {
        this.prefs.edit().putString("slot:" + strArr[i], app.id).putString("slotLabel:" + strArr[i], app.label).apply();
        buildHome();
    }

    /* renamed from: lambda$47$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m43lambda$47$aucommonolauncherMainActivity(App app, DialogInterface dialogInterface, int i) {
        HashSet hashSet = new HashSet(this.prefs.getStringSet("folder:" + this.folders[i], new HashSet()));
        hashSet.add(app.id);
        this.prefs.edit().putStringSet("folder:" + this.folders[i], hashSet).apply();
        buildHome();
    }

    void syncWallpaper(boolean z) {
        Bitmap bitmap;
        final String string = this.prefs.getString("wallpaper", "");
        final String str = "mono-v2:" + string;
        if (z || !str.equals(this.prefs.getString("systemWallpaper", ""))) {
            final WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
            if (!wallpaperManager.isWallpaperSupported() || !wallpaperManager.isSetWallpaperAllowed()) {
                toast("Android has blocked wallpaper changes on this device.");
                return;
            }
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
            final int min = Math.min(displayMetrics.widthPixels, 1440);
            final int max = (int) ((displayMetrics.heightPixels * min) / Math.max(1, displayMetrics.widthPixels));
            if (string.isEmpty()) {
                bitmap = Bitmap.createBitmap(min, max, Bitmap.Config.ARGB_8888);
                BlockField still = new BlockField(this);
                still.layout(0, 0, min, max);
                still.draw(new Canvas(bitmap));
            } else {
                bitmap = null;
            }
            final Bitmap bitmap2 = bitmap;
            this.wallpaperWorker.execute(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda24
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m44lambda$48$aucommonolauncherMainActivity(bitmap2, string, min, max, wallpaperManager, str);
                }
            });
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Removed duplicated region for block: B:37:0x0158  */
    /* JADX WARN: Removed duplicated region for block: B:39:0x015d  */
    /* JADX WARN: Removed duplicated region for block: B:69:0x00dd  */
    /* JADX WARN: Removed duplicated region for block: B:73:0x00e3  */
    /* JADX WARN: Type inference failed for: r6v0, types: [int] */
    /* JADX WARN: Type inference failed for: r6v1 */
    /* JADX WARN: Type inference failed for: r6v14 */
    /* JADX WARN: Type inference failed for: r6v15 */
    /* JADX WARN: Type inference failed for: r6v16 */
    /* JADX WARN: Type inference failed for: r6v2 */
    /* JADX WARN: Type inference failed for: r6v7, types: [android.graphics.Bitmap] */
    /* JADX WARN: Type inference failed for: r9v5, types: [android.graphics.Canvas] */
    /* renamed from: lambda$48$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    /* synthetic */ void m44lambda$48$aucommonolauncherMainActivity(Bitmap fallback, String photo, int width, int height, WallpaperManager wallpaperManager, String key) {
        Bitmap target = fallback;
        Bitmap source = null;
        try {
            if (target == null) {
                source = decodePhoto(Uri.parse(photo));
                if (source == null) {
                    throw new IOException("Image unavailable");
                }
                target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                float scale = Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
                float w = source.getWidth() * scale;
                float h = source.getHeight() * scale;
                new Canvas(target).drawBitmap(source, null, new android.graphics.RectF((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f), new Paint(Paint.FILTER_BITMAP_FLAG));
            }
            if (wallpaperManager.setBitmap(target, null, false, WallpaperManager.FLAG_SYSTEM) == 0) {
                throw new IOException("Wallpaper not applied");
            }
            this.prefs.edit().putString("systemWallpaper", key).apply();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.m45lambda$50$aucommonolauncherMainActivity();
                }
            });
        } catch (Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.m46lambda$51$aucommonolauncherMainActivity();
                }
            });
        } finally {
            if (source != null) {
                source.recycle();
            }
            if (target != null) {
                target.recycle();
            }
        }
    }

    static /* synthetic */ void lambda$49(ImageDecoder imageDecoder, ImageDecoder.ImageInfo imageInfo, ImageDecoder.Source source) {
        imageDecoder.setAllocator(1);
        int width = imageInfo.getSize().getWidth();
        int height = imageInfo.getSize().getHeight();
        float min = Math.min(1.0f, 2048.0f / Math.max(width, height));
        imageDecoder.setTargetSize(Math.max(1, (int) (width * min)), Math.max(1, (int) (height * min)));
    }

    /* renamed from: lambda$50$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m45lambda$50$aucommonolauncherMainActivity() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        toast("Home wallpaper updated");
    }

    /* renamed from: lambda$51$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m46lambda$51$aucommonolauncherMainActivity() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        toast("Wallpaper could not be applied. Try Sync home wallpaper in settings.");
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        if (this.pet != null) {
            this.pet.dispose();
            this.pet = null;
        }
        super.onDestroy();
        this.handler.removeCallbacksAndMessages(null);
        this.launcher.unregisterCallback(this.packageChanges);
        unregisterReceiver(this.batteryUpdates);
        this.appWorker.shutdown();
        this.wallpaperWorker.shutdown();
        this.assistantWorker.shutdownNow();
    }

    static /* synthetic */ void lambda$52(Set set, ArrayList arrayList, DialogInterface dialogInterface, int i, boolean z) {
        String str = ((App) arrayList.get(i)).id;
        if (z) {
            set.add(str);
        } else {
            set.remove(str);
        }
    }

    void editFolder(final String str) {
        int size = this.apps.size();
        String[] strArr = new String[size];
        boolean[] zArr = new boolean[this.apps.size()];
        final HashSet hashSet = new HashSet(this.prefs.getStringSet("folder:" + str, new HashSet()));
        final ArrayList arrayList = new ArrayList(this.apps);
        for (int i = 0; i < size; i++) {
            strArr[i] = ((App) arrayList.get(i)).label;
            zArr[i] = hashSet.contains(((App) arrayList.get(i)).id);
        }
        new MonoDialog().setTitle("Apps in " + str).setMultiChoiceItems(strArr, zArr, new DialogInterface.OnMultiChoiceClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda26
            @Override // android.content.DialogInterface.OnMultiChoiceClickListener
            public final void onClick(DialogInterface dialogInterface, int i2, boolean z) {
                MainActivity.lambda$52(hashSet, arrayList, dialogInterface, i2, z);
            }
        }).setPositiveButton("Save", new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda27
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i2) {
                MainActivity.this.m47lambda$53$aucommonolauncherMainActivity(str, hashSet, dialogInterface, i2);
            }
        }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
    }

    /* renamed from: lambda$53$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m47lambda$53$aucommonolauncherMainActivity(String str, Set set, DialogInterface dialogInterface, int i) {
        this.prefs.edit().putStringSet("folder:" + str, set).apply();
        buildHome();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    /* renamed from: openPetChat, reason: merged with bridge method [inline-methods] */
    public void m48lambda$54$aucommonolauncherMainActivity() {
        try {
            Intent launchIntentForPackage = getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
            if (launchIntentForPackage != null) {
                startActivity(launchIntentForPackage);
                return;
            }
        } catch (Exception e) {
        }
        safeStart(new Intent("android.intent.action.VIEW", Uri.parse("https://chatgpt.com/")));
    }

    // ---- Home screen assistant ----

    static final int REQUEST_VOICE = 13;

    Assistant assistant() {
        if (this.assistant == null) {
            this.assistant = Assistant.overHttps();
        }
        return this.assistant;
    }

    SecretStore secrets() {
        if (this.secrets == null) {
            this.secrets = new SecretStore(this.prefs);
        }
        return this.secrets;
    }

    View askBar() {
        LinearLayout bar = row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16.0f), 0, dp(6.0f), 0);
        bar.setBackground(bg(BLACK, 22));
        TextView hint = text("Ask Mono anything", 15);
        hint.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        hint.setTextColor(-4598813);
        bar.addView(hint, new LinearLayout.LayoutParams(0, -1, 1.0f));
        TextView mic = text("🎙", 18);
        mic.setContentDescription("Ask by voice");
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAssistant(true);
            }
        });
        bar.addView(mic, new LinearLayout.LayoutParams(dp(44.0f), -1));
        bar.setContentDescription("Ask Mono, the assistant");
        bar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAssistant(false);
            }
        });
        return bar;
    }

    void showAssistant(boolean voice) {
        m57lambda$63$aucommonolauncherMainActivity();
        final LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setFocusableInTouchMode(true);
        panel.setPadding(dp(18.0f), this.home.getPaddingTop(), dp(18.0f), this.home.getPaddingBottom());
        panel.setBackgroundColor(-435609549);

        LinearLayout header = row();
        TextView title = text("Mono", 20);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48.0f), 1.0f));
        TextView fresh = text("New chat", 13);
        fresh.setTextColor(ACCENT);
        fresh.setContentDescription("Start a new conversation");
        header.addView(fresh, new LinearLayout.LayoutParams(-2, dp(48.0f)));
        TextView close = text("✕", 20);
        close.setContentDescription("Close assistant");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.m57lambda$63$aucommonolauncherMainActivity();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(48.0f), dp(48.0f)));
        panel.addView(header);

        final ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(8.0f), 0, dp(8.0f));
        scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));

        LinearLayout input = row();
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setBackground(bg(BLACK, 24));
        input.setPadding(dp(14.0f), 0, dp(4.0f), 0);
        final EditText field = new EditText(this);
        field.setHint("Ask, or say “open Spotify”");
        field.setHintTextColor(-7428935);
        field.setTextColor(-1);
        field.setBackground(null);
        field.setMaxLines(4);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.addView(field, new LinearLayout.LayoutParams(0, -2, 1.0f));
        TextView mic = text("🎙", 18);
        mic.setContentDescription("Ask by voice");
        input.addView(mic, new LinearLayout.LayoutParams(dp(44.0f), dp(48.0f)));
        final TextView send = text("➤", 20);
        send.setTextColor(ACCENT);
        send.setContentDescription("Send");
        input.addView(send, new LinearLayout.LayoutParams(dp(44.0f), dp(48.0f)));
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, -2);
        inputLp.topMargin = dp(8.0f);
        panel.addView(input, inputLp);

        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                renderAssistant(list, field);
                scroll.post(new Runnable() {
                    @Override
                    public void run() {
                        scroll.fullScroll(View.FOCUS_DOWN);
                    }
                });
                send.setAlpha(assistant().busy ? 0.35f : 1.0f);
            }
        };
        this.assistantRefresh = refresh;
        fresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!assistant().busy) {
                    assistant().clear();
                    refresh.run();
                }
            }
        });
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askAssistant(field.getText().toString());
                field.setText("");
            }
        });
        field.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int action, android.view.KeyEvent event) {
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    askAssistant(field.getText().toString());
                    field.setText("");
                    return true;
                }
                return false;
            }
        });
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listenForAssistant();
            }
        });

        this.drawer = panel;
        this.drawerRefresh = refresh;
        this.home.setVisibility(View.INVISIBLE);
        if (this.pet != null) {
            this.pet.setVisibility(View.INVISIBLE);
            this.pet.setActive(false);
        }
        this.root.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        refresh.run();
        if (voice && hasAssistantKey()) {
            listenForAssistant();
        } else if (hasAssistantKey()) {
            focusSearch(field);
        }
    }

    boolean hasAssistantKey() {
        return secrets().has("claudeKey");
    }

    void renderAssistant(LinearLayout list, final EditText field) {
        list.removeAllViews();
        if (!hasAssistantKey()) {
            TextView intro = text("Mono can answer questions, open apps, set timers and alarms, and sort apps into folders. It runs on Claude using your own Anthropic API key, which stays encrypted on this phone.", 15);
            intro.setGravity(Gravity.START);
            intro.setLineSpacing(0.0f, 1.15f);
            list.addView(intro);
            list.addView(assistantButton("Add Claude API key", new Runnable() {
                @Override
                public void run() {
                    editAssistantKey();
                }
            }));
            list.addView(assistantButton("Get a key at console.anthropic.com", new Runnable() {
                @Override
                public void run() {
                    safeStart(new Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys")));
                }
            }));
            list.addView(assistantButton("Open ChatGPT instead", new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.m48lambda$54$aucommonolauncherMainActivity();
                }
            }));
            return;
        }
        java.util.List<Assistant.Line> lines = assistant().lines();
        if (lines.isEmpty()) {
            TextView hello = text("What can I do for you?", 17);
            hello.setGravity(Gravity.START);
            list.addView(hello);
            for (final String idea : new String[]{"Set a 10 minute timer", "Open Spotify", "What's the weather today?", "Put my banking apps in the Work folder"}) {
                list.addView(assistantButton(idea, new Runnable() {
                    @Override
                    public void run() {
                        askAssistant(idea);
                    }
                }));
            }
            return;
        }
        for (Assistant.Line line : lines) {
            TextView bubble = text(line.text, line.kind == Assistant.Line.ACTION ? 12 : 15);
            bubble.setGravity(Gravity.START);
            bubble.setTextIsSelectable(line.kind == Assistant.Line.ASSISTANT);
            bubble.setLineSpacing(0.0f, 1.12f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = dp(8.0f);
            if (line.kind == Assistant.Line.USER) {
                bubble.setPadding(dp(14.0f), dp(9.0f), dp(14.0f), dp(9.0f));
                bubble.setBackground(bg(-14581884, 18));
                lp.gravity = Gravity.END;
                lp.leftMargin = dp(48.0f);
            } else if (line.kind == Assistant.Line.ASSISTANT) {
                bubble.setPadding(dp(14.0f), dp(9.0f), dp(14.0f), dp(9.0f));
                bubble.setBackground(bg(BLACK, 18));
                lp.rightMargin = dp(32.0f);
            } else if (line.kind == Assistant.Line.ACTION) {
                bubble.setText("✓ " + line.text);
                bubble.setTextColor(ACCENT);
                lp.leftMargin = dp(6.0f);
            } else {
                bubble.setTextColor(-21846);
                lp.leftMargin = dp(6.0f);
            }
            list.addView(bubble, lp);
        }
        if (assistant().busy) {
            TextView thinking = text("Thinking…", 13);
            thinking.setGravity(Gravity.START);
            thinking.setTextColor(-4598813);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = dp(10.0f);
            lp.leftMargin = dp(6.0f);
            list.addView(thinking, lp);
        }
    }

    TextView assistantButton(String label, final Runnable action) {
        TextView button = text(label, 14);
        button.setTextColor(ACCENT);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14.0f), dp(11.0f), dp(14.0f), dp(11.0f));
        button.setBackground(bg(BLACK, 18));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(10.0f);
        button.setLayoutParams(lp);
        return button;
    }

    void askAssistant(String question) {
        final String text = question.trim();
        if (text.isEmpty() || assistant().busy) {
            return;
        }
        final String key = secrets().get("claudeKey");
        if (key == null) {
            toast("Add your Claude API key first");
            editAssistantKey();
            return;
        }
        final String context = assistantContext();
        if (this.assistantWorker.isShutdown()) {
            return;
        }
        this.assistantWorker.execute(new Runnable() {
            @Override
            public void run() {
                assistant().send(key, text, context, new Assistant.Host() {
                    @Override
                    public String runTool(String name, org.json.JSONObject input) throws Exception {
                        return runAssistantTool(name, input);
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.this.handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (MainActivity.this.assistantRefresh != null && MainActivity.this.drawer != null && MainActivity.this.drawerRefresh == MainActivity.this.assistantRefresh) {
                                    MainActivity.this.assistantRefresh.run();
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    String assistantContext() {
        StringBuilder folderNames = new StringBuilder();
        for (String folder : this.folders) {
            if (folderNames.length() > 0) {
                folderNames.append(", ");
            }
            folderNames.append(folder);
        }
        String now = new SimpleDateFormat("EEEE d MMMM yyyy, " + (DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a"), Locale.getDefault()).format(new Date());
        return "(Phone context: local time " + now + ". Home screen folders: " + folderNames + ".)";
    }

    void listenForAssistant() {
        Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Ask Mono");
        try {
            startActivityForResult(intent, REQUEST_VOICE);
        } catch (Exception e) {
            toast("Voice input isn't available on this phone");
        }
    }

    void editAssistantKey() {
        final EditText field = new EditText(this);
        field.setHint("sk-ant-…");
        field.setSingleLine();
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog.Builder builder = new MonoDialog()
                .setTitle("Claude API key")
                .setMessage("Paste a key from console.anthropic.com. It's encrypted on this phone and only sent to Anthropic when you ask Mono something. Usage is billed to your Anthropic account.")
                .setView(field)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String key = field.getText().toString().trim();
                        if (key.isEmpty()) {
                            return;
                        }
                        if (secrets().put("claudeKey", key)) {
                            toast("Key saved");
                        } else {
                            toast("Couldn't store the key securely on this phone");
                        }
                        if (MainActivity.this.drawerRefresh != null && MainActivity.this.drawerRefresh == MainActivity.this.assistantRefresh) {
                            MainActivity.this.assistantRefresh.run();
                        }
                    }
                })
                .setNegativeButton("Cancel", null);
        if (hasAssistantKey()) {
            builder.setNeutralButton("Remove key", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    secrets().remove("claudeKey");
                    assistant().clear();
                    toast("Key removed");
                    if (MainActivity.this.drawerRefresh != null && MainActivity.this.drawerRefresh == MainActivity.this.assistantRefresh) {
                        MainActivity.this.assistantRefresh.run();
                    }
                }
            });
        }
        builder.show();
    }

    /** Runs one tool call on the main thread and waits for its result. Called from the assistant worker. */
    String runAssistantTool(final String name, final org.json.JSONObject input) throws Exception {
        final String[] result = new String[1];
        final Exception[] failure = new Exception[1];
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        this.handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    result[0] = assistantTool(name, input);
                } catch (Exception e) {
                    failure[0] = e;
                }
                done.countDown();
            }
        });
        if (!done.await(20, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new Exception("the launcher didn't respond");
        }
        if (failure[0] != null) {
            throw failure[0];
        }
        return result[0];
    }

    App matchApp(String name) {
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) {
            return null;
        }
        App starts = null;
        App contains = null;
        for (App app : this.apps) {
            String label = app.label.toLowerCase(Locale.ROOT);
            if (label.equals(wanted)) {
                return app;
            }
            if (starts == null && label.startsWith(wanted)) {
                starts = app;
            }
            if (contains == null && label.contains(wanted)) {
                contains = app;
            }
        }
        return starts != null ? starts : contains;
    }

    String assistantTool(String name, org.json.JSONObject input) throws Exception {
        if (name.equals("find_apps")) {
            String query = input.optString("query").trim().toLowerCase(Locale.ROOT);
            StringBuilder found = new StringBuilder();
            int count = 0;
            for (App app : this.apps) {
                if (query.isEmpty() || app.label.toLowerCase(Locale.ROOT).contains(query)) {
                    found.append(count == 0 ? "" : ", ").append(app.label);
                    count++;
                }
            }
            return count == 0 ? "No installed apps match \"" + query + "\"." : "Installed apps: " + found;
        }
        if (name.equals("open_app")) {
            App app = matchApp(input.optString("name"));
            if (app == null) {
                throw new Exception("no installed app called \"" + input.optString("name") + "\"");
            }
            this.launcher.startMainActivity(ComponentName.unflattenFromString(app.id), Process.myUserHandle(), null, null);
            recordRecent(app.id);
            return "Opened " + app.label;
        }
        if (name.equals("open_website")) {
            Uri uri = Uri.parse(input.optString("url").trim());
            if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
                throw new Exception("only web addresses can be opened");
            }
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
            return "Opened " + uri.getHost();
        }
        if (name.equals("set_timer")) {
            int seconds = input.optInt("seconds");
            if (seconds <= 0 || seconds > 86400) {
                throw new Exception("timers must be between 1 second and 24 hours");
            }
            Intent intent = new Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                    .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds)
                    .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true);
            if (!input.optString("label").isEmpty()) {
                intent.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, input.optString("label"));
            }
            startActivity(intent);
            int minutes = seconds / 60;
            return "Timer set for " + (minutes > 0 ? minutes + " min" + (seconds % 60 > 0 ? " " + (seconds % 60) + " s" : "") : seconds + " s");
        }
        if (name.equals("set_alarm")) {
            int hour = input.optInt("hour", -1);
            int minute = input.optInt("minute", -1);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                throw new Exception("that isn't a valid time");
            }
            Intent intent = new Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
                    .putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                    .putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                    .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true);
            if (!input.optString("label").isEmpty()) {
                intent.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, input.optString("label"));
            }
            startActivity(intent);
            return String.format(Locale.ROOT, "Alarm set for %02d:%02d", hour, minute);
        }
        if (name.equals("add_app_to_folder")) {
            App app = matchApp(input.optString("app"));
            if (app == null) {
                throw new Exception("no installed app called \"" + input.optString("app") + "\"");
            }
            String folder = null;
            for (String existing : this.folders) {
                if (existing.equalsIgnoreCase(input.optString("folder").trim())) {
                    folder = existing;
                }
            }
            if (folder == null) {
                throw new Exception("there's no folder called \"" + input.optString("folder") + "\"");
            }
            HashSet<String> members = new HashSet<>(this.prefs.getStringSet("folder:" + folder, new HashSet<String>()));
            boolean added = members.add(app.id);
            this.prefs.edit().putStringSet("folder:" + folder, members).apply();
            this.homeDirty = true;
            return (added ? "Added " : "Already had ") + app.label + (added ? " to " : " in ") + folder;
        }
        throw new Exception("unknown tool " + name);
    }

    void attachPet() {
        if (!this.prefs.getBoolean("petEnabled", true)) {
            this.pet = null;
            return;
        }
        this.pet = new LauncherPet(this, new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda30
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.showAssistant(false);
            }
        }, new LauncherPet.Moved() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda31
            @Override // au.com.mono.launcher.LauncherPet.Moved
            public final void save(float f, float f2) {
                MainActivity.this.m49lambda$55$aucommonolauncherMainActivity(f, f2);
            }
        });
        final LauncherPet launcherPet = this.pet;
        this.root.addView(launcherPet, new FrameLayout.LayoutParams(dp(72.0f), dp(81.0f)));
        launcherPet.setFactsEnabled(this.prefs.getBoolean("petFacts", true));
        launcherPet.setActive(this.resumed);
        this.root.post(new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda32
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m50lambda$56$aucommonolauncherMainActivity(launcherPet);
            }
        });
    }

    /* renamed from: lambda$55$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m49lambda$55$aucommonolauncherMainActivity(float f, float f2) {
        this.prefs.edit().putFloat("petX", f / Math.max(1, this.root.getWidth() - dp(72.0f))).putFloat("petY", f2 / Math.max(1, this.root.getHeight() - dp(81.0f))).apply();
    }

    /* renamed from: lambda$56$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m50lambda$56$aucommonolauncherMainActivity(LauncherPet launcherPet) {
        if (this.pet != launcherPet) {
            return;
        }
        float max = Math.max(0, this.root.getWidth() - launcherPet.getWidth());
        float max2 = Math.max(0, this.root.getHeight() - launcherPet.getHeight());
        launcherPet.setX(Math.max(0.0f, Math.min(max, this.prefs.getFloat("petX", 0.8f) * max)));
        launcherPet.setY(Math.max(this.home.getPaddingTop(), Math.min(max2 - this.home.getPaddingBottom(), this.prefs.getFloat("petY", 0.66f) * max2)));
    }

    void petSettings() {
        new MonoDialog().setTitle("Your robot companion").setItems(new String[]{"Show a fact now", this.prefs.getBoolean("petFacts", true) ? "Turn random facts off" : "Turn random facts on", this.prefs.getBoolean("petEnabled", true) ? "Hide pet" : "Show pet", "Reset position"}, new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda33
            @Override // android.content.DialogInterface.OnClickListener
            public final void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.m51lambda$57$aucommonolauncherMainActivity(dialogInterface, i);
            }
        }).setNegativeButton("Close", (DialogInterface.OnClickListener) null).show();
    }

    /* renamed from: lambda$57$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m51lambda$57$aucommonolauncherMainActivity(DialogInterface dialogInterface, int i) {
        if (i == 0) {
            if (this.pet != null) {
                this.pet.showFact();
            } else {
                toast("Show the pet first");
            }
        }
        if (i == 1) {
            boolean z = !this.prefs.getBoolean("petFacts", true);
            this.prefs.edit().putBoolean("petFacts", z).apply();
            if (this.pet != null) {
                this.pet.setFactsEnabled(z);
            }
        }
        if (i == 2) {
            this.prefs.edit().putBoolean("petEnabled", !this.prefs.getBoolean("petEnabled", true)).apply();
            buildHome();
        }
        if (i == 3) {
            this.prefs.edit().remove("petX").remove("petY").putBoolean("petEnabled", true).apply();
            buildHome();
        }
    }

    /* renamed from: lambda$58$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m52lambda$58$aucommonolauncherMainActivity(DialogInterface dialogInterface, int i) {
        if (i == 0) {
            if (Build.VERSION.SDK_INT >= 29) {
                RoleManager roleManager = (RoleManager) getSystemService(RoleManager.class);
                if (roleManager.isRoleAvailable("android.app.role.HOME") && !roleManager.isRoleHeld("android.app.role.HOME")) {
                    startActivityForResult(roleManager.createRequestRoleIntent("android.app.role.HOME"), 12);
                    return;
                }
            }
            safeStart(new Intent("android.settings.HOME_SETTINGS"));
        }
        if (i == 1) {
            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
            intent.setType("image/*");
            intent.addCategory("android.intent.category.OPENABLE");
            intent.addFlags(65);
            startActivityForResult(intent, 11);
        }
        if (i == 2) {
            this.prefs.edit().remove("wallpaper").apply();
            buildHome();
            syncWallpaper(true);
        }
        if (i == 3) {
            new MonoDialog().setTitle("Icon size").setItems(new String[]{"Small", "Medium", "Large"}, new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda15
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface2, int i2) {
                    MainActivity.this.m53lambda$59$aucommonolauncherMainActivity(dialogInterface2, i2);
                }
            }).show();
        }
        if (i == 4) {
            manageFolders();
        }
        if (i == 5) {
            safeStart(new Intent("android.settings.SETTINGS"));
        }
        if (i == 7) {
            syncWallpaper(true);
        }
        if (i == 8) {
            nameFolder(null);
        }
        if (i == 9) {
            manageFolders();
        }
        if (i == 10) {
            final String[] shortcutKeys = shortcutKeys();
            new MonoDialog().setItems(shortcutKeys, new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda16
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface2, int i2) {
                    MainActivity.this.m55lambda$60$aucommonolauncherMainActivity(shortcutKeys, dialogInterface2, i2);
                }
            }).show();
        }
        if (i == 11) {
            petSettings();
        }
        if (i == 12) {
            this.refreshFailures = 0;
            refreshApps();
            loadPhotoBackground();
            toast("Refreshing apps and icons");
        }
        if (i == 6) {
            new MonoDialog().setTitle("Your Mono home").setMessage("Ask Mono: tap the Ask bar or the robot to talk to the assistant. It can answer questions, open apps, set timers and alarms, and add apps to folders. Tap the mic to ask by voice. It needs your own Claude API key (Settings, Assistant).\n\nSwipe up anywhere for All apps, down for Recent apps, right for the Clock and left for the learning page.\n\nHold an app in All apps, then drag it onto a folder or a free space. Hold and drag folders to move them. Drop anything on the Remove bin to take it off the home screen (apps stay installed). Open a folder and use Folder options to rename or delete it.\n\nHold empty home space or the clock for settings. Long press a dock or shortcut slot to change it; Impact Hub can be an app, a website or a browser shortcut.\n\nThird-party widgets and work or private profile apps aren't supported yet. To switch back, open Set as default home and choose your previous launcher.").setPositiveButton("Got it", (DialogInterface.OnClickListener) null).show();
        }
    }

    /* renamed from: lambda$59$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m53lambda$59$aucommonolauncherMainActivity(DialogInterface dialogInterface, int i) {
        this.prefs.edit().putInt("iconSize", new int[]{21, 25, 31}[i]).apply();
        buildHome();
    }

    /* renamed from: lambda$60$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m55lambda$60$aucommonolauncherMainActivity(String[] strArr, DialogInterface dialogInterface, int i) {
        chooseSlot(strArr[i]);
    }

    String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    void settings() {
        new MonoDialog().setTitle("Mono Launcher " + versionName()).setItems(new String[]{"Assistant", "Home screen", "Wallpaper", "Set as default home", "Phone settings", "How to use"}, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    assistantSettings();
                } else if (which == 1) {
                    settingsGroup("Home screen", new String[]{"Edit dock and shortcuts", "New folder", "Manage folders", "Icon size", "Refresh apps"}, new int[]{10, 8, 9, 3, 12});
                } else if (which == 2) {
                    settingsGroup("Wallpaper", new String[]{"Choose photo wallpaper", "Restore Mono wallpaper", "Sync home wallpaper"}, new int[]{1, 2, 7});
                } else if (which == 3) {
                    MainActivity.this.m52lambda$58$aucommonolauncherMainActivity(dialog, 0);
                } else if (which == 4) {
                    MainActivity.this.m52lambda$58$aucommonolauncherMainActivity(dialog, 5);
                } else {
                    MainActivity.this.m52lambda$58$aucommonolauncherMainActivity(dialog, 6);
                }
            }
        }).show();
    }

    void settingsGroup(String title, String[] labels, final int[] actions) {
        new MonoDialog().setTitle(title).setItems(labels, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.m52lambda$58$aucommonolauncherMainActivity(dialog, actions[which]);
            }
        }).setNegativeButton("Back", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                settings();
            }
        }).show();
    }

    void assistantSettings() {
        new MonoDialog().setTitle("Assistant").setItems(new String[]{hasAssistantKey() ? "Change or remove Claude API key" : "Add Claude API key", "Clear conversation", "Robot companion"}, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    editAssistantKey();
                } else if (which == 1) {
                    if (!assistant().busy) {
                        assistant().clear();
                        toast("Conversation cleared");
                    }
                } else {
                    petSettings();
                }
            }
        }).setNegativeButton("Back", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                settings();
            }
        }).show();
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        super.onActivityResult(i, i2, intent);
        if (i == REQUEST_VOICE) {
            if (i2 == -1 && intent != null) {
                ArrayList<String> heard = intent.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
                if (heard != null && !heard.isEmpty()) {
                    if (this.drawerRefresh == null || this.drawerRefresh != this.assistantRefresh) {
                        showAssistant(false);
                    }
                    askAssistant(heard.get(0));
                }
            }
            return;
        }
        if (i != 11 || i2 != -1 || intent == null || intent.getData() == null) {
            return;
        }
        Uri data = intent.getData();
        try {
            getContentResolver().takePersistableUriPermission(data, intent.getFlags() & 1);
            this.prefs.edit().putString("wallpaper", data.toString()).apply();
            buildHome();
            syncWallpaper(true);
        } catch (Exception e) {
            toast("Could not keep access to that image. Try another photo.");
        }
    }

    void handlePin(Intent intent) {
        final LauncherApps.PinItemRequest pinItemRequest;
        if ("android.content.pm.action.CONFIRM_PIN_SHORTCUT".equals(intent.getAction()) && (pinItemRequest = this.launcher.getPinItemRequest(intent)) != null && pinItemRequest.isValid() && pinItemRequest.getShortcutInfo() != null) {
            final ShortcutInfo shortcutInfo = pinItemRequest.getShortcutInfo();
            new MonoDialog().setTitle("Add " + ((Object) shortcutInfo.getShortLabel()) + "?").setMessage("Use this shortcut for Impact Hub on your Mono home screen?").setPositiveButton("Add", new DialogInterface.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda46
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    MainActivity.this.m56lambda$61$aucommonolauncherMainActivity(pinItemRequest, shortcutInfo, dialogInterface, i);
                }
            }).setNegativeButton("Cancel", (DialogInterface.OnClickListener) null).show();
        }
    }

    /* renamed from: lambda$61$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m56lambda$61$aucommonolauncherMainActivity(LauncherApps.PinItemRequest pinItemRequest, ShortcutInfo shortcutInfo, DialogInterface dialogInterface, int i) {
        try {
            if (pinItemRequest.accept()) {
                this.prefs.edit().putString("slot:Impact Hub", "pin:" + shortcutInfo.getPackage() + ":" + shortcutInfo.getId()).apply();
                buildHome();
            }
        } catch (Exception e) {
            toast("Could not add shortcut");
        }
    }

    void safeStart(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            toast("No app available for this action");
        }
    }

    void toast(String str) {
        Toast.makeText(this, str, 1).show();
    }

    void updateBattery() {
        if (this.battery != null) {
            this.battery.setText(this.batteryPercent < 0 ? "—%" : String.valueOf(this.batteryPercent) + "%");
            this.battery.setContentDescription("Battery " + ((Object) this.battery.getText()));
        }
    }

    int nextLearningIndex(String str, ArrayList<Integer> arrayList) {
        ArrayList<Integer> remaining = LearningDeck.remaining(this.prefs.getString("learningDeck:" + str, ""), arrayList, this.prefs.getInt("learningLast:" + str, -1), this.learningRandom);
        int intValue = remaining.remove(0).intValue();
        StringBuilder sb = new StringBuilder();
        Iterator<Integer> it = remaining.iterator();
        while (it.hasNext()) {
            int intValue2 = it.next().intValue();
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(intValue2);
        }
        this.prefs.edit().putString("learningDeck:" + str, sb.toString()).putInt("learningLast:" + str, intValue).apply();
        return intValue;
    }

    String nextQuote() {
        ArrayList<Integer> arrayList = new ArrayList<>();
        for (int i = 0; i < LearningContent.QUOTES.length; i++) {
            arrayList.add(Integer.valueOf(i));
        }
        return LearningContent.QUOTES[nextLearningIndex("quotes", arrayList)];
    }

    LearningContent.Question nextQuestion() {
        String string = this.prefs.getString("learningMode", "Countries");
        ArrayList<Integer> arrayList = new ArrayList<>();
        for (int i = 0; i < LearningContent.QUESTIONS.length; i++) {
            boolean z = !LearningContent.QUESTIONS[i].country.isEmpty();
            if (string.equals("Mixed") || string.equals("Countries") == z) {
                arrayList.add(Integer.valueOf(i));
            }
        }
        return LearningContent.QUESTIONS[nextLearningIndex(string, arrayList)];
    }

    Button pageButton(String str, final Runnable runnable) {
        Button button = new Button(this);
        button.setText(str);
        button.setAllCaps(false);
        button.setTextColor(-1);
        button.setTextSize(14.0f);
        button.setBackground(bg(-15054239, 16));
        button.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda65
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                runnable.run();
            }
        });
        return button;
    }

    void pageSpace(LinearLayout linearLayout, int i) {
        linearLayout.addView(new View(this), new LinearLayout.LayoutParams(1, dp(i)));
    }

    void showSidePage(int i) {
        m57lambda$63$aucommonolauncherMainActivity();
        this.sidePage = i;
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(dp(22.0f), this.home.getPaddingTop(), dp(22.0f), this.home.getPaddingBottom());
        linearLayout.setBackgroundColor(i < 0 ? -1727456468 : -318103243);
        LinearLayout row = row();
        int i2 = 18;
        TextView text = text(i < 0 ? "Clock" : "A little more curious", 18);
        text.setGravity(8388627);
        float f = 48.0f;
        float f2 = 1.0f;
        row.addView(text, new LinearLayout.LayoutParams(0, dp(48.0f), 1.0f));
        row.addView(pageButton("Home", new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.m57lambda$63$aucommonolauncherMainActivity();
            }
        }), new LinearLayout.LayoutParams(dp(76.0f), dp(44.0f)));
        linearLayout.addView(row);
        int i3 = -1;
        if (i < 0) {
            this.focusClock = new FocusClock();
            linearLayout.addView(this.focusClock, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            TextView text2 = text("Swipe left to return home", 12);
            text2.setTextColor(-3091238);
            linearLayout.addView(text2, new LinearLayout.LayoutParams(-1, dp(32.0f)));
        } else {
            final ScrollView scrollView = new ScrollView(this);
            scrollView.setFillViewport(true);
            scrollView.setVerticalScrollBarEnabled(false);
            LinearLayout linearLayout2 = new LinearLayout(this);
            linearLayout2.setOrientation(1);
            scrollView.addView(linearLayout2);
            linearLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1.0f));
            pageSpace(linearLayout2, 20);
            TextView text3 = text("A THOUGHT TO TAKE WITH YOU", 11);
            text3.setTextColor(-4598813);
            int i4 = 8388611;
            text3.setGravity(8388611);
            linearLayout2.addView(text3);
            final TextView text4 = text(nextQuote(), 25);
            text4.setGravity(8388611);
            char c = 0;
            text4.setPadding(0, dp(12.0f), 0, dp(14.0f));
            linearLayout2.addView(text4);
            linearLayout2.addView(pageButton("Another thought", new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda4
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m58lambda$64$aucommonolauncherMainActivity(text4);
                }
            }), new LinearLayout.LayoutParams(-1, dp(46.0f)));
            pageSpace(linearLayout2, 24);
            LinearLayout row2 = row();
            Object string = this.prefs.getString("learningMode", "Countries");
            String[] strArr = {"Countries", "Trivia", "Mixed"};
            int i5 = 0;
            while (i5 < 3) {
                char c2 = c;
                int i6 = i4;
                LinearLayout linearLayout3 = linearLayout2;
                LinearLayout linearLayout4 = linearLayout;
                int i7 = i2;
                int i8 = i3;
                final String str = strArr[i5];
                Button pageButton = pageButton(str, new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda5
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.m59lambda$65$aucommonolauncherMainActivity(str);
                    }
                });
                if (str.equals(string)) {
                    pageButton.setBackground(bg(-14457222, 14));
                }
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, dp(46.0f), 1.0f);
                layoutParams.setMargins(dp(2.0f), 0, dp(2.0f), 0);
                row2.addView(pageButton, layoutParams);
                i5++;
                f2 = 1.0f;
                i3 = i8;
                linearLayout2 = linearLayout3;
                i2 = i7;
                c = c2;
                i4 = i6;
                linearLayout = linearLayout4;
                f = 48.0f;
            }
            linearLayout2.addView(row2);
            pageSpace(linearLayout2, i2);
            final LearningContent.Question nextQuestion = nextQuestion();
            int i9 = 22;
            TextView text5 = text(nextQuestion.prompt, 22);
            text5.setGravity(i4);
            text5.setLineSpacing(dp(3.0f), f2);
            linearLayout2.addView(text5);
            pageSpace(linearLayout2, i2);
            TextView text6 = text(String.valueOf(nextQuestion.answer) + "\n\n" + nextQuestion.detail, 17);
            text6.setGravity(i4);
            text6.setTextColor(-2824975);
            text6.setPadding(dp(16.0f), dp(16.0f), dp(16.0f), dp(16.0f));
            text6.setBackground(bg(-15385519, i2));
            int i10 = 8;
            text6.setVisibility(8);
            TextView text7 = text("", 16);
            text7.setGravity(i4);
            text7.setVisibility(8);
            final ArrayList arrayList = new ArrayList();
            Iterator<String> it = LearningContent.choices(nextQuestion, new Random()).iterator();
            while (it.hasNext()) {
                LinearLayout linearLayout5 = linearLayout2;
                final String next = it.next();
                final Button pageButton2 = pageButton(next, new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda6
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.lambda$66();
                    }
                });
                arrayList.add(pageButton2);
                pageButton2.setMinHeight(dp(52.0f));
                pageButton2.setPadding(dp(12.0f), dp(10.0f), dp(12.0f), dp(10.0f));
                final TextView textView = text7;
                final TextView textView2 = text6;
                pageButton2.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda7
                    @Override // android.view.View.OnClickListener
                    public final void onClick(View view) {
                        MainActivity.this.m60lambda$67$aucommonolauncherMainActivity(next, nextQuestion, textView, textView2, arrayList, pageButton2, view);
                    }
                });
                linearLayout5.addView(pageButton2, new LinearLayout.LayoutParams(-1, -2));
                pageSpace(linearLayout5, 8);
                i3 = -1;
                linearLayout2 = linearLayout5;
                text7 = textView;
                text6 = textView2;
                i4 = 8388611;
                linearLayout = linearLayout;
                i9 = 22;
                f = 48.0f;
                i10 = 8;
            }
            linearLayout2.addView(text7);
            pageSpace(linearLayout2, i10);
            linearLayout2.addView(text6);
            pageSpace(linearLayout2, 14);
            linearLayout2.addView(pageButton("Next question", new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda8
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m61lambda$68$aucommonolauncherMainActivity();
                }
            }), new LinearLayout.LayoutParams(i3, dp(f)));
            pageSpace(linearLayout2, 28);
            final TextView text8 = text("Explore a country", i9);
            text8.setGravity(i4);
            linearLayout2.addView(text8);
            final TextView text9 = text("", 21);
            text9.setTextColor(ACCENT);
            text9.setGravity(i4);
            text9.setPadding(0, dp(12.0f), 0, dp(10.0f));
            linearLayout2.addView(text9);
            final CountryMapView countryMapView = new CountryMapView(this, "");
            linearLayout2.addView(countryMapView, new LinearLayout.LayoutParams(i3, dp(320.0f)));
            final TextView text10 = text("", 16);
            text10.setGravity(i4);
            text10.setLineSpacing(dp(3.0f), 1.0f);
            text10.setPadding(0, dp(12.0f), 0, dp(12.0f));
            linearLayout2.addView(text10);
            final LearningContent.Question[] questionArr = new LearningContent.Question[1];
            LinearLayout linearLayout6 = linearLayout2;
            Runnable runnable = new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda9
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m62lambda$69$aucommonolauncherMainActivity(questionArr, text9, text10, countryMapView);
                }
            };
            runnable.run();
            linearLayout6.addView(pageButton("Another country", runnable), new LinearLayout.LayoutParams(i3, dp(f)));
            pageSpace(linearLayout6, i10);
            linearLayout6.addView(pageButton("Open interactive map", new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda10
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.m64lambda$70$aucommonolauncherMainActivity(questionArr);
                }
            }), new LinearLayout.LayoutParams(i3, dp(46.0f)));
            linearLayout6.addView(pageButton("Explore countries ↓", new Runnable() { // from class: au.com.mono.launcher.MainActivity$$ExternalSyntheticLambda11
                @Override // java.lang.Runnable
                public final void run() {
                    scrollView.smoothScrollTo(0, text8.getTop());
                }
            }), 0, new LinearLayout.LayoutParams(i3, dp(46.0f)));
            pageSpace(linearLayout6, 18);
            TextView text11 = text("Choose one of three answers.\nSwipe right to return home.", 12);
            text11.setTextColor(-4598813);
            linearLayout6.addView(text11);
            pageSpace(linearLayout6, 16);
        }
        this.drawer = linearLayout;
        this.home.setVisibility(4);
        if (this.pet != null) {
            this.pet.setVisibility(4);
            this.pet.setActive(false);
        }
        this.root.addView(linearLayout, new FrameLayout.LayoutParams(i3, i3));
    }

    /* renamed from: lambda$64$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m58lambda$64$aucommonolauncherMainActivity(TextView textView) {
        textView.setText(nextQuote());
    }

    /* renamed from: lambda$65$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m59lambda$65$aucommonolauncherMainActivity(String str) {
        this.prefs.edit().putString("learningMode", str).apply();
        showSidePage(1);
    }

    static /* synthetic */ void lambda$66() {
    }

    /* renamed from: lambda$67$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m60lambda$67$aucommonolauncherMainActivity(String str, LearningContent.Question question, TextView textView, TextView textView2, ArrayList arrayList, Button button, View view) {
        int i;
        textView.setText(str.equals(question.answer) ? "Correct!" : "Good try — the answer is " + question.answer);
        textView.setTextColor(-5177354);
        textView.setVisibility(0);
        textView2.setVisibility(0);
        Iterator it = arrayList.iterator();
        while (it.hasNext()) {
            Button button2 = (Button) it.next();
            button2.setEnabled(false);
            button2.setTextColor(-1);
            if (button2.getText().toString().equals(question.answer)) {
                button2.setBackground(bg(-15242914, 14));
            } else if (button2 == button) {
                button2.setBackground(bg(-8960940, 14));
            } else {
                button2.setAlpha(0.55f);
            }
        }
    }

    /* renamed from: lambda$68$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m61lambda$68$aucommonolauncherMainActivity() {
        showSidePage(1);
    }

    /* renamed from: lambda$69$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m62lambda$69$aucommonolauncherMainActivity(LearningContent.Question[] questionArr, TextView textView, TextView textView2, CountryMapView countryMapView) {
        ArrayList<Integer> arrayList = new ArrayList<>();
        for (int i = 0; i < LearningContent.QUESTIONS.length; i++) {
            if (!LearningContent.QUESTIONS[i].country.isEmpty()) {
                arrayList.add(Integer.valueOf(i));
            }
        }
        LearningContent.Question question = LearningContent.QUESTIONS[nextLearningIndex("discovery", arrayList)];
        questionArr[0] = question;
        textView.setText(question.country);
        textView2.setText(question.detail);
        countryMapView.setCountry(question.country);
    }

    /* renamed from: lambda$70$au-com-mono-launcher-MainActivity, reason: not valid java name */
    /* synthetic */ void m64lambda$70$aucommonolauncherMainActivity(LearningContent.Question[] questionArr) {
        safeStart(new Intent("android.intent.action.VIEW", Uri.parse("geo:0,0?q=" + Uri.encode(questionArr[0].country))));
    }

    class FocusClock extends View {
        final Paint ink;

        FocusClock() {
            super(MainActivity.this);
            this.ink = new Paint(1);
            setImportantForAccessibility(1);
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Calendar calendar = Calendar.getInstance();
            boolean is24HourFormat = DateFormat.is24HourFormat(MainActivity.this);
            String format = new SimpleDateFormat(is24HourFormat ? "HH" : "h", Locale.getDefault()).format(calendar.getTime());
            String format2 = new SimpleDateFormat("mm", Locale.getDefault()).format(calendar.getTime());
            float width = getWidth();
            float f = 0.06f * width;
            float height = getHeight() * 0.29f;
            this.ink.setColor(-1);
            this.ink.setTypeface(Typeface.create("sans-serif-light", 0));
            this.ink.setTextSize(0.31f * width);
            canvas.drawText(format, f, height, this.ink);
            float measureText = this.ink.measureText(format);
            this.ink.setTypeface(Typeface.create("sans-serif", 0));
            this.ink.setTextSize(0.14f * width);
            canvas.drawText(format2, measureText + f + MainActivity.this.dp(8.0f), height, this.ink);
            float f2 = 0.073f * width;
            this.ink.setTextSize(f2);
            this.ink.setTypeface(Typeface.create("sans-serif-medium", 0));
            String upperCase = new SimpleDateFormat("dd MMMM", Locale.getDefault()).format(calendar.getTime()).toUpperCase(Locale.getDefault());
            canvas.save();
            canvas.translate(f2 + f, MainActivity.this.dp(20.0f) + height + this.ink.measureText(upperCase));
            canvas.rotate(-90.0f);
            canvas.drawText(upperCase, 0.0f, 0.0f, this.ink);
            canvas.restore();
            this.ink.setTextSize(0.067f * width);
            float f3 = f + (0.17f * width);
            canvas.drawText(new SimpleDateFormat("EEE", Locale.getDefault()).format(calendar.getTime()).toUpperCase(Locale.getDefault()), f3, MainActivity.this.dp(38.0f) + height, this.ink);
            this.ink.setTypeface(Typeface.create("sans-serif", 0));
            this.ink.setTextSize(width * 0.038f);
            this.ink.setColor(-2958879);
            canvas.drawText(String.valueOf(is24HourFormat ? "" : String.valueOf(new SimpleDateFormat("a", Locale.getDefault()).format(calendar.getTime())) + "  ·  ") + (MainActivity.this.batteryPercent >= 0 ? String.valueOf(MainActivity.this.batteryPercent) + "% battery" : ""), f3, height + MainActivity.this.dp(65.0f), this.ink);
            setContentDescription(String.valueOf(DateFormat.getTimeFormat(MainActivity.this).format(calendar.getTime())) + ", " + new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(calendar.getTime()) + ", " + MainActivity.this.batteryPercent + " percent battery");
        }
    }

    class Clock extends View {
        Paint p;

        Clock() {
            super(MainActivity.this);
            this.p = new Paint(3);
            setFocusable(true);
        }

        void hand(Canvas canvas, float f, float f2, float f3, float f4, int i, int i2) {
            double radians = Math.toRadians(f4);
            this.p.setColor(i);
            this.p.setStrokeWidth(MainActivity.this.dp(i2));
            this.p.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(f, f2, f + (((float) Math.sin(radians)) * f3), f2 - (((float) Math.cos(radians)) * f3), this.p);
        }

        @Override // android.view.View
        protected void onDraw(Canvas canvas) {
            float width = getWidth() / 2.0f;
            float height = getHeight() / 2.0f;
            float min = Math.min(width, height) - MainActivity.this.dp(2.0f);
            this.p.setShader(new LinearGradient(0.0f, 0.0f, getWidth(), getHeight(), -434222752, -435149768, Shader.TileMode.CLAMP));
            canvas.drawCircle(width, height, min, this.p);
            this.p.setShader(null);
            this.p.setStyle(Paint.Style.STROKE);
            this.p.setStrokeWidth(MainActivity.this.dp(1.0f));
            this.p.setColor(-2141680989);
            canvas.drawCircle(width, height, min, this.p);
            this.p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 60; i++) {
                double d = (i * 3.141592653589793d) / 30.0d;
                int i2 = i % 5;
                float dp = MainActivity.this.dp(i2 == 0 ? 8 : 3);
                this.p.setColor(-4598813);
                this.p.setStrokeWidth(MainActivity.this.dp(i2 == 0 ? 1.5f : 1.0f));
                canvas.drawLine((((float) Math.sin(d)) * (min - MainActivity.this.dp(10.0f))) + width, height - (((float) Math.cos(d)) * (min - MainActivity.this.dp(10.0f))), (((float) Math.sin(d)) * ((min - MainActivity.this.dp(10.0f)) - dp)) + width, height - (((float) Math.cos(d)) * ((min - MainActivity.this.dp(10.0f)) - dp)), this.p);
            }
            Calendar calendar = Calendar.getInstance();
            hand(canvas, width, height, min * 0.48f, (calendar.get(10) + (calendar.get(12) / 60.0f)) * 30.0f, -1, 3);
            hand(canvas, width, height, min * 0.7f, calendar.get(12) * 6, -1, 2);
            hand(canvas, width, height, min * 0.78f, calendar.get(13) * 6, MainActivity.ACCENT, 1);
            this.p.setColor(MainActivity.ACCENT);
            canvas.drawCircle(width, height, MainActivity.this.dp(3.0f), this.p);
        }
    }

}
