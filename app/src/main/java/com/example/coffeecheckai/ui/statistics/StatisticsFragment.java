package com.example.coffeecheckai.ui.statistics;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;

import com.example.coffeecheckai.R;
import com.example.coffeecheckai.data.AppDatabase;
import com.example.coffeecheckai.data.KetQuaKiemTra;
import com.example.coffeecheckai.report.FileExporter;
import com.example.coffeecheckai.report.PdfReportGenerator;
import com.example.coffeecheckai.utils.AppSettings;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import android.widget.Toast;
import java.io.File;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thống kê theo khoảng thời gian: tỉ lệ Đạt/Lỗi tổng thể (tính theo số hạt),
 * xu hướng chất lượng theo từng ngày, và số liệu chi tiết.
 */
public class StatisticsFragment extends Fragment {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private MaterialButtonToggleGroup toggleRange;
    private CircularProgressIndicator progressDonut;
    private TextView textCenterPercent, textGoodPercent, textBadPercent, textGoodSummary;
    private LinearLayout layoutTrend, layoutGrades;
    private TextView textTrendEmpty;
    private TextView textTotalScans, textTotalBeans, textGoodBeans, textBadBeans;

    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private long rangeFrom;
    private long rangeTo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_statistics, container, false);

        toggleRange = root.findViewById(R.id.toggleRange);
        progressDonut = root.findViewById(R.id.progressDonut);
        textCenterPercent = root.findViewById(R.id.textCenterPercent);
        textGoodPercent = root.findViewById(R.id.textGoodPercent);
        textBadPercent = root.findViewById(R.id.textBadPercent);
        textGoodSummary = root.findViewById(R.id.textGoodSummary);
        layoutTrend = root.findViewById(R.id.layoutTrend);
        layoutGrades = root.findViewById(R.id.layoutGrades);
        root.findViewById(R.id.btnExportPdf).setOnClickListener(v -> showExportDialog());
        textTrendEmpty = root.findViewById(R.id.textTrendEmpty);
        textTotalScans = root.findViewById(R.id.textTotalScans);
        textTotalBeans = root.findViewById(R.id.textTotalBeans);
        textGoodBeans = root.findViewById(R.id.textGoodBeans);
        textBadBeans = root.findViewById(R.id.textBadBeans);

        executor = Executors.newSingleThreadExecutor();

        setRangeLastDays(7);

        toggleRange.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            if (checkedId == R.id.btnRange7) {
                setRangeLastDays(7);
                load();
            } else if (checkedId == R.id.btnRange30) {
                setRangeLastDays(30);
                load();
            } else if (checkedId == R.id.btnRange90) {
                setRangeLastDays(90);
                load();
            } else if (checkedId == R.id.btnRangeCustom) {
                showCustomRangePicker();
            }
        });

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    // =========================================================
    // KHOẢNG THỜI GIAN
    // =========================================================

    private void setRangeLastDays(int days) {
        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
        rangeTo = end.getTimeInMillis();
        rangeFrom = startOfDay(rangeTo) - (days - 1L) * DAY_MS;
    }

    private static long startOfDay(long timeMillis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timeMillis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void showCustomRangePicker() {
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder
                .dateRangePicker()
                .setTitleText("Chọn khoảng thời gian")
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) return;
            // Date picker trả về UTC 00:00; đổi sang đầu ngày / cuối ngày theo giờ máy.
            rangeFrom = startOfDay(selection.first + java.util.TimeZone.getDefault()
                    .getOffset(selection.first));
            rangeTo = startOfDay(selection.second + java.util.TimeZone.getDefault()
                    .getOffset(selection.second)) + DAY_MS - 1;
            load();
        });

        picker.show(getParentFragmentManager(), "range_picker");
    }

    // =========================================================
    // DỮ LIỆU
    // =========================================================

    private void load() {
        if (executor == null) return;

        final Context appContext = requireContext().getApplicationContext();
        final long from = rangeFrom;
        final long to = rangeTo;

        executor.execute(() -> {
            List<KetQuaKiemTra> items = AppDatabase.getInstance(appContext)
                    .ketQuaDao().getBetween(from, to);

            mainHandler.post(() -> {
                if (isAdded()) render(items);
            });
        });
    }

    private void render(List<KetQuaKiemTra> items) {
        int totalBeans = 0;
        int fine = 0;
        int defect = 0;
        for (KetQuaKiemTra item : items) {
            totalBeans += item.tongHat;
            fine += item.soFine;
            defect += item.soDefect;
        }

        int goodPercent = totalBeans > 0 ? Math.round(fine * 100f / totalBeans) : 0;
        int badPercent = totalBeans > 0 ? 100 - goodPercent : 0;

        progressDonut.setProgress(goodPercent);
        textCenterPercent.setText(goodPercent + "%");
        textGoodPercent.setText(goodPercent + "%");
        textBadPercent.setText(badPercent + "%");
        String summary = fine + " / " + totalBeans + " hạt đạt chuẩn";
        if (totalBeans > 0) {
            summary += "\nXếp loại chung: "
                    + new AppSettings(requireContext()).gradeFor(badPercent);
        }
        textGoodSummary.setText(summary);

        textTotalScans.setText(String.valueOf(items.size()));
        textTotalBeans.setText(String.valueOf(totalBeans));
        textGoodBeans.setText(String.valueOf(fine));
        textBadBeans.setText(String.valueOf(defect));

        renderTrend(items);
        renderGrades(items);
    }

    /** Mỗi ngày có dữ liệu là một dòng: tỉ lệ hạt đạt của ngày đó. */
    private void renderTrend(List<KetQuaKiemTra> items) {
        layoutTrend.removeAllViews();

        if (items.isEmpty()) {
            textTrendEmpty.setVisibility(View.VISIBLE);
            return;
        }
        textTrendEmpty.setVisibility(View.GONE);

        SimpleDateFormat dayFormat = new SimpleDateFormat("dd/MM", new Locale("vi", "VN"));

        // items đã sắp xếp tăng dần theo thời gian -> LinkedHashMap giữ thứ tự ngày.
        Map<String, int[]> perDay = new LinkedHashMap<>(); // [tổng hạt, hạt đạt]
        for (KetQuaKiemTra item : items) {
            String key = dayFormat.format(new java.util.Date(item.thoiGian));
            int[] acc = perDay.get(key);
            if (acc == null) {
                acc = new int[2];
                perDay.put(key, acc);
            }
            acc[0] += item.tongHat;
            acc[1] += item.soFine;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Map.Entry<String, int[]> entry : perDay.entrySet()) {
            int total = entry.getValue()[0];
            int good = entry.getValue()[1];
            int percent = total > 0 ? Math.round(good * 100f / total) : 0;

            View row = inflater.inflate(R.layout.layout_item_trend_row, layoutTrend, false);
            ((TextView) row.findViewById(R.id.textTrendLabel)).setText(entry.getKey());
            ((LinearProgressIndicator) row.findViewById(R.id.progressTrend)).setProgress(percent);
            ((TextView) row.findViewById(R.id.textTrendPercent)).setText(percent + "%");
            layoutTrend.addView(row);
        }
    }

    private void renderGrades(List<KetQuaKiemTra> items) {
        layoutGrades.removeAllViews();
        String[] names = {"Tốt", "Khá", "Trung bình", "Cần kiểm tra"};
        int[] colors = {0xFF34D399, 0xFFA3E635, 0xFFFBBF24, 0xFFF87171};
        int[] counts = new int[4];
        for (KetQuaKiemTra k : items) {
            int idx = 3;
            for (int i = 0; i < 3; i++) if (names[i].equals(k.xepLoai)) idx = i;
            counts[idx]++;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < 4; i++) {
            View row = inflater.inflate(R.layout.layout_item_grade_row, layoutGrades, false);
            ((TextView) row.findViewById(R.id.textGradeName)).setText(names[i]);
            LinearProgressIndicator bar = row.findViewById(R.id.progressGrade);
            bar.setIndicatorColor(colors[i]);
            bar.setProgress(items.isEmpty() ? 0 : Math.round(counts[i] * 100f / items.size()));
            ((TextView) row.findViewById(R.id.textGradeCount)).setText(String.valueOf(counts[i]));
            layoutGrades.addView(row);
        }
    }

    // =========================================================
    // XUẤT PDF
    // =========================================================

    private void showExportDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View v = getLayoutInflater().inflate(R.layout.dialog_export_pdf, null);
        dialog.setContentView(v);

        AppSettings settings = new AppSettings(requireContext());
        TextInputEditText editInspector = v.findViewById(R.id.editInspector);
        TextInputEditText editFacility = v.findViewById(R.id.editFacility);
        editInspector.setText(settings.getInspector());
        editFacility.setText(settings.getFacility());

        v.findViewById(R.id.btnSavePdf).setOnClickListener(b -> {
            doExport(v, settings, false);
            dialog.dismiss();
        });
        v.findViewById(R.id.btnSharePdf).setOnClickListener(b -> {
            doExport(v, settings, true);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void doExport(View v, AppSettings settings, boolean share) {
        if (executor == null) return;

        PdfReportGenerator.Options o = new PdfReportGenerator.Options();
        o.summary = ((MaterialSwitch) v.findViewById(R.id.swSummary)).isChecked();
        o.trend = ((MaterialSwitch) v.findViewById(R.id.swTrend)).isChecked();
        o.table = ((MaterialSwitch) v.findViewById(R.id.swTable)).isChecked();
        o.thumbnails = ((MaterialSwitch) v.findViewById(R.id.swThumbs)).isChecked();
        o.inspector = String.valueOf(((TextInputEditText) v.findViewById(R.id.editInspector)).getText()).trim();
        o.facility = String.valueOf(((TextInputEditText) v.findViewById(R.id.editFacility)).getText()).trim();
        settings.setInspector(o.inspector);
        settings.setFacility(o.facility);

        int checked = ((com.google.android.material.chip.ChipGroup) v.findViewById(R.id.chipRange))
                .getCheckedChipId();
        long from = rangeFrom, to = rangeTo;
        int days = checked == R.id.chip7 ? 7 : checked == R.id.chip30 ? 30 : checked == R.id.chip90 ? 90 : 0;
        if (days > 0) {
            Calendar end = Calendar.getInstance();
            end.set(Calendar.HOUR_OF_DAY, 23);
            end.set(Calendar.MINUTE, 59);
            end.set(Calendar.SECOND, 59);
            to = end.getTimeInMillis();
            from = startOfDay(to) - (days - 1L) * DAY_MS;
        }
        o.from = from;
        o.to = to;

        final Context appContext = requireContext().getApplicationContext();
        final long qFrom = from, qTo = to;
        Toast.makeText(appContext, "Đang tạo báo cáo PDF...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                List<KetQuaKiemTra> items = AppDatabase.getInstance(appContext)
                        .ketQuaDao().getBetween(qFrom, qTo);
                String name = "BaoCao_CoffeeCheck_"
                        + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new java.util.Date())
                        + ".pdf";
                File file = new PdfReportGenerator()
                        .generate(FileExporter.cacheFile(appContext, name), items, o);

                String where = share ? null : FileExporter.saveToDownloads(appContext, file, "application/pdf");
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (share) {
                        startActivity(FileExporter.shareIntent(requireContext(), file, "application/pdf"));
                    } else {
                        Toast.makeText(requireContext(), "Đã lưu: " + where, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(appContext,
                        "Không tạo được PDF: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
    }
}
