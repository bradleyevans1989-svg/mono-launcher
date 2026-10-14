package au.com.mono.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import au.com.mono.launcher.LearningContent;
import java.util.ArrayList;
import java.util.Random;

/* loaded from: classes.dex */
final class LauncherPet extends View {
    boolean active;
    TextView bubble;
    float downX;
    float downY;
    boolean dragged;
    final Runnable frame;
    final Runnable greet;
    final Runnable hideBubble;
    final Paint ink;
    int lastFact;
    final Moved moved;
    boolean scheduled;
    final int slop;
    boolean speakingEnabled;
    float startX;
    float startY;
    long waveStart;

    interface Moved {
        void save(float f, float f2);
    }

    /* renamed from: lambda$0$au-com-mono-launcher-LauncherPet, reason: not valid java name */
    /* synthetic */ void m2lambda$0$aucommonolauncherLauncherPet() {
        if (this.bubble != null) {
            this.bubble.setVisibility(8);
        }
    }

    boolean canAnimate() {
        return this.active && isAttachedToWindow() && isShown() && getWindowVisibility() == 0;
    }

    LauncherPet(Context context, final Runnable runnable, Moved moved) {
        super(context);
        this.ink = new Paint(3);
        this.speakingEnabled = true;
        this.lastFact = -1;
        this.hideBubble = new Runnable() { // from class: au.com.mono.launcher.LauncherPet$$ExternalSyntheticLambda1
            @Override // java.lang.Runnable
            public final void run() {
                LauncherPet.this.m2lambda$0$aucommonolauncherLauncherPet();
            }
        };
        this.greet = new Runnable() { // from class: au.com.mono.launcher.LauncherPet.1
            @Override // java.lang.Runnable
            public void run() {
                if (LauncherPet.this.canAnimate()) {
                    LauncherPet.this.waveStart = SystemClock.uptimeMillis();
                    LauncherPet.this.removeCallbacks(LauncherPet.this.frame);
                    LauncherPet.this.post(LauncherPet.this.frame);
                    if (LauncherPet.this.speakingEnabled) {
                        LauncherPet.this.showFact();
                    }
                    LauncherPet.this.postDelayed(this, 30000L);
                }
            }
        };
        this.frame = new Runnable() { // from class: au.com.mono.launcher.LauncherPet.2
            @Override // java.lang.Runnable
            public void run() {
                if (LauncherPet.this.canAnimate()) {
                    LauncherPet.this.invalidate();
                    if (SystemClock.uptimeMillis() - LauncherPet.this.waveStart < 2400) {
                        LauncherPet.this.postDelayed(this, 80L);
                    }
                }
            }
        };
        this.moved = moved;
        this.slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setContentDescription("Robot companion. Tap to ask; drag to move.");
        setClickable(true);
        setFocusable(true);
        setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.LauncherPet$$ExternalSyntheticLambda2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                runnable.run();
            }
        });
    }

    void setActive(boolean z) {
        boolean z2 = this.active;
        this.active = z;
        if (canAnimate()) {
            if (z2 && this.scheduled) {
                return;
            }
            removeCallbacks(this.greet);
            this.scheduled = true;
            postDelayed(this.greet, 30000L);
            return;
        }
        removeCallbacks(this.frame);
        removeCallbacks(this.greet);
        this.scheduled = false;
        removeCallbacks(this.hideBubble);
        if (this.bubble != null) {
            this.bubble.setVisibility(8);
        }
    }

    @Override // android.view.View
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setActive(this.active);
    }

    @Override // android.view.View
    protected void onDetachedFromWindow() {
        this.active = false;
        removeCallbacks(this.frame);
        removeCallbacks(this.greet);
        removeCallbacks(this.hideBubble);
        this.scheduled = false;
        this.bubble = null;
        super.onDetachedFromWindow();
    }

    void dispose() {
        this.active = false;
        removeCallbacks(this.frame);
        removeCallbacks(this.greet);
        removeCallbacks(this.hideBubble);
        this.scheduled = false;
        if (this.bubble != null && (this.bubble.getParent() instanceof ViewGroup)) {
            ((ViewGroup) this.bubble.getParent()).removeView(this.bubble);
        }
        this.bubble = null;
    }

    @Override // android.view.View
    protected void onWindowVisibilityChanged(int i) {
        super.onWindowVisibilityChanged(i);
        setActive(this.active);
    }

    @Override // android.view.View
    protected void onVisibilityChanged(View view, int i) {
        super.onVisibilityChanged(view, i);
        if (this.frame != null) {
            setActive(this.active);
        }
    }

    @Override // android.view.View
    public boolean onTouchEvent(MotionEvent motionEvent) {
        switch (motionEvent.getActionMasked()) {
            case 0:
                getParent().requestDisallowInterceptTouchEvent(true);
                this.downX = motionEvent.getRawX();
                this.downY = motionEvent.getRawY();
                this.startX = getX();
                this.startY = getY();
                this.dragged = false;
                if (this.bubble != null) {
                    this.bubble.setVisibility(8);
                    break;
                }
                break;
            case 1:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (!this.dragged) {
                    performClick();
                    break;
                } else {
                    this.moved.save(getX(), getY());
                    break;
                }
            case 2:
                float rawX = motionEvent.getRawX() - this.downX;
                float rawY = motionEvent.getRawY() - this.downY;
                if (Math.hypot(rawX, rawY) > this.slop) {
                    this.dragged = true;
                }
                if (this.dragged) {
                    View view = (View) getParent();
                    setX(Math.max(0.0f, Math.min(view.getWidth() - getWidth(), this.startX + rawX)));
                    setY(Math.max(view.getPaddingTop(), Math.min((view.getHeight() - getHeight()) - view.getPaddingBottom(), this.startY + rawY)));
                    break;
                }
                break;
            case 3:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (this.dragged) {
                    this.moved.save(getX(), getY());
                    break;
                }
                break;
        }
        return true;
    }

    void setFactsEnabled(boolean z) {
        this.speakingEnabled = z;
        if (z || this.bubble == null) {
            return;
        }
        this.bubble.setVisibility(8);
    }

    void showFact() {
        if (!canAnimate() || !(getParent() instanceof FrameLayout)) {
            return;
        }
        FrameLayout frameLayout = (FrameLayout) getParent();
        if (this.bubble == null) {
            this.bubble = new TextView(getContext());
            this.bubble.setTextColor(-1048577);
            this.bubble.setTextSize(13.0f);
            this.bubble.setPadding(dp(12.0f), dp(10.0f), dp(12.0f), dp(10.0f));
            GradientDrawable gradientDrawable = new GradientDrawable();
            gradientDrawable.setColor(-233490356);
            gradientDrawable.setCornerRadius(dp(15.0f));
            gradientDrawable.setStroke(dp(1.0f), -9247761);
            this.bubble.setBackground(gradientDrawable);
            this.bubble.setElevation(dp(5.0f));
            this.bubble.setOnClickListener(new View.OnClickListener() { // from class: au.com.mono.launcher.LauncherPet$$ExternalSyntheticLambda0
                @Override // android.view.View.OnClickListener
                public final void onClick(View view) {
                    LauncherPet.this.m3lambda$2$aucommonolauncherLauncherPet(view);
                }
            });
            frameLayout.addView(this.bubble, new FrameLayout.LayoutParams(Math.min(dp(235.0f), frameLayout.getWidth() - dp(16.0f)), -2));
        }
        ArrayList arrayList = new ArrayList();
        for (LearningContent.Question question : LearningContent.QUESTIONS) {
            if (!question.country.isEmpty()) {
                arrayList.add(question);
            }
        }
        int nextInt = new Random().nextInt(arrayList.size());
        if (nextInt == this.lastFact) {
            nextInt = (nextInt + 1) % arrayList.size();
        }
        this.lastFact = nextInt;
        LearningContent.Question question2 = (LearningContent.Question) arrayList.get(nextInt);
        this.bubble.setText(String.valueOf(question2.country) + "\n" + question2.detail.replace("\n\n", " ") + "\n\nTap for another fact");
        this.bubble.setVisibility(0);
        this.bubble.bringToFront();
        this.bubble.measure(View.MeasureSpec.makeMeasureSpec(Math.min(dp(235.0f), frameLayout.getWidth() - dp(16.0f)), 1073741824), View.MeasureSpec.makeMeasureSpec(0, 0));
        this.bubble.setX(Math.max(dp(8.0f), Math.min((frameLayout.getWidth() - this.bubble.getMeasuredWidth()) - dp(8.0f), (getX() + (getWidth() / 2.0f)) - (this.bubble.getMeasuredWidth() / 2.0f))));
        float y = (getY() - this.bubble.getMeasuredHeight()) - dp(4.0f);
        if (y < dp(38.0f)) {
            y = getY() + getHeight();
        }
        this.bubble.setY(Math.max(dp(38.0f), Math.min((frameLayout.getHeight() - this.bubble.getMeasuredHeight()) - dp(38.0f), y)));
        removeCallbacks(this.hideBubble);
        postDelayed(this.hideBubble, 9000L);
    }

    /* renamed from: lambda$2$au-com-mono-launcher-LauncherPet, reason: not valid java name */
    /* synthetic */ void m3lambda$2$aucommonolauncherLauncherPet(View view) {
        showFact();
    }

    int dp(float f) {
        return (int) ((f * getResources().getDisplayMetrics().density) + 0.5f);
    }

    @Override // android.view.View
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override // android.view.View
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float uptimeMillis = (SystemClock.uptimeMillis() - this.waveStart) / 1000.0f;
        boolean z = uptimeMillis >= 0.0f && uptimeMillis < 2.4f;
        float f = z ? uptimeMillis : 0.0f;
        float sin = z ? ((float) Math.sin(f * 5.0f)) * 1.5f : 0.0f;
        canvas.save();
        canvas.scale(getWidth() / 80.0f, getHeight() / 90.0f);
        this.ink.setColor(1342177280);
        canvas.drawOval(18.0f, 72.0f, 62.0f, 78.0f, this.ink);
        canvas.translate(0.0f, sin);
        this.ink.setColor(-12418149);
        this.ink.setStrokeWidth(3.0f);
        this.ink.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(40.0f, 14.0f, 40.0f, 23.0f, this.ink);
        this.ink.setColor(-8784410);
        canvas.drawCircle(40.0f, 12.0f, 4.0f, this.ink);
        this.ink.setColor(-14518902);
        canvas.drawRoundRect(22.0f, 54.0f, 58.0f, 72.0f, 9.0f, 9.0f, this.ink);
        // Two matching arms hang from the shoulders; the right one swings up to wave.
        canvas.drawRoundRect(14.0f, 54.0f, 21.0f, 67.0f, 3.5f, 3.5f, this.ink);
        this.ink.setColor(-6424337);
        canvas.drawCircle(17.5f, 67.5f, 3.5f, this.ink);
        canvas.save();
        canvas.rotate(z ? (((float) Math.sin(16.0f * f)) * 22.0f) - 140.0f : 0.0f, 62.5f, 56.0f);
        this.ink.setColor(-14518902);
        canvas.drawRoundRect(59.0f, 54.0f, 66.0f, 67.0f, 3.5f, 3.5f, this.ink);
        this.ink.setColor(-6424337);
        canvas.drawCircle(62.5f, 67.5f, 3.5f, this.ink);
        canvas.restore();
        this.ink.setColor(-9708051);
        canvas.drawRoundRect(12.0f, 22.0f, 68.0f, 59.0f, 14.0f, 14.0f, this.ink);
        this.ink.setColor(-15585723);
        canvas.drawRoundRect(18.0f, 29.0f, 62.0f, 52.0f, 10.0f, 10.0f, this.ink);
        this.ink.setColor(-6160400);
        float f2 = (!z || f <= 1.0f || f >= 1.16f) ? 4 : 1;
        float f3 = 39.0f - f2;
        float f4 = f2 + 39.0f;
        canvas.drawOval(26.0f, f3, 33.0f, f4, this.ink);
        canvas.drawOval(47.0f, f3, 54.0f, f4, this.ink);
        this.ink.setStyle(Paint.Style.STROKE);
        this.ink.setStrokeWidth(1.5f);
        canvas.drawArc(35.0f, 40.0f, 45.0f, 46.0f, 10.0f, 160.0f, false, this.ink);
        this.ink.setStyle(Paint.Style.FILL);
        this.ink.setColor(-4915209);
        canvas.drawCircle(40.0f, 63.0f, 2.5f, this.ink);
        this.ink.setColor(-12414552);
        canvas.drawRoundRect(22.0f, 70.0f, 34.0f, 76.0f, 4.0f, 4.0f, this.ink);
        canvas.drawRoundRect(46.0f, 70.0f, 58.0f, 76.0f, 4.0f, 4.0f, this.ink);
        canvas.restore();
    }
}
