package com.example.coffeecheckai.utils;

import android.content.Context;
import android.content.SharedPreferences;

/** Đọc/ghi cài đặt người dùng (SharedPreferences), dùng chung cho Scan và Settings. */
public class AppSettings {

    public static final int MODE_AUTO = 0;      // phát hiện + phân loại
    public static final int MODE_DETECT = 1;    // chỉ phát hiện hạt
    public static final int MODE_CLASSIFY = 2;  // chỉ phân loại (cả ảnh là 1 hạt)

    public static final int DETECTOR_YOLO = 0;    // YOLO (bean_detector.tflite)
    public static final int DETECTOR_OPENCV = 1;  // OpenCV contour (Chương 2)

    private static final String PREFS = "coffeecheck_settings";
    private static final String KEY_THRESHOLD = "confidence_threshold";
    private static final String KEY_MODE = "scan_mode";
    private static final String KEY_DETECTOR = "detector_method";

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Ngưỡng độ tin cậy tối thiểu để lưu một lần quét vào lịch sử (0 - 1). */
    public float getThreshold() {
        return prefs.getFloat(KEY_THRESHOLD, 0.5f);
    }

    public void setThreshold(float value) {
        prefs.edit().putFloat(KEY_THRESHOLD, value).apply();
    }

    public int getMode() {
        return prefs.getInt(KEY_MODE, MODE_AUTO);
    }

    public void setMode(int mode) {
        prefs.edit().putInt(KEY_MODE, mode).apply();
    }

    /** Phương pháp tìm hạt: YOLO (mặc định) hoặc OpenCV contour. */
    public int getDetector() {
        return prefs.getInt(KEY_DETECTOR, DETECTOR_YOLO);
    }

    public void setDetector(int detector) {
        prefs.edit().putInt(KEY_DETECTOR, detector).apply();
    }
}
