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
    private boolean ordinaryDynamicPresentation;
    private long dynamicOwner,dynamicEpoch;
    private boolean dynamicVisible;
    private com.example.alpr_v1.domain.PlateTextConsensus dynamicReading;
    private State dynamicState=State.SEARCHING;
    private String dynamicStatusPrimary="",dynamicStatusHint="";
    public void setDynamicPresentation(boolean enabled) {
        if(ordinaryDynamicPresentation==enabled)return;
        ordinaryDynamicPresentation=enabled;dynamicOwner=dynamicEpoch=0;dynamicReading=null;dynamicState=State.SEARCHING;
        dynamicStatusPrimary=dynamicStatusHint="";
        if(!enabled){clearResult();configureLayout(layoutRoot);}
        else configureDynamicResultSpace();
    }
    private void configureDynamicResultSpace() {
        float density=layoutRoot.getResources().getDisplayMetrics().density;
        float extra=Math.max(0,layoutRoot.getResources().getConfiguration().fontScale-1f);
        boolean landscape=compactLandscape(layoutRoot);
        View row=layoutRoot.findViewById(R.id.main_device_result_row);
        if(!landscape&&row!=null) {
            int height=Math.round((64+(int)Math.ceil(extra*56/4)*4)*density);
            if(row.getLayoutParams().height!=height){row.getLayoutParams().height=height;row.requestLayout();}
        }
        if(landscape) {
            View slot=layoutRoot.findViewById(R.id.main_result_slot);
            int height=Math.max(Math.round(26*density),resultText.getLineHeight()+Math.round(4*density));
            if(slot!=null&&slot.getLayoutParams().height!=height){slot.getLayoutParams().height=height;slot.requestLayout();}
        }
    }
    public void renderDynamicResult(com.example.alpr_v1.tracking.DynamicVehicleDisplaySnapshot snapshot,long entityId) {
        renderDynamicResult(snapshot,entityId,null);
    }
    public void renderDynamicResult(com.example.alpr_v1.tracking.DynamicVehicleDisplaySnapshot snapshot,long entityId,
            com.example.alpr_v1.acquisition.ScanAcquisitionSnapshot scan) {
        setDynamicPresentation(true);
        if(snapshot==null){showState(dynamicReading==null?State.SEARCHING:State.RECOVERING,"");return;}
        updateDynamicStatus(snapshot,entityId,scan);
        com.example.alpr_v1.tracking.DynamicVehicleDisplaySnapshot.Vehicle vehicle=snapshot==null?null:snapshot.vehicles.get(entityId);
        com.example.alpr_v1.tracking.DynamicVehicleIdentity.Presentation identity=snapshot==null?null:snapshot.identities.get(entityId);
        com.example.alpr_v1.domain.PlateTextConsensus reading=vehicle==null?com.example.alpr_v1.domain.PlateTextConsensus.EMPTY:vehicle.displayReading;
        dynamicState=identity==null?State.SEARCHING:!identity.visible?State.RECOVERING:
                reading.available()?State.CONFIRMED:vehicle!=null&&vehicle.mzInProgress?State.RECOGNIZING:State.TRACKING;
        if(vehicle==null||identity==null||identity.verifying||!reading.available()) {
            if(dynamicReading!=null||dynamicOwner!=entityId)clearResult();
            dynamicOwner=entityId;dynamicReading=null;showState(dynamicState,"");return;
        }
        if(dynamicOwner!=entityId){clearResult();dynamicOwner=entityId;dynamicReading=null;}
        if(dynamicReading!=reading||dynamicEpoch!=identity.epoch||dynamicVisible!=identity.visible) {
            currentEvidenceEpoch=currentEvidenceSource=0L;
            showCurrentResult(vehicle.plateTrackId,reading.text,reading.confidence,
                    reading.stable,reading.observations,vehicle.displayReadingSourceNanos,identity.epoch);
            resultMeta.setText("P"+identity.number+" · "+resultMeta.getText());
            if(!identity.visible) {
                resultConfidence.setText(resultConfidence.getResources().getString(R.string.dynamic_result_last,reading.confidence*100.0));
                resultTray.setContentDescription("Ostatni odczyt · P"+identity.number+" · "+reading.text+" · "+resultConfidence.getText());
            }
            dynamicReading=reading;dynamicEpoch=identity.epoch;dynamicVisible=identity.visible;
        }
        showState(dynamicState,"");
    }

    private void updateDynamicStatus(com.example.alpr_v1.tracking.DynamicVehicleDisplaySnapshot snapshot,long id,
            com.example.alpr_v1.acquisition.ScanAcquisitionSnapshot scan) {
        dynamicStatusPrimary=dynamicStatusHint="";
        com.example.alpr_v1.acquisition.DynamicVehicleSizeGate.Decision size=snapshot.sizeDecisions.get(id);
        if(size==null&&id==0&&snapshot.sizeDecisions.size()==1)size=snapshot.sizeDecisions.values().iterator().next();
        android.content.res.Resources resources=statusPrimary.getResources();
        if(size!=null&&size.reason==com.example.alpr_v1.acquisition.DynamicVehicleSizeGate.Reason.INVALID_CONFIGURATION) {
            dynamicStatusPrimary=resources.getString(R.string.dynamic_threshold_invalid);
            dynamicStatusHint=resources.getString(R.string.dynamic_threshold_fix);return;
        }
        if(size!=null&&size.reason==com.example.alpr_v1.acquisition.DynamicVehicleSizeGate.Reason.VEHICLE_TOO_SMALL) {
            dynamicStatusPrimary=resources.getString(R.string.dynamic_vehicle_too_small);
            dynamicStatusHint=resources.getString(R.string.dynamic_vehicle_size_hint,Math.round(size.widthPx),Math.round(size.heightPx),size.requiredWidthPx,size.requiredHeightPx);return;
        }
        com.example.alpr_v1.tracking.DynamicVehicleDisplaySnapshot.Vehicle vehicle=snapshot.vehicles.get(id);
        com.example.alpr_v1.tracking.DynamicVehicleIdentity.Presentation identity=snapshot.identities.get(id);
        com.example.alpr_v1.tracking.DynamicPlateGeometryGate.Decision plateGeometry=snapshot.plateGeometryDecisions.get(id);
        if(plateGeometry!=null&&!plateGeometry.accepted()&&identity!=null&&identity.visible&&!identity.verifying
                &&plateGeometry.owner!=null&&plateGeometry.owner.epoch==identity.epoch
                &&plateGeometry.owner.analysisRunId==snapshot.analysisRunId) {
            dynamicStatusPrimary=resources.getString(R.string.dynamic_plate_geometry_waiting);
            dynamicStatusHint=resources.getString(vehicle!=null&&vehicle.reading.available()
                    ?R.string.dynamic_plate_geometry_last:R.string.dynamic_plate_geometry_hint);return;
        }
        if(vehicle!=null&&identity!=null&&identity.visible&&!identity.verifying&&vehicle.displayReading.available()) {
            dynamicStatusPrimary=resources.getString(R.string.dynamic_reading_available);
            dynamicStatusHint=resources.getString(R.string.dynamic_best_reading_hint);return;
        }
        if(vehicle==null||identity==null||!identity.visible||identity.verifying||vehicle.displayReading.available())return;
        if(vehicle.mzInProgress) {
            dynamicStatusPrimary=resources.getString(R.string.dynamic_reading_verifying);
            dynamicStatusHint=resources.getString(R.string.dynamic_collecting_read);return;
        }
        int mt=0,mz=0;
        if(scan!=null) {
            if(scan.activeEntityId==id){mt=scan.mtAttempts;mz=scan.freshMzAttempts;}
            for(com.example.alpr_v1.acquisition.AcquisitionCandidate candidate:scan.queue.candidates)
                if(candidate.entityId==id){mt=Math.max(mt,candidate.mtAttempts);mz=Math.max(mz,candidate.freshMzAttempts);}
        }
        int primary=vehicle.plateTrackId>0?(mz>0?R.string.dynamic_no_characters:R.string.dynamic_plate_located):
                mt>0?R.string.dynamic_stage_no_plate:R.string.dynamic_locating_plate;
        dynamicStatusPrimary=resources.getString(primary);
        dynamicStatusHint=resources.getString(vehicle.plateTrackId>0?R.string.dynamic_collecting_read:R.string.dynamic_keep_vehicle);
    }
    private DiagnosticsVisibilityListener diagnosticsVisibilityListener;
    private boolean timerCompleted;
    private CharSequence basePrimary="",baseSecondary="";
    private ResearchOperationPresentation operationStatus;
    private boolean operationPrimary,operationWarning;
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
        basePrimary=statusPrimary.getText();
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
        if(ordinaryDynamicPresentation&&(next==State.TRACKING||next==State.RECOGNIZING||next==State.CONFIRMED
                ||next==State.RECOVERING||next==State.SEARCHING))next=dynamicState;
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
            setPrimary(statusPrimary.getResources().getString(R.string.main_ui_stopped));
            updateStatusDescription();
            statusStrip.setVisibility(View.VISIBLE);
            event.setVisibility(View.GONE);
            calmHint.setText(R.string.recognition_searching);
            tintDot(state);
            applyTimerCompletionCopy();
            return;
        }
        statusStrip.setVisibility(View.VISIBLE);
        if (changed) {
            setPrimary(statusPrimary.getResources().getString(primaryText(state)));
            calmHint.setText(hintText(state));
            tintDot(state);
        }
        if(ordinaryDynamicPresentation&&(state==State.SEARCHING||state==State.TRACKING||state==State.RECOGNIZING
                ||state==State.CONFIRMED||state==State.RECOVERING)) {
            setPrimary(dynamicStatusPrimary.isEmpty()?statusPrimary.getResources().getString(primaryText(state)):dynamicStatusPrimary);
            calmHint.setText(dynamicStatusHint.isEmpty()?statusPrimary.getResources().getString(hintText(state)):dynamicStatusHint);
            setStatusSecondary(dynamicStatusHint);
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
        if (!primaryText.isEmpty()) setPrimary(primaryText);
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
        showResult(trackId,text,confidence,stable,hitCount,false);
    }
    private long currentEvidenceSource,currentEvidenceEpoch;
    public void showCurrentResult(long trackId,String text,double confidence,boolean stable,int hitCount,
            long sourceNanos,long ownerEpoch) {
        if(ownerEpoch<currentEvidenceEpoch||ownerEpoch==currentEvidenceEpoch&&sourceNanos<currentEvidenceSource)return;
        currentEvidenceEpoch=ownerEpoch;currentEvidenceSource=sourceNanos;
        showResult(trackId,text,confidence,stable,hitCount,true);
    }
    private void showResult(long trackId,String text,double confidence,boolean stable,int hitCount,boolean currentEvidence) {
        if (missingModelsStatusLocked) return;
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) return;
        boolean sameResult = trackId == resultTrackId && normalized.equals(stableResult);
        if (!currentEvidence && sameResult && confidence + 0.001 < stableConfidence) return;
        if (!sameResult) stableConfidence = 0.0;
        resultTrackId = trackId;
        stableResult = normalized;
        stableConfidence = currentEvidence?confidence:Math.max(stableConfidence, confidence);
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
            if(ordinaryDynamicPresentation) {
                resultTray.animate().cancel();resultTray.setAlpha(1f);resultTray.setTranslationY(0f);resultTray.setVisibility(View.VISIBLE);
            } else {
            resultTray.setAlpha(0f);
            resultTray.setTranslationY(12f);
            resultTray.setVisibility(View.VISIBLE);
            resultTray.animate().alpha(1f).translationY(0f).setDuration(180L).start();
            }
        }
        if(currentEvidence&&!stable)state=State.RECOGNIZING;
        showState(stable ? State.CONFIRMED : State.RECOGNIZING, "");
    }

    public void onTargetLost() {
        if(ordinaryDynamicPresentation)return;
        showState(State.RECOVERING, "");
        long elapsed = android.os.SystemClock.elapsedRealtime() - resultShownAtMillis;
        long delay = Math.max(0L, RESULT_MINIMUM_HOLD_MS - elapsed);
        handler.postDelayed(() -> {
            if (!ordinaryDynamicPresentation&&state == State.RECOVERING) hideResult();
        }, delay);
    }

    public void clearResult() {
        dynamicReading=null;
        currentEvidenceSource=currentEvidenceEpoch=0L;
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
        setPrimary(statusPrimary.getResources().getString(R.string.main_timer_finished_status));
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
            if(id==R.id.tv_frame_summary&&root.findViewById(R.id.main_calibration_actions).getVisibility()==View.VISIBLE)
                heightDp=Math.round(128*Math.max(1,font));
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
        baseSecondary=text;renderStatus();
    }
    private void setPrimary(CharSequence text){basePrimary=text;renderStatus();}
    /** File progress uses the fixed header, never the camera image or a measurement HUD. */
    public void setOperationStatus(ResearchOperationPresentation status,boolean replacePrimary,boolean warning){
        operationStatus=status;operationPrimary=replacePrimary;operationWarning=warning;renderStatus();
    }
    private void renderStatus(){
        CharSequence primary=basePrimary,secondary=baseSecondary;
        if(operationStatus!=null&&operationStatus.visible){
            if(operationStatus.active){
                if(operationPrimary&&!timerCompleted&&state!=State.ERROR&&state!=State.SETUP_REQUIRED){
                    primary=operationStatus.title;secondary=operationStatus.detail;
                }else secondary=operationStatus.title+(operationStatus.determinate?" · "+operationStatus.percent+"%":"");
            }else if(operationWarning)secondary=operationStatus.title;
        }
        setTextIfChanged(statusPrimary,primary);setTextIfChanged(statusSecondary,secondary);
        statusSecondary.setVisibility(secondary.length()==0?View.GONE:View.VISIBLE);updateStatusDescription();
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
                statusDot.getContext(), ordinaryDynamicPresentation&&value!=State.ERROR&&value!=State.SETUP_REQUIRED
                        ?R.color.cockpit_cyan:accentColor
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
