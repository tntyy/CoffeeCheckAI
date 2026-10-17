package com.example.coffeecheckai.ui.scan;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.coffeecheckai.R;
import com.example.coffeecheckai.ml.CoffeeClassifier;
import com.example.coffeecheckai.utils.GoiYHelper;
import com.example.coffeecheckai.utils.ImageUtils;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.core.Rect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanFragment extends Fragment {

    // =========================
    // CAMERA
    // =========================

    private PreviewView previewView;
    private MaterialButton buttonCapture;
    private MaterialButton buttonGallery;
    private MaterialButton buttonFlash;
    private MaterialButton buttonRetake;

    private ImageCapture imageCapture;

    // =========================
    // RESULT UI
    // =========================

    private ImageView imageResult;
    private View resultPanel;
    private View cameraControls;

    private TextView textTotal;
    private TextView textFine;
    private TextView textDefect;
    private TextView textDefectRate;
    private TextView textGrade;
    private TextView textSuggestion;

    private ProgressBar progressProcessing;

    // =========================
    // AI
    // =========================

    private CoffeeClassifier classifier;

    // =========================
    // BACKGROUND THREAD
    // =========================

    private ExecutorService executorService;

    // =========================
    // GALLERY PICKER
    // =========================

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri != null) {
                            processGalleryImage(uri);
                        }
                    }
            );

    // =========================================================
    // ON CREATE VIEW
    // =========================================================

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState) {

        View root = inflater.inflate(
                R.layout.fragment_scan,
                container,
                false
        );

        findViews(root);

        executorService =
                Executors.newSingleThreadExecutor();

        initClassifier();

        startCamera();

        setupListeners();

        return root;
    }

    // =========================================================
    // FIND VIEWS
    // =========================================================

    private void findViews(View root) {

        previewView =
                root.findViewById(R.id.previewView);

        buttonCapture =
                root.findViewById(R.id.buttonCapture);

        buttonGallery =
                root.findViewById(R.id.buttonGallery);

        buttonFlash =
                root.findViewById(R.id.buttonFlash);

        buttonRetake =
                root.findViewById(R.id.buttonRetake);

        imageResult =
                root.findViewById(R.id.imageResult);

        resultPanel =
                root.findViewById(R.id.resultPanel);

        cameraControls =
                root.findViewById(R.id.cameraControls);

        textTotal =
                root.findViewById(R.id.textTotal);

        textFine =
                root.findViewById(R.id.textFine);

        textDefect =
                root.findViewById(R.id.textDefect);

        textDefectRate =
                root.findViewById(R.id.textDefectRate);

        textGrade =
                root.findViewById(R.id.textGrade);

        textSuggestion =
                root.findViewById(R.id.textSuggestion);

        progressProcessing =
                root.findViewById(R.id.progressProcessing);
    }

    // =========================================================
    // INIT CLASSIFIER
    // =========================================================

    private void initClassifier() {

        try {

            classifier =
                    new CoffeeClassifier(
                            requireContext()
                    );

            Log.d(
                    "ScanFragment",
                    "CoffeeClassifier loaded successfully"
            );

        } catch (IOException e) {

            Log.e(
                    "ScanFragment",
                    "Không thể load model TFLite",
                    e
            );

            Toast.makeText(
                    requireContext(),
                    "Không thể tải model AI",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // =========================================================
    // LISTENERS
    // =========================================================

    private void setupListeners() {

        // Chụp ảnh
        buttonCapture.setOnClickListener(
                view -> takePhoto()
        );

        // Chọn ảnh từ thư viện
        buttonGallery.setOnClickListener(
                view -> galleryLauncher.launch("image/*")
        );

        // Chụp lại
        buttonRetake.setOnClickListener(
                view -> hideResult()
        );

        // Flash
        buttonFlash.setOnClickListener(
                view -> toggleFlash()
        );
    }

    // =========================================================
    // CAMERA
    // =========================================================

    private void startCamera() {

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(
                        requireContext()
                );

        future.addListener(
                () -> {

                    try {

                        ProcessCameraProvider cameraProvider =
                                future.get();

                        Preview preview =
                                new Preview.Builder()
                                        .build();

                        preview.setSurfaceProvider(
                                previewView.getSurfaceProvider()
                        );

                        imageCapture =
                                new ImageCapture.Builder()
                                        .build();

                        cameraProvider.unbindAll();

                        cameraProvider.bindToLifecycle(
                                this,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                        );

                    } catch (
                            ExecutionException |
                            InterruptedException e) {

                        Log.e(
                                "ScanFragment",
                                "Camera error",
                                e
                        );
                    }

                },
                ContextCompat.getMainExecutor(
                        requireContext()
                )
        );
    }

    // =========================================================
    // TAKE PHOTO
    // =========================================================

    private void takePhoto() {

        if (imageCapture == null) {

            Toast.makeText(
                    requireContext(),
                    "Camera chưa sẵn sàng",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        File appDir =
                new File(
                        requireContext()
                                .getExternalFilesDir(
                                        Environment.DIRECTORY_PICTURES
                                ),
                        "CoffeeCheck"
                );

        if (!appDir.exists()) {
            appDir.mkdirs();
        }

        File photoFile =
                new File(
                        appDir,
                        "coffee_" +
                                System.currentTimeMillis() +
                                ".jpg"
                );

        ImageCapture.OutputFileOptions options =
                new ImageCapture.OutputFileOptions.Builder(
                        photoFile
                ).build();

        imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(
                        requireContext()
                ),
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(
                            @NonNull ImageCapture.OutputFileResults outputFileResults) {

                        Toast.makeText(
                                requireContext(),
                                "Đã chụp ảnh, đang phân tích...",
                                Toast.LENGTH_SHORT
                        ).show();

                        processImage(photoFile);
                    }

                    @Override
                    public void onError(
                            @NonNull ImageCaptureException exception) {

                        Log.e(
                                "ScanFragment",
                                "Capture error",
                                exception
                        );

                        Toast.makeText(
                                requireContext(),
                                "Không thể chụp ảnh",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    // =========================================================
    // GALLERY
    // =========================================================

    private void processGalleryImage(Uri uri) {

        showLoading();

        executorService.execute(
                () -> {

                    File tempFile = null;

                    try {

                        /*
                         * Đọc ảnh từ Gallery
                         */
                        InputStream inputStream =
                                requireContext()
                                        .getContentResolver()
                                        .openInputStream(uri);

                        if (inputStream == null) {

                            showMessage(
                                    "Không thể mở ảnh"
                            );

                            return;
                        }

                        /*
                         * Tạo file tạm
                         */
                        tempFile =
                                new File(
                                        requireContext()
                                                .getCacheDir(),
                                        "gallery_" +
                                                System.currentTimeMillis() +
                                                ".jpg"
                                );

                        FileOutputStream outputStream =
                                new FileOutputStream(
                                        tempFile
                                );

                        byte[] buffer =
                                new byte[8192];

                        int length;

                        while (
                                (length =
                                        inputStream.read(buffer)) > 0) {

                            outputStream.write(
                                    buffer,
                                    0,
                                    length
                            );
                        }

                        outputStream.close();
                        inputStream.close();

                        /*
                         * Dùng CHUNG pipeline với Camera.
                         */
                        processImage(tempFile);

                    } catch (Exception e) {

                        Log.e(
                                "ScanFragment",
                                "Gallery error",
                                e
                        );

                        showMessage(
                                "Không thể đọc ảnh từ thư viện"
                        );
                    }
                }
        );
    }

    // =========================================================
    // PROCESS IMAGE
    // CAMERA + GALLERY ĐỀU ĐI VÀO ĐÂY
    // =========================================================

    private void processImage(File photoFile) {

        if (classifier == null) {

            showMessage(
                    "Model AI chưa được tải"
            );

            return;
        }

        showLoading();

        executorService.execute(
                () -> {

                    Bitmap original = null;
                    Bitmap enhanced = null;
                    Bitmap resultBitmap = null;

                    try {

                        /*
                         * Đọc ảnh
                         */
                        original =
                                BitmapFactory.decodeFile(
                                        photoFile.getAbsolutePath()
                                );

                        if (original == null) {

                            showMessage(
                                    "Không đọc được ảnh"
                            );

                            return;
                        }

                        /*
                         * 1. OpenCV enhance
                         */
                        enhanced =
                                ImageUtils.enhance(
                                        original
                                );

                        /*
                         * 2. Tìm tất cả hạt cà phê
                         */
                        List<Rect> rects =
                                ImageUtils.findAllBeanRects(
                                        enhanced
                                );

                        if (rects == null ||
                                rects.isEmpty()) {

                            showMessage(
                                    "Không phát hiện được hạt cà phê"
                            );

                            return;
                        }

                        /*
                         * 3. Phân loại từng hạt
                         */
                        List<String> labels =
                                new ArrayList<>();

                        int fineCount = 0;
                        int defectCount = 0;

                        for (Rect rect : rects) {

                            Bitmap beanBitmap =
                                    ImageUtils.cropRect(
                                            enhanced,
                                            rect
                                    );

                            CoffeeClassifier.ClassificationResult result =
                                    classifier.classify(
                                            beanBitmap
                                    );

                            String label =
                                    result.label;

                            labels.add(label);

                            if (
                                    "Fine".equalsIgnoreCase(
                                            label
                                    )) {

                                fineCount++;

                            } else if (
                                    "Defect".equalsIgnoreCase(
                                            label
                                    )) {

                                defectCount++;
                            }

                            beanBitmap.recycle();
                        }

                        /*
                         * 4. Tổng hợp
                         */
                        int total =
                                fineCount +
                                        defectCount;

                        float defectRate =
                                total > 0
                                        ? defectCount *
                                          100f /
                                          total
                                        : 0f;

                        /*
                         * 5. Xếp loại tạm thời
                         *
                         * Sau này sẽ thay bằng
                         * đúng quy tắc trong đề cương.
                         */
                        String grade =
                                calculateGrade(
                                        defectRate
                                );

                        /*
                         * 6. Gợi ý
                         */
                        String suggestion =
                                GoiYHelper.getSuggestion(
                                        defectRate,
                                        fineCount,
                                        defectCount
                                );

                        /*
                         * 7. Vẽ bounding box
                         */
                        resultBitmap =
                                ImageUtils.drawAllBeanBoxes(
                                        enhanced,
                                        rects,
                                        labels
                                );

                        /*
                         * 8. Hiển thị kết quả
                         */
                        showResult(
                                resultBitmap,
                                total,
                                fineCount,
                                defectCount,
                                defectRate,
                                grade,
                                suggestion
                        );

                    } catch (Exception e) {

                        Log.e(
                                "ScanFragment",
                                "AI processing error",
                                e
                        );

                        showMessage(
                                "Lỗi khi phân tích ảnh: " +
                                        e.getMessage()
                        );

                    } finally {

                        if (original != null &&
                                !original.isRecycled()) {

                            original.recycle();
                        }

                        if (enhanced != null &&
                                !enhanced.isRecycled()) {

                            enhanced.recycle();
                        }
                    }
                }
        );
    }

    // =========================================================
    // SHOW RESULT
    // =========================================================

    private void showResult(
            Bitmap resultBitmap,
            int total,
            int fine,
            int defect,
            float defectRate,
            String grade,
            String suggestion) {

        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(
                () -> {

                    hideLoading();

                    previewView.setVisibility(
                            View.GONE
                    );

                    cameraControls.setVisibility(
                            View.GONE
                    );

                    imageResult.setImageBitmap(
                            resultBitmap
                    );

                    imageResult.setVisibility(
                            View.VISIBLE
                    );

                    resultPanel.setVisibility(
                            View.VISIBLE
                    );

                    textTotal.setText(
                            "Tổng hạt\n" + total
                    );

                    textFine.setText(
                            "Fine\n" + fine
                    );

                    textDefect.setText(
                            "Defect\n" + defect
                    );

                    textDefectRate.setText(
                            "Tỷ lệ lỗi\n" +
                                    String.format(
                                            "%.1f%%",
                                            defectRate
                                    )
                    );

                    textGrade.setText(
                            "Xếp loại: " +
                                    grade
                    );

                    textSuggestion.setText(
                            "Gợi ý: " +
                                    suggestion
                    );
                }
        );
    }

    // =========================================================
    // HIDE RESULT
    // =========================================================

    private void hideResult() {

        imageResult.setVisibility(
                View.GONE
        );

        resultPanel.setVisibility(
                View.GONE
        );

        previewView.setVisibility(
                View.VISIBLE
        );

        cameraControls.setVisibility(
                View.VISIBLE
        );
    }

    // =========================================================
    // LOADING
    // =========================================================

    private void showLoading() {

        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(
                () -> {

                    if (progressProcessing != null) {

                        progressProcessing.setVisibility(
                                View.VISIBLE
                        );
                    }
                }
        );
    }

    private void hideLoading() {

        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(
                () -> {

                    if (progressProcessing != null) {

                        progressProcessing.setVisibility(
                                View.GONE
                        );
                    }
                }
        );
    }

    // =========================================================
    // MESSAGE
    // =========================================================

    private void showMessage(String message) {

        if (!isAdded()) {
            return;
        }

        requireActivity().runOnUiThread(
                () -> {

                    hideLoading();

                    Toast.makeText(
                            requireContext(),
                            message,
                            Toast.LENGTH_LONG
                    ).show();
                }
        );
    }

    // =========================================================
    // GRADE - TẠM THỜI
    // =========================================================

    private String calculateGrade(
            float defectRate) {

        if (defectRate == 0) {
            return "Tốt";
        }

        if (defectRate <= 10) {
            return "Khá";
        }

        if (defectRate <= 25) {
            return "Trung bình";
        }

        return "Cần kiểm tra";
    }

    // =========================================================
    // FLASH
    // =========================================================

    private void toggleFlash() {

        if (imageCapture == null) {
            return;
        }

        int currentMode =
                imageCapture.getFlashMode();

        if (currentMode ==
                ImageCapture.FLASH_MODE_ON) {

            imageCapture.setFlashMode(
                    ImageCapture.FLASH_MODE_OFF
            );

            Toast.makeText(
                    requireContext(),
                    "Flash: Tắt",
                    Toast.LENGTH_SHORT
            ).show();

        } else {

            imageCapture.setFlashMode(
                    ImageCapture.FLASH_MODE_ON
            );

            Toast.makeText(
                    requireContext(),
                    "Flash: Bật",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    public void onDestroyView() {

        super.onDestroyView();

        if (classifier != null) {

            classifier.close();

            classifier = null;
        }

        if (executorService != null) {

            executorService.shutdown();

            executorService = null;
        }
    }
}