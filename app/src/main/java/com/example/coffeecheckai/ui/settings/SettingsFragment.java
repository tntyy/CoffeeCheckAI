package com.example.coffeecheckai.ui.settings;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.coffeecheckai.R;
import com.example.coffeecheckai.data.AppDatabase;
import com.example.coffeecheckai.data.KetQuaKiemTra;
import com.example.coffeecheckai.report.FileExporter;
import com.example.coffeecheckai.utils.AppSettings;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Cài đặt chi tiết: phân tích ảnh, quy tắc xếp loại, camera,
 * dữ liệu & lịch sử, thông tin ứng dụng.
 */
public class SettingsFragment extends Fragment {

    private static final int[] KEEP_DAYS = {0, 30, 90, 180};
    private static final String[] KEEP_LABELS = {"Giữ mãi", "30 ngày", "90 ngày", "180 ngày"};

    private AppSettings settings;
    private View root;
    private final Executor io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        root = inflater.inflate(R.layout.fragment_settings, container, false);
        settings = new AppSettings(requireContext());

        setupProfile();
        setupVersion();
        setupDetector();
        setupMode();
        setupSlider(R.id.sliderThreshold, R.id.textThresholdValue, settings.getThreshold(), "%.2f",
                v -> settings.setThreshold(v));
        setupSlider(R.id.sliderDetect, R.id.textDetectValue, settings.getDetectThreshold(), "%.2f",
                v -> settings.setDetectThreshold(v));
        setupSwitch(R.id.swQuality, settings.isQualityCheck(), settings::setQualityCheck);
        setupSwitch(R.id.swEnhance, settings.isEnhanceDisplay(), settings::setEnhanceDisplay);
        setupGrade();
        setupSwitch(R.id.swFlash, settings.isFlashDefault(), settings::setFlashDefault);
        setupSwitch(R.id.swGrid, settings.isGrid(), settings::setGrid);
        setupSwitch(R.id.swVibrate, settings.isVibrate(), settings::setVibrate);
        setupData();
        setupInfoRows();

        return root;
    }

    private interface FloatSetter { void set(float v); }
    private interface BoolSetter { void set(boolean v); }

    // ---------------- Hồ sơ ----------------

    private void setupProfile() {
        refreshProfile();
        root.findViewById(R.id.cardProfile).setOnClickListener(v -> {
            LinearLayout box = new LinearLayout(requireContext());
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (20 * getResources().getDisplayMetrics().density);
            box.setPadding(pad, pad / 2, pad, 0);
            android.widget.EditText name = new android.widget.EditText(requireContext());
            name.setHint("Người kiểm tra");
            name.setText(settings.getInspector());
            android.widget.EditText facility = new android.widget.EditText(requireContext());
            facility.setHint("Cơ sở / lô hàng");
            facility.setText(settings.getFacility());
            box.addView(name);
            box.addView(facility);
            new AlertDialog.Builder(requireContext())
                    .setTitle("Hồ sơ (in lên báo cáo PDF)")
                    .setView(box)
                    .setPositiveButton("Lưu", (d, w) -> {
                        settings.setInspector(name.getText().toString().trim());
                        settings.setFacility(facility.getText().toString().trim());
                        refreshProfile();
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });
    }

    private void refreshProfile() {
        String n = settings.getInspector();
        String f = settings.getFacility();
        ((TextView) root.findViewById(R.id.textProfileName))
                .setText(n.isEmpty() ? "CoffeeCheck AI" : n);
        ((TextView) root.findViewById(R.id.textProfileSub))
                .setText(f.isEmpty() ? "Chạm để nhập tên người kiểm tra / cơ sở" : "Cơ sở: " + f);
    }

    private void setupVersion() {
        String versionName = "1.0";
        try {
            PackageInfo info = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0);
            if (info.versionName != null) versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            // giữ giá trị mặc định
        }
        ((TextView) root.findViewById(R.id.textVersionBadge)).setText("v" + versionName);
        ((TextView) root.findViewById(R.id.textVersion)).setText(versionName);
    }

    // ---------------- Phân tích ảnh ----------------

    private void setupDetector() {
        RadioGroup group = root.findViewById(R.id.radioGroupDetector);
        group.check(settings.getDetector() == AppSettings.DETECTOR_OPENCV
                ? R.id.radioOpenCv : R.id.radioYolo);
        group.setOnCheckedChangeListener((g, id) -> settings.setDetector(
                id == R.id.radioOpenCv ? AppSettings.DETECTOR_OPENCV : AppSettings.DETECTOR_YOLO));
    }

    private void setupMode() {
        RadioGroup group = root.findViewById(R.id.radioGroupMode);
        switch (settings.getMode()) {
            case AppSettings.MODE_DETECT: group.check(R.id.radioDetect); break;
            case AppSettings.MODE_CLASSIFY: group.check(R.id.radioClassify); break;
            default: group.check(R.id.radioAuto);
        }
        group.setOnCheckedChangeListener((g, id) -> {
            if (id == R.id.radioDetect) settings.setMode(AppSettings.MODE_DETECT);
            else if (id == R.id.radioClassify) settings.setMode(AppSettings.MODE_CLASSIFY);
            else settings.setMode(AppSettings.MODE_AUTO);
        });
    }

    private void setupSlider(int sliderId, int textId, float value, String fmt, FloatSetter setter) {
        Slider slider = root.findViewById(sliderId);
        TextView text = root.findViewById(textId);
        float v = Math.max(slider.getValueFrom(), Math.min(slider.getValueTo(), value));
        slider.setValue(v);
        text.setText(String.format(Locale.US, fmt, v));
        slider.addOnChangeListener((s, val, fromUser) -> {
            text.setText(String.format(Locale.US, fmt, val));
            if (fromUser) setter.set(val);
        });
    }

    private void setupSwitch(int id, boolean value, BoolSetter setter) {
        MaterialSwitch sw = root.findViewById(id);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener((b, checked) -> setter.set(checked));
    }

    // ---------------- Quy tắc xếp loại ----------------

    private void setupGrade() {
        Slider good = root.findViewById(R.id.sliderGradeGood);
        Slider fair = root.findViewById(R.id.sliderGradeFair);
        Slider mid = root.findViewById(R.id.sliderGradeMid);
        good.setValue(clamp(good, settings.getGradeGood()));
        fair.setValue(clamp(fair, settings.getGradeFair()));
        mid.setValue(clamp(mid, settings.getGradeMid()));
        showGrade();

        Slider.OnChangeListener l = (s, v, fromUser) -> {
            if (!fromUser) return;
            // giữ thứ tự Tốt ≤ Khá ≤ Trung bình
            float g = good.getValue(), f = fair.getValue(), m = mid.getValue();
            if (f < g) { f = g; fair.setValue(clamp(fair, f)); }
            if (m < f) { m = f; mid.setValue(clamp(mid, m)); }
            settings.setGradeLimits(g, fair.getValue(), mid.getValue());
            showGrade();
        };
        good.addOnChangeListener(l);
        fair.addOnChangeListener(l);
        mid.addOnChangeListener(l);

        root.findViewById(R.id.rowResetGrade).setOnClickListener(v -> {
            settings.resetGradeLimits();
            good.setValue(0f);
            fair.setValue(10f);
            mid.setValue(25f);
            showGrade();
        });
    }

    private static float clamp(Slider s, float v) {
        return Math.max(s.getValueFrom(), Math.min(s.getValueTo(), v));
    }

    private void showGrade() {
        ((TextView) root.findViewById(R.id.textGradeGood))
                .setText(fmtPct(((Slider) root.findViewById(R.id.sliderGradeGood)).getValue()));
        ((TextView) root.findViewById(R.id.textGradeFair))
                .setText(fmtPct(((Slider) root.findViewById(R.id.sliderGradeFair)).getValue()));
        ((TextView) root.findViewById(R.id.textGradeMid))
                .setText(fmtPct(((Slider) root.findViewById(R.id.sliderGradeMid)).getValue()));
    }

    private static String fmtPct(float v) {
        return v == Math.round(v) ? Math.round(v) + "%" : String.format(Locale.US, "%.1f%%", v);
    }

    // ---------------- Dữ liệu ----------------

    private void setupData() {
        setupSwitch(R.id.swAutoSave, settings.isAutoSave(), settings::setAutoSave);
        refreshKeepDays();
        refreshStorage();

        root.findViewById(R.id.rowKeepDays).setOnClickListener(v -> {
            int checked = 0;
            for (int i = 0; i < KEEP_DAYS.length; i++) {
                if (KEEP_DAYS[i] == settings.getKeepDays()) checked = i;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Tự xóa lịch sử cũ hơn")
                    .setSingleChoiceItems(KEEP_LABELS, checked, (d, which) -> {
                        settings.setKeepDays(KEEP_DAYS[which]);
                        refreshKeepDays();
                        d.dismiss();
                    })
                    .show();
        });

        root.findViewById(R.id.rowExportCsv).setOnClickListener(v -> exportCsv());

        root.findViewById(R.id.rowClearHistory).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Xóa toàn bộ lịch sử?")
                        .setMessage("Tất cả kết quả và ảnh đã lưu sẽ bị xóa, không thể khôi phục.")
                        .setPositiveButton("Xóa", (d, w) -> clearHistory())
                        .setNegativeButton("Hủy", null)
                        .show());
    }

    private void refreshKeepDays() {
        String label = KEEP_LABELS[0];
        for (int i = 0; i < KEEP_DAYS.length; i++) {
            if (KEEP_DAYS[i] == settings.getKeepDays()) label = KEEP_LABELS[i];
        }
        ((TextView) root.findViewById(R.id.textKeepDays)).setText(label);
    }

    private void refreshStorage() {
        final Context app = requireContext().getApplicationContext();
        io.execute(() -> {
            int count = AppDatabase.getInstance(app).ketQuaDao().count();
            long bytes = 0;
            File[] files = new File(app.getFilesDir(), "history").listFiles();
            if (files != null) for (File f : files) bytes += f.length();
            final String text = count + " lượt · "
                    + String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
            main.post(() -> {
                if (isAdded()) ((TextView) root.findViewById(R.id.textStorage)).setText(text);
            });
        });
    }

    private void clearHistory() {
        final Context app = requireContext().getApplicationContext();
        io.execute(() -> {
            AppDatabase.getInstance(app).ketQuaDao().deleteAll();
            File[] files = new File(app.getFilesDir(), "history").listFiles();
            if (files != null) for (File f : files) //noinspection ResultOfMethodCallIgnored
                f.delete();
            main.post(() -> {
                Toast.makeText(app, "Đã xóa lịch sử", Toast.LENGTH_SHORT).show();
                if (isAdded()) refreshStorage();
            });
        });
    }

    private void exportCsv() {
        final Context app = requireContext().getApplicationContext();
        io.execute(() -> {
            try {
                List<KetQuaKiemTra> items = AppDatabase.getInstance(app).ketQuaDao().getAll();
                if (items.isEmpty()) {
                    main.post(() -> Toast.makeText(app, "Chưa có dữ liệu để xuất", Toast.LENGTH_SHORT).show());
                    return;
                }
                File out = FileExporter.cacheFile(app, "LichSu_CoffeeCheck_"
                        + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".csv");
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                try (Writer w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
                    w.write('﻿'); // BOM để Excel đọc đúng tiếng Việt
                    w.write("Thoi gian,Tong hat,Hat tot,Hat loi,Ty le loi (%),Xep loai,Do tin cay TB\n");
                    for (KetQuaKiemTra k : items) {
                        w.write(f.format(new Date(k.thoiGian)) + "," + k.tongHat + "," + k.soFine + ","
                                + k.soDefect + "," + String.format(Locale.US, "%.1f", k.tyLeLoi) + ","
                                + (k.xepLoai == null ? "" : k.xepLoai) + ","
                                + String.format(Locale.US, "%.2f", k.doTinCayTB) + "\n");
                    }
                }
                main.post(() -> {
                    if (isAdded()) startActivity(FileExporter.shareIntent(requireContext(), out, "text/csv"));
                });
            } catch (Exception e) {
                main.post(() -> Toast.makeText(app, "Không xuất được CSV: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    // ---------------- Thông tin ----------------

    private void setupInfoRows() {
        root.findViewById(R.id.rowModels).setOnClickListener(v -> showInfo(
                "Mô hình AI",
                "• Tìm hạt: YOLOv8n (hoặc OpenCV contour).\n"
                        + "• Phân loại: MobileNetV2, hai lớp Fine / Defect, độ chính xác 94,26% "
                        + "trên tập kiểm thử.\n• Mọi xử lý chạy ngay trên thiết bị."));

        root.findViewById(R.id.rowPrivacy).setOnClickListener(v -> showInfo(
                "Chính sách bảo mật",
                "Ứng dụng xử lý ảnh hoàn toàn trên thiết bị. Ảnh và kết quả kiểm tra "
                        + "chỉ được lưu trong bộ nhớ riêng của ứng dụng, không gửi lên máy chủ."));

        root.findViewById(R.id.rowTerms).setOnClickListener(v -> showInfo(
                "Điều khoản sử dụng",
                "Kết quả phân loại chỉ mang tính tham khảo, được tạo bởi mô hình AI và có thể "
                        + "sai trong điều kiện ánh sáng hoặc góc chụp không tốt. Ứng dụng là "
                        + "sản phẩm học tập, không thay thế kiểm định chất lượng chính thức."));

        root.findViewById(R.id.rowContact).setOnClickListener(v -> showInfo(
                "Liên hệ hỗ trợ",
                "CoffeeCheck AI là đồ án môn Chuyên đề thiết kế ứng dụng trên thiết bị số. "
                        + "Vui lòng liên hệ nhóm thực hiện đề tài để được hỗ trợ."));
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Đóng", null)
                .show();
    }
}
