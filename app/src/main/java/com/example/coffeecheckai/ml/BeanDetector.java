package com.example.coffeecheckai.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.util.Log;

import org.opencv.core.Rect;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Phát hiện vị trí từng hạt cà phê bằng model YOLOv8n (bean_detector.tflite,
 * 1 lớp "coffee_bean"). Việc phân loại Fine/Defect vẫn do CoffeeClassifier làm.
 *
 * Class này tự thích nghi với các dạng output TFLite của Ultralytics:
 *  - [1, 4+nc, N] (kênh trước) hoặc [1, N, 4+nc] (kênh sau)
 *  - toạ độ chuẩn hoá 0..1 hoặc tính bằng pixel của ảnh 640x640
 */
public class BeanDetector {

    private static final String TAG = "BeanDetector";
    public static final String MODEL_FILE = "bean_detector.tflite";

    private static final float NMS_IOU_THRESHOLD = 0.45f;
    private static final int MAX_DETECTIONS = 300;
    private static final int MIN_BOX_SIZE_PX = 8;

    private final Interpreter interpreter;
    private final int inputSize;
    private final boolean inputChannelsFirst; // true: [1,3,H,W], false: [1,H,W,3]
    private final boolean channelsFirst;      // layout của OUTPUT
    private final int numAnchors;
    private final int numChannels;

    private float confidenceThreshold = 0.4f;

    public BeanDetector(Context context) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(loadModelFile(context), options);

        // Input có thể là NHWC [1,H,W,3] hoặc NCHW [1,3,H,W] tuỳ cách xuất model.
        int[] inShape = interpreter.getInputTensor(0).shape();
        inputChannelsFirst = inShape.length == 4 && inShape[1] == 3 && inShape[3] != 3;
        inputSize = inputChannelsFirst ? inShape[2] : inShape[1];

        int[] outShape = interpreter.getOutputTensor(0).shape();
        // Ultralytics: [1, 4+nc, N] với N >> 4+nc
        channelsFirst = outShape[1] < outShape[2];
        numChannels = channelsFirst ? outShape[1] : outShape[2];
        numAnchors = channelsFirst ? outShape[2] : outShape[1];

