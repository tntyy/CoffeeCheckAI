package com.example.coffeecheckai.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Xử lý ảnh bằng OpenCV cho CoffeeCheckAI.
 * Khác với bản EcoScan gốc (chỉ tìm 1 vật thể trung tâm), lớp này phát hiện
 * TẤT CẢ các hạt cà phê có trong 1 ảnh, kể cả khi các hạt dính/chạm sát
 * nhau (dùng watershed để tách), vì mỗi ảnh mẫu chứa nhiều hạt.
 *
 * Thuật toán đã được test và tinh chỉnh bằng script Python/Colab riêng
 * (test_multi_bean_detection_colab.py) trên ảnh thật trước khi port sang
 * đây - giữ ĐÚNG các bước và tham số đã test, không tự ý đổi.
 */
public class ImageUtils {

    static {
        OpenCVLoader.initLocal();
    }

    // ==== Ngưỡng diện tích (tính theo % diện tích ảnh) ====
    private static final double MIN_AREA_RATIO = 0.003;       // 1 hạt nhỏ nhất
    private static final double MAX_AREA_RATIO = 0.30;        // 1 hạt lớn nhất (sau khi đã tách)
    private static final double CLUSTER_MAX_AREA_RATIO = 0.60; // cụm hạt dính nhau, trước khi tách

    // ==== Ngưỡng hình dạng: loại bỏ vệt dài/mỏng (bóng, vết bẩn...) không
    // giống hạt cà phê. 1.0 = hình tròn hoàn hảo. ====
    private static final double MIN_CIRCULARITY = 0.35;

    private static final int BLUR_KSIZE = 7;
    private static final double CANNY_LOW = 20;
    private static final double CANNY_HIGH = 60;
    private static final int CLOSE_ITER = 3;

    // CLAHE: tăng tương phản cục bộ để bắt được hạt nhạt màu/ít tương phản
    // với nền (gần chìm vào nền sáng).
    private static final double CLAHE_CLIP = 3.0;

    // Watershed: "tâm hạt" phải nhô cao bao nhiêu (0-1) so với biên mới được
    // tính là 1 tâm riêng, dùng để tách các hạt đang dính sát nhau.
    private static final double DIST_THRESH = 0.45;

    // ==== Kiểm tra chất lượng ảnh ====
    private static final double MIN_BRIGHTNESS = 60;     // trung bình độ sáng (0-255)
    private static final double MAX_BRIGHTNESS = 220;
    private static final double MIN_SHARPNESS = 60;      // phương sai Laplacian

    /**
     * Kiểm tra ảnh quá tối / quá sáng / quá mờ bằng OpenCV
     * (độ sáng trung bình + phương sai Laplacian trên ảnh thu nhỏ).
     *
     * @return câu cảnh báo cho người dùng, hoặc null nếu ảnh đạt yêu cầu
     */
    public static String checkQuality(Bitmap input) {
        Mat src = new Mat();
        Mat gray = new Mat();
        Mat lap = new Mat();
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble std = new MatOfDouble();
        try {
            Utils.bitmapToMat(input, src);
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);

            // Thu nhỏ để độ mờ không phụ thuộc độ phân giải máy ảnh
            double scale = 640.0 / Math.max(gray.cols(), gray.rows());
            if (scale < 1.0) {
                Imgproc.resize(gray, gray, new Size(), scale, scale, Imgproc.INTER_AREA);
            }

            double brightness = Core.mean(gray).val[0];

            Imgproc.Laplacian(gray, lap, CvType.CV_64F);
            Core.meanStdDev(lap, mean, std);
            double sharpness = Math.pow(std.get(0, 0)[0], 2);

            android.util.Log.d("ImageUtils", "quality brightness=" + brightness
                    + " sharpness=" + sharpness);

            if (brightness < MIN_BRIGHTNESS) {
                return "Ảnh hơi tối, nên chụp ở nơi đủ sáng để kết quả chính xác hơn";
            }
            if (brightness > MAX_BRIGHTNESS) {
                return "Ảnh quá sáng/chói, nên tránh ánh sáng gắt";
            }
            if (sharpness < MIN_SHARPNESS) {
                return "Ảnh bị mờ, nên giữ máy ổn định và chụp lại cho rõ";
            }
            return null;
        } finally {
            src.release();
            gray.release();
            lap.release();
            mean.release();
            std.release();
        }
    }

    /**
     * Tăng nhẹ tương phản và độ sáng của ảnh, giúp làm nổi bật các hạt cà phê
     * trước khi đưa vào bước phát hiện contour và phân loại AI.
     */
    public static Bitmap enhance(Bitmap input) {
        Mat src = new Mat();
        Utils.bitmapToMat(input, src);

        Mat enhanced = new Mat();
        src.convertTo(enhanced, -1, 1.15, 10); // alpha = tương phản, beta = độ sáng

        Bitmap output = Bitmap.createBitmap(enhanced.cols(), enhanced.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(enhanced, output);

        src.release();
        enhanced.release();
        return output;
    }

    /**
     * Phát hiện TẤT CẢ hạt cà phê trong ảnh, kể cả khi chúng dính/chạm nhau.
     * Quy trình: xám -> CLAHE (tăng tương phản cục bộ) -> làm mờ -> Canny ->
     * đóng biên -> lấp đầy contour thành khối đặc ("cụm nghi ngờ là hạt") ->
     * watershed (dùng distance transform tìm tâm từng hạt) để tách các cụm
     * dính nhau thành từng hạt riêng -> lọc theo diện tích + độ tròn.
     *
     * @param input ảnh Bitmap gốc (đã enhance() trước đó)
     * @return danh sách Rect, mỗi Rect là vùng bao quanh 1 hạt cà phê phát hiện được
     */
    public static List<Rect> findAllBeanRects(Bitmap input) {
        Mat src = new Mat();
        Utils.bitmapToMat(input, src); // RGBA

        Mat bgr = new Mat();
        Imgproc.cvtColor(src, bgr, Imgproc.COLOR_RGBA2BGR); // dùng cho watershed (cần ảnh 3 kênh)

        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY);

        CLAHE clahe = Imgproc.createCLAHE(CLAHE_CLIP, new Size(8, 8));
        Mat grayEq = new Mat();
        clahe.apply(gray, grayEq);

        Mat blurred = new Mat();
        Imgproc.GaussianBlur(grayEq, blurred, new Size(BLUR_KSIZE, BLUR_KSIZE), 0);

        Mat edges = new Mat();
        Imgproc.Canny(blurred, edges, CANNY_LOW, CANNY_HIGH);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
        Mat closed = new Mat();
        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel, new Point(-1, -1), CLOSE_ITER);
        Imgproc.dilate(closed, closed, kernel);

        double imageArea = (double) src.rows() * src.cols();
        double minArea = imageArea * MIN_AREA_RATIO;
        double clusterMaxArea = imageArea * CLUSTER_MAX_AREA_RATIO;

        // Lấp đầy các contour khép kín thành khối đặc (binary mask).
        List<MatOfPoint> rawContours = new ArrayList<>();
        Imgproc.findContours(closed, rawContours, new Mat(), Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE);

        Mat binary = Mat.zeros(gray.size(), CvType.CV_8UC1);
        for (MatOfPoint c : rawContours) {
            double area = Imgproc.contourArea(c);
            if (area < minArea || area > clusterMaxArea) continue;
            Imgproc.drawContours(binary, Collections.singletonList(c), -1, new Scalar(255), -1);
        }
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel);

        // Watershed để tách các hạt dính nhau trong cùng 1 khối.
        Mat sureBg = new Mat();
        Imgproc.dilate(binary, sureBg, kernel, new Point(-1, -1), 3);

        Mat dist = new Mat();
        Imgproc.distanceTransform(binary, dist, Imgproc.DIST_L2, 5);
        Core.normalize(dist, dist, 0, 1.0, Core.NORM_MINMAX);

        Mat sureFg = new Mat();
        Imgproc.threshold(dist, sureFg, DIST_THRESH, 1.0, Imgproc.THRESH_BINARY);
        sureFg.convertTo(sureFg, CvType.CV_8U, 255);

        Mat unknown = new Mat();
        Core.subtract(sureBg, sureFg, unknown);

        Mat markers = new Mat();
        int numMarkers = Imgproc.connectedComponents(sureFg, markers);
        Core.add(markers, new Scalar(1), markers);
        markers.setTo(new Scalar(0), unknown);

        Imgproc.watershed(bgr, markers);

        List<Rect> results = new ArrayList<>();

        // label 1 = nền, label -1 = đường biên watershed vẽ ra -> bỏ qua.
        // Mỗi label từ 2 trở lên là 1 hạt riêng biệt.
        for (int label = 2; label <= numMarkers; label++) {
            Mat mask = new Mat();
            Core.compare(markers, new Scalar(label), mask, Core.CMP_EQ);

            int area = Core.countNonZero(mask);
            double areaRatio = area / imageArea;
            if (areaRatio < MIN_AREA_RATIO || areaRatio > MAX_AREA_RATIO) {
                mask.release();
                continue;
            }

            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(mask, contours, new Mat(), Imgproc.RETR_EXTERNAL,
                    Imgproc.CHAIN_APPROX_SIMPLE);
            if (contours.isEmpty()) {
                mask.release();
                continue;
            }

            MatOfPoint c = Collections.max(contours, Comparator.comparingDouble(Imgproc::contourArea));
            double perimeter = Imgproc.arcLength(new MatOfPoint2f(c.toArray()), true);
            if (perimeter <= 0) {
                mask.release();
                continue;
            }

            double circularity = 4 * Math.PI * area / (perimeter * perimeter);
            if (circularity < MIN_CIRCULARITY) {
                mask.release();
                continue;
            }

            results.add(Imgproc.boundingRect(c));
            mask.release();
        }

        src.release();
        bgr.release();
        gray.release();
        grayEq.release();
        blurred.release();
        edges.release();
        closed.release();
        kernel.release();
        binary.release();
        sureBg.release();
        dist.release();
        sureFg.release();
        unknown.release();
        markers.release();

        return results;
    }

    /**
     * Cắt 1 vùng Rect cụ thể (1 hạt) ra khỏi ảnh gốc thành Bitmap riêng,
     * có thêm padding nhẹ quanh viền để giữ ngữ cảnh xung quanh hạt.
     */
    public static Bitmap cropRect(Bitmap input, Rect rect) {
        Mat src = new Mat();
        Utils.bitmapToMat(input, src);

        // 0.15 khớp padding_ratio của crop_beans.py lúc tạo dữ liệu train classifier.
        int padX = (int) (rect.width * 0.15);
        int padY = (int) (rect.height * 0.15);

        int x = Math.max(0, rect.x - padX);
        int y = Math.max(0, rect.y - padY);
        int w = Math.min(src.cols() - x, rect.width + 2 * padX);
        int h = Math.min(src.rows() - y, rect.height + 2 * padY);

        Rect padded = new Rect(x, y, w, h);
        Mat cropped = new Mat(src, padded);

        Bitmap result = Bitmap.createBitmap(cropped.cols(), cropped.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(cropped, result);

        cropped.release();
        src.release();
        return result;
    }

    /**
     * Vẽ khung màu quanh từng hạt đã phát hiện lên ảnh gốc để hiển thị kết quả
     * trực quan: xanh lá = Fine (đạt), đỏ = Defect (lỗi).
     */
    public static Bitmap drawAllBeanBoxes(Bitmap input, List<Rect> rects, List<String> labels) {
        Bitmap output = input.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);

        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            String label = (i < labels.size()) ? labels.get(i) : "";

            paint.setColor("Fine".equalsIgnoreCase(label) ? Color.GREEN : Color.RED);
            canvas.drawRect(r.x, r.y, r.x + r.width, r.y + r.height, paint);
        }

        return output;
    }
}