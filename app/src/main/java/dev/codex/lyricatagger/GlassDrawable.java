package dev.codex.lyricatagger;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

final class GlassDrawable extends GradientDrawable {
    GlassDrawable(float radiusDp, float density) {
        super(Orientation.TL_BR, new int[]{
                Color.argb(34, 255, 255, 255),
                Color.argb(28, 130, 75, 210),
                Color.argb(214, 7, 5, 18)
        });
        setCornerRadius(radiusDp * density);
        setStroke(Math.max(1, Math.round(1.1f * density)), Color.argb(48, 255, 255, 255));
    }
}
