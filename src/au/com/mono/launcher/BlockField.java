package au.com.mono.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * The home screen background: an isometric field of rounded blocks that ripples under your finger.
 *
 * Each block is a damped spring pulled towards rest and towards its four neighbours, so a touch
 * lifts the blocks under it, the swell spreads outwards, overshoots and settles. Blocks brighten as
 * they rise. The view only animates while the field is moving and draws a single still frame
 * otherwise, so it costs nothing at rest.
 */
final class BlockField extends View {
    static final int BACKGROUND = 0xff070f1d;
    static final float[] STOPS = {0f, 0.18f, 0.4f, 0.58f, 0.76f, 1f};
    static final int[] COLORS = {0xff17596b, 0xff1f7186, 0xff46337a, 0xff6c2766, 0xff231a40, 0xff0b1428};

    static final float STIFFNESS = 26f;
    static final float COUPLING = 240f;
    static final float DAMPING = 2.4f;
    static final float STEP = 1f / 120f;

    final float density;
    final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path top = new Path();
    final Path body = new Path();
    final Path rightSide = new Path();

    float tileW;
    float tileH;
    float depth;
    float lift;
    int i0;
    int j0;
    int cols;
    int rows;
    float[] z = new float[0];
    float[] v = new float[0];
    byte[] marks = new byte[0];

    boolean moving;
    /** Lively fields drift through colours and ripple on their own; still ones (wallpaper, Calm) only answer touch. */
    boolean lively;
    Quiet quiet;
    final long born = SystemClock.uptimeMillis();
    long nextDrop;
    final java.util.Random random = new java.util.Random();
    float time;

    /** Lets the launcher hold the ambient motion while the field is covered, or when the user picked Calm. */
    interface Quiet {
        boolean quiet();
    }
    boolean paused;
    long lastFrame;
    float lastPokeX = Float.NaN;
    float lastPokeY = Float.NaN;

