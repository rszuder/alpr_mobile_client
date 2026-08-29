package com.example.alpr_v1.ui;

import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.alpr_v1.R;

import java.util.Locale;

/** Jedyny regulator częstotliwości i hierarchii komunikatów ekranu live. */
public final class LivePresentationController {
    public interface DiagnosticsVisibilityListener {
        void onDiagnosticsVisibilityChanged(boolean visible);
    }
    public enum State {
        STOPPED,
        SEARCHING,
        TRACKING,
        RECOGNIZING,
        CONFIRMED,
        RECOVERING,
        ERROR
    }

    private static final long EVENT_HOLD_MS = 1_400L;
    private static final long RESULT_MINIMUM_HOLD_MS = 2_000L;
    private static final long DIAGNOSTICS_REFRESH_MS = 1_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final View statusStrip;
    private final View statusDot;
    private final TextView statusPrimary;
    private final TextView statusSecondary;
    private final TextView event;
    private final View diagnosticsPanel;
    private final TextView diagnosticsText;
    private final TextView calmHint;
    private final View resultTray;
    private final TextView resultText;
    private final TextView resultConfidence;
    private final TextView resultMeta;

    private State state = State.STOPPED;
    private String lastEvent = "";
    private int eventGeneration;
    private boolean diagnosticsExpanded;
    private long lastDiagnosticsUpdateMillis;
    private long resultShownAtMillis;
    private long resultTrackId;
    private String stableResult = "";
    private double stableConfidence;
    private DiagnosticsVisibilityListener diagnosticsVisibilityListener;

    public LivePresentationController(
            View statusStrip,
            View statusDot,
            TextView statusPrimary,
            TextView statusSecondary,
            View diagnosticsToggle,
            TextView event,
            View diagnosticsPanel,
            TextView diagnosticsText,
            TextView calmHint,
            View resultTray,
            TextView resultText,
            TextView resultConfidence,
            TextView resultMeta
    ) {
        this.statusStrip = statusStrip;
        this.statusDot = statusDot;
        this.statusPrimary = statusPrimary;
        this.statusSecondary = statusSecondary;
        this.statusSecondary.setVisibility(View.GONE);
        this.event = event;
        this.diagnosticsPanel = diagnosticsPanel;
        this.diagnosticsText = diagnosticsText;
        this.calmHint = calmHint;
        this.resultTray = resultTray;
        this.resultText = resultText;
        this.resultConfidence = resultConfidence;
        this.resultMeta = resultMeta;
        View.OnClickListener toggleDiagnostics =
                view -> setDiagnosticsExpanded(!diagnosticsExpanded);
        statusStrip.setOnClickListener(toggleDiagnostics);
        diagnosticsToggle.setOnClickListener(toggleDiagnostics);
    }

    public void showState(State next, String secondaryText) {
        State safeState = next == null ? State.SEARCHING : next;
        if (state == State.CONFIRMED
                && resultTray.getVisibility() == View.VISIBLE
                && (safeState == State.TRACKING || safeState == State.RECOGNIZING)) {
            safeState = State.CONFIRMED;
        }
        boolean changed = safeState != state;
        state = safeState;
        // Szczegóły techniczne przekazywane przez starsze wywołania są dostępne
        // w rozwijanej diagnostyce. Pasek główny pokazuje tylko komunikat użytkowy.
        statusSecondary.setText("");
        statusSecondary.setVisibility(View.GONE);
        if (state == State.STOPPED) {
            statusStrip.setVisibility(View.GONE);
            diagnosticsPanel.setVisibility(View.GONE);
            diagnosticsText.setVisibility(View.GONE);
            event.setVisibility(View.GONE);
            calmHint.setText(R.string.recognition_searching);
            return;
        }
        statusStrip.setVisibility(View.VISIBLE);
        if (changed) {
            statusPrimary.setText(primaryText(state));
            calmHint.setText(hintText(state));
            tintDot(state);
        }
        boolean showDiagnostics = diagnosticsExpanded;
        diagnosticsPanel.setVisibility(showDiagnostics ? View.VISIBLE : View.GONE);
        diagnosticsText.setVisibility(showDiagnostics ? View.VISIBLE : View.GONE);
    }

    public void showTransient(CharSequence message) {
        String text = message == null ? "" : message.toString().trim();
        if (text.isEmpty()) return;
        if (text.equals(lastEvent) && event.getVisibility() == View.VISIBLE) return;
        lastEvent = text;
        int generation = ++eventGeneration;
        event.setText(text);
        event.setAlpha(0f);
        event.setVisibility(View.VISIBLE);
        event.animate().alpha(1f).setDuration(140L).start();
        handler.postDelayed(() -> {
            if (generation != eventGeneration) return;
            event.animate().alpha(0f).setDuration(180L).withEndAction(() -> {
                if (generation == eventGeneration) event.setVisibility(View.GONE);
            }).start();
        }, EVENT_HOLD_MS);
    }

    public void showResult(
            long trackId,
            String text,
            double confidence,
            boolean stable,
            int hitCount
    ) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) return;
        boolean sameResult = trackId == resultTrackId && normalized.equals(stableResult);
        if (sameResult && confidence + 0.001 < stableConfidence) return;
        if (!sameResult) stableConfidence = 0.0;
        resultTrackId = trackId;
        stableResult = normalized;
        stableConfidence = Math.max(stableConfidence, confidence);
        resultShownAtMillis = android.os.SystemClock.elapsedRealtime();
        resultText.setText(normalized);
        resultConfidence.setText(String.format(
                Locale.forLanguageTag("pl-PL"),
                stable ? "stabilne · %.0f%%" : "odczyt · %.0f%%",
                Math.max(0.0, Math.min(1.0, stableConfidence)) * 100.0
        ));
        resultMeta.setText(resultMeta.getResources().getQuantityString(
                R.plurals.live_hit_count,
                Math.max(1, hitCount),
                Math.max(1, hitCount)
        ));
        if (resultTray.getVisibility() != View.VISIBLE) {
            resultTray.setAlpha(0f);
            resultTray.setTranslationY(12f);
            resultTray.setVisibility(View.VISIBLE);
            resultTray.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        }
        showState(stable ? State.CONFIRMED : State.RECOGNIZING, "");
    }

    public void onTargetLost() {
        showState(State.RECOVERING, "");
        long elapsed = android.os.SystemClock.elapsedRealtime() - resultShownAtMillis;
        long delay = Math.max(0L, RESULT_MINIMUM_HOLD_MS - elapsed);
        handler.postDelayed(() -> {
            if (state == State.RECOVERING) hideResult();
        }, delay);
    }

    public void clearResult() {
        stableResult = "";
        stableConfidence = 0.0;
        resultTrackId = 0L;
        hideResult();
    }

    public void updateDiagnostics(CharSequence text) {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastDiagnosticsUpdateMillis < DIAGNOSTICS_REFRESH_MS) return;
        lastDiagnosticsUpdateMillis = now;
        diagnosticsText.setText(text == null ? "" : text);
    }

    public boolean diagnosticsExpanded() {
        return diagnosticsExpanded;
    }

    public void setDiagnosticsVisibilityListener(
            DiagnosticsVisibilityListener listener
    ) {
        diagnosticsVisibilityListener = listener;
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        eventGeneration++;
        setDiagnosticsExpanded(false);
        clearResult();
        showState(State.STOPPED, "");
    }

    private void setDiagnosticsExpanded(boolean expanded) {
        diagnosticsExpanded = expanded;
        boolean showDiagnostics = expanded && state != State.STOPPED;
        diagnosticsPanel.setVisibility(showDiagnostics ? View.VISIBLE : View.GONE);
        diagnosticsText.setVisibility(showDiagnostics ? View.VISIBLE : View.GONE);
        if (diagnosticsVisibilityListener != null) {
            diagnosticsVisibilityListener.onDiagnosticsVisibilityChanged(
                    showDiagnostics
            );
        }
    }

    private void hideResult() {
        resultTray.animate().cancel();
        resultTray.setVisibility(View.GONE);
    }

    private int primaryText(State value) {
        switch (value) {
            case TRACKING: return R.string.live_state_tracking;
            case RECOGNIZING: return R.string.live_state_recognizing;
            case CONFIRMED: return R.string.live_state_confirmed;
            case RECOVERING: return R.string.live_state_recovering;
            case ERROR: return R.string.live_state_error;
            case SEARCHING:
            default: return R.string.live_state_searching;
        }
    }

    private int hintText(State value) {
        switch (value) {
            case TRACKING: return R.string.live_hint_tracking;
            case RECOGNIZING: return R.string.live_hint_recognizing;
            case CONFIRMED: return R.string.live_hint_confirmed;
            case RECOVERING: return R.string.live_hint_recovering;
            case ERROR: return R.string.recognition_unavailable;
            case SEARCHING:
            default: return R.string.recognition_searching;
        }
    }

    private void tintDot(State value) {
        int accentColor;
        int backgroundColor;
        switch (value) {
            case CONFIRMED:
            case TRACKING:
                accentColor = R.color.alpr_success;
                backgroundColor = R.color.alpr_status_success;
                break;
            case RECOGNIZING:
            case RECOVERING:
                accentColor = R.color.alpr_warning;
                backgroundColor = R.color.alpr_status_working;
                break;
            case ERROR:
                accentColor = R.color.alpr_error;
                backgroundColor = R.color.alpr_status_error;
                break;
            case SEARCHING:
            default:
                accentColor = R.color.alpr_accent;
                backgroundColor = R.color.alpr_status_searching;
                break;
        }
        int resolvedAccent = ContextCompat.getColor(
                statusDot.getContext(), accentColor
        );
        statusDot.setBackgroundTintList(ColorStateList.valueOf(
                resolvedAccent
        ));
        statusPrimary.setTextColor(resolvedAccent);
        statusStrip.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(statusStrip.getContext(), backgroundColor)
        ));
    }
}
