package com.example.alpr_v1.ui;

import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
        PREVIEW,
        SEARCHING,
        STATIC_IDLE,
        TRACKING,
        RECOGNIZING,
        CONFIRMED,
        RECOVERING,
        SETUP_REQUIRED,
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
    private boolean missingModelsStatusLocked;
    private DiagnosticsVisibilityListener diagnosticsVisibilityListener;
    private boolean timerCompleted;
    private final android.view.ViewTreeObserver.OnPreDrawListener hudFrame = () -> { layoutHudPanels(); return true; };
    private final View modelInfoPanel;
    private final View layoutRoot;
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener gridLabels;

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
        layoutRoot=statusStrip.getRootView();
        configureLayout(layoutRoot);
        modelInfoPanel=layoutRoot.findViewById(R.id.live_model_info);
        if(diagnosticsPanel instanceof LiveHudView)((LiveHudView)diagnosticsPanel).setProtectedAreaSupplier(() -> {
            HudProtectedArea protectedArea=new HudProtectedArea();
            DetectionOverlayView detections=layoutRoot.findViewById(R.id.detection_overlay);
            TvCalibrationOverlayView tv=layoutRoot.findViewById(R.id.tv_setup_overlay);
            if(detections!=null&&detections.isShown())protectedArea.include(detections.hudProtectedArea());
            if(tv!=null&&tv.isShown())protectedArea.include(tv.hudProtectedArea());
            View target=layoutRoot.findViewById(R.id.auto_zoom_target);
            if(target!=null&&target.isShown()&&detections!=null){
                android.graphics.Rect rect=new android.graphics.Rect();int[] origin=new int[2];
                detections.getLocationOnScreen(origin);
                if(target.getGlobalVisibleRect(rect)){
                    android.graphics.RectF local=new android.graphics.RectF(rect);local.offset(-origin[0],-origin[1]);protectedArea.add(local);
                }
            }
            return protectedArea;
        });
        layoutRoot.getViewTreeObserver().addOnPreDrawListener(hudFrame);
        gridLabels=()->{fitGridLabels(layoutRoot);layoutHudPanels();};
        layoutRoot.getViewTreeObserver().addOnGlobalLayoutListener(gridLabels);
        View.OnClickListener toggleDiagnostics =
                view -> setDiagnosticsExpanded(!diagnosticsExpanded);
        statusStrip.setOnClickListener(toggleDiagnostics);
        diagnosticsToggle.setOnClickListener(toggleDiagnostics);
    }

    public void showState(State next, String secondaryText) {
        State safeState = resolveStateForMissingModels(
                next,
                missingModelsStatusLocked
        );
        if (state == State.CONFIRMED
                && resultTray.getVisibility() == View.VISIBLE
                && (safeState == State.TRACKING || safeState == State.RECOGNIZING)) {
            safeState = State.CONFIRMED;
        }
        boolean changed = safeState != state;
        state = safeState;
        // Szczegóły techniczne przekazywane przez starsze wywołania są dostępne
        // w rozwijanej diagnostyce. Pasek główny pokazuje tylko komunikat użytkowy.
        setStatusSecondary(state == State.STATIC_IDLE ? statusSecondary.getResources().getString(R.string.main_ui_static_wait) : "");
        diagnosticsPanel.setVisibility(diagnosticsExpanded ? View.VISIBLE : View.GONE);
        diagnosticsText.setVisibility(View.GONE);
        if (state == State.STOPPED) {
            setTextIfChanged(statusPrimary,statusPrimary.getResources().getString(R.string.main_ui_stopped));
            statusStrip.setContentDescription(statusPrimary.getText());
            statusStrip.setVisibility(View.VISIBLE);
            event.setVisibility(View.GONE);
            calmHint.setText(R.string.recognition_searching);
            tintDot(state);
            applyTimerCompletionCopy();
            return;
        }
        statusStrip.setVisibility(View.VISIBLE);
        if (changed) {
            setTextIfChanged(statusPrimary,statusPrimary.getResources().getString(primaryText(state)));
            calmHint.setText(hintText(state));
            tintDot(state);
        }
        applyTimerCompletionCopy();
        updateStatusDescription();
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

    public void showUserStatus(
            State next,
            CharSequence primary,
            CharSequence hint
    ) {
        if (missingModelsStatusLocked && next != State.SETUP_REQUIRED) {
            showState(State.SETUP_REQUIRED, "");
            return;
        }
        showState(next, "");
        String primaryText = primary == null ? "" : primary.toString().trim();
        String hintText = hint == null ? "" : hint.toString().trim();
        if (!primaryText.isEmpty()) setTextIfChanged(statusPrimary,primaryText);
        if (!hintText.isEmpty()) calmHint.setText(hintText);
        if(next!=State.STATIC_IDLE) setStatusSecondary(hintText);
        applyTimerCompletionCopy();
        updateStatusDescription();
    }

    public void showMissingModelsStatus(
            CharSequence primary,
            CharSequence hint
    ) {
        missingModelsStatusLocked = true;
        showUserStatus(State.SETUP_REQUIRED, primary, hint);
    }

    public void clearMissingModelsStatus() {
        missingModelsStatusLocked = false;
    }

    public void showResult(
            long trackId,
            String text,
            double confidence,
            boolean stable,
            int hitCount
    ) {
        if (missingModelsStatusLocked) return;
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
                stable ? "stabilne · MZ %.0f%%" : "odczyt · MZ %.0f%%",
                Math.max(0.0, Math.min(1.0, stableConfidence)) * 100.0
        ));
        resultConfidence.setContentDescription(resultConfidence.getResources().getString(R.string.main_ui_mz_result_description,resultConfidence.getText()));
        resultMeta.setText(resultMeta.getResources().getQuantityString(
                R.plurals.live_hit_count,
                Math.max(1, hitCount),
                Math.max(1, hitCount)
        ));
        resultTray.setContentDescription(normalized+" · "+resultConfidence.getText()+" · "+resultMeta.getText());
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
        diagnosticsText.setText("");
        clearResult();
        showState(State.STOPPED, "");
    }

    private void setDiagnosticsExpanded(boolean expanded) {
        if(expanded&&!diagnosticsExpanded&&diagnosticsPanel instanceof LiveHudView)((LiveHudView)diagnosticsPanel).resetPlacement();
        diagnosticsExpanded = expanded;
        diagnosticsPanel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        diagnosticsText.setVisibility(View.GONE);

        if (diagnosticsVisibilityListener != null) diagnosticsVisibilityListener.onDiagnosticsVisibilityChanged(expanded);
        layoutHudPanels();
    }

    /** Presentation only: the deadline and session completion are owned elsewhere. */
    public void setTimerCompleted(boolean completed){
        if(timerCompleted==completed)return;
        timerCompleted=completed;showState(state,"");
    }
    private void applyTimerCompletionCopy(){
        if(!timerCompleted||state==State.ERROR||state==State.SETUP_REQUIRED)return;
        setTextIfChanged(statusPrimary,statusPrimary.getResources().getString(R.string.main_timer_finished_status));
        setStatusSecondary(statusSecondary.getResources().getString(R.string.main_timer_finished_hint));
        updateStatusDescription();
    }
    /** The two existing HUD views stay in their own parents and can be visible together. */
    public boolean toggleModelInfo(){
        if(modelInfoPanel==null)return false;
        boolean visible=modelInfoPanel.getVisibility()!=View.VISIBLE;
        modelInfoPanel.setVisibility(visible?View.VISIBLE:View.INVISIBLE);

        layoutHudPanels();
        return visible;
    }
    /** Visibility affects only instruments and colors, never the preview or the fixed footer slot. */
    private void layoutHudPanels(){
        View first=layoutRoot.findViewById(R.id.hud1_viewport);
        if(first==null)return;
        first.setVisibility(diagnosticsExpanded?View.VISIBLE:View.GONE);
        LiveHudView hud=(LiveHudView)diagnosticsPanel;
        if(diagnosticsExpanded&&hud.isShown())hud.placeInstruments();
        boolean visible=diagnosticsExpanded&&hud.isShown()&&hud.visibleInstrumentCount()>0;
        View blockedNotice=layoutRoot.findViewById(R.id.hud1_blocked_notice);
        if(blockedNotice!=null)blockedNotice.setVisibility(diagnosticsExpanded&&hud.isShown()&&hud.isSpaceBlocked()?View.VISIBLE:View.INVISIBLE);
        com.google.android.material.button.MaterialButton button=layoutRoot.findViewById(R.id.live_diagnostics_toggle);
        if(button!=null){
            CockpitControlStyle.apply(button,visible?CockpitControlStyle.State.ON:CockpitControlStyle.State.OFF);
            CharSequence state=diagnosticsExpanded&&!visible?button.getResources().getString(R.string.cockpit_hud_occluded)
                    :visible?"włączone / widoczna telemetria":"wyłączone";
            if(!state.equals(androidx.core.view.ViewCompat.getStateDescription(button)))androidx.core.view.ViewCompat.setStateDescription(button,state);
        }
        button=layoutRoot.findViewById(R.id.live_model_info_toggle);
        if(button!=null)CockpitControlStyle.apply(button,modelInfoPanel!=null&&modelInfoPanel.isShown()?CockpitControlStyle.State.ON:CockpitControlStyle.State.OFF);
    }
    public void dispose(){
        if(layoutRoot.getViewTreeObserver().isAlive()){layoutRoot.getViewTreeObserver().removeOnGlobalLayoutListener(gridLabels);layoutRoot.getViewTreeObserver().removeOnPreDrawListener(hudFrame);}
        handler.removeCallbacksAndMessages(null);
    }

    /** Stable dimensions for a window/font configuration, never driven by a clock tick. */
    public static void configureLayout(View root){
        float density=root.getResources().getDisplayMetrics().density;
        float extra=Math.max(0,root.getResources().getConfiguration().fontScale-1f);
        com.google.android.material.appbar.MaterialToolbar toolbar=root.findViewById(R.id.top_app_bar);
        if(toolbar!=null&&root.getResources().getConfiguration().screenWidthDp<360&&extra>.1f)
            toolbar.setTitleTextAppearance(root.getContext(),R.style.MainCompactToolbarTitle);
        boolean landscape=compactLandscape(root);
        float font=root.getResources().getConfiguration().fontScale;
        LinearLayout information=root.findViewById(R.id.main_information_strip);
        if(information!=null)information.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        if(landscape){
            View header=root.findViewById(R.id.normal_camera_hud),strip=root.findViewById(R.id.live_status_strip),panel=root.findViewById(R.id.control_panel);
            if(header!=null)header.setPadding(Math.round(8*density),0,Math.round(8*density),0);
            if(strip!=null)strip.setPadding(Math.round(8*density),Math.round(2*density),Math.round(8*density),Math.round(2*density));
            if(panel!=null)panel.setPadding(Math.round(8*density),Math.round(2*density),Math.round(8*density),Math.round(2*density));
            TextView primary=root.findViewById(R.id.live_status),secondary=root.findViewById(R.id.live_status_secondary);
            if(primary!=null&&secondary!=null){
                ((LinearLayout)primary.getParent()).setOrientation(LinearLayout.HORIZONTAL);
                for(TextView text:new TextView[]{primary,secondary}){
                    LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)text.getLayoutParams();p.width=0;p.weight=1;p.topMargin=0;p.setMarginStart(text==secondary?Math.round(8*density):0);
                    text.setMaxLines(1);text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                }
            }
            LinearLayout clock=root.findViewById(R.id.experiment_timer_status);
            if(clock!=null){clock.setOrientation(LinearLayout.HORIZONTAL);clock.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.END);}
            TextView clockCaption=root.findViewById(R.id.experiment_timer_caption);
            if(clockCaption!=null)((LinearLayout.LayoutParams)clockCaption.getLayoutParams()).setMarginStart(Math.round(4*density));
            TextView summary=root.findViewById(R.id.tv_frame_status);if(summary!=null)summary.setTextSize(10);
        }
        for(int id:new int[]{R.id.normal_camera_hud,R.id.auto_zoom_control,R.id.camera_action_row,R.id.tv_frame_summary,R.id.main_device_result_row}){
            View v=root.findViewById(id);if(v==null)continue;
            boolean info=id==R.id.tv_frame_summary||id==R.id.main_device_result_row;
            int base=info?(id==R.id.tv_frame_summary?48:64):64;
            int heightDp=base+(int)Math.ceil(extra*24/4)*4;
            if(landscape)heightDp=id==R.id.normal_camera_hud?Math.max(24,(int)Math.ceil((font*17+4)/4)*4):
                    info?Math.max(64,(int)Math.ceil((font*12+28)/4)*4):Math.max(48,(int)Math.ceil((font*12+27)/4)*4);
            int height=Math.round(heightDp*density);
            if(info){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)v.getLayoutParams();p.width=landscape?0:ViewGroup.LayoutParams.MATCH_PARENT;p.weight=landscape?1:0;}
            if(v.getLayoutParams().height!=height){v.getLayoutParams().height=height;v.requestLayout();}
        }
        configureInstrumentStrip(root,landscape,density);
    }
    private static void configureInstrumentStrip(View root,boolean landscape,float density){
        View level=root.findViewById(R.id.phone_orientation_panel);
        if(level!=null){int size=Math.round((landscape?48:56)*density);level.getLayoutParams().width=size;level.getLayoutParams().height=size;}
        LinearLayout data=root.findViewById(R.id.main_data_column),tv=root.findViewById(R.id.main_tv_configuration);
        View result=root.findViewById(R.id.main_result_slot);
        if(data!=null&&tv!=null&&result!=null){
            LinearLayout destination=landscape?tv:data;
            if(result.getParent()!=destination){((ViewGroup)result.getParent()).removeView(result);destination.addView(result);}
            result.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,landscape?Math.round(26*density):0,landscape?0:1));
            View status=root.findViewById(R.id.tv_frame_status);
            status.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
            View notice=root.findViewById(R.id.hud1_blocked_notice);
            if(notice!=null){
                LinearLayout noticeParent=landscape?data:tv;
                if(notice.getParent()!=noticeParent){((ViewGroup)notice.getParent()).removeView(notice);noticeParent.addView(notice);}
                notice.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Math.round(18*density)));
            }
            View divider=root.findViewById(R.id.instrument_level_divider);
            if(divider!=null)divider.setVisibility(View.VISIBLE);
            root.findViewById(R.id.tv_frame_summary).setBackgroundResource(landscape?R.drawable.bg_cockpit_section_side:R.drawable.bg_cockpit_section_bottom);
        }
    }
    private static boolean compactLandscape(View root){
        android.content.res.Configuration c=root.getResources().getConfiguration();
        return c.screenWidthDp>c.screenHeightDp&&c.screenHeightDp<480;
    }
    public static void fitGridLabels(View root){
        if(compactLandscape(root))for(int id:new int[]{R.id.live_scene_mode_toggle,R.id.collection_toggle}){
            TextView v=root.findViewById(id);if(v!=null&&v.getText().toString().contains("\n"))v.setText(v.getText().toString().replace('\n',' '));
        }
        for(int id:new int[]{R.id.collection_toggle,R.id.gallery_open_button}){
            TextView v=root.findViewById(id);if(v==null||v.getWidth()==0)continue;
            String text=v.getText().toString(),first=text.split("\n",2)[0];
            if(v.getPaint().measureText(first)<=v.getWidth()-v.getCompoundPaddingLeft()-v.getCompoundPaddingRight())continue;
            String shorter=id==R.id.collection_toggle?text.replace("Akwizycja",root.getResources().getString(R.string.main_grid_acquisition_short)):
                    text.replace("Weryfikuj",root.getResources().getString(R.string.main_grid_review_short));
            if(!shorter.equals(text))v.setText(shorter);
        }
    }

    /** Presentation identity only; never selects or locks an ALPR track. */
    public long presentedTrackId(){return resultTray.getVisibility()==View.VISIBLE?resultTrackId:0L;}

    private void setStatusSecondary(CharSequence text){
        setTextIfChanged(statusSecondary,text);
        statusSecondary.setVisibility(text.length()==0?View.GONE:View.VISIBLE);
    }
    private static void setTextIfChanged(TextView view,CharSequence text){
        if(!text.toString().contentEquals(view.getText()))view.setText(text);
    }
    private void updateStatusDescription(){
        String text=statusPrimary.getText().toString();
        if(statusSecondary.getVisibility()==View.VISIBLE)text+=". "+statusSecondary.getText();
        if(!text.contentEquals(statusStrip.getContentDescription()==null?"":statusStrip.getContentDescription()))statusStrip.setContentDescription(text);
    }

    private void hideResult() {
        resultTray.animate().cancel();
        resultTray.setVisibility(View.GONE);
    }

    private int primaryText(State value) {
        switch (value) {
            case STATIC_IDLE: return R.string.static_scene_idle;
            case PREVIEW: return R.string.live_state_preview;
            case TRACKING: return R.string.live_state_tracking;
            case RECOGNIZING: return R.string.live_state_recognizing;
            case CONFIRMED: return R.string.live_state_confirmed;
            case RECOVERING: return R.string.live_state_recovering;
            case SETUP_REQUIRED: return R.string.live_state_models_missing;
            case ERROR: return R.string.live_state_error;
            case SEARCHING:
            default: return R.string.live_state_searching;
        }
    }

    private int hintText(State value) {
        switch (value) {
            case STATIC_IDLE: return R.string.main_ui_static_wait;
            case PREVIEW: return R.string.live_hint_preview;
            case TRACKING: return R.string.live_hint_tracking;
            case RECOGNIZING: return R.string.live_hint_recognizing;
            case CONFIRMED: return R.string.live_hint_confirmed;
            case RECOVERING: return R.string.live_hint_recovering;
            case SETUP_REQUIRED: return R.string.live_hint_models_missing;
            case ERROR: return R.string.recognition_unavailable;
            case SEARCHING:
            default: return R.string.recognition_searching;
        }
    }

    private void tintDot(State value) {
        int accentColor;
        int backgroundColor;
        switch (value) {
            case PREVIEW:
                accentColor = R.color.alpr_primary;
                backgroundColor = R.color.alpr_status_searching;
                break;
            case STATIC_IDLE:
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
            case SETUP_REQUIRED:
            case STOPPED:
                accentColor = R.color.alpr_text_secondary;
                backgroundColor = R.color.alpr_status_searching;
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

    static State resolveStateForMissingModels(
            State requested,
            boolean missingModels
    ) {
        State safe = requested == null ? State.SEARCHING : requested;
        if (missingModels
                && safe != State.STOPPED
                && safe != State.PREVIEW
                && safe != State.ERROR) {
            return State.SETUP_REQUIRED;
        }
        return safe;
    }
}
