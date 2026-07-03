package dev.codex.lyricatagger;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

final class AetherBackgroundView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    AetherBackgroundView(Context context) {
        super(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float t = (SystemClock.uptimeMillis() % 20000L) / 20000f;

        paint.setShader(new LinearGradient(
                0, 0, width, height,
                new int[]{
                        Color.rgb(3, 2, 13),
                        Color.rgb(12, 5, 31),
                        Color.rgb(20, 12, 42),
                        Color.rgb(4, 3, 15)
                },
                new float[]{0f, 0.34f, 0.68f, 1f},
                Shader.TileMode.CLAMP
        ));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        drawGlow(canvas, width * (0.20f + 0.08f * wave(t, 0f)), height * 0.18f, width * 0.72f, Color.argb(110, 124, 58, 255));
        drawGlow(canvas, width * (0.84f + 0.05f * wave(t, 0.4f)), height * 0.56f, width * 0.58f, Color.argb(90, 50, 125, 255));
        drawGlow(canvas, width * 0.42f, height * (0.82f + 0.04f * wave(t, 0.7f)), width * 0.68f, Color.argb(70, 198, 70, 255));

        for (int layer = 0; layer < 3; layer++) {
            drawWave(canvas, width, height, t, layer);
        }

        paint.setShader(null);
        paint.setColor(Color.argb(80, 255, 255, 255));
        for (int i = 0; i < 34; i++) {
            float x = ((i * 73) % Math.max(width, 1)) + wave(t, i * 0.03f) * 10f;
            float y = ((i * 131) % Math.max(height, 1)) + wave(t, i * 0.07f) * 14f;
            float r = 0.8f + (i % 4) * 0.35f;
            canvas.drawCircle(x, y, r, paint);
        }

        postInvalidateOnAnimation();
    }

    private void drawGlow(Canvas canvas, float cx, float cy, float radius, int color) {
        paint.setShader(new RadialGradient(
                cx, cy, radius,
                color,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private void drawWave(Canvas canvas, int width, int height, float t, int layer) {
        path.reset();
        float base = height * (0.22f + layer * 0.12f);
        float amp = 22f + layer * 14f;
        path.moveTo(0, base);
        for (int x = 0; x <= width; x += 16) {
            float y = base
                    + (float) Math.sin((x / 62f) + t * 6.28f + layer) * amp
                    + (float) Math.cos((x / 113f) - t * 8.2f) * amp * 0.48f;
            path.lineTo(x, y);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f + layer);
        paint.setColor(Color.argb(72 - layer * 12, 181, 76, 255));
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private float wave(float t, float shift) {
        return (float) Math.sin((t + shift) * Math.PI * 2.0);
    }
}
