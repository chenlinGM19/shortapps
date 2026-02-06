package com.shortapps.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ColorWheelView extends View {

    private Paint colorWheelPaint;
    private Paint pointerPaint;
    private float centerX, centerY, radius;
    private int selectedColor = Color.RED;
    private float selectedAngle = 0f;
    private OnColorSelectedListener listener;

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    public ColorWheelView(Context context) { super(context); init(); }
    public ColorWheelView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        colorWheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        colorWheelPaint.setStyle(Paint.Style.STROKE);

        pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointerPaint.setStyle(Paint.Style.STROKE);
        pointerPaint.setStrokeWidth(6f);
        pointerPaint.setColor(Color.WHITE);
        pointerPaint.setShadowLayer(5f, 0f, 0f, Color.BLACK);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        radius = Math.min(centerX, centerY) - 30; // More padding for selector
        
        colorWheelPaint.setStrokeWidth(radius * 0.3f);
        
        int[] colors = {Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN, Color.GREEN, Color.YELLOW, Color.RED};
        SweepGradient sweepGradient = new SweepGradient(centerX, centerY, colors, null);
        colorWheelPaint.setShader(sweepGradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Draw Wheel
        canvas.drawCircle(centerX, centerY, radius, colorWheelPaint);
        
        // Draw Selector
        float angleRad = (float) Math.toRadians(selectedAngle);
        float selX = centerX + (float) Math.cos(angleRad) * radius;
        float selY = centerY + (float) Math.sin(angleRad) * radius;
        
        canvas.drawCircle(selX, selY, radius * 0.15f + 4, pointerPaint);
        
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(selectedColor);
        canvas.drawCircle(selX, selY, radius * 0.15f, fillPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Prevent ScrollView from intercepting touches
        getParent().requestDisallowInterceptTouchEvent(true);
        
        float x = event.getX() - centerX;
        float y = event.getY() - centerY;
        
        float angle = (float) Math.toDegrees(Math.atan2(y, x));
        if (angle < 0) angle += 360;
        
        selectedAngle = angle;

        // HSV color
        float[] hsv = {angle, 1f, 1f};
        selectedColor = Color.HSVToColor(hsv);
        
        if (listener != null) {
            listener.onColorSelected(selectedColor);
        }
        
        invalidate();
        return true;
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        this.listener = listener;
    }
}