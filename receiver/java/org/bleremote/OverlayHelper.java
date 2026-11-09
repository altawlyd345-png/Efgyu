package org.bleremote;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Text / ticker / image / box layers drawn ABOVE the native video players.
 * (A native video surface always covers Kivy widgets, so layers over a video must be native views too.)
 * Coordinates are screen fractions; y is measured from the bottom, like everywhere else in the app.
 */
public class OverlayHelper {
    static FrameLayout root;

    static void removeRoot() {
        try {
            if (root != null && root.getParent() != null) ((ViewGroup) root.getParent()).removeView(root);
        } catch (Exception e) { }
        root = null;
    }

    public static void clear(final Activity a) {
        a.runOnUiThread(new Runnable() { public void run() { removeRoot(); } });
    }

    static int parseColor(String hex, int def) {
        try { return Color.parseColor(hex); } catch (Exception e) { return def; }
    }

    static int withAlpha(int rgb, double opacity) {
        int al = (int) Math.round(255.0 * Math.max(0.0, Math.min(1.0, opacity)));
        return (al << 24) | (rgb & 0x00FFFFFF);
    }

    static Bitmap loadBitmap(String path, int maxW, int maxH) {
        try {
            BitmapFactory.Options op = new BitmapFactory.Options();
            op.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, op);
            int s = 1;
            while (op.outWidth / (2 * s) >= maxW && op.outHeight / (2 * s) >= maxH) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            return BitmapFactory.decodeFile(path, o2);
        } catch (Throwable e) {
            return null;
        }
    }

    /** layersJson: JSON array of layer objects prepared by the Receiver (see receiver_app.video_overlay). */
    public static void show(final Activity a, final String layersJson) {
        a.runOnUiThread(new Runnable() {
            public void run() {
                try {
                    removeRoot();
                    JSONArray arr = new JSONArray(layersJson);
                    if (arr.length() == 0) return;
                    FrameLayout r = new FrameLayout(a);
                    a.addContentView(r, new ViewGroup.LayoutParams(-1, -1));
                    root = r;
                    DisplayMetrics dm = a.getResources().getDisplayMetrics();
                    int pw = dm.widthPixels, ph = dm.heightPixels;
                    for (int i = 0; i < arr.length(); i++) {
                        try { addLayer(a, r, arr.getJSONObject(i), pw, ph); } catch (Exception e) { }
                    }
                } catch (Exception e) { }
            }
        });
    }

    static void addLayer(Activity a, FrameLayout r, JSONObject o, int pw, int ph) throws Exception {
        String kind = o.optString("kind", "text");
        double x = o.optDouble("x", 0.0), y = o.optDouble("y", 0.0);
        double w = o.optDouble("w", 1.0), h = o.optDouble("h", 0.2);
        int wp = (int) Math.max(1, Math.min(20000, Math.round(w * pw)));
        int hp = (int) Math.max(1, Math.min(20000, Math.round(h * ph)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(wp, hp);
        lp.leftMargin = (int) Math.round(x * pw);
        lp.topMargin = (int) Math.round((1.0 - y - h) * ph);
        String bgHex = o.optString("bg", "");
        double bgOp = o.optDouble("bg_opacity", 1.0);
        View v;
        if (kind.equals("image")) {
            String path = o.optString("path", "");
            if (path.length() == 0) return;
            Bitmap b = loadBitmap(path, Math.min(wp, pw), Math.min(hp, ph));
            if (b == null) return;
            ImageView iv = new ImageView(a);
            iv.setImageBitmap(b);
            String fit = o.optString("fit", "contain");
            if (fit.equals("fill")) iv.setScaleType(ImageView.ScaleType.FIT_XY);
            else if (fit.equals("cover")) iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            else iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            v = iv;
        } else if (kind.equals("box")) {
            v = new View(a);
        } else {
            TextView t = new TextView(a);
            t.setText(o.optString("text", ""));
            t.setTextColor(parseColor(o.optString("color", "#FFFFFF"), Color.WHITE));
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) Math.max(6.0, o.optDouble("size", 0.08) * ph));
            String al = o.optString("align", "center");
            int g = Gravity.CENTER;
            if (al.equals("right")) g = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            else if (al.equals("left")) g = Gravity.LEFT | Gravity.CENTER_VERTICAL;
            t.setGravity(g);
            if (kind.equals("ticker") && o.optBoolean("scroll", true)) {
                t.setSingleLine(true);
                t.setHorizontallyScrolling(true);
                t.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                t.setMarqueeRepeatLimit(-1);
                t.setSelected(true);
            }
            v = t;
        }
        if (kind.equals("box") || bgHex.length() > 0) {
            v.setBackgroundColor(withAlpha(parseColor(bgHex.length() > 0 ? bgHex : "#000000", Color.BLACK), bgOp));
        }
        float op = (float) Math.max(0.0, Math.min(1.0, o.optDouble("opacity", 1.0)));
        double rot = o.optDouble("rotation", 0.0);
        if (rot != 0.0) v.setRotation((float) (-rot));      // Kivy turns counter-clockwise, Android clockwise
        r.addView(v, lp);
        final View fv = v;
        long dur = (long) Math.round(1000.0 * Math.max(0.1, Math.min(5.0, o.optDouble("dur", 0.6))));
        if (!o.optString("anim_in", "none").equals("none")) {
            fv.setAlpha(0f);
            fv.animate().alpha(op).setDuration(dur).start();
        } else {
            fv.setAlpha(op);
        }
        double hold = o.optDouble("hold", 0.0);
        if (hold > 0.0) {
            final long fd = dur;
            fv.postDelayed(new Runnable() {
                public void run() { fv.animate().alpha(0f).setDuration(fd).start(); }
            }, (long) Math.round(hold * 1000.0));
        }
    }
}
