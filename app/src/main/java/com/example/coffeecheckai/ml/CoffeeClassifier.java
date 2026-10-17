package com.example.coffeecheckai.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Phân loại 1 hạt cà phê (ảnh đã cắt riêng) thành Fine / Defect bằng model
 * TensorFlow Lite đã train (MobileNetV2, 2 lớp).
 *
 * Model & nhãn load từ assets: trash_classifier.tflite, trash_labels.txt
 * (giữ nguyên tên file như lúc train trên Colab để khỏi phải đổi lại).
 */
public class CoffeeClassifier {

    private static final int INPUT_SIZE = 224; // khớp IMG_SIZE lúc train trên Colab

    private final Interpreter interpreter;
    private final List<String> labels;

    public CoffeeClassifier(Context context) throws IOException {
        interpreter = new Interpreter(loadModelFile(context, "trash_classifier.tflite"));
        labels = loadLabels(context, "trash_labels.txt");
    }

    public static class ClassificationResult {
        public String label;      // "Fine" hoặc "Defect"
        public float confidence;  // 0.0 - 1.0
    }

    /**
     * Phân loại 1 ảnh hạt cà phê đã cắt riêng.
     */
    public ClassificationResult classify(Bitmap bitmap) {
        ByteBuffer input = preprocess(bitmap);
        float[][] output = new float[1][labels.size()];
        interpreter.run(input, output);

        int bestIdx = 0;
        for (int i = 1; i < output[0].length; i++) {
            if (output[0][i] > output[0][bestIdx]) bestIdx = i;
        }

        ClassificationResult result = new ClassificationResult();
        result.label = labels.get(bestIdx);
        result.confidence = output[0][bestIdx];
        return result;
    }

    /**
     * QUAN TRỌNG: công thức tiền xử lý này phải khớp CHÍNH XÁC với
     * preprocess_input của MobileNetV2 dùng lúc train trên Colab
     * (đưa giá trị pixel về khoảng -1..1). Không được đổi công thức này.
     */
    private ByteBuffer preprocess(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : pixels) {
            buffer.putFloat(((pixel >> 16) & 0xFF) / 127.5f - 1f); // R
            buffer.putFloat(((pixel >> 8) & 0xFF) / 127.5f - 1f);  // G
            buffer.putFloat((pixel & 0xFF) / 127.5f - 1f);         // B
        }
        return buffer;
    }

    private MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(modelName);
        FileInputStream inputStream = new FileInputStream(fd.getFileDescriptor());
        FileChannel channel = inputStream.getChannel();
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }

    private List<String> loadLabels(Context context, String fileName) throws IOException {
        List<String> list = new ArrayList<>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(fileName)));
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty()) list.add(line.trim());
        }
        reader.close();
        return list;
    }

    public void close() {
        if (interpreter != null) interpreter.close();
    }
}