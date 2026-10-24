package com.example.coffeecheckai.ui.settings;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.coffeecheckai.R;
import com.example.coffeecheckai.utils.AppSettings;
import com.google.android.material.slider.Slider;

import java.util.Locale;

/**
 * Cài đặt: ngưỡng độ tin cậy (quyết định có lưu lịch sử hay không),
 * chế độ quét mặc định, thông tin ứng dụng.
 */
public class SettingsFragment extends Fragment {

    private AppSettings settings;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_settings, container, false);

        settings = new AppSettings(requireContext());

        setupVersion(root);
        setupThreshold(root);
        setupMode(root);
        setupDetector(root);
        setupInfoRows(root);

        return root;
    }

    private void setupVersion(View root) {
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

    private void setupThreshold(View root) {
        Slider slider = root.findViewById(R.id.sliderThreshold);
        TextView textValue = root.findViewById(R.id.textThresholdValue);

        float current = Math.max(0f, Math.min(1f, settings.getThreshold()));
        slider.setValue(current);
        textValue.setText(String.format(Locale.US, "%.2f", current));

        slider.addOnChangeListener((s, value, fromUser) -> {
            textValue.setText(String.format(Locale.US, "%.2f", value));
            if (fromUser) settings.setThreshold(value);
        });
    }

    private void setupMode(View root) {
        RadioGroup group = root.findViewById(R.id.radioGroupMode);

        switch (settings.getMode()) {
            case AppSettings.MODE_DETECT:
                group.check(R.id.radioDetect);
                break;
            case AppSettings.MODE_CLASSIFY:
                group.check(R.id.radioClassify);
                break;
            default:
                group.check(R.id.radioAuto);
        }

        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (checkedId == R.id.radioDetect) {
                settings.setMode(AppSettings.MODE_DETECT);
            } else if (checkedId == R.id.radioClassify) {
                settings.setMode(AppSettings.MODE_CLASSIFY);
            } else {
                settings.setMode(AppSettings.MODE_AUTO);
            }
        });
    }

    private void setupDetector(View root) {
        RadioGroup group = root.findViewById(R.id.radioGroupDetector);

        group.check(settings.getDetector() == AppSettings.DETECTOR_OPENCV
                ? R.id.radioOpenCv : R.id.radioYolo);

        group.setOnCheckedChangeListener((g, checkedId) ->
                settings.setDetector(checkedId == R.id.radioOpenCv
                        ? AppSettings.DETECTOR_OPENCV
                        : AppSettings.DETECTOR_YOLO));
    }

    private void setupInfoRows(View root) {
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
                "CoffeeCheck AI là đồ án môn Chuyên đề thiết kế ứng dụng cho thiết bị di động. "
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