        Log.d(TAG, "inputShape=" + java.util.Arrays.toString(inShape)
                + " inputSize=" + inputSize + " inputChannelsFirst=" + inputChannelsFirst
                + " output=" + outShape[1] + "x" + outShape[2]
                + " outputChannelsFirst=" + channelsFirst);
    }

    public void setConfidenceThreshold(float threshold) {
        this.confidenceThreshold = threshold;
    }

    public static class Detection {
        public final Rect rect;
        public final float confidence;

        Detection(Rect rect, float confidence) {
            this.rect = rect;
            this.confidence = confidence;
        }
    }

    public List<Rect> detect(Bitmap input) {
        List<Rect> rects = new ArrayList<>();
        for (Detection d : detectWithConfidence(input)) rects.add(d.rect);
        return rects;
    }

    public List<Detection> detectWithConfidence(Bitmap input) {
        int origW = input.getWidth();
        int origH = input.getHeight();

        // Letterbox: giữ tỉ lệ, đệm xám (114) cho đủ inputSize x inputSize.
        float scale = Math.min((float) inputSize / origW, (float) inputSize / origH);
        int scaledW = Math.round(origW * scale);
        int scaledH = Math.round(origH * scale);
        float padX = (inputSize - scaledW) / 2f;
        float padY = (inputSize - scaledH) / 2f;

        Bitmap letterboxed = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(letterboxed);
        canvas.drawColor(Color.rgb(114, 114, 114));
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(padX, padY);
        canvas.drawBitmap(input, matrix, null);

        ByteBuffer inputBuffer = preprocess(letterboxed);
        letterboxed.recycle();

        float[][][] output = channelsFirst
                ? new float[1][numChannels][numAnchors]
                : new float[1][numAnchors][numChannels];
        interpreter.run(inputBuffer, output);

        // Gom ứng viên vượt ngưỡng (chưa quy đổi toạ độ).
        List<float[]> raw = new ArrayList<>();
        float maxCoord = 0f;
        for (int i = 0; i < numAnchors; i++) {
            float conf = value(output, 4, i);
            for (int c = 5; c < numChannels; c++) {
                conf = Math.max(conf, value(output, c, i));
            }
            if (conf < confidenceThreshold) continue;

            float cx = value(output, 0, i);
            float cy = value(output, 1, i);
            float w = value(output, 2, i);
            float h = value(output, 3, i);
            maxCoord = Math.max(maxCoord, Math.max(Math.max(cx, cy), Math.max(w, h)));
            raw.add(new float[]{cx, cy, w, h, conf});
        }

        // Toạ độ chuẩn hoá (0..1) thì nhân với inputSize để về pixel.
        float coordScale = (maxCoord <= 1.5f) ? inputSize : 1f;

        List<Detection> candidates = new ArrayList<>();
        for (float[] r : raw) {
            float cx = r[0] * coordScale;
            float cy = r[1] * coordScale;
            float w = r[2] * coordScale;
            float h = r[3] * coordScale;

            float x1 = clamp((cx - w / 2f - padX) / scale, 0, origW);
            float y1 = clamp((cy - h / 2f - padY) / scale, 0, origH);
            float x2 = clamp((cx + w / 2f - padX) / scale, 0, origW);
            float y2 = clamp((cy + h / 2f - padY) / scale, 0, origH);

            if (x2 - x1 < MIN_BOX_SIZE_PX || y2 - y1 < MIN_BOX_SIZE_PX) continue;

            Rect rect = new Rect((int) x1, (int) y1, (int) (x2 - x1), (int) (y2 - y1));
            candidates.add(new Detection(rect, r[4]));
        }

        return nonMaxSuppression(candidates);
    }

    private float value(float[][][] out, int channel, int anchor) {
        return channelsFirst ? out[0][channel][anchor] : out[0][anchor][channel];
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private List<Detection> nonMaxSuppression(List<Detection> detections) {
        List<Detection> sorted = new ArrayList<>(detections);
        sorted.sort((a, b) -> Float.compare(b.confidence, a.confidence));

        List<Detection> kept = new ArrayList<>();
        boolean[] removed = new boolean[sorted.size()];

        for (int i = 0; i < sorted.size() && kept.size() < MAX_DETECTIONS; i++) {
            if (removed[i]) continue;
            Detection a = sorted.get(i);
            kept.add(a);
            for (int j = i + 1; j < sorted.size(); j++) {
                if (!removed[j] && iou(a.rect, sorted.get(j).rect) >= NMS_IOU_THRESHOLD) {
                    removed[j] = true;
                }
            }
        }
        return kept;
    }

    private static double iou(Rect a, Rect b) {
        int x1 = Math.max(a.x, b.x);
        int y1 = Math.max(a.y, b.y);
        int x2 = Math.min(a.x + a.width, b.x + b.width);
        int y2 = Math.min(a.y + a.height, b.y + b.height);

        double inter = (double) Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        double union = (double) a.width * a.height + (double) b.width * b.height - inter;
        return union <= 0 ? 0 : inter / union;
    }

    /** Chuẩn hoá [0,1] đúng cách Ultralytics train (khác CoffeeClassifier dùng [-1,1]). */
    private ByteBuffer preprocess(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3);
        buffer.order(ByteOrder.nativeOrder());

        int plane = inputSize * inputSize;
        int[] pixels = new int[plane];
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize);

        if (inputChannelsFirst) {
            // NCHW: toàn bộ kênh R, rồi toàn bộ kênh G, rồi toàn bộ kênh B
            float[] data = new float[3 * plane];
            for (int i = 0; i < plane; i++) {
                int pixel = pixels[i];
                data[i] = ((pixel >> 16) & 0xFF) / 255f;
                data[plane + i] = ((pixel >> 8) & 0xFF) / 255f;
                data[2 * plane + i] = (pixel & 0xFF) / 255f;
            }
            buffer.asFloatBuffer().put(data);
        } else {
            // NHWC: R,G,B xen kẽ theo từng pixel
            for (int pixel : pixels) {
                buffer.putFloat(((pixel >> 16) & 0xFF) / 255f);
                buffer.putFloat(((pixel >> 8) & 0xFF) / 255f);
                buffer.putFloat((pixel & 0xFF) / 255f);
            }
        }
        buffer.rewind();
        return buffer;
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        try (AssetFileDescriptor fd = context.getAssets().openFd(MODEL_FILE);
             FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
            FileChannel channel = stream.getChannel();
            return channel.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    public void close() {
        interpreter.close();
    }
}
