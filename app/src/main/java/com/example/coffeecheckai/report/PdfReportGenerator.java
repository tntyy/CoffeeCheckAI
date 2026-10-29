package com.example.coffeecheckai.report;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.example.coffeecheckai.data.KetQuaKiemTra;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Tạo báo cáo PDF (A4) bằng android.graphics.pdf.PdfDocument, không cần thư viện ngoài. */
public class PdfReportGenerator {

    public static class Options {
        public boolean summary = true;
        public boolean trend = true;
        public boolean table = true;
        public boolean thumbnails = false;
        public String inspector = "";
        public String facility = "";
        public long from, to;
    }

    private static final int W = 595, H = 842, M = 36;
    private static final int PURPLE = 0xFF4C1D95;

    private final Paint title = paint(18, true, PURPLE);
    private final Paint h2 = paint(13, true, 0xFF222222);
    private final Paint text = paint(10, false, 0xFF222222);
    private final Paint small = paint(9, false, 0xFF777777);
    private final Paint bold = paint(10, true, 0xFF222222);

    private PdfDocument doc;
    private PdfDocument.Page page;
    private Canvas c;
    private int pageNo = 0;
    private float y;

    private static Paint paint(float size, boolean bold, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return p;
    }

    public File generate(File out, List<KetQuaKiemTra> items, Options o) throws IOException {
        doc = new PdfDocument();
        newPage();

        SimpleDateFormat d = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        c.drawText("BÁO CÁO KIỂM TRA CHẤT LƯỢNG CÀ PHÊ", M, y += 18, title);
        String meta = "CoffeeCheck AI · " + d.format(new Date(o.from)) + " – " + d.format(new Date(o.to));
        c.drawText(meta, M, y += 16, small);
        String who = (o.inspector.isEmpty() ? "" : "Người kiểm tra: " + o.inspector)
                + (o.facility.isEmpty() ? "" : (o.inspector.isEmpty() ? "" : " · ") + "Cơ sở: " + o.facility);
        if (!who.isEmpty()) c.drawText(who, M, y += 13, small);
        y += 14;

        int total = 0, fine = 0, defect = 0;
        int[] grades = new int[4];
        for (KetQuaKiemTra k : items) {
            total += k.tongHat; fine += k.soFine; defect += k.soDefect;
            grades[gradeIndex(k.xepLoai)]++;
        }

        if (o.summary) {
            int good = total > 0 ? Math.round(fine * 100f / total) : 0;
            String[] labels = {"Lượt quét", "Tổng số hạt", "Hạt tốt", "Hạt lỗi"};
            String[] vals = {String.valueOf(items.size()), String.valueOf(total),
                    total > 0 ? good + "%" : "-", total > 0 ? (100 - good) + "%" : "-"};
            int[] bg = {0xFFEDE9FE, 0xFFE0E7FF, 0xFFDCFCE7, 0xFFFEE2E2};
            float bw = (W - 2 * M - 3 * 8) / 4f;
            Paint fill = new Paint();
            for (int i = 0; i < 4; i++) {
                float x = M + i * (bw + 8);
                fill.setColor(bg[i]);
                c.drawRoundRect(new RectF(x, y, x + bw, y + 46), 6, 6, fill);
                Paint big = paint(16, true, 0xFF222222);
                c.drawText(vals[i], x + (bw - big.measureText(vals[i])) / 2, y + 22, big);
                c.drawText(labels[i], x + (bw - small.measureText(labels[i])) / 2, y + 38, small);
            }
            y += 62;

            c.drawText("Phân bố xếp loại", M, y += 4, h2);
            y += 10;
            String[] gn = {"Tốt", "Khá", "Trung bình", "Cần kiểm tra"};
            int[] gc = {0xFF34D399, 0xFFA3E635, 0xFFFBBF24, 0xFFF87171};
            int max = 1;
            for (int g : grades) max = Math.max(max, g);
            for (int i = 0; i < 4; i++) {
                c.drawText(gn[i], M, y + 9, text);
                fill.setColor(gc[i]);
                float len = 260f * grades[i] / max;
                c.drawRoundRect(new RectF(M + 80, y, M + 80 + Math.max(len, 2), y + 11), 3, 3, fill);
                c.drawText(String.valueOf(grades[i]), M + 90 + len, y + 9, text);
                y += 17;
            }
            y += 12;
        }

        if (o.trend) {
            ensure(150);
            c.drawText("Xu hướng tỷ lệ lỗi theo ngày (%)", M, y += 4, h2);
            y += 10;
            drawTrend(items);
            y += 20;
        }

        if (o.table) {
            ensure(60);
            c.drawText("Chi tiết các lượt quét", M, y += 4, h2);
            y += 10;
            drawTable(items, o.thumbnails);
        }

        footer();
        doc.finishPage(page);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            doc.writeTo(fos);
        } finally {
            doc.close();
        }
        return out;
    }

    private static int gradeIndex(String g) {
        if ("Tốt".equals(g)) return 0;
        if ("Khá".equals(g)) return 1;
        if ("Trung bình".equals(g)) return 2;
        return 3;
    }

    private void drawTrend(List<KetQuaKiemTra> items) {
        float cw = W - 2 * M, ch = 110;
        Paint bg = new Paint();
        bg.setColor(0xFFFAF5FF);
        c.drawRect(M, y, M + cw, y + ch, bg);

        SimpleDateFormat f = new SimpleDateFormat("dd/MM", Locale.US);
        Map<String, int[]> perDay = new LinkedHashMap<>();
        for (KetQuaKiemTra k : items) {
            int[] a = perDay.get(f.format(new Date(k.thoiGian)));
            if (a == null) { a = new int[2]; perDay.put(f.format(new Date(k.thoiGian)), a); }
            a[0] += k.tongHat; a[1] += k.soDefect;
        }
        if (perDay.isEmpty()) {
            c.drawText("Không có dữ liệu trong khoảng thời gian này", M + 10, y + 55, small);
            y += ch;
            return;
        }
        Paint grid = new Paint();
        grid.setColor(0xFFE5E7EB);
        for (int i = 0; i <= 4; i++) {
            float gy = y + 10 + (ch - 34) * i / 4f;
            c.drawLine(M + 28, gy, M + cw - 8, gy, grid);
            c.drawText(String.valueOf(100 - i * 25), M + 4, gy + 3, small);
        }
        int n = perDay.size();
        float[] xs = new float[n], ys = new float[n];
        String[] lab = new String[n];
        int i = 0;
        for (Map.Entry<String, int[]> e : perDay.entrySet()) {
            float rate = e.getValue()[0] > 0 ? 100f * e.getValue()[1] / e.getValue()[0] : 0;
            xs[i] = n == 1 ? M + 28 + (cw - 36) / 2 : M + 28 + (cw - 36) * i / (n - 1);
            ys[i] = y + 10 + (ch - 34) * (1 - rate / 100f);
            lab[i] = e.getKey();
            i++;
        }
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0xFF7C3AED);
        line.setStrokeWidth(2f);
        line.setStyle(Paint.Style.STROKE);
        Path path = new Path();
        for (i = 0; i < n; i++) {
            if (i == 0) path.moveTo(xs[i], ys[i]); else path.lineTo(xs[i], ys[i]);
        }
        c.drawPath(path, line);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0xFF7C3AED);
        int step = Math.max(1, n / 7);
        for (i = 0; i < n; i++) {
            c.drawCircle(xs[i], ys[i], 2.5f, dot);
            if (i % step == 0) c.drawText(lab[i], xs[i] - 12, y + ch - 6, small);
        }
        y += ch;
    }

    private void drawTable(List<KetQuaKiemTra> items, boolean thumbs) {
        float[] cw = thumbs ? new float[]{40, 95, 50, 50, 50, 55, 75} : new float[]{95, 55, 55, 55, 60, 100};
        String[] head = thumbs ? new String[]{"Ảnh", "Thời gian", "Tổng", "Tốt", "Lỗi", "% lỗi", "Xếp loại"}
                : new String[]{"Thời gian", "Tổng", "Tốt", "Lỗi", "% lỗi", "Xếp loại"};
        float rowH = thumbs ? 34 : 18;
        SimpleDateFormat f = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US);
        Paint fill = new Paint();
        Paint ln = new Paint();
        ln.setColor(0xFFDDDDDD);

        tableHeader(head, cw, fill);
        for (KetQuaKiemTra k : items) {
            if (y + rowH > H - 50) {
                newPage();
                tableHeader(head, cw, fill);
            }
            float x = M;
            int ci = 0;
            if (thumbs) {
                Bitmap b = thumb(k.duongDanAnh, (int) rowH - 4);
                if (b != null) { c.drawBitmap(b, x + 3, y + 2, null); b.recycle(); }
                x += cw[ci++];
            }
            String[] vals = {f.format(new Date(k.thoiGian)), String.valueOf(k.tongHat),
                    String.valueOf(k.soFine), String.valueOf(k.soDefect),
                    String.format(Locale.US, "%.1f", k.tyLeLoi), k.xepLoai == null ? "" : k.xepLoai};
            for (String v : vals) {
                c.drawText(v, x + 4, y + rowH / 2 + 3, text);
                x += cw[ci++];
            }
            y += rowH;
            c.drawLine(M, y, M + sum(cw), y, ln);
        }
        if (items.isEmpty()) {
            c.drawText("Không có lượt quét nào.", M + 4, y + 12, small);
            y += 18;
        }
    }

    private void tableHeader(String[] head, float[] cw, Paint fill) {
        fill.setColor(0xFFEDE9FE);
        c.drawRect(M, y, M + sum(cw), y + 18, fill);
        float x = M;
        for (int i = 0; i < head.length; i++) {
            c.drawText(head[i], x + 4, y + 12, bold);
            x += cw[i];
        }
        y += 18;
    }

    private static float sum(float[] a) {
        float s = 0;
        for (float v : a) s += v;
        return s;
    }

    private Bitmap thumb(String path, int size) {
        if (path == null) return null;
        try {
            BitmapFactory.Options bo = new BitmapFactory.Options();
            bo.inSampleSize = 8;
            Bitmap b = BitmapFactory.decodeFile(path, bo);
            if (b == null) return null;
            Bitmap s = Bitmap.createScaledBitmap(b, size, size, true);
            if (s != b) b.recycle();
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private void ensure(float need) {
        if (y + need > H - 50) newPage();
    }

    private void newPage() {
        if (page != null) {
            footer();
            doc.finishPage(page);
        }
        pageNo++;
        page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
        c = page.getCanvas();
        y = M;
    }

    private void footer() {
        c.drawText("Tạo bởi CoffeeCheck AI", M, H - 22, small);
        String s = "Trang " + pageNo;
        c.drawText(s, W - M - small.measureText(s), H - 22, small);
    }
}
