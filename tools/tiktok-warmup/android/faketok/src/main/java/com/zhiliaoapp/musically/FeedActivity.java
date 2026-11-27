package com.zhiliaoapp.musically;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Имитация ленты TikTok: свайп вверх — следующее «видео», двойной тап — лайк. Всё пишется в logcat. */
public class FeedActivity extends Activity {
    private static final String TAG = "WARMUP_TEST";
    private int video = 0, likes = 0;
    private float downY;
    private TextView title;
    private GestureDetector gd;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF101010);
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new FrameLayout.LayoutParams(-1, -1));

        // правая колонка кнопок, как у TikTok (подписи для спецвозможностей)
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.addView(button("❤", "Like video. 12K likes"));
        rail.addView(button("💬", "Read or add comments. 34 comments"));
        rail.addView(button("★", "Add or remove this video from Favorites"));
        rail.addView(button("↗", "Share video. 5 shares"));
        root.addView(rail, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
        setContentView(root);
        show();

        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                likes++;
                Log.i(TAG, "LIKE " + likes + " on video " + video);
                show();
                return true;
            }
        });
    }

    private TextView button(String icon, String desc) {
        TextView t = new TextView(this);
        t.setText(icon);
        t.setTextSize(26);
        t.setTextColor(Color.WHITE);
        t.setPadding(30, 40, 30, 40);
        t.setContentDescription(desc);
        t.setClickable(true);
        t.setOnClickListener(v -> Log.i(TAG, "CLICK " + desc));
        return t;
    }

    private void show() {
        title.setText("Видео #" + video + "\n❤ " + likes);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        gd.onTouchEvent(ev);
        int act = ev.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            Log.i(TAG, "TOUCH " + MotionEvent.actionToString(act) + " x=" + (int) ev.getX() + " y=" + (int) ev.getY()
                    + " h=" + getWindow().getDecorView().getHeight());
        }
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) downY = ev.getY();
        if (ev.getActionMasked() == MotionEvent.ACTION_UP && downY - ev.getY() > 300) {
            video++;
            Log.i(TAG, "SWIPE " + video);
            show();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "PAUSED (TikTok свёрнут)");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "RESUMED");
    }
}
