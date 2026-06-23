package com.lumi.app.image;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ImageSaveManager {

    private final Context appContext;

    public ImageSaveManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public Uri saveToGallery(String filePath) throws Exception {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("empty image path");
        }
        File src = new File(filePath);
        if (!src.exists() || !src.isFile()) {
            throw new IllegalArgumentException("image file not found");
        }

        String displayName = "lumi_" + System.currentTimeMillis() + ".png";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Lumi");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri uri = appContext.getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("gallery insert failed");

        try (InputStream in = new FileInputStream(src);
             OutputStream out = appContext.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("gallery output stream null");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            appContext.getContentResolver().update(uri, done, null, null);
        }
        return uri;
    }
}