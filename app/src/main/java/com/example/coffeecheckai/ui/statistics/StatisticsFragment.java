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
    private LinearLayout layoutTrend;
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
        textGoodSummary.setText(fine + " / " + totalBeans + " hạt đạt chuẩn");

        textTotalScans.setText(String.valueOf(items.size()));
        textTotalBeans.setText(String.valueOf(totalBeans));
        textGoodBeans.setText(String.valueOf(fine));
        textBadBeans.setText(String.valueOf(defect));

        renderTrend(items);
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
