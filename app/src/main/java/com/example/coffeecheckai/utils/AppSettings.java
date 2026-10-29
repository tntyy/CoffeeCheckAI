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
    private static final String KEY_ONBOARDING = "onboarding_done";
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

    /** Đã xem màn giới thiệu lần đầu chưa. */
    public boolean isOnboardingDone() {
        return prefs.getBoolean(KEY_ONBOARDING, false);
    }

    public void setOnboardingDone(boolean done) {
        prefs.edit().putBoolean(KEY_ONBOARDING, done).apply();
    }

    // ---------------- Cài đặt mở rộng ----------------

    /** Ngưỡng phát hiện hạt của YOLO (0.1 - 0.9). */
    public float getDetectThreshold() { return prefs.getFloat("detect_threshold", 0.4f); }
    public void setDetectThreshold(float v) { prefs.edit().putFloat("detect_threshold", v).apply(); }

    public boolean isQualityCheck() { return prefs.getBoolean("quality_check", true); }
    public void setQualityCheck(boolean v) { prefs.edit().putBoolean("quality_check", v).apply(); }

    /** Tăng cường ảnh (OpenCV) chỉ áp dụng cho ảnh kết quả hiển thị. */
    public boolean isEnhanceDisplay() { return prefs.getBoolean("enhance_display", true); }
    public void setEnhanceDisplay(boolean v) { prefs.edit().putBoolean("enhance_display", v).apply(); }

    // Quy tắc xếp loại (tỷ lệ lỗi %)
    public float getGradeGood() { return prefs.getFloat("grade_good", 0f); }
    public float getGradeFair() { return prefs.getFloat("grade_fair", 10f); }
    public float getGradeMid() { return prefs.getFloat("grade_mid", 25f); }
    public void setGradeLimits(float good, float fair, float mid) {
        prefs.edit().putFloat("grade_good", good).putFloat("grade_fair", fair)
                .putFloat("grade_mid", mid).apply();
    }
    public void resetGradeLimits() { setGradeLimits(0f, 10f, 25f); }

    // Camera
    public boolean isFlashDefault() { return prefs.getBoolean("flash_default", false); }
    public void setFlashDefault(boolean v) { prefs.edit().putBoolean("flash_default", v).apply(); }
    public boolean isGrid() { return prefs.getBoolean("grid", true); }
    public void setGrid(boolean v) { prefs.edit().putBoolean("grid", v).apply(); }
    public boolean isVibrate() { return prefs.getBoolean("vibrate", true); }
    public void setVibrate(boolean v) { prefs.edit().putBoolean("vibrate", v).apply(); }

    // Dữ liệu
    public boolean isAutoSave() { return prefs.getBoolean("auto_save", true); }
    public void setAutoSave(boolean v) { prefs.edit().putBoolean("auto_save", v).apply(); }
    /** Số ngày giữ lịch sử; 0 = không tự xóa. */
    public int getKeepDays() { return prefs.getInt("keep_days", 0); }
    public void setKeepDays(int d) { prefs.edit().putInt("keep_days", d).apply(); }

    // Hồ sơ (in lên báo cáo PDF)
    public String getInspector() { return prefs.getString("inspector", ""); }
    public void setInspector(String v) { prefs.edit().putString("inspector", v).apply(); }
    public String getFacility() { return prefs.getString("facility", ""); }
    public void setFacility(String v) { prefs.edit().putString("facility", v).apply(); }

    /** Xếp loại theo tỷ lệ lỗi (%) và các ngưỡng trong Cài đặt. */
    public String gradeFor(float defectRate) {
        if (defectRate <= getGradeGood()) return "Tốt";
        if (defectRate <= getGradeFair()) return "Khá";
        if (defectRate <= getGradeMid()) return "Trung bình";
        return "Cần kiểm tra";
    }
}
