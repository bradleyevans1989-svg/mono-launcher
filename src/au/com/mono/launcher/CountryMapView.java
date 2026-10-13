package au.com.mono.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import au.com.mono.launcher.CountryMapView;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

/* loaded from: classes.dex */
final class CountryMapView extends View {
    static volatile ArrayList<Area> cached;
    static final Object lock = new Object();
    final float density;
    boolean failed;
    final Paint ink;
    String selected;

    static final class Area {
        float lat;
        float lon;
        String name;
        final ArrayList<Path> paths = new ArrayList<>();
        final RectF bounds = new RectF();

        Area() {
        }
    }

    CountryMapView(final Context context, String str) {
        super(context);
        this.ink = new Paint(3);
        this.density = getResources().getDisplayMetrics().density;
        setCountry(str);
        if (cached == null) {
            new Thread(new Runnable() { // from class: au.com.mono.launcher.CountryMapView$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    CountryMapView.this.m0lambda$0$aucommonolauncherCountryMapView(context);
                }
            }, "Mono offline map").start();
        }
    }

    /* renamed from: lambda$0$au-com-mono-launcher-CountryMapView, reason: not valid java name */
    /* synthetic */ void m0lambda$0$aucommonolauncherCountryMapView(Context context) {
        Throwable th;
        try {
            synchronized (lock) {
                if (cached == null) {
                    ArrayList<Area> arrayList = new ArrayList<>();
                    try {
                        DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(context.getAssets().open("countries.bin")));
                        try {
                            int readInt = dataInputStream.readInt();
                            for (int i = 0; i < readInt; i++) {
                                Area area = new Area();
                                area.name = dataInputStream.readUTF();
                                area.lon = dataInputStream.readFloat();
                                area.lat = dataInputStream.readFloat();
                                int readInt2 = dataInputStream.readInt();
                                for (int i2 = 0; i2 < readInt2; i2++) {
                                    Path path = new Path();
                                    path.setFillType(Path.FillType.EVEN_ODD);
                                    int readInt3 = dataInputStream.readInt();
                                    for (int i3 = 0; i3 < readInt3; i3++) {
                                        int readInt4 = dataInputStream.readInt();
                                        for (int i4 = 0; i4 < readInt4; i4++) {
                                            float readFloat = dataInputStream.readFloat();
                                            float f = -dataInputStream.readFloat();
                                            if (i4 == 0) {
                                                path.moveTo(readFloat, f);
                                            } else {
                                                path.lineTo(readFloat, f);
                                            }
                                        }
                                        path.close();
                                    }
                                    RectF rectF = new RectF();
                                    path.computeBounds(rectF, true);
                                    if (area.paths.isEmpty()) {
                                        area.bounds.set(rectF);
                                    } else {
                                        area.bounds.union(rectF);
                                    }
                                    area.paths.add(path);
                                }
                                arrayList.add(area);
                            }
                            dataInputStream.close();
                            cached = arrayList;
                        } catch (Throwable th2) {
                            th = th2;
                            try {
                                dataInputStream.close();
                                throw th;
                            } catch (Throwable th3) {
                                th = th3;
                                if (th == null) {
                                    throw th;
                                }
                                if (th == th) {
                                    throw th;
                                }
                                th.addSuppressed(th);
                                throw th;
                            }
                        }
                    } catch (Throwable th4) {
                        th = th4;
                        th = null;
                    }
                }
            }
        } catch (Exception e) {
            this.failed = true;
        }
        postInvalidate();
    }

    void setCountry(String str) {
        this.selected = str;
        setContentDescription("Map highlighting " + str + " in cyan, with nearby countries and a world locator");
        invalidate();
    }

    float dp(float f) {
        return f * this.density;
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        Area area;
        super.onDraw(canvas);
        canvas.drawColor(-16179406);
        ArrayList<Area> arrayList = cached;
        this.ink.setTypeface(Typeface.create("sans-serif", 0));
        this.ink.setTextAlign(Paint.Align.CENTER);
        this.ink.setTextSize(dp(13.0f));
        this.ink.setColor(-4598813);
        if (arrayList == null) {
            canvas.drawText(this.failed ? "Map unavailable" : "Loading offline map…", getWidth() / 2.0f, getHeight() / 2.0f, this.ink);
            return;
        }
        Iterator<Area> it = arrayList.iterator();
        while (true) {
            if (!it.hasNext()) {
                area = null;
                break;
            }
            Area next = it.next();
            if (next.name.equals(this.selected)) {
                area = next;
                break;
            }
        }
        if (area == null) {
            canvas.drawText("Map unavailable for " + this.selected, getWidth() / 2.0f, getHeight() / 2.0f, this.ink);
            return;
        }
        RectF rectF = new RectF(dp(8.0f), dp(26.0f), getWidth() - dp(8.0f), getHeight() * 0.66f);
        float max = Math.max(12.0f, Math.max(area.bounds.width(), area.bounds.height()) * 1.8f);
        drawMap(canvas, arrayList, area, rectF, area.lon, -area.lat, max, max, true);
        drawMap(canvas, arrayList, area, new RectF(dp(8.0f), getHeight() * 0.69f, getWidth() - dp(8.0f), getHeight() - dp(20.0f)), 0.0f, -12.5f, 360.0f, 155.0f, false);
        this.ink.setStyle(Paint.Style.FILL);
        this.ink.setTextAlign(Paint.Align.LEFT);
        this.ink.setColor(-9247761);
        this.ink.setTextSize(dp(12.0f));
        canvas.drawText(String.valueOf(this.selected) + " · highlighted", dp(10.0f), dp(17.0f), this.ink);
        this.ink.setColor(-4598813);
        this.ink.setTextSize(dp(9.0f));
        canvas.drawText("World locator · Natural Earth · simplified boundaries", dp(10.0f), getHeight() - dp(5.0f), this.ink);
    }

    void drawMap(Canvas canvas, ArrayList<Area> arrayList, final Area area, RectF rectF, float f, float f2, float f3, float f4, boolean z) {
        float f5;
        RectF rectF2 = rectF;
        float min = Math.min(rectF.width() / f3, rectF.height() / f4);
        float centerX = rectF.centerX() - (f * min);
        float centerY = rectF.centerY() - (f2 * min);
        RectF rectF3 = new RectF((rectF2.left - centerX) / min, (rectF2.top - centerY) / min, (rectF2.right - centerX) / min, (rectF2.bottom - centerY) / min);
        canvas.save();
        canvas.clipRect(rectF2);
        canvas.translate(centerX, centerY);
        canvas.scale(min, min);
        Iterator<Area> it = arrayList.iterator();
        while (it.hasNext()) {
            float f6 = centerX;
            Area next = it.next();
            if (RectF.intersects(rectF3, next.bounds)) {
                Iterator<Path> it2 = next.paths.iterator();
                while (it2.hasNext()) {
                    Path next2 = it2.next();
                    this.ink.setStyle(Paint.Style.FILL);
                    this.ink.setColor(next == area ? -10690342 : -14070684);
                    canvas.drawPath(next2, this.ink);
                    this.ink.setStyle(Paint.Style.STROKE);
                    this.ink.setStrokeWidth(dp(z ? 0.65f : 0.35f) / min);
                    this.ink.setColor(-9267285);
                    canvas.drawPath(next2, this.ink);
                }
                rectF2 = rectF;
                centerX = f6;
            } else {
                rectF2 = rectF;
                centerX = f6;
            }
        }
        canvas.restore();
        this.ink.setStyle(Paint.Style.FILL);
        float f7 = (area.lon * min) + centerX;
        float f8 = ((-area.lat) * min) + centerY;
        this.ink.setStyle(Paint.Style.STROKE);
        this.ink.setStrokeWidth(dp(1.4f));
        this.ink.setColor(-5177354);
        canvas.drawCircle(f7, f8, dp(z ? 5 : 4), this.ink);
        this.ink.setStyle(Paint.Style.FILL);
        if (z) {
            ArrayList arrayList2 = new ArrayList(arrayList);
            arrayList2.sort(new Comparator() { // from class: au.com.mono.launcher.CountryMapView$$ExternalSyntheticLambda0
                @Override // java.util.Comparator
                public final int compare(Object obj, Object obj2) {
                    return CountryMapView.this.m1lambda$1$aucommonolauncherCountryMapView(area, (CountryMapView.Area) obj, (CountryMapView.Area) obj2);
                }
            });
            ArrayList arrayList3 = new ArrayList();
            Iterator it3 = arrayList2.iterator();
            int i = 0;
            while (it3.hasNext()) {
                Area area2 = (Area) it3.next();
                float f9 = (area2.lon * min) + centerX;
                float f10 = ((-area2.lat) * min) + centerY;
                if (!rectF2.contains(f9, f10)) {
                    f5 = centerX;
                } else if (area2.name.equals("Antarctica")) {
                    f5 = centerX;
                } else {
                    this.ink.setTextSize(dp(area2 == area ? 12 : 10));
                    this.ink.setTypeface(Typeface.create("sans-serif", area2 == area ? 1 : 0));
                    float measureText = this.ink.measureText(area2.name) / 2.0f;
                    f5 = centerX;
                    float max = Math.max(rectF2.left + measureText + dp(3.0f), Math.min((rectF2.right - measureText) - dp(3.0f), f9));
                    float max2 = Math.max(rectF2.top + dp(14.0f), Math.min(rectF2.bottom - dp(4.0f), f10 + dp(16.0f)));
                    RectF rectF4 = new RectF((max - measureText) - dp(3.0f), max2 - dp(12.0f), measureText + max + dp(3.0f), max2 + dp(3.0f));
                    Iterator it4 = arrayList3.iterator();
                    boolean z2 = false;
                    while (it4.hasNext()) {
                        if (RectF.intersects((RectF) it4.next(), rectF4)) {
                            z2 = true;
                        }
                    }
                    if (!z2) {
                        arrayList3.add(rectF4);
                        this.ink.setColor(-435609806);
                        canvas.drawRoundRect(rectF4, dp(3.0f), dp(3.0f), this.ink);
                        this.ink.setTextAlign(Paint.Align.CENTER);
                        this.ink.setColor(area2 == area ? -5177354 : -1970700);
                        canvas.drawText(area2.name, max, max2, this.ink);
                        i++;
                        if (i == 8) {
                            return;
                        }
                        rectF2 = rectF;
                        centerX = f5;
                    }
                }
                rectF2 = rectF;
                centerX = f5;
            }
        }
    }

    /* renamed from: lambda$1$au-com-mono-launcher-CountryMapView, reason: not valid java name */
    /* synthetic */ int m1lambda$1$aucommonolauncherCountryMapView(Area area, Area area2, Area area3) {
        return Double.compare(distance(area2, area), distance(area3, area));
    }

    double distance(Area area, Area area2) {
        return Math.pow(area.lon - area2.lon, 2.0d) + Math.pow(area.lat - area2.lat, 2.0d);
    }
}
