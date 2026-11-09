package org.bleremote;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Downloads one URL to a file (used for Google Fonts). Returns "" on success, otherwise a short error text. */
public class FontDownloader {
    public static String download(Activity a, String url, String dest, String ua) {
        HttpURLConnection c = null;
        try {
            // The app pins itself to the Wi-Fi of the Receiver; that Wi-Fi may have no internet, so use the default network here.
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    ConnectivityManager cm = (ConnectivityManager) a.getSystemService(Context.CONNECTIVITY_SERVICE);
                    cm.bindProcessToNetwork(null);
                }
            } catch (Exception e) { }
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", ua);
            int code = c.getResponseCode();
            if (code != 200) return "HTTP " + code;
            InputStream in = c.getInputStream();
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[16384];
            int n;
            long total = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                total += n;
                if (total > 30000000L) { out.close(); in.close(); return "too large"; }
            }
            out.close();
            in.close();
            return "";
        } catch (Exception e) {
            return String.valueOf(e);
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
