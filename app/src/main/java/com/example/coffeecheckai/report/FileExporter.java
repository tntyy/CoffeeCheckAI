package com.example.coffeecheckai.report;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Lưu file báo cáo vào Tải xuống và chia sẻ qua FileProvider. */
public class FileExporter {

    public static File cacheFile(Context c, String name) {
        File dir = new File(c.getCacheDir(), "reports");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, name);
    }

    /** Chép file vào thư mục Tải xuống. Trả về mô tả vị trí lưu. */
    public static String saveToDownloads(Context c, File src, String mime) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, src.getName());
            v.put(MediaStore.Downloads.MIME_TYPE, mime);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CoffeeCheckAI");
            Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("Không tạo được file");
            try (OutputStream out = c.getContentResolver().openOutputStream(uri)) {
                copy(src, out);
            }
            return "Tải xuống/CoffeeCheckAI/" + src.getName();
        }
        File dir = c.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) throw new IOException("Không có bộ nhớ ngoài");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File dst = new File(dir, src.getName());
        try (OutputStream out = new java.io.FileOutputStream(dst)) {
            copy(src, out);
        }
        return dst.getAbsolutePath();
    }

    public static Intent shareIntent(Context c, File f, String mime) {
        Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", f);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(i, "Chia sẻ");
    }

    private static void copy(File src, OutputStream out) throws IOException {
        try (FileInputStream in = new FileInputStream(src)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
}
