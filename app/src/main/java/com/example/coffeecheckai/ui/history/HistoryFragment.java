package com.example.coffeecheckai.ui.history;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeecheckai.R;
import com.example.coffeecheckai.data.AppDatabase;
import com.example.coffeecheckai.data.KetQuaKiemTra;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lịch sử các lần quét: tìm kiếm, lọc Đạt/Lỗi, xem chi tiết, vuốt để xoá.
 * "Đạt" = tỉ lệ lỗi tối đa 10%, "Lỗi" = tỉ lệ lỗi trên 10%.
 */
public class HistoryFragment extends Fragment {

    private static final float GOOD_MAX_DEFECT_RATE = 10f;

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd/MM/yyyy · HH:mm", new Locale("vi", "VN"));

    private RecyclerView recyclerHistory;
    private View layoutEmpty;
    private TextInputEditText editSearch;
    private ChipGroup chipGroupFilter;

    private HistoryAdapter adapter;
    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<KetQuaKiemTra> allItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_history, container, false);

        recyclerHistory = root.findViewById(R.id.recyclerHistory);
        layoutEmpty = root.findViewById(R.id.layoutEmpty);
        editSearch = root.findViewById(R.id.editSearch);
        chipGroupFilter = root.findViewById(R.id.chipGroupFilter);

        executor = Executors.newSingleThreadExecutor();

        adapter = new HistoryAdapter(item -> showDetail(item));
        recyclerHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerHistory.setAdapter(adapter);

        setupSwipeToDelete();

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                applyFilter();
            }
        });

        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilter());

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    // =========================================================
    // DỮ LIỆU
    // =========================================================

    private void loadData() {
        final Context appContext = requireContext().getApplicationContext();

        executor.execute(() -> {
            List<KetQuaKiemTra> items =
                    AppDatabase.getInstance(appContext).ketQuaDao().getAll();

            mainHandler.post(() -> {
                if (!isAdded()) return;
                allItems.clear();
                allItems.addAll(items);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        if (!isAdded()) return;

        String keyword = editSearch.getText() == null
                ? "" : editSearch.getText().toString().trim().toLowerCase(Locale.ROOT);

        int checkedId = chipGroupFilter.getCheckedChipId();

        List<KetQuaKiemTra> filtered = new ArrayList<>();
        for (KetQuaKiemTra item : allItems) {

            if (checkedId == R.id.chipGood && item.tyLeLoi > GOOD_MAX_DEFECT_RATE) continue;
            if (checkedId == R.id.chipBad && item.tyLeLoi <= GOOD_MAX_DEFECT_RATE) continue;

            if (!keyword.isEmpty() && !matches(item, keyword)) continue;

            filtered.add(item);
        }

        adapter.submit(filtered);

        boolean empty = filtered.isEmpty();
        layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private static boolean matches(KetQuaKiemTra item, String keyword) {
        String haystack = (DATE_FORMAT.format(new Date(item.thoiGian)) + " "
                + (item.xepLoai == null ? "" : item.xepLoai) + " "
                + (item.ghiChu == null ? "" : item.ghiChu) + " "
                + item.tongHat + " hạt").toLowerCase(Locale.ROOT);
        return haystack.contains(keyword);
    }

    // =========================================================
    // VUỐT ĐỂ XOÁ
    // =========================================================

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;

                KetQuaKiemTra item = adapter.getItem(position);
                deleteItem(item);
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(recyclerHistory);
    }

    private void deleteItem(KetQuaKiemTra item) {
        final Context appContext = requireContext().getApplicationContext();

        allItems.remove(item);
        applyFilter();

        executor.execute(() -> {
            AppDatabase.getInstance(appContext).ketQuaDao().delete(item);
            if (item.duongDanAnh != null) {
                //noinspection ResultOfMethodCallIgnored
                new File(item.duongDanAnh).delete();
            }
        });
    }

    // =========================================================
    // CHI TIẾT
    // =========================================================

    private void showDetail(KetQuaKiemTra item) {
        Context context = requireContext();
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad, pad, pad);

        ImageView image = new ImageView(context);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Bitmap bitmap = decodeSampled(item.duongDanAnh, 1080);
        if (bitmap != null) {
            image.setImageBitmap(bitmap);
            content.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView info = new TextView(context);
        info.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        info.setTextSize(14);
        info.setPadding(0, pad, 0, 0);
        info.setText(String.format(Locale.getDefault(),
                "%s\n\nTổng hạt: %d\nFine: %d\nDefect: %d\nTỷ lệ lỗi: %.1f%%\n"
                        + "Xếp loại: %s\nĐộ tin cậy trung bình: %.0f%%\n\nGợi ý: %s",
                DATE_FORMAT.format(new Date(item.thoiGian)),
                item.tongHat, item.soFine, item.soDefect, item.tyLeLoi,
                item.xepLoai == null ? "—" : item.xepLoai,
                item.doTinCayTB * 100f,
                item.ghiChu == null ? "—" : item.ghiChu));
        content.addView(info);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);

        new AlertDialog.Builder(context)
                .setTitle("Chi tiết lần kiểm tra")
                .setView(scroll)
                .setPositiveButton("Đóng", null)
                .show();
    }

    static Bitmap decodeSampled(String path, int maxSide) {
        if (path == null) return null;

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, options);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        if (adapter != null) {
            adapter.shutdown();
        }
    }

    // =========================================================
    // ADAPTER
    // =========================================================

    interface OnItemClick {
        void onClick(KetQuaKiemTra item);
    }

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {

        private final List<KetQuaKiemTra> items = new ArrayList<>();
        private final OnItemClick listener;
        private final ExecutorService thumbExecutor = Executors.newSingleThreadExecutor();
        private final Handler handler = new Handler(Looper.getMainLooper());

        HistoryAdapter(OnItemClick listener) {
            this.listener = listener;
        }

        void submit(List<KetQuaKiemTra> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        KetQuaKiemTra getItem(int position) {
            return items.get(position);
        }

        void shutdown() {
            thumbExecutor.shutdownNow();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_item_history_rv, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            KetQuaKiemTra item = items.get(position);
            Context context = h.itemView.getContext();

            h.textTitle.setText(DATE_FORMAT.format(new Date(item.thoiGian)));
            h.textInfo.setText("Tổng " + item.tongHat + " hạt");
            h.textGood.setText("Đạt " + item.soFine);
            h.textBad.setText("Lỗi " + item.soDefect);
            h.textPercent.setText(String.format(Locale.getDefault(), "%.1f%%", item.tyLeLoi));

            int colorRes = colorForRate(item.tyLeLoi);
            int color = ContextCompat.getColor(context, colorRes);
            h.textPercent.setTextColor(color);
            h.textBadge.setText(item.xepLoai == null ? "—" : item.xepLoai);
            h.textBadge.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));
            h.textBadge.setTextColor(ContextCompat.getColor(context, R.color.white));

            // Ảnh thu nhỏ: giải mã ở luồng nền, kiểm tra tag để tránh gán nhầm khi cuộn.
            final String path = item.duongDanAnh;
            h.imageThumb.setTag(path);
            h.imageThumb.setImageDrawable(null);
            thumbExecutor.execute(() -> {
                Bitmap thumb = decodeSampled(path, 160);
                handler.post(() -> {
                    if (thumb != null && path != null && path.equals(h.imageThumb.getTag())) {
                        h.imageThumb.setImageBitmap(thumb);
                    }
                });
            });

            h.itemView.setOnClickListener(v -> listener.onClick(item));
        }

        private static int colorForRate(float rate) {
            if (rate <= 0f) return R.color.success;
            if (rate <= GOOD_MAX_DEFECT_RATE) return R.color.primary;
            if (rate <= 25f) return R.color.warning;
            return R.color.error;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ImageView imageThumb;
            final TextView textTitle, textInfo, textGood, textBad, textPercent, textBadge;

            Holder(@NonNull View itemView) {
                super(itemView);
                imageThumb = itemView.findViewById(R.id.imageThumb);
                textTitle = itemView.findViewById(R.id.textTitle);
                textInfo = itemView.findViewById(R.id.textInfo);
                textGood = itemView.findViewById(R.id.textGood);
                textBad = itemView.findViewById(R.id.textBad);
                textPercent = itemView.findViewById(R.id.textPercent);
                textBadge = itemView.findViewById(R.id.textBadge);
            }
        }
    }
}