    BlockField(Context context) {
        super(context);
        this.density = context.getResources().getDisplayMetrics().density;
        this.rim.setStyle(Paint.Style.STROKE);
        this.rim.setStrokeWidth(Math.max(1f, this.density * 0.8f));
        this.glyph.setStyle(Paint.Style.STROKE);
        this.glyph.setStrokeCap(Paint.Cap.ROUND);
        this.glyph.setStrokeWidth(this.density * 1.6f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutGrid(w, h);
    }

    void layoutGrid(int width, int height) {
        this.tileW = Math.max(24f, Math.min(width / 7.5f, this.density * 58f));
        this.tileH = this.tileW * 0.5f;
        this.depth = this.tileH * 1.1f;
        this.lift = this.tileH * 0.7f;
        buildShapes();
        // Find the iso cells that cover the screen, with a margin for raised and sunken blocks.
        float half = this.tileW / 2f;
        float halfH = this.tileH / 2f;
        float[] xs = {-this.tileW, width + this.tileW};
        float[] ys = {-this.tileH * 2f, height + this.depth + this.tileH * 2f};
        float minI = Float.MAX_VALUE, maxI = -Float.MAX_VALUE, minJ = Float.MAX_VALUE, maxJ = -Float.MAX_VALUE;
        for (float x : xs) {
            for (float y : ys) {
                float u = (x - width / 2f) / half;
                float w = y / halfH;
                float i = (w + u) / 2f;
                float j = (w - u) / 2f;
                minI = Math.min(minI, i);
                maxI = Math.max(maxI, i);
                minJ = Math.min(minJ, j);
                maxJ = Math.max(maxJ, j);
            }
        }
        this.i0 = (int) Math.floor(minI) - 1;
        this.j0 = (int) Math.floor(minJ) - 1;
        this.cols = (int) Math.ceil(maxI) + 2 - this.i0;
        this.rows = (int) Math.ceil(maxJ) + 2 - this.j0;
        int n = this.cols * this.rows;
        this.z = new float[n];
        this.v = new float[n];
        this.marks = new byte[n];
        for (int a = 0; a < this.cols; a++) {
            for (int b = 0; b < this.rows; b++) {
                int hash = ((a + this.i0) * 73856093) ^ ((b + this.j0) * 19349663);
                hash = (hash ^ (hash >>> 13)) * 0x5bd1e995;
                hash = (hash ^ (hash >>> 15)) & 0x7fffffff;
                this.marks[a * this.rows + b] = (byte) (hash % 100 < 9 ? 1 + (hash / 100) % 3 : 0);
            }
        }
        this.moving = false;
    }

    /** Builds the rounded top face, the block's silhouette and its lit right side, centred on the top face. */
    void buildShapes() {
        float hw = this.tileW / 2f * 0.9f;
        float hh = this.tileH / 2f * 0.9f;
        float d = this.depth;
        rounded(this.top, new float[]{0, -hh, hw, 0, 0, hh, -hw, 0}, 0.22f);
        rounded(this.body, new float[]{0, -hh, hw, 0, hw, d, 0, hh + d, -hw, d, -hw, 0}, 0.18f);
        Path half = new Path();
        half.addRect(new RectF(0, -hh, hw + 1, hh + d + 1), Path.Direction.CW);
        this.rightSide.set(this.body);
        this.rightSide.op(half, Path.Op.INTERSECT);
    }

    /** A closed polygon whose corners are rounded off by {@code amount} of each edge. */
    static void rounded(Path path, float[] pts, float amount) {
        path.reset();
        int n = pts.length / 2;
        for (int k = 0; k < n; k++) {
            float px = pts[((k + n - 1) % n) * 2], py = pts[((k + n - 1) % n) * 2 + 1];
            float cx = pts[k * 2], cy = pts[k * 2 + 1];
            float nx = pts[((k + 1) % n) * 2], ny = pts[((k + 1) % n) * 2 + 1];
            float ax = cx + (px - cx) * amount, ay = cy + (py - cy) * amount;
            float bx = cx + (nx - cx) * amount, by = cy + (ny - cy) * amount;
            if (k == 0) {
                path.moveTo(ax, ay);
            } else {
                path.lineTo(ax, ay);
            }
            path.quadTo(cx, cy, bx, by);
        }
        path.close();
    }

    /** Lifts the blocks around a touch. Call with every pointer event; it never consumes them. */
    void touch(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            poke(x, y, 1f);
            this.lastPokeX = x;
            this.lastPokeY = y;
        } else if (action == MotionEvent.ACTION_MOVE && !Float.isNaN(this.lastPokeX)) {
            float moved = (float) Math.hypot(x - this.lastPokeX, y - this.lastPokeY);
            if (moved > this.tileW * 0.35f) {
                poke(x, y, Math.min(0.7f, 0.25f + moved / (this.tileW * 4f)));
                this.lastPokeX = x;
                this.lastPokeY = y;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            this.lastPokeX = Float.NaN;
        }
    }

    void poke(float x, float y, float strength) {
        if (this.cols == 0) {
            return;
        }
        float radius = this.tileW * 1.9f;
        float half = this.tileW / 2f;
        float halfH = this.tileH / 2f;
        float u = (x - getWidth() / 2f) / half;
        float w = y / halfH;
        int ci = Math.round((w + u) / 2f) - this.i0;
        int cj = Math.round((w - u) / 2f) - this.j0;
        int reach = 4;
        for (int a = ci - reach; a <= ci + reach; a++) {
            for (int b = cj - reach; b <= cj + reach; b++) {
                if (a < 0 || b < 0 || a >= this.cols || b >= this.rows) {
                    continue;
                }
                float bx = getWidth() / 2f + ((a + this.i0) - (b + this.j0)) * half;
                float by = ((a + this.i0) + (b + this.j0)) * halfH;
                float dist = (float) Math.hypot(bx - x, (by - y) * 1.6f);
                if (dist < radius) {
                    float fall = 1f - dist / radius;
                    this.v[a * this.rows + b] += 10f * strength * fall * fall;
                }
            }
        }
        if (!this.moving) {
            this.moving = true;
            this.lastFrame = SystemClock.uptimeMillis();
        }
        if (!this.paused) {
            postInvalidateOnAnimation();
        }
    }

    void setPaused(boolean paused) {
        this.paused = paused;
        if (!paused && (this.moving || this.lively)) {
            this.lastFrame = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
    }

    /** Advances the springs by {@code seconds}; returns false once everything has come to rest. */
    boolean step(float seconds) {
        int steps = Math.max(1, Math.min(8, Math.round(seconds / STEP)));
        float[] z = this.z;
        float[] v = this.v;
        int rows = this.rows;
        int cols = this.cols;
        float peak = 0f;
        for (int s = 0; s < steps; s++) {
            for (int a = 0; a < cols; a++) {
                for (int b = 0; b < rows; b++) {
                    int k = a * rows + b;
                    float here = z[k];
                    float sum = (a > 0 ? z[k - rows] : 0f) + (a < cols - 1 ? z[k + rows] : 0f)
                            + (b > 0 ? z[k - 1] : 0f) + (b < rows - 1 ? z[k + 1] : 0f);
                    float accel = -STIFFNESS * here + COUPLING * (sum - 4f * here) - DAMPING * v[k];
                    v[k] += accel * STEP;
                }
            }
            for (int k = 0; k < z.length; k++) {
                z[k] += v[k] * STEP;
            }
        }
        for (int k = 0; k < z.length; k++) {
            peak = Math.max(peak, Math.max(Math.abs(z[k]), Math.abs(v[k]) * 0.1f));
        }
        if (peak < 0.003f) {
            java.util.Arrays.fill(z, 0f);
            java.util.Arrays.fill(v, 0f);
            return false;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        canvas.drawColor(BACKGROUND);
        if (this.cols == 0 || width == 0 || height == 0) {
            return;
        }
        boolean ambient = this.lively && !this.paused && (this.quiet == null || !this.quiet.quiet());
        if (ambient) {
            long now = SystemClock.uptimeMillis();
            this.time = (now - this.born) / 1000f;
            if (now >= this.nextDrop) {
                this.nextDrop = now + 900 + this.random.nextInt(1600);
                poke(this.random.nextFloat() * width, height * (0.05f + this.random.nextFloat() * 0.9f), 0.45f + this.random.nextFloat() * 0.45f);
            }
        }
        if (this.moving) {
            long now = SystemClock.uptimeMillis();
            float elapsed = Math.min(0.05f, (now - this.lastFrame) / 1000f);
            this.lastFrame = now;
            this.moving = step(elapsed);
        }
        float half = this.tileW / 2f;
        float halfH = this.tileH / 2f;
        float centre = width / 2f;
        int last = this.cols + this.rows - 2;
        // Back to front: blocks further down the screen (larger i + j) overlap the ones behind them.
        for (int s = 0; s <= last; s++) {
            int aFrom = Math.max(0, s - (this.rows - 1));
            int aTo = Math.min(this.cols - 1, s);
            for (int a = aFrom; a <= aTo; a++) {
                int b = s - a;
                int i = a + this.i0;
                int j = b + this.j0;
                float x = centre + (i - j) * half;
                if (x < -this.tileW || x > width + this.tileW) {
                    continue;
                }
                float baseY = (i + j) * halfH;
                if (baseY < -this.tileH * 2f || baseY - this.lift * 1.6f > height + this.tileH) {
                    continue;
                }
                int k = a * this.rows + b;
                float swell = this.lively ? 0.2f * (float) (Math.sin(i * 0.55f + this.time * 1.7f) * Math.cos(j * 0.45f - this.time * 1.2f)) : 0f;
                float h = Math.max(-1.2f, Math.min(1.6f, this.z[k] + swell));
                float y = baseY - h * this.lift;
                drawBlock(canvas, x, y, baseY / height, h, this.marks[k]);
            }
        }
        if ((this.moving || ambient) && !this.paused) {
            postInvalidateOnAnimation();
        }
    }

    void drawBlock(Canvas canvas, float x, float y, float place, float h, int mark) {
        float glow = Math.max(0f, Math.min(1f, h));
        int base = gradient(place);
        if (this.lively) {
            // The whole palette turns slowly through the colour wheel, a band of shifted hue sweeps down the
            // screen, and rising blocks swing further round so ripples flash new colours.
            float degrees = this.time * 4f + 40f * (float) Math.sin(place * 4.7f - this.time * 0.6f) + 70f * glow;
            base = hue(base, degrees);
        }
        float sink = Math.max(0f, Math.min(1f, -h));
        int topColor = mix(base, 0xffbff8ff, 0.06f + 0.55f * glow);
        if (place > 0.35f && place < 0.75f) {
            topColor = mix(base, 0xffffc4ee, 0.06f + 0.55f * glow);
        }
        topColor = mix(topColor, BACKGROUND, 0.35f * sink);
        canvas.translate(x, y);
        this.fill.setColor(shade(base, 0.32f));
        canvas.drawPath(this.body, this.fill);
        this.fill.setColor(shade(base, 0.55f + 0.2f * glow));
        canvas.drawPath(this.rightSide, this.fill);
        this.fill.setColor(topColor);
        canvas.drawPath(this.top, this.fill);
        this.rim.setColor(mix(topColor, 0xffffffff, 0.35f + 0.4f * glow) & 0x00ffffff | ((int) (70 + 150 * glow) << 24));
        canvas.drawPath(this.top, this.rim);
        if (mark != 0) {
            drawMark(canvas, mark, mix(topColor, 0xffffffff, 0.55f + 0.4f * glow));
        }
        canvas.translate(-x, -y);
    }

    /** Draws a small ×, ○ or ✳ lying flat on the block's top face. */
    void drawMark(Canvas canvas, int mark, int color) {
        float r = this.tileW * 0.11f;
        this.glyph.setColor(color);
        canvas.save();
        canvas.scale(1f, 0.5f);
        if (mark == 1) {
            canvas.drawLine(-r, -r, r, r, this.glyph);
            canvas.drawLine(-r, r, r, -r, this.glyph);
        } else if (mark == 2) {
            canvas.drawCircle(0, 0, r, this.glyph);
        } else {
            for (int k = 0; k < 3; k++) {
                double angle = Math.PI / 3 * k + Math.PI / 2;
                float dx = (float) Math.cos(angle) * r * 1.15f;
                float dy = (float) Math.sin(angle) * r * 1.15f;
                canvas.drawLine(-dx, -dy, dx, dy, this.glyph);
            }
        }
        canvas.restore();
    }

    static int hue(int color, float degrees) {
        double rad = Math.toRadians(degrees);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        float r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        float nr = r * (0.213f + 0.787f * c - 0.213f * s) + g * (0.715f - 0.715f * c - 0.715f * s) + b * (0.072f - 0.072f * c + 0.928f * s);
        float ng = r * (0.213f - 0.213f * c + 0.143f * s) + g * (0.715f + 0.285f * c + 0.140f * s) + b * (0.072f - 0.072f * c - 0.283f * s);
        float nb = r * (0.213f - 0.213f * c - 0.787f * s) + g * (0.715f - 0.715f * c + 0.715f * s) + b * (0.072f + 0.928f * c + 0.072f * s);
        return Color.rgb(clamp(nr), clamp(ng), clamp(nb));
    }

    static int clamp(float v) {
        return v < 0f ? 0 : v > 255f ? 255 : (int) v;
    }

    static int gradient(float t) {
        t = Math.max(0f, Math.min(1f, t));
        for (int k = 1; k < STOPS.length; k++) {
            if (t <= STOPS[k]) {
                float f = (t - STOPS[k - 1]) / (STOPS[k] - STOPS[k - 1]);
                return mix(COLORS[k - 1], COLORS[k], f);
            }
        }
        return COLORS[COLORS.length - 1];
    }

    static int mix(int from, int to, float f) {
        f = Math.max(0f, Math.min(1f, f));
        return Color.rgb(
                (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * f),
                (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * f),
                (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f));
    }

    static int shade(int color, float f) {
        return Color.rgb((int) (Color.red(color) * f), (int) (Color.green(color) * f), (int) (Color.blue(color) * f));
    }
}
