package com.example.coffeecheckai.ui.scan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** Lưới 3x3 phủ lên khung camera để căn hạt cà phê vào giữa. */
public class GridOverlayView extends View {

    private final Paint paint = new Paint();

    public GridOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(0x55FFFFFF);
        paint.setStrokeWidth(2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        for (int i = 1; i <= 2; i++) {
            canvas.drawLine(w * i / 3f, 0, w * i / 3f, h, paint);
            canvas.drawLine(0, h * i / 3f, w, h * i / 3f, paint);
        }
    }
}
