package com.example.alpr_v1.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import com.example.alpr_v1.R;
import com.google.android.material.button.MaterialButton;

/** Appearance only. Availability and actions remain owned by their existing controllers. */
public final class CockpitControlStyle {
    public enum State { OFF, ON, BUSY, STOP }
    private CockpitControlStyle() {}

    public static void apply(MaterialButton button, State state) {
        int key = state.ordinal() * 2 + (button.isEnabled() ? 1 : 0);
        if (Integer.valueOf(key).equals(button.getTag(R.id.cockpit_control_state))) return;
        button.setTag(R.id.cockpit_control_state, key);
        int active = button.getContext().getColor(R.color.cockpit_cyan);
        int neutral = button.getContext().getColor(R.color.alpr_text_primary);
        int muted = button.getContext().getColor(R.color.alpr_text_muted);
        int outline = button.getContext().getColor(R.color.alpr_outline);
        int surface = button.getContext().getColor(R.color.alpr_surface);
        int foreground = state == State.ON ? active : state == State.STOP
                ? button.getContext().getColor(R.color.alpr_error) : state == State.BUSY
                ? button.getContext().getColor(R.color.alpr_warning) : neutral;
        button.setActivated(state == State.ON);
        button.setIconTint(colors(foreground, muted));
        button.setTextColor(colors(foreground, muted));
        button.setStrokeColor(colors(state == State.OFF ? button.getContext().getColor(R.color.cockpit_available_outline) : foreground, outline));
        int fill = state == State.OFF ? surface : Color.argb(22, Color.red(foreground), Color.green(foreground), Color.blue(foreground));
        button.setBackgroundTintList(colors(fill, surface));
        button.setAlpha(1f);
        String description = state == State.ON ? "włączone / wybrane" : state == State.BUSY ? "w toku"
                : state == State.STOP ? "zatrzymaj" : "wyłączone / dostępna akcja";
        androidx.core.view.ViewCompat.setStateDescription(button,button.isEnabled() ? description : "zablokowane · " + description);
        button.setTooltipText(button.getContentDescription());
    }

    private static ColorStateList colors(int normal, int disabled) {
        return new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}}, new int[]{disabled, normal});
    }
}
