package com.example.coffeecheckai.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.coffeecheckai.R;
import com.example.coffeecheckai.utils.AppSettings;
import com.google.android.material.button.MaterialButton;

/** 3 màn giới thiệu hiện ở lần mở app đầu tiên. */
public class OnboardingActivity extends AppCompatActivity {

    private static final int[] IMAGES = {
            R.drawable.img_onboard1,
            R.drawable.img_onboard2,
            R.drawable.img_robot_logo
    };
    private static final String[] TITLES = {
            "Công nghệ AI nhận diện chính xác",
            "Phát hiện & phân loại tự động",
            "Báo cáo thông minh"
    };
    private static final String[] DESCS = {
            "Phân tích chất lượng hạt cà phê một cách nhanh chóng và chính xác.",
            "Tự động nhận diện từng hạt, phân loại Fine / Defect và thống kê chi tiết kết quả phân tích.",
            "Nhận kết quả trực quan, lưu lịch sử, xem thống kê và gợi ý cải thiện chất lượng cà phê."
    };

    private int page = 0;
    private ImageView image;
    private TextView title;
    private TextView desc;
    private LinearLayout dots;
    private MaterialButton buttonNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        image = findViewById(R.id.imageIllustration);
        title = findViewById(R.id.textTitle);
        desc = findViewById(R.id.textDesc);
        dots = findViewById(R.id.dots);
        buttonNext = findViewById(R.id.buttonNext);

        findViewById(R.id.buttonSkip).setOnClickListener(v -> finishOnboarding());
        buttonNext.setOnClickListener(v -> {
            if (page < IMAGES.length - 1) {
                page++;
                render();
            } else {
                finishOnboarding();
            }
        });

        if (savedInstanceState != null) {
            page = savedInstanceState.getInt("page", 0);
        }
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("page", page);
    }

    private void render() {
        image.setImageResource(IMAGES[page]);
        title.setText(TITLES[page]);
        desc.setText(DESCS[page]);
        buttonNext.setText(page == IMAGES.length - 1 ? "Bắt đầu ngay" : "Tiếp tục");

        dots.removeAllViews();
        int margin = (int) (4 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < IMAGES.length; i++) {
            View dot = new View(this);
            dot.setBackgroundResource(i == page ? R.drawable.dot_active : R.drawable.dot_inactive);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (10 * getResources().getDisplayMetrics().density),
                    (int) (10 * getResources().getDisplayMetrics().density));
            lp.setMargins(margin, 0, margin, 0);
            dots.addView(dot, lp);
        }
    }

    private void finishOnboarding() {
        new AppSettings(this).setOnboardingDone(true);
        Intent intent = new Intent(this, SplashActivity.class);
        intent.putExtra("skip_delay", true);
        startActivity(intent);
        finish();
    }
}
