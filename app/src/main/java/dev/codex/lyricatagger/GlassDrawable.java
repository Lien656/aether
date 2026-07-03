package dev.codex.lyricatagger;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

final class GlassDrawable extends GradientDrawable {
    GlassDrawable(float radiusDp, float density) {
        super(Orientation.TL_BR, new int[]{
                Color.argb(92, 255, 255, 255),
                Color.argb(34, 129, 81, 255),
                Color.argb(38, 0, 0, 0)
        });
        setCornerRadius(radiusDp * density);
        setStroke(Math.max(1, Math.round(1.1f * density)), Color.argb(96, 255, 255, 255));
    }
}
