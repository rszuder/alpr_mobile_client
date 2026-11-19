package com.example.alpr_v1.ui;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.graphics.Rect;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.SeekBar;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import com.example.alpr_v1.R;
import com.example.alpr_v1.camera.CameraGeometryProvider;
import com.example.alpr_v1.camera.CameraSetupControl;
import com.example.alpr_v1.camera.CameraSetupSnapshot;
import com.example.alpr_v1.camera.CameraSetupReadiness;
import com.example.alpr_v1.camera.CameraSelection;
import com.example.alpr_v1.setup.CameraPreviewGeometry;
import com.example.alpr_v1.setup.TvDistanceCalculator;
import com.example.alpr_v1.setup.TvSetupCalibration;
import com.example.alpr_v1.setup.TvCornerDraft;
import com.example.alpr_v1.setup.TvEditDraft;
import com.example.alpr_v1.vision.Point2;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONObject;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** One owner of setup presentation. Camera acquisition and measurement configuration are independent. */
public final class TvSetupController {
    public static final String ACTION="tv_setup_requested_action";
    private static final String STORED="tv_setup_calibration_v1", INPUTS="tv_setup_inputs_v1", OUTLINE="tv_outline_visible_v1";
    private boolean persistedApprovalExpected;
    public enum Phase { NORMAL, WAITING, CAMERA_SETUP, EDITING, REVIEW, CAMERA_ERROR, COMMITTING, CANCELLING }
    public enum Mode { CALIBRATION, DISTANCE, F6_PHOTO }
    public enum EditorExit { SAVED, CANCELLED, BACKGROUND }
    private java.util.function.Consumer<EditorExit> editorExit=reason->{};
    public void setEditorExitListener(java.util.function.Consumer<EditorExit> listener){editorExit=listener==null?reason->{}:listener;}
    private boolean photoMode;
    private String researchSummary="";
    public void setResearchSummary(String summary){
        String value=summary==null?"":summary;if(value.equals(researchSummary))return;
        researchSummary=value;
        // Bounds belong to the fixed main layout, never to preview freshness or text wrapping.
        TextView caption=find(R.id.tv_frame_status);caption.setTextSize(value.isEmpty()?10:12);caption.setMaxLines(value.isEmpty()?2:3);
        renderOutlineControl();
    }
    private com.example.alpr_v1.experiment.Final180PhotoRoiApproval photoApproval;
    private String photoLoadKey="",photoApprovalFailure="";
    private long photoRequest;
    private int draftInitializations;
    private boolean firstDraftChangeLogged;
    private boolean freshPhotoDraft;
    private List<com.example.alpr_v1.setup.TvCornerEditGeometry.Position> entryEditorPoints;
    private androidx.appcompat.app.AlertDialog exitQuestion;
    private final AppCompatActivity activity;
    private final SharedPreferences preferences;
    private final PreviewView preview;
    private final View normalPanel,normalHud,panel,root;
    private final TvCalibrationOverlayView overlay;
    private final DetectionOverlayView detections;
    private final BooleanSupplier editable;
    private final Runnable startPreview;
    private final BiConsumer<String,JSONObject> event;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final OnBackPressedCallback back;
    private final TextView title,status,instruction,coverage,error,result;
    private final MaterialButton clear,confirm,cancel,retry;
    private final MaterialButtonToggleGroup corners,units,ratios;
    private final EditText diagonal,width,height,aspectW,aspectH,margin,measured;
    private final int[] cornerIds={R.id.tv_corner_lg,R.id.tv_corner_pg,R.id.tv_corner_pd,R.id.tv_corner_ld};
    private final int[] cornerNames={R.string.tv_corner_lg_description,R.string.tv_corner_pg_description,R.string.tv_corner_pd_description,R.string.tv_corner_ld_description};
    private Runnable controlsRefresh=()->{},geometryRefresh=()->{};
    private java.util.function.Supplier<String> inputSummary=this::plannedInputSummary;
    public void setInputSummaryProvider(java.util.function.Supplier<String> provider){inputSummary=java.util.Objects.requireNonNull(provider);renderOutlineControl();}
    public void refreshInputSummary(){renderOutlineControl();}
    private String plannedInputSummary(){
        boolean tv=preferences.getBoolean(com.example.alpr_v1.SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,false)
                &&"roi_budget".equals(preferences.getString(com.example.alpr_v1.SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget"))
                &&"TV_BBOX_CROP".equals(preferences.getString(com.example.alpr_v1.SettingsActivity.KEY_TV_INPUT_MODE,"CAMERA_FULL_FRAME"));
        return activity.getString(R.string.main_ui_input_planned,activity.getString(tv?R.string.main_ui_input_tv:R.string.main_ui_input_full));
    }
    private volatile TvSetupCalibration calibration;
    private TvSetupCalibration entryCalibration;
    private TvEditDraft draft,resumeSource;
    private boolean resumeCameraRequested;
    private volatile CameraPreviewGeometry geometry;
    private CameraPreviewGeometry entryGeometry,sourceContext,frozenGeometry;
    private JSONObject metadata,fields=new JSONObject();
    private List<Point2> pendingSave;
    private Phase phase=Phase.NORMAL;
    private boolean calculatorMode,binding,invalidationSent,presentationLaidOut=true;
    private String inputMode="DIAGONAL_IN",metadataReason="",frozenDigest;
    private CharSequence toolbarTitle;
    private android.graphics.drawable.Drawable toolbarNavigation;
    private int toolbarHeight;
    private float toolbarTitlePixels;
    private final Map<Integer,Boolean> menuVisibility=new HashMap<>();
    private final int normalPreviewTopToTop,normalPreviewTopToBottom;
    private final ConstraintLayout.LayoutParams normalPreviewLayout,normalSetupLayout;
    private CameraSetupControl cameraControls;
    private volatile CameraSetupControl.State cameraState=CameraSetupControl.State.unavailable();
    private CameraSetupControl.State entryCameraState;
    private boolean cameraStep,calibrationVisited,cameraBinding,zoomDragging,lastLandscape;
    private String lensSignature="";
    private final Spinner lens;
    private final SeekBar zoom;
    private final TextView cameraDetail,zoomValue;
    private final MaterialButton next;
    private long transition;
    private long waitingDeadline;
    private boolean geometryPresented;
    private CameraGeometryProvider.GeometryState geometryState;
    private String cameraFailure="";
    private String cameraFailureCode="";
    private boolean outlineVisible,imeVisible,revealPosted;
    private int imeBottom,windowBottomWithoutIme;
    private ConstraintLayout.LayoutParams imePreviewLayout;
    private EditText lastInput;
    private final TvReferenceLayout reference;
    private String referenceLensSignature="";
    private boolean cornerFeedbackPosted;
    private final int[] nudgeIds={R.id.tv_corner_left,R.id.tv_corner_up,R.id.tv_corner_down,R.id.tv_corner_right};

    public TvSetupController(AppCompatActivity activity,PreviewView preview,DetectionOverlayView detections,
            BooleanSupplier editable,Runnable startPreview,BiConsumer<String,JSONObject> event) {
        this.activity=activity;this.preview=preview;this.detections=detections;this.editable=editable;
        this.startPreview=startPreview;this.event=event;
        ConstraintLayout.LayoutParams originalPreview=(ConstraintLayout.LayoutParams)preview.getLayoutParams();
        normalPreviewLayout=new ConstraintLayout.LayoutParams(originalPreview);
        normalPreviewTopToTop=originalPreview.topToTop;normalPreviewTopToBottom=originalPreview.topToBottom;
        preferences=activity.getSharedPreferences("alpr_ui",0);root=activity.findViewById(R.id.main);
        LivePresentationController.configureLayout(root);
        normalPanel=activity.findViewById(R.id.control_panel);normalHud=activity.findViewById(R.id.normal_camera_hud);
        panel=activity.findViewById(R.id.tv_setup_panel);overlay=activity.findViewById(R.id.tv_setup_overlay);
        normalSetupLayout=new ConstraintLayout.LayoutParams((ConstraintLayout.LayoutParams)panel.getLayoutParams());
        title=find(R.id.tv_setup_title);status=find(R.id.tv_setup_status);instruction=find(R.id.tv_setup_instruction);
        coverage=find(R.id.tv_setup_coverage);error=find(R.id.tv_setup_error);result=find(R.id.tv_distance_result);
        clear=find(R.id.tv_setup_clear);confirm=find(R.id.tv_setup_confirm);cancel=find(R.id.tv_setup_cancel);retry=find(R.id.tv_setup_retry);
        next=find(R.id.tv_setup_next);lens=find(R.id.tv_camera_lens);zoom=find(R.id.tv_camera_zoom);
        cameraDetail=find(R.id.tv_camera_detail);zoomValue=find(R.id.tv_camera_zoom_value);
        corners=find(R.id.tv_corner_choices);units=find(R.id.tv_distance_units);ratios=find(R.id.tv_distance_ratios);
        diagonal=find(R.id.tv_input_diagonal);width=find(R.id.tv_input_width);height=find(R.id.tv_input_height);
        aspectW=find(R.id.tv_input_aspect_w);aspectH=find(R.id.tv_input_aspect_h);margin=find(R.id.tv_input_margin);measured=find(R.id.tv_input_measured);
        String storedApproval=preferences.getString(STORED,"");
        calibration=TvSetupCalibration.restore(storedApproval);persistedApprovalExpected=!storedApproval.isEmpty();
        draft=TvEditDraft.restore(preferences.getString(TvEditDraft.PREFERENCE,""));
        outlineVisible=preferences.getBoolean(OUTLINE,true);
        try{fields=new JSONObject(preferences.getString(INPUTS,"{}"));inputMode=fields.optString("mode","DIAGONAL_IN");}catch(Exception ignored){}
        overlay.configure(preview,this::onDraftChanged,()->editable.getAsBoolean()&&phase==Phase.EDITING&&!calculatorMode&&!cameraStep&&presentationReady()&&controlsReady());overlay.setCorners(calibration.corners);
        overlay.attachLoupe(find(R.id.tv_corner_loupe));
        reference=new TvReferenceLayout(activity);
        ((MaterialButtonToggleGroup)find(R.id.tv_corner_zoom_choices)).addOnButtonCheckedListener((group,id,checked)->{
            if(checked&&!binding)overlay.setLoupeZoom(id==R.id.tv_corner_zoom_8?8:4);
        });
        find(R.id.tv_corner_step).setOnClickListener(v->overlay.setStep(overlay.step()==1?5:1));
        bindNudge(R.id.tv_corner_left,-1,0);bindNudge(R.id.tv_corner_up,0,-1);bindNudge(R.id.tv_corner_down,0,1);bindNudge(R.id.tv_corner_right,1,0);
        back=new OnBackPressedCallback(false){@Override public void handleOnBackPressed(){navigateBack();}};
        activity.getOnBackPressedDispatcher().addCallback(activity,back);
        clear.setOnClickListener(v->clearDraft());confirm.setOnClickListener(v->requestExit(true));cancel.setOnClickListener(v->requestUserCancel());
        find(R.id.tv_frame_edit).setOnClickListener(v->open(Mode.CALIBRATION));
        find(R.id.tv_setup_details).setOnClickListener(v->{String state=calibration.status(geometry);
            String label=photoMode?editorState():activity.getString("READY".equals(state)?R.string.tv_precision_saved:"STALE".equals(state)?R.string.tv_precision_stale:R.string.tv_precision_missing);
            MaterialAlertDialogBuilder dialog=new com.example.alpr_v1.ui.ImmersiveDialogBuilder(activity,R.style.ThemeOverlay_Alpr_TvSetupDialog).setTitle(photoMode?R.string.f6_photo_roi_title:R.string.tv_editor_details_title)
                    .setMessage(phase==Phase.CAMERA_ERROR?geometryDetails():label+"\n\n"+(photoMode?activity.getString(R.string.tv_precision_photo_rectangle)+"\n\n":"")+coverage.getText()+(photoMode&&!photoApprovalFailure.isEmpty()?"\n\n"+photoApprovalFailure:"")
                            +(photoMode&&!controlsReady()?"\n\n"+cameraState.reason:"")).setPositiveButton(R.string.tv_editor_close_details,null);
            if(com.example.alpr_v1.BuildConfig.DEBUG&&phase==Phase.CAMERA_ERROR)dialog.setNeutralButton(R.string.tv_camera_copy,(d,which)->{
                android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ALPR camera geometry",geometryDetails()));});
            dialog.show();});
        next.setOnClickListener(v->{if(phase!=Phase.CAMERA_SETUP||!presentationReady()||!controlsReady()||zoomDragging)return;
            cameraFailureCode="";cameraFailure="";
            cameraStep=false;sourceContext=geometry;phase=Phase.EDITING;
            if(!seedCorners(false))return;
            overlay.setEditing(true);configurePanel();render();find(R.id.tv_setup_body_scroll).post(()->((android.widget.ScrollView)find(R.id.tv_setup_body_scroll)).scrollTo(0,0));});
        retry.setOnClickListener(v->{resetWaitingBudget();if(phase==Phase.REVIEW){sourceContext=geometry;overlay.setCorners(Collections.emptyList());overlay.selectCorner(0);phase=Phase.WAITING;}
            else phase=Phase.WAITING;overlay.setGeometry(geometry);
            if(cameraControls!=null&&!cameraState.ready())cameraControls.apply(cameraState.requested,cameraState.requestedZoom);
            startPreview.run();requestGeometry();armTimeout();reconcile();});
        corners.addOnButtonCheckedListener((group,id,checked)->{if(checked&&!binding)for(int i=0;i<4;i++)if(cornerIds[i]==id)overlay.selectCorner(i);});
        bindInputs();
        lens.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                if(cameraBinding||cameraControls==null||!editing()||calculatorMode||!cameraStep)return;
                Object item=parent.getItemAtPosition(position);if(!(item instanceof CameraSetupControl.Choice))return;
                CameraSelection selected=((CameraSetupControl.Choice)item).selection;if(!availableLens(selected))return;
                if(selected.equals(cameraState.requested)||cameraState.applied!=null&&selected.equals(cameraState.applied.selection)
                        ||cameraState.requested.isDefault()&&cameraState.applied==null&&position==0)return;
                requestCamera(selected,1);
            }});
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){if(user)zoomValue.setText(String.format(Locale.getDefault(),"Zoom %.2f×",zoomForProgress(progress)));}
            public void onStartTrackingTouch(SeekBar bar){zoomDragging=true;next.setEnabled(false);}
            public void onStopTrackingTouch(SeekBar bar){zoomDragging=false;if(cameraControls!=null&&editing()&&!calculatorMode&&cameraStep)requestCamera(cameraState.requested,zoomForProgress(bar.getProgress()));}
        });
        preview.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{presentationLaidOut=true;
            if(editing()){preview.post(this::reconcile);requestGeometry();}});
        root.getViewTreeObserver().addOnPreDrawListener(()->{
            if(reference!=null&&reference.enabled()&&reference.windowChanged()){applyVisibility();requestGeometry();}
            if(reference!=null&&reference.enabled()&&!reference.geometryLayoutPending()){
                presentationLaidOut=true;if(restoring())preview.post(this::reconcile);
            }
            if(!imeVisible){int[] p=new int[2];root.getLocationInWindow(p);windowBottomWithoutIme=p[1]+root.getHeight();}
            if((phase==Phase.EDITING||phase==Phase.CAMERA_SETUP)&&presentationReady()&&controlsReady())geometryPresented=true;
            return true;
        });
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(editing()&&lastLandscape!=landscape()){
            lastLandscape=landscape();configurePanel();applyVisibility();requestGeometry();}});
        find(R.id.tv_setup_body_scroll).addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->revealFocusedField());
        CameraWindowUi.observeIme(root,this::onIme);
        renderOutlineControl();
    }
    @SuppressWarnings("unchecked") private <T extends View>T find(int id){return (T)activity.findViewById(id);}
    public Phase phase(){return phase;}
    public Mode mode(){return photoMode?Mode.F6_PHOTO:calculatorMode?Mode.DISTANCE:Mode.CALIBRATION;}
    public boolean editing(){return phase!=Phase.NORMAL;}
    public boolean restoring(){return phase==Phase.COMMITTING||phase==Phase.CANCELLING;}
    public boolean outlineVisible(){return outlineVisible;}
    private boolean canNudge(){return phase==Phase.EDITING&&!calculatorMode&&!cameraStep&&editable.getAsBoolean()&&presentationReady()&&controlsReady()&&overlay.editing()&&overlay.selectedPosition()!=null;}
    private void bindNudge(int id,int dx,int dy){
        MaterialButton button=find(id);boolean[] touch={false},suppressClick={false};
        button.setOnClickListener(v->{if(!suppressClick[0]&&canNudge())overlay.nudgeSelected(dx,dy);});
        button.setOnTouchListener((view,event)->{
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                if(!canNudge()||!overlay.startNudge(dx,dy))return false;
                touch[0]=true;view.setPressed(true);view.getParent().requestDisallowInterceptTouchEvent(true);return true;
            }
            if(!touch[0])return false;
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_POINTER_DOWN){
                overlay.stopNudge(action!=MotionEvent.ACTION_UP);touch[0]=false;view.setPressed(false);view.getParent().requestDisallowInterceptTouchEvent(false);
                if(action==MotionEvent.ACTION_UP){suppressClick[0]=true;view.performClick();suppressClick[0]=false;}
            }
            return true;
        });
    }
    private void onDraftChanged(){
        if(editing()&&photoMode&&entryEditorPoints!=null&&!firstDraftChangeLogged&&pointsChanged()){
            firstDraftChangeLogged=true;diagnostic("first_corner_move","");
        }
        if(binding||!editing()||calculatorMode)return;
        if(!overlay.isManipulating()){saveDraft();render();return;}
        confirm.setEnabled(false);
        if(!cornerFeedbackPosted){cornerFeedbackPosted=true;root.postOnAnimation(()->{cornerFeedbackPosted=false;if(editing()&&!calculatorMode)renderCornerFeedback();});}
    }
    private void saveDraft(){
        if(photoMode)return;
        if(calculatorMode||overlay.isManipulating()||overlay.placedCount()!=4||sourceContext==null
                ||phase!=Phase.EDITING&&phase!=Phase.CAMERA_SETUP)return;
        CameraSetupSnapshot camera=new CameraSetupSnapshot(new CameraSelection(sourceContext.cameraId,sourceContext.physicalCameraId),(float)sourceContext.zoomRatio);
        draft=new TvEditDraft(overlay.draftPositions(),overlay.selectedCorner(),sourceContext,camera);
        preferences.edit().putString(TvEditDraft.PREFERENCE,draft.toJson().toString()).apply();
    }
    private boolean canShowStoredOutline(){
        return calibration.corners.size()==4&&TvEditDraft.sameCoordinates(calibration.reference,geometry)
                &&geometry!=null;
    }
    private void presentStoredOutline(){
        overlay.setStaleOutline(!"READY".equals(calibration.status(geometry)));
        overlay.setCorners(canShowStoredOutline()?calibration.corners:Collections.emptyList());
    }
    private void renderCornerFeedback(){
        boolean ready=phase==Phase.EDITING&&!calculatorMode&&!cameraStep&&presentationReady();
        int corner=overlay.selectedCorner();String name=((TextView)find(cornerIds[corner])).getText().toString();
        textIfChanged(find(R.id.tv_corner_active),activity.getString(R.string.tv_precision_active,name,activity.getString(cornerNames[corner])));
        com.example.alpr_v1.setup.TvCornerEditGeometry.Position point=overlay.selectedPosition();
        TextView coordinates=find(R.id.tv_corner_coordinates),limit=find(R.id.tv_corner_limit_status);
        if(ready&&geometry!=null&&point!=null){
            double x=point.x*geometry.sourceWidth,y=point.y*geometry.sourceHeight;
            textIfChanged(coordinates,activity.getString(R.string.tv_corner_coordinates,x,y));
            coordinates.setContentDescription(activity.getString(R.string.tv_corner_coordinate_description,name,x,y,geometry.sourceWidth,geometry.sourceHeight));
        }else{textIfChanged(coordinates,activity.getString(R.string.tv_corner_coordinates_empty));coordinates.setContentDescription(activity.getString(R.string.tv_corner_coordinates_empty));}
        textIfChanged(limit,ready&&point==null?activity.getString(R.string.tv_precision_place_corner):ready&&overlay.boundaryLimited()?activity.getString(R.string.tv_corner_limit):ready&&overlay.selectedOnBoundary()?activity.getString(R.string.tv_corner_on_edge):"");
        limit.setTextColor(color(ready&&overlay.boundaryLimited()?R.color.alpr_warning:R.color.alpr_text_secondary));
        boolean can=canNudge()&&!overlay.isDragging();for(int id:nudgeIds){View button=find(id);button.setEnabled(can);if(!can||!overlay.isManipulating())button.setPressed(false);}
        String[] directions={"w lewo","w górę","w dół","w prawo"};
        for(int i=0;i<nudgeIds.length;i++)find(nudgeIds[i]).setContentDescription(activity.getString(R.string.tv_precision_move_description,directions[i],overlay.step()));
        MaterialButton stepButton=find(R.id.tv_corner_step);stepButton.setEnabled(can);
        String stepText=activity.getString(overlay.step()==1?R.string.tv_precision_step_one:R.string.tv_precision_step_five);
        textIfChanged(stepButton,stepText);stepButton.setContentDescription(activity.getString(R.string.tv_precision_step_description,stepText,overlay.step()==1?5:1));
        binding=true;corners.check(cornerIds[corner]);
        ((MaterialButtonToggleGroup)find(R.id.tv_corner_zoom_choices)).check(overlay.loupeZoom()==8?R.id.tv_corner_zoom_8:R.id.tv_corner_zoom_4);binding=false;
    }
    private boolean pointsChanged(){
        if(entryEditorPoints==null)return false;
        List<com.example.alpr_v1.setup.TvCornerEditGeometry.Position> now=overlay.draftPositions();
        if(now.size()!=entryEditorPoints.size())return true;
        for(int i=0;i<now.size();i++){
            com.example.alpr_v1.setup.TvCornerEditGeometry.Position a=now.get(i),b=entryEditorPoints.get(i);
            if(a==null||b==null){if(a!=b)return true;}
            else if(Double.compare(a.x,b.x)!=0||Double.compare(a.y,b.y)!=0)return true;
        }return false;
    }
    private String editorState(){
        if(pointsChanged())return activity.getString(R.string.tv_precision_dirty);
        if(photoMode){
            if(freshPhotoDraft)return activity.getString(R.string.tv_precision_new_photo_frame);
            String stored=preferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,"");
            if(!stored.isEmpty())try{
                JSONObject record=new JSONObject(stored);
                com.example.alpr_v1.experiment.Final180PhotoRoiApproval.reviewCorners(stored,record.getInt("source_width_px"),record.getInt("source_height_px"));
                if(photoApproval==null){
                    if(photoApprovalFailure.isEmpty()||"PHOTO_APPROVAL_CHECK_PENDING".equals(photoApprovalFailure))return activity.getString(R.string.f6_saved_waiting_source);
                    return activity.getString(R.string.tv_precision_stale);
                }
                JSONObject saved=photoApproval.reviewAgainst(geometry,entryCalibration.approval());
                if(overlay.placedCount()!=4)return activity.getString(R.string.f6_saved_waiting_source);
                if(sameCorners(saved.getJSONArray("corners_norm")))return activity.getString(!controlsReady()||!presentationReady()?R.string.f6_saved_waiting_source:R.string.tv_precision_saved);
            }catch(Exception invalid){return activity.getString(R.string.tv_precision_stale);}
        }else if(entryCalibration!=null&&"READY".equals(entryCalibration.status(entryGeometry))&&entryGeometry!=null
                &&(geometry==null||TvEditDraft.sameCoordinates(entryGeometry,geometry))&&calibration.corners.size()==4){
            List<Point2> now=overlay.corners();boolean same=now.size()==4;
            for(int i=0;same&&i<4;i++)same=Float.compare(now.get(i).x,calibration.corners.get(i).x)==0&&Float.compare(now.get(i).y,calibration.corners.get(i).y)==0;
            if(same)return activity.getString(!controlsReady()||!presentationReady()?R.string.f6_saved_waiting_source:R.string.tv_precision_saved);
        }
        return activity.getString(R.string.tv_precision_to_save);
    }
    private boolean sameCorners(org.json.JSONArray saved)throws Exception{
        List<Point2> now=overlay.corners();if(now.size()!=saved.length())return false;
        for(int i=0;i<now.size();i++)if(Float.compare(now.get(i).x,(float)saved.getJSONObject(i).getDouble("x"))!=0
                ||Float.compare(now.get(i).y,(float)saved.getJSONObject(i).getDouble("y"))!=0)return false;
        return true;
    }
    private void noteEntryPoints(){if(entryEditorPoints==null)entryEditorPoints=overlay.draftPositions();}
    public String approvedTvRecord(){JSONObject approved=calibration.approval();return approved==null?"":approved.toString();}
    private void loadPhotoApproval(){
        String saved=preferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,"");
        if(saved.isEmpty())return;
        String context=preferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoiApproval.PREFERENCE,"");
        JSONObject approved=entryCalibration.approval();
        String key=saved+"|"+context+"|"+approved.optString("digest_sha256");
        if(key.equals(photoLoadKey))return;
        photoLoadKey=key;photoApproval=null;photoApprovalFailure="PHOTO_APPROVAL_CHECK_PENDING";
        final com.example.alpr_v1.setup.TvBboxCrop source=photoSelectionForEditing();
        final long token=++photoRequest;
        try{
            if(!context.isEmpty()||source.digest.equals(new JSONObject(saved).optString("tv_bbox_crop_digest_sha256"))){
                photoApproval=com.example.alpr_v1.experiment.Final180PhotoRoiApproval.resolve(saved,context,source,approved,null);
                photoApprovalFailure="";return;
            }
        }catch(Exception invalid){photoApprovalFailure=failureReason(invalid);diagnostic("photo_approval_rejected",photoApprovalFailure);return;}
        java.io.File sessions=com.example.alpr_v1.experiment.ResearchSessionStore.sessionsRoot(activity);
        java.util.concurrent.CompletableFuture.runAsync(()->{
            com.example.alpr_v1.experiment.Final180PhotoRoiApproval found=null;String failure="";
            try{found=com.example.alpr_v1.experiment.Final180PhotoRoiApproval.resolve(saved,context,source,approved,sessions);}
            catch(Exception invalid){failure=failureReason(invalid);}
            final com.example.alpr_v1.experiment.Final180PhotoRoiApproval result=found;final String reason=failure;
            handler.post(()->{
                if(token!=photoRequest||!photoMode||!editing()||!saved.equals(preferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,"")))return;
                photoApproval=result;photoApprovalFailure=reason;diagnostic("photo_approval_loaded",reason);render();
            });
        });
    }
    private void diagnostic(String cause,String reason){
        diagnostic(cause,reason,entryGeometry);
    }
    private static String failureReason(Throwable error){
        StringBuilder text=new StringBuilder();int depth=0;
        while(error!=null&&depth++<16){if(text.length()>0)text.append(" <- ");text.append(error);Throwable next=error.getCause();if(next==error)break;error=next;}
        return text.toString();
    }
    private void diagnostic(String cause,String reason,CameraPreviewGeometry previous){
        try{
            JSONObject d=new JSONObject().put("cause",cause).put("reason",reason).put("phase",phase.name())
                    .put("editor_callback_token",photoRequest).put("dirty",pointsChanged()).put("draft_initializations",draftInitializations)
                    .put("coordinate_space","source_frame_normalized_v1").put("bbox_coordinate_space","camera_full_bitmap_xy")
                    .put("elapsed_nanos",android.os.SystemClock.elapsedRealtimeNanos()).put("time_domain","ANDROID_ELAPSED_REALTIME")
                    .put("source_geometry",geometry==null?JSONObject.NULL:geometry.toJson())
                    .put("previous_source_geometry",previous==null?JSONObject.NULL:previous.toJson())
                    .put("entry_geometry",entryGeometry==null?JSONObject.NULL:entryGeometry.toJson())
                    .put("tv_approval",calibration.approval()==null?JSONObject.NULL:calibration.approval().optString("digest_sha256"));
            String saved=preferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,"");
            org.json.JSONArray draftPoints=new org.json.JSONArray();
            for(com.example.alpr_v1.setup.TvCornerEditGeometry.Position p:overlay.draftPositions())draftPoints.put(p==null?JSONObject.NULL:new JSONObject().put("x",p.x).put("y",p.y));
            d.put("draft_corners_norm",draftPoints);
            if(geometry!=null&&draftPoints.length()==4)try{
                int[] box=com.example.alpr_v1.setup.TvBboxCrop.bounds(draftPoints,geometry.sourceWidth,geometry.sourceHeight);
                d.put("draft_bbox_camera_xy",com.example.alpr_v1.setup.TvBboxCrop.box(box[0],box[1],box[2],box[3]));
            }catch(Exception invalidDraft){d.put("draft_bbox_reason",invalidDraft.getMessage());}
            if(!saved.isEmpty()){JSONObject p=new JSONObject(saved);d.put("roi_digest",p.optString("digest_sha256")).put("roi_tv_digest",p.optString("tv_bbox_crop_digest_sha256"))
                    .put("stored_corners_norm",p.optJSONArray("corners_norm")).put("stored_bbox_camera_xy",p.optJSONObject("photo_roi_camera_xy"));}
            com.example.alpr_v1.logging.AppLog.info(activity,"F6_CALIBRATION",d.toString());
        }catch(Exception ignored){android.util.Log.w("F6_CALIBRATION","diagnostic unavailable: "+ignored);}
    }
    /** Approval review is independent of frame freshness. Only SAVE/START use fresh controls.
     * The entry snapshot is authentic camera state captured on opening, never invented geometry. */
    private com.example.alpr_v1.setup.TvBboxCrop photoSelectionForEditing(){
        if(entryCalibration==null||entryGeometry==null
                ||geometry!=null&&!TvEditDraft.sameCoordinates(entryGeometry,geometry))throw new IllegalStateException("F6_PHOTO_ROI_REQUIRED_OR_STALE");
        CameraSetupControl.State camera=currentCameraState();
        CameraSetupSnapshot applied=camera.applied;
        if(applied==null&&entryCameraState!=null&&camera.requested.equals(entryCameraState.requested)
                &&Math.abs(camera.requestedZoom-entryCameraState.requestedZoom)<1e-5)applied=entryCameraState.applied;
        if(applied==null)throw new IllegalStateException("CAMERA_SETUP_SNAPSHOT_MISSING");
        return com.example.alpr_v1.setup.TvBboxCrop.prepare(entryCalibration.snapshot(entryGeometry,null,null,
                TvDistanceCalculator.unavailable("NOT_USED_FOR_TV_BBOX_INPUT"),entryCalibration.confirmedAt),entryGeometry,applied);
    }
    private void renderReferenceContent(){reference.content(cameraStep,phase,photoMode,editorState(),overlay.placedCount()==4);}
    private void requestUserCancel(){
        if(!calculatorMode&&pointsChanged()){
            if(exitQuestion!=null&&exitQuestion.isShowing())return;
            exitQuestion=new com.example.alpr_v1.ui.ImmersiveDialogBuilder(activity,R.style.ThemeOverlay_Alpr_TvSetupDialog)
                    .setTitle(R.string.tv_precision_close_question).setMessage(photoMode?R.string.tv_precision_close_photo:R.string.tv_precision_close_tv)
                    .setNegativeButton(R.string.tv_precision_continue,null)
                    .setPositiveButton(R.string.tv_precision_close_without_save,(dialog,which)->requestExit(false)).create();
            exitQuestion.show();
        }else requestExit(false);
    }
    public void toggleOutline(){
        outlineVisible=!outlineVisible;preferences.edit().putBoolean(OUTLINE,outlineVisible).apply();
        renderOutlineControl();applyVisibility();
        renderReferenceContent();
        if(outlineVisible&&!"READY".equals(calibration.status(geometry)))Toast.makeText(activity,
                calibration.corners.isEmpty()?"TV: NIESKALIBROWANY — ustaw narożniki":"TV: NIEAKTUALNY — sprawdź i popraw narożniki",Toast.LENGTH_SHORT).show();
    }
    private void renderOutlineControl(){
        android.view.MenuItem item=((MaterialToolbar)find(R.id.top_app_bar)).getMenu().findItem(R.id.menu_tv_outline);
        if(item!=null){int title=outlineVisible?R.string.tv_outline_hide:R.string.tv_outline_show;
            if(!activity.getString(title).contentEquals(item.getTitle()))item.setTitle(title);
            int icon=outlineVisible?R.drawable.ic_tv_outline_visible:R.drawable.ic_tv_outline_hidden;
            if(!Integer.valueOf(icon).equals(root.getTag(R.id.menu_tv_outline))){item.setIcon(icon);root.setTag(R.id.menu_tv_outline,icon);}}
        String state=calibration.status(geometry);
        int label="READY".equals(state)?R.string.tv_precision_saved:"STALE".equals(state)?R.string.tv_precision_stale
                :draft!=null?R.string.tv_precision_to_save:R.string.tv_precision_missing;
        String text=activity.getString(R.string.main_ui_tv_summary,activity.getString(label),
                activity.getString(outlineVisible?R.string.main_ui_outline_visible:R.string.main_ui_outline_hidden))+"\n"+inputSummary.get();
        if(!researchSummary.isEmpty())text=researchSummary;
        TextView summary=find(R.id.tv_frame_status);
        if(!text.contentEquals(summary.getText()))summary.setText(text);
        summary.setContentDescription(text+("STALE".equals(state)?". "+calibration.reason(geometry):
                "READY".equals(state)&&calibration.reused()?". "+activity.getString(R.string.f25_tv_reused_detail):""));
        boolean sourceKnown=geometry!=null&&(cameraControls==null||cameraState.ready());
        int tvLabel=calibration.approval()==null?R.string.tv_precision_missing
                :!sourceKnown?R.string.main_calibration_wait:label;
        MaterialButton tv=find(R.id.tv_frame_edit);
        String tvText=activity.getString(R.string.main_tv_calibrate_action)+"\n"+activity.getString(tvLabel);
        textIfChanged(tv,tvText);tv.setContentDescription(tvText+("STALE".equals(state)?". "+calibration.reason(geometry):""));
        tv.setEnabled(editable.getAsBoolean());
        MaterialButton photo=find(R.id.f6_photo_roi_edit);
        int photoLabel=R.string.tv_precision_missing;String reason="";
        String saved=preferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,"");
        if(!saved.isEmpty())try{
            com.example.alpr_v1.experiment.Final180PhotoRoiApproval.review(saved,
                    preferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoiApproval.PREFERENCE,""))
                    .reviewAgainst(geometry,calibration.approval());
            photoLabel=sourceKnown?R.string.tv_precision_saved:R.string.main_calibration_wait;
        }catch(Exception invalid){photoLabel=R.string.main_calibration_check;reason=". "+failureReason(invalid);}
        String photoText=activity.getString(R.string.main_photo_calibrate_short)+"\n"+activity.getString(photoLabel);
        textIfChanged(photo,photoText);photo.setContentDescription(activity.getString(R.string.main_photo_calibrate_action)+". "+activity.getString(photoLabel)+reason);
    }
    private boolean seedCorners(boolean reset){
        if(!reset&&overlay.placedCount()==4)return true;
        if(photoMode){
            try{
                java.util.List<Point2> points=null;
                if(!reset&&!preferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,"").isEmpty()){
                    try{
                        loadPhotoApproval();
                        if(photoApproval==null)throw new IllegalArgumentException(photoApprovalFailure);
                        photoApproval.reviewAgainst(geometry,entryCalibration.approval());
                        org.json.JSONArray saved=com.example.alpr_v1.experiment.Final180PhotoRoiApproval.reviewCorners(
                                preferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,""),geometry.sourceWidth,geometry.sourceHeight);
                        points=new java.util.ArrayList<>();for(int i=0;i<4;i++)points.add(new Point2((float)saved.getJSONObject(i).getDouble("x"),(float)saved.getJSONObject(i).getDouble("y")));
                    }catch(Exception incompatible){photoApprovalFailure=failureReason(incompatible);diagnostic("photo_draft_rejected_saved",photoApprovalFailure);}
                }
                freshPhotoDraft=points==null;
                if(freshPhotoDraft)points=TvCornerDraft.insideTv(calibration.corners,geometry,density());
                overlay.setCorners(points);overlay.selectCorner(0);noteEntryPoints();draftInitializations++;diagnostic("photo_draft_initialized",photoApprovalFailure);return true;
            }catch(Exception unavailable){cameraFailureCode="DRAFT_NOT_VISIBLE";cameraFailure=failureReason(unavailable);photoApprovalFailure=cameraFailure;
                diagnostic("photo_draft_unavailable",photoApprovalFailure);phase=Phase.CAMERA_ERROR;overlay.setEditing(false);render();return false;}
        }
        try{
            TvEditDraft saved=reset?null:draft!=null?draft:TvEditDraft.fromCalibration(entryCalibration);
            if(saved!=null&&TvEditDraft.sameCoordinates(saved.reference,geometry))overlay.restoreDraft(saved.points,saved.selected);
            else{overlay.setCorners(TvCornerDraft.create(geometry,density()));overlay.selectCorner(0);}
            noteEntryPoints();return true;
        }catch(IllegalArgumentException error){cameraFailureCode="DRAFT_NOT_VISIBLE";cameraFailure=error.getMessage();phase=Phase.CAMERA_ERROR;overlay.setEditing(false);render();return false;}
    }
    private boolean keyboardForm(){return editing()&&!restoring()&&calculatorMode&&imeVisible;}
    private void onIme(CameraWindowUi.ImeState state){
        if(imeVisible==state.visible&&imeBottom==state.bottom)return;
        if(state.visible&&!imeVisible){
            int[] p=new int[2];root.getLocationInWindow(p);windowBottomWithoutIme=Math.max(windowBottomWithoutIme,p[1]+root.getHeight());
            imePreviewLayout=new ConstraintLayout.LayoutParams((ConstraintLayout.LayoutParams)preview.getLayoutParams());
            imePreviewLayout.width=preview.getWidth();imePreviewLayout.height=preview.getHeight();
            imePreviewLayout.topToTop=ConstraintLayout.LayoutParams.PARENT_ID;imePreviewLayout.topToBottom=ConstraintLayout.LayoutParams.UNSET;
            imePreviewLayout.topMargin=preview.getTop()-root.getPaddingTop();
            imePreviewLayout.bottomToTop=imePreviewLayout.bottomToBottom=ConstraintLayout.LayoutParams.UNSET;
            imePreviewLayout.endToStart=imePreviewLayout.endToEnd=ConstraintLayout.LayoutParams.UNSET;
        }
        imeVisible=state.visible;imeBottom=state.bottom;
        if(editing()){configurePanel();render();revealFocusedField();}
        if(!imeVisible)imePreviewLayout=null;
    }
    private int keyboardOverlap(){
        int[] p=new int[2];root.getLocationInWindow(p);
        int contentBottom=p[1]+root.getHeight()-root.getPaddingBottom();
        return Math.max(0,contentBottom-(windowBottomWithoutIme-imeBottom));
    }
    private void revealFocusedField(){
        if(!keyboardForm()||revealPosted)return;revealPosted=true;
        find(R.id.tv_setup_body_scroll).post(()->{revealPosted=false;if(!keyboardForm())return;
            View focused=currentInput();
            if(focused==null&&lastInput!=null&&lastInput.isShown()){lastInput.requestFocus();focused=lastInput;}
            if(focused==null||!focused.isShown())return;
            View box=focused;while(box.getParent() instanceof View&&!(box instanceof com.google.android.material.textfield.TextInputLayout))box=(View)box.getParent();
            android.widget.ScrollView scroll=find(R.id.tv_setup_body_scroll);
            Rect target=new Rect(0,0,box.getWidth(),box.getHeight());scroll.offsetDescendantRectToMyCoords(box,target);
            int top=scroll.getScrollY(),bottom=top+scroll.getHeight();
            if(target.bottom>bottom)scroll.scrollBy(0,target.bottom-bottom+dp(8));
            if(target.top<scroll.getScrollY())scroll.scrollTo(0,Math.max(0,target.top-dp(8)));
        });
    }
    private EditText currentInput(){
        for(EditText edit:new EditText[]{diagonal,width,height,aspectW,aspectH,margin,measured})if(edit.hasFocus())return edit;
        return null;
    }
    private Mode pendingMenuMode;
    public void openAfterMenu(Mode mode){
        if(pendingMenuMode==mode)return;pendingMenuMode=mode;
        if(com.example.alpr_v1.BuildConfig.DEBUG)android.util.Log.d("ALPR_TV_NAV","MENU_CALLBACK mode="+mode);
        ((MaterialToolbar)find(R.id.top_app_bar)).dismissPopupMenus();
        handler.post(()->{if(pendingMenuMode!=mode)return;pendingMenuMode=null;open(mode);});
    }
    public void setControlsRefresh(Runnable callback){controlsRefresh=callback;}
    public void setGeometryRefresh(Runnable callback){geometryRefresh=callback;}
    public void setCameraControls(CameraSetupControl controls){cameraControls=controls;controls.setListener(this::onCameraControls);}
    public boolean choosingCamera(){return editing()&&!calculatorMode&&cameraStep;}
    private void onCameraControls(CameraSetupControl.State state){
        long previous=cameraState.revision;boolean wasReady=cameraState.ready();
        boolean sourceRequestChanged=!cameraState.requested.equals(state.requested)||Math.abs(cameraState.requestedZoom-state.requestedZoom)>1e-5;
        cameraState=state;
        revalidateForCamera(state.readiness);
        if(editing()&&!restoring()&&previous!=state.revision&&sourceRequestChanged){
            calibration=calibration.awaitingVerification("CAMERA_SETUP_CHANGED");overlay.setCorners(Collections.emptyList());sourceContext=null;
            if(!calculatorMode){cameraStep=true;phase=Phase.WAITING;overlay.setEditing(false);resetWaitingBudget();armTimeout();configurePanel();}
        }
        if(editing()&&!calculatorMode&&!restoring()&&!state.ready()&&(phase==Phase.EDITING||phase==Phase.CAMERA_SETUP)){
            // A missing fresh frame suspends interaction, not operator approval or editor layout.
            overlay.suspendInteraction();
            if(phase==Phase.CAMERA_SETUP||sourceContext==null){phase=Phase.WAITING;overlay.setEditing(false);armTimeout();}
            requestGeometry();
        }else if(editing()&&state.ready()&&!wasReady)requestGeometry();
        if(editing()&&wasReady!=state.ready())diagnostic("camera_freshness_changed",state.reason);
        renderCameraControls();reconcile();renderReferenceContent();
    }
    private void requestCamera(CameraSelection selection,float ratio){
        if(cameraControls==null||!editable.getAsBoolean()||!editing()||calculatorMode||!cameraStep||restoring())return;
        saveDraft();resumeSource=null;
        cameraStep=true;sourceContext=null;overlay.setCorners(Collections.emptyList());overlay.setEditing(false);
        calibration=calibration.awaitingVerification("CAMERA_SETUP_CHANGED");phase=Phase.WAITING;resetWaitingBudget();armTimeout();
        cameraControls.apply(selection,ratio);requestGeometry();reconcile();
    }
    private float zoomForProgress(int value){double min=Math.max(.01,cameraState.minZoom),max=Math.max(min,cameraState.maxZoom);
        return (float)(min*Math.pow(max/min,value/1000.0));}
    private boolean controlsReady(){
        if(cameraControls==null)return geometry!=null;
        CameraSetupSnapshot applied=cameraState.applied;
        return cameraState.ready()&&geometry!=null&&(cameraState.readiness==null||cameraState.readiness.ready()&&cameraState.readiness.bindingGeneration==geometry.bindingGeneration)
                &&geometry.cameraId.equals(applied.selection.logicalCameraId)
                &&(applied.selection.physicalCameraId==null||applied.selection.physicalCameraId.equals(geometry.physicalCameraId))
                &&Math.abs(applied.zoomRatio-geometry.zoomRatio)<1e-4;
    }
    private void renderCameraControls(){
        if(lens==null)return;cameraBinding=true;
        CameraSelection selected=cameraState.applied==null?cameraState.requested:cameraState.applied.selection;
        List<CameraSetupControl.Choice> displayed=new java.util.ArrayList<>(cameraState.choices);
        if(!selected.isDefault()&&!availableLens(selected))displayed.add(0,new CameraSetupControl.Choice(selected,
                cameraState.ready()?"Aktywny obiektyw":"Obiektyw niedostępny","Kamera "+selected+" · wybierz dostępny obiektyw z listy"));
        StringBuilder signature=new StringBuilder();for(CameraSetupControl.Choice choice:displayed)signature.append(choice.selection).append(choice.label).append(choice.detail);
        if(!signature.toString().equals(lensSignature)){lensSignature=signature.toString();
            ArrayAdapter<CameraSetupControl.Choice> adapter=new ArrayAdapter<CameraSetupControl.Choice>(activity,android.R.layout.simple_spinner_item,displayed){
                private View style(View view){TextView text=(TextView)view;text.setTextColor(color(R.color.alpr_text_primary));text.setBackgroundColor(color(R.color.alpr_surface));text.setTextSize(16);text.setMinHeight(dp(48));text.setPadding(dp(12),dp(8),dp(12),dp(8));return view;}
                @Override public View getView(int position,View convert,ViewGroup parent){return style(super.getView(position,convert,parent));}
                @Override public View getDropDownView(int position,View convert,ViewGroup parent){return style(super.getDropDownView(position,convert,parent));}
                @Override public boolean isEnabled(int position){return availableLens(getItem(position).selection);}
            };adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);lens.setAdapter(adapter);
        }
        int index=0;for(int i=0;i<displayed.size();i++)if(displayed.get(i).selection.equals(selected))index=i;
        if(!displayed.isEmpty()&&lens.getSelectedItemPosition()!=index)lens.setSelection(index);
        String cameraDescription=displayed.isEmpty()?CameraOperatorPresentation.readableDetail(activity,cameraState.reason):displayed.get(index).detail;
        if(cameraControls!=null)cameraDescription+="\n"+com.example.alpr_v1.camera.CameraController.describeObservation(cameraControls.observation());
        textIfChanged(cameraDetail,cameraDescription);
        lens.setEnabled(editable.getAsBoolean()&&!cameraState.choices.isEmpty());
        zoom.setEnabled(editable.getAsBoolean()&&!cameraState.loading&&cameraState.maxZoom>cameraState.minZoom);
        if(!zoomDragging){float value=cameraState.applied==null?cameraState.requestedZoom:cameraState.applied.zoomRatio;
            textIfChanged(zoomValue,cameraState.applied==null?String.format(Locale.getDefault(),"Żądany zoom %.2f× · oczekiwanie na potwierdzenie",value):
                    String.format(Locale.getDefault(),"Zoom %.2f× · zakres %.2f–%.2f×",value,cameraState.minZoom,cameraState.maxZoom));
            double span=Math.log(Math.max(1,cameraState.maxZoom/Math.max(.01f,cameraState.minZoom)));
            zoom.setProgress(span==0?0:(int)Math.round(1000*Math.log(value/Math.max(.01f,cameraState.minZoom))/span));}
        cameraBinding=false;
        renderCameraFov();
        renderReferenceLenses();
    }
    private void renderReferenceLenses(){
        if(!reference.enabled())return;
        com.google.android.material.chip.ChipGroup group=find(R.id.tv_ref_lens_choices);
        StringBuilder signature=new StringBuilder();for(CameraSetupControl.Choice choice:cameraState.choices)signature.append(choice.selection).append(choice.label);
        if(!signature.toString().equals(referenceLensSignature)){referenceLensSignature=signature.toString();group.removeAllViews();
            for(CameraSetupControl.Choice choice:cameraState.choices){
                com.google.android.material.chip.Chip chip=new com.google.android.material.chip.Chip(activity);chip.setId(View.generateViewId());chip.setTag(choice.selection);
                chip.setText(choice.label);chip.setCheckable(true);chip.setEnsureMinTouchTargetSize(true);chip.setMinHeight(dp(48));chip.setTextSize(14);chip.setChipCornerRadius(dp(12));
                chip.setOnClickListener(v->{if(!choice.selection.equals(cameraState.requested))requestCamera(choice.selection,1);});group.addView(chip);
            }}
        CameraSelection selected=cameraState.applied==null?cameraState.requested:cameraState.applied.selection;
        for(int i=0;i<group.getChildCount();i++){com.google.android.material.chip.Chip chip=(com.google.android.material.chip.Chip)group.getChildAt(i);boolean checked=selected.equals(chip.getTag());
            chip.setChecked(checked);chip.setEnabled(editable.getAsBoolean()&&choosingCamera()&&!restoring());chip.setChipBackgroundColor(ColorStateList.valueOf(color(checked?R.color.tv_ref_accent:R.color.tv_ref_surface)));
            chip.setTextColor(color(checked?R.color.tv_ref_background:R.color.tv_ref_text));chip.setCheckedIconVisible(false);}
    }
    private void renderCameraFov(){
        TvDistanceCalculator.FieldOfView fov=TvDistanceCalculator.effectiveFov(metadata,geometry);
        String full=cameraControls!=null&&!controlsReady()?"FOV: N/A · oczekiwanie na potwierdzony kadr":fov.available()?String.format(Locale.getDefault(),"Efektywny FOV: %.1f° × %.1f° · Camera2",fov.horizontalDegrees,fov.verticalDegrees):
                "FOV: N/A · "+(metadata==null&&!metadataReason.isEmpty()?metadataReason:fov.reason);
        TextView view=find(R.id.tv_camera_fov);textIfChanged(view,reference.enabled()&&!fov.available()?"FOV: N/A · brak metadanych":full);view.setContentDescription(full);
    }
    private boolean availableLens(CameraSelection selection){for(CameraSetupControl.Choice choice:cameraState.choices)if(choice.selection.equals(selection))return true;return false;}
    private void navigateBack(){if(photoMode){requestUserCancel();return;}saveDraft();if(!calculatorMode&&!cameraStep&&!restoring()){
        cameraStep=true;phase=controlsReady()&&presentationReady()?Phase.CAMERA_SETUP:Phase.WAITING;overlay.setEditing(false);configurePanel();render();
        }else requestUserCancel();}
    private boolean rollbackCamera(){
        if(cameraControls==null||entryCameraState==null)return false;
        if(cameraState.requested.equals(entryCameraState.requested)&&Math.abs(cameraState.requestedZoom-entryCameraState.requestedZoom)<1e-4)return false;
        cameraControls.apply(entryCameraState.requested,entryCameraState.requestedZoom);return true;
    }
    public void maintainPreparationUi(){if(cameraControls!=null)onCameraControls(cameraControls.state());applyVisibility();}
    public void openRequested(){Mode requested=TvSetupNavigation.requestedMode(preferences.getString(ACTION,""));
        if(requested!=null&&editable.getAsBoolean()){
            preferences.edit().remove(ACTION).apply();open(requested);}}

    public void open(boolean calculator){
        open(calculator?Mode.DISTANCE:Mode.CALIBRATION);
    }
    public void open(Mode mode){
        if(mode==Mode.F6_PHOTO&&!"READY".equals(calibration.status(geometry))){
            boolean approvedReview=false;
            try{JSONObject approved=calibration.approval();
                approvedReview=approved!=null&&com.example.alpr_v1.setup.SetupJson.verified(approved)
                        &&(geometry==null||TvEditDraft.sameCoordinates(CameraPreviewGeometry.fromJson(approved.getJSONObject("source_geometry")),geometry));
            }catch(Exception invalid){approvedReview=false;}
            if(!approvedReview){Toast.makeText(activity,R.string.f6_roi_requires_tv,Toast.LENGTH_LONG).show();return;}
        }
        if(phase==Phase.COMMITTING||phase==Phase.CANCELLING)return;
        if(editing()&&mode==mode())return; // Repeated readiness/menu callbacks keep the current draft.
        if(!editable.getAsBoolean()){Toast.makeText(activity,"Ustawienie TV jest dostępne przed START",Toast.LENGTH_SHORT).show();return;}
        boolean firstEntry=!editing();
        firstDraftChangeLogged=false;
        freshPhotoDraft=false;
        entryEditorPoints=null;
        if(firstEntry){entryCalibration=calibration;entryGeometry=geometry;sourceContext=geometry;pendingSave=null;
            entryCameraState=cameraState;calibrationVisited=false;
            resumeSource=mode==Mode.CALIBRATION?(draft!=null?draft:TvEditDraft.fromCalibration(calibration)):null;resumeCameraRequested=false;}
        else if(!calculatorMode)saveDraft();
        calculatorMode=mode==Mode.DISTANCE;photoMode=mode==Mode.F6_PHOTO;resetWaitingBudget();
        if(photoMode){
            photoApprovalFailure="";
            String saved=preferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,"");
            String context=preferences.getString(com.example.alpr_v1.experiment.Final180PhotoRoiApproval.PREFERENCE,"");
            if(!saved.isEmpty()&&!context.isEmpty())try{photoApproval=com.example.alpr_v1.experiment.Final180PhotoRoiApproval.review(saved,context);}
            catch(Exception invalid){photoApproval=null;photoApprovalFailure=failureReason(invalid);}
        }
        binding=true;overlay.setStep(1);overlay.setLoupeZoom(4);binding=false;
        if(photoMode){cameraStep=false;calibrationVisited=true;sourceContext=geometry;resumeSource=null;}
        if(!calculatorMode&&!calibrationVisited){cameraStep=true;calibrationVisited=true;sourceContext=null;}
        phase=calculatorMode?Phase.EDITING:sourceContext!=null&&geometry!=null&&!TvEditDraft.sameCoordinates(sourceContext,geometry)?Phase.REVIEW:Phase.WAITING;
        MaterialToolbar toolbar=find(R.id.top_app_bar);
        if(firstEntry){
            toolbarTitle=toolbar.getTitle();toolbarNavigation=toolbar.getNavigationIcon();
            toolbarHeight=toolbar.getLayoutParams().height;TextView oldTitle=toolbarTitleView(toolbar);toolbarTitlePixels=oldTitle==null?0:oldTitle.getTextSize();
            menuVisibility.clear();for(int i=0;i<toolbar.getMenu().size();i++){
                android.view.MenuItem item=toolbar.getMenu().getItem(i);menuVisibility.put(item.getItemId(),item.isVisible());}
            overlay.setCorners(Collections.emptyList());overlay.selectCorner(0);
        }
        toolbar.setNavigationIcon(R.drawable.ic_tv_close);toolbar.setNavigationContentDescription(R.string.tv_editor_cancel);
        toolbar.setNavigationOnClickListener(v->navigateBack());
        overlay.setEditing(false);overlay.setGeometry(phase==Phase.REVIEW?null:geometry);
        configurePanel();applyVisibility();back.setEnabled(true);controlsRefresh.run();
        if(firstEntry)startPreview.run();requestGeometry();if(!calculatorMode)armTimeout();reconcile();render();
        if(com.example.alpr_v1.BuildConfig.DEBUG)preview.post(()->android.util.Log.d("ALPR_TV_NAV",
                "PRESENTED mode="+mode()+" phase="+phase+" form_visible="+find(R.id.tv_distance_scroll).isShown()));
    }
    private void configurePanel(){
        overlay.setAnalysisBounds(photoMode);
        boolean compact=compactScreen();
        panel.setPadding(panel.getPaddingLeft(),dp(compact?8:12),panel.getPaddingRight(),dp(compact?8:12));
        topMargin(corners,compact?8:12);topMargin(coverage,compact?4:8);
        topMargin(find(R.id.tv_setup_waiting),compact?4:8);topMargin(find(R.id.tv_setup_actions),compact?4:8);
        title.setText(photoMode?R.string.f6_photo_roi_title:calculatorMode?R.string.tv_distance_title:R.string.tv_camera_corners_title);
        LinearLayout coordinatesRow=find(R.id.tv_corner_coordinate_header);boolean stackedCoordinates=fontScale()>1.3f;
        coordinatesRow.setOrientation(stackedCoordinates?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        TextView activeName=find(R.id.tv_corner_active),xy=find(R.id.tv_corner_coordinates);
        activeName.setLayoutParams(new LinearLayout.LayoutParams(stackedCoordinates?-1:0,-2,stackedCoordinates?0:1));
        xy.setLayoutParams(new LinearLayout.LayoutParams(stackedCoordinates?-1:dp(132),-2));
        clear.setVisibility(calculatorMode||cameraStep?View.GONE:View.VISIBLE);
        corners.setVisibility(calculatorMode||cameraStep?View.GONE:View.VISIBLE);
        coverage.setVisibility(!calculatorMode&&!cameraStep&&!compact&&fontScale()<=1.2f?View.VISIBLE:View.GONE);
        find(R.id.tv_setup_details).setVisibility(!calculatorMode&&!cameraStep&&(compact||fontScale()>1.2f)?View.VISIBLE:View.GONE);
        find(R.id.tv_distance_scroll).setVisibility(calculatorMode?View.VISIBLE:View.GONE);
        find(R.id.tv_camera_controls_scroll).setVisibility(!calculatorMode&&cameraStep?View.VISIBLE:View.GONE);
        find(R.id.tv_camera_controls_scroll).getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;
        confirm.setVisibility(calculatorMode||cameraStep?View.GONE:View.VISIBLE);
        next.setVisibility(!calculatorMode&&cameraStep?View.VISIBLE:View.GONE);
        cancel.setText(calculatorMode?(fontScale()>1.2f?R.string.tv_editor_back_compact:R.string.tv_editor_back):R.string.tv_editor_cancel);
        boolean compactActions=!calculatorMode&&compact&&fontScale()>1.2f&&!landscape();
        confirm.setText(photoMode?(compactActions?R.string.tv_precision_save_photo_compact:R.string.tv_precision_save_photo)
                :(compactActions?R.string.tv_precision_save_tv_compact:R.string.tv_precision_save_tv));
        confirm.setContentDescription(activity.getString(photoMode?R.string.tv_precision_save_photo:R.string.tv_precision_save_tv));
        // Secondary copy may collapse at large fonts; corner choice and primary actions never scroll away.
        title.setVisibility(keyboardForm()||!calculatorMode&&cameraStep||compact||fontScale()>1.2f?View.GONE:View.VISIBLE);
        instruction.setVisibility(!keyboardForm()&&(calculatorMode||!cameraStep)&&!compact&&fontScale()<=1.2f?View.VISIBLE:View.GONE);
        LinearLayout actions=find(R.id.tv_setup_actions);boolean stacked=fontScale()>1.2f&&!landscape()&&!compactActions;
        actions.setBaselineAligned(false);
        actions.setOrientation(stacked?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        for(MaterialButton button:new MaterialButton[]{cancel,confirm,next}){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(stacked?ViewGroup.LayoutParams.MATCH_PARENT:0,ViewGroup.LayoutParams.WRAP_CONTENT,stacked?0:1);
            if(button!=cancel){if(stacked)lp.topMargin=dp(8);else lp.setMarginStart(dp(12));}button.setLayoutParams(lp);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP,landscape()||compactActions?14:16);
        }
        MaterialToolbar toolbar=find(R.id.top_app_bar);toolbar.setTitle(calculatorMode?(fontScale()>1.2f?R.string.tv_distance_title_compact:R.string.tv_distance_title):
                cameraStep?(fontScale()>1.2f?R.string.tv_camera_step_compact:R.string.tv_camera_step_title):
                fontScale()>1.2f?R.string.tv_camera_corners_compact:R.string.tv_camera_corners_title);
        toolbar.setNavigationContentDescription(!calculatorMode&&!cameraStep?R.string.tv_camera_back:R.string.tv_editor_close_keep_draft);
        if(photoMode)toolbar.setTitle(R.string.f6_photo_roi_title);
        else if(!calculatorMode)toolbar.setTitle(R.string.tv_editor_title);
        toolbar.setNavigationIcon(!calculatorMode&&!cameraStep?androidx.appcompat.R.drawable.abc_ic_ab_back_material:R.drawable.ic_tv_close);
        int toolbarSize=dp(Math.max(48,Math.round((calculatorMode?24:36)*fontScale()+16)));
        if(toolbar.getLayoutParams().height!=toolbarSize){toolbar.getLayoutParams().height=toolbarSize;toolbar.requestLayout();}
        TextView toolbarText=toolbarTitleView(toolbar);if(toolbarText!=null){toolbarText.setSingleLine(calculatorMode);toolbarText.setMaxLines(calculatorMode?1:2);toolbarText.setTextSize(TypedValue.COMPLEX_UNIT_SP,calculatorMode?18:16);}
        renderCameraControls();
    }
    private void applyVisibility(){
        boolean restoring=phase==Phase.COMMITTING||phase==Phase.CANCELLING;
        boolean active=editing();
        CameraWindowUi.setMainEditorOpen(root,active);
        find(R.id.main_hud_storage).setVisibility(active?View.INVISIBLE:View.VISIBLE);
        // The reference composition belongs exclusively to the calibration wizard.
        // Mapping errors retain the existing recovery layout so Retry/Cancel have priority.
        reference.setEnabled(active&&!calculatorMode&&!restoring&&phase!=Phase.CAMERA_ERROR);
        if(reference.enabled()){
            TvSetupMenuState.render(((MaterialToolbar)find(R.id.top_app_bar)).getMenu(),true,editable.getAsBoolean(),Mode.CALIBRATION,false);
            normalPanel.setVisibility(View.GONE);normalHud.setVisibility(View.INVISIBLE);detections.setVisibility(View.INVISIBLE);
            panel.setVisibility(View.VISIBLE);overlay.setVisibility(!cameraStep?View.VISIBLE:View.GONE);
            find(R.id.top_app_bar).setVisibility(View.VISIBLE);find(R.id.tv_setup_actions).setVisibility(View.VISIBLE);find(R.id.tv_frame_summary).setVisibility(View.GONE);
            reference.layout(cameraStep,landscape());renderReferenceLenses();renderOutlineControl();renderReferenceContent();return;
        }
        ConstraintLayout.LayoutParams desired=new ConstraintLayout.LayoutParams(normalPreviewLayout);
        desired.topToTop=active&&!restoring?ConstraintLayout.LayoutParams.UNSET:normalPreviewTopToTop;
        desired.topToBottom=active&&!restoring?R.id.top_app_bar:normalPreviewTopToBottom;
        // In landscape a centered short image can otherwise sit underneath the toolbar.
        if(landscape()&&active&&!restoring){desired.topToTop=ConstraintLayout.LayoutParams.UNSET;desired.topToBottom=R.id.top_app_bar;}
        boolean keyboard=keyboardForm();
        boolean side=active&&!restoring&&landscape()&&!keyboard;
        ConstraintLayout.LayoutParams setupLayout=new ConstraintLayout.LayoutParams(normalSetupLayout);
        if(side){
            desired.endToEnd=ConstraintLayout.LayoutParams.UNSET;desired.endToStart=R.id.tv_setup_panel;
            desired.bottomToTop=ConstraintLayout.LayoutParams.UNSET;desired.bottomToBottom=ConstraintLayout.LayoutParams.PARENT_ID;
            setupLayout.width=Math.min(dp(360),Math.round((root.getWidth()-root.getPaddingLeft()-root.getPaddingRight())*.45f));
            setupLayout.height=0;setupLayout.startToStart=ConstraintLayout.LayoutParams.UNSET;setupLayout.endToEnd=ConstraintLayout.LayoutParams.PARENT_ID;
            setupLayout.topToTop=ConstraintLayout.LayoutParams.UNSET;setupLayout.topToBottom=R.id.top_app_bar;setupLayout.bottomToBottom=ConstraintLayout.LayoutParams.PARENT_ID;
        }
        if(keyboard){
            if(imePreviewLayout!=null)desired=new ConstraintLayout.LayoutParams(imePreviewLayout);
            setupLayout.width=0;setupLayout.height=0;setupLayout.startToStart=setupLayout.endToEnd=ConstraintLayout.LayoutParams.PARENT_ID;
            setupLayout.topToTop=ConstraintLayout.LayoutParams.UNSET;setupLayout.topToBottom=R.id.top_app_bar;
            setupLayout.bottomToBottom=ConstraintLayout.LayoutParams.PARENT_ID;setupLayout.bottomMargin=keyboardOverlap();
            if(landscape()){setupLayout.topToTop=ConstraintLayout.LayoutParams.PARENT_ID;setupLayout.topToBottom=ConstraintLayout.LayoutParams.UNSET;}
        }
        find(R.id.top_app_bar).setVisibility(keyboard&&landscape()?View.GONE:View.VISIBLE);
        find(R.id.tv_setup_actions).setVisibility(keyboard?View.GONE:View.VISIBLE);
        if(!sameLayout((ConstraintLayout.LayoutParams)preview.getLayoutParams(),desired)){presentationLaidOut=false;preview.setLayoutParams(desired);}
        if(!sameLayout((ConstraintLayout.LayoutParams)panel.getLayoutParams(),setupLayout))panel.setLayoutParams(setupLayout);
        LinearLayout.LayoutParams body=(LinearLayout.LayoutParams)find(R.id.tv_setup_body_scroll).getLayoutParams();
        int bodyHeight=ViewGroup.LayoutParams.WRAP_CONTENT;
        if(active&&!restoring&&!side&&!keyboard&&root.getHeight()>0){
            View actions=find(R.id.tv_setup_actions),toolbar=find(R.id.top_app_bar);
            int limit=root.getHeight()-root.getPaddingTop()-root.getPaddingBottom()-Math.max(toolbar.getHeight(),toolbar.getLayoutParams().height)-dp(100)
                    -Math.max(dp(48),actions.getMeasuredHeight())-panel.getPaddingTop()-panel.getPaddingBottom()-((ViewGroup.MarginLayoutParams)actions.getLayoutParams()).topMargin;
            android.widget.ScrollView scroll=find(R.id.tv_setup_body_scroll);
            if(scroll.getChildAt(0).getMeasuredHeight()>Math.max(dp(48),limit))bodyHeight=Math.max(dp(48),limit);
        }
        boolean fill=side||keyboard;
        if(body.weight!=(fill?1:0)||body.height!=(fill?0:bodyHeight)){
            body.weight=fill?1:0;body.height=fill?0:bodyHeight;find(R.id.tv_setup_body_scroll).setLayoutParams(body);}
        if(active)TvSetupMenuState.render(((MaterialToolbar)find(R.id.top_app_bar)).getMenu(),true,editable.getAsBoolean(),mode(),restoring);
        normalPanel.setVisibility(!active||restoring?View.VISIBLE:View.GONE);
        panel.setVisibility(active&&!restoring?View.VISIBLE:View.GONE);
        normalHud.setVisibility(active?View.INVISIBLE:View.VISIBLE);
        detections.setVisibility(active?View.INVISIBLE:View.VISIBLE);
        overlay.setVisibility(active?(!calculatorMode&&!cameraStep&&!restoring?View.VISIBLE:View.GONE):
                outlineVisible&&canShowStoredOutline()?View.VISIBLE:View.GONE);
        android.view.MenuItem eye=((MaterialToolbar)find(R.id.top_app_bar)).getMenu().findItem(R.id.menu_tv_outline);
        find(R.id.tv_frame_summary).setVisibility(!active||restoring?View.VISIBLE:View.GONE);
        renderOutlineControl();
        // Child renderers can update, but an invisible real parent cannot flash back on screen.
    }
    private boolean presentationReady(){
        return geometry!=null&&preview.getWidth()>0&&preview.getHeight()>0
                &&geometry.previewWidth==preview.getWidth()&&geometry.previewHeight==preview.getHeight()
                &&presentationLaidOut&&(!reference.enabled()||!reference.geometryLayoutPending());
    }
    private void requestGeometry(){preview.post(geometryRefresh);}
    private void resetWaitingBudget(){transition++;waitingDeadline=0;geometryPresented=false;cameraFailure="";cameraFailureCode="";}
    private void armTimeout(){
        if(calculatorMode&&phase!=Phase.CANCELLING)return;
        if(waitingDeadline!=0)return;
        long ticket=++transition;waitingDeadline=SystemClock.uptimeMillis()+3000;
        handler.postDelayed(()->{
        if(ticket!=transition||!editing())return;
        waitingDeadline=0;
        if(phase==Phase.CANCELLING){
            calibration=(entryCalibration==null?calibration:entryCalibration).awaitingVerification("CAMERA_GEOMETRY_UNAVAILABLE");
            finishPresentation();return;
        }
        if(phase==Phase.WAITING||phase==Phase.COMMITTING||(phase==Phase.EDITING||phase==Phase.CAMERA_SETUP)&&!geometryPresented){
            cameraFailureCode=geometryState==null?"FRAME_NOT_AVAILABLE":geometryState.reasonCode;
            cameraFailure=cameraControls!=null&&!cameraState.ready()&&!cameraState.reason.isEmpty()?cameraState.reason:geometryFailureMessage();phase=Phase.CAMERA_ERROR;pendingSave=null;
            applyVisibility();configurePanel();overlay.setEditing(false);render();}
    },3000);}

    public void onGeometryState(CameraGeometryProvider.GeometryState state){
        geometryState=state;
        updateGeometry(state.geometry,state.distanceMetadata,state.distanceReason,state.reasonCode);
    }
    /** Compatibility entry point for legacy geometry consumers; production uses the publisher state. */
    public void onGeometry(CameraPreviewGeometry next,JSONObject cameraMetadata,String reason){
        onGeometryState(next==null?CameraGeometryProvider.GeometryState.waiting(
                CameraGeometryProvider.Availability.WAITING_TRANSFORM,reason,"",-1):
                CameraGeometryProvider.GeometryState.ready(next,cameraMetadata,reason));
    }
    private void updateGeometry(CameraPreviewGeometry next,JSONObject cameraMetadata,String reason,String geometryReason){
        CameraPreviewGeometry previous=geometry;
        geometry=next;metadata=cameraMetadata;metadataReason=reason;
        renderCameraFov();
        if(previous==null||next==null||!previous.sameGeometry(next))geometryPresented=false;
        if(previous==null||next==null||!previous.sameGeometry(next))detections.setCameraGeometry(next);
        overlay.setGeometry(next);
        if(!editing()){
            if(next==null||previous!=null&&!previous.sameGeometry(next))calibration=calibration.awaitingVerification("CAMERA_GEOMETRY_CHANGED");
            revalidateForCamera(cameraState.readiness);
            presentStoredOutline();
            applyVisibility();
        }else if(!cameraStep&&sourceContext!=null&&next!=null&&!TvEditDraft.sameCoordinates(sourceContext,next)){
            calibration=calibration.awaitingVerification("CAMERA_GEOMETRY_CHANGED");
            if(!calculatorMode){phase=Phase.REVIEW;pendingSave=null;overlay.setEditing(false);overlay.setGeometry(null);applyVisibility();}
        }else if(!calculatorMode&&next==null&&(phase==Phase.EDITING||phase==Phase.CAMERA_SETUP)){
            // CameraX can briefly withdraw its transform while a surface/layout is updating.
            phase=Phase.WAITING;overlay.setEditing(false);armTimeout();}
        if(frozenGeometry!=null&&!frozenGeometry.sameGeometry(next)&&!invalidationSent){invalidationSent=true;emitInvalidation(geometryReason.isEmpty()?"CAMERA_GEOMETRY_CHANGED":geometryReason);}
        if(previous==null?next!=null:next==null||!previous.sameGeometry(next))diagnostic("geometry_changed",geometryReason,previous);
        reconcile();
    }
    private void reconcile(){
        if(!editing())return;
        if(!calculatorMode&&phase==Phase.WAITING&&resumeSource!=null){resumeFrame();render();return;}
        if(presentationReady()){
            if(phase==Phase.COMMITTING||phase==Phase.CANCELLING){finishExit();return;}
            if(phase==Phase.WAITING){
                if(!controlsReady()){render();return;}
                if(cameraStep){phase=Phase.CAMERA_SETUP;overlay.setEditing(false);render();return;}
                if(photoMode&&entryEditorPoints==null&&entryCalibration!=null&&(entryGeometry==null||!"READY".equals(entryCalibration.status(entryGeometry)))){
                    entryCalibration=entryCalibration.revalidated(geometry,cameraState.readiness,SystemClock.elapsedRealtimeNanos(),Instant.now().toString());
                    calibration=entryCalibration;
                    if(!"READY".equals(entryCalibration.status(geometry))){phase=Phase.REVIEW;render();return;}
                    CameraPreviewGeometry previousEntry=entryGeometry;entryGeometry=geometry;
                    diagnostic("photo_review_source_verified","",previousEntry);
                }
                if(sourceContext==null)sourceContext=geometry;
                if(!TvEditDraft.sameCoordinates(sourceContext,geometry)){phase=Phase.REVIEW;}
                else{
                    if(!calculatorMode&&!seedCorners(false))return;
                    phase=Phase.EDITING;overlay.setEditing(!calculatorMode);
                }
            }
        }
        if(phase==Phase.EDITING)overlay.setEditing(!calculatorMode);
        render();
    }
    private void resumeFrame(){
        TvEditDraft saved=resumeSource;
        if(cameraControls!=null&&saved.camera!=null){
            if(cameraState.choices.isEmpty())return;
            if(!availableLens(saved.camera.selection)||saved.camera.zoomRatio<cameraState.minZoom||saved.camera.zoomRatio>cameraState.maxZoom){
                resumeSource=null;cameraStep=true;phase=Phase.CAMERA_SETUP;
                cameraFailureCode="SAVED_FRAME_UNAVAILABLE";cameraFailure=activity.getString(R.string.tv_draft_frame_unavailable);return;
            }
            if(!saved.camera.same(cameraState.applied)){
                if(!resumeCameraRequested){resumeCameraRequested=true;cameraControls.apply(saved.camera.selection,saved.camera.zoomRatio);requestGeometry();}
                return;
            }
        }
        if(!presentationReady()||!controlsReady())return;
        resumeSource=null;
        if(!TvEditDraft.sameCoordinates(saved.reference,geometry)){
            phase=Phase.REVIEW;cameraStep=false;sourceContext=saved.reference;overlay.restoreDraft(saved.points,saved.selected);
            overlay.setGeometry(null);overlay.setEditing(false);configurePanel();applyVisibility();return;
        }
        sourceContext=geometry;cameraStep=false;phase=Phase.EDITING;
        overlay.restoreDraft(saved.points,saved.selected);overlay.setEditing(true);configurePanel();applyVisibility();
        noteEntryPoints();
    }
    private void clearDraft(){
        resumeSource=null;
        resetWaitingBudget();
        sourceContext=geometry;overlay.setGeometry(geometry);overlay.setCorners(Collections.emptyList());
        if(geometry!=null&&!seedCorners(true))return;
        phase=Phase.WAITING;armTimeout();reconcile();
    }
    private void requestExit(boolean save){
        if(!editing())return;
        if(!save)saveDraft();
        if(!save&&rollbackCamera()){
            calibration=(entryCalibration==null?calibration:entryCalibration).awaitingVerification("CAMERA_SETUP_CANCELLED_REQUIRES_CHECK");
            finishPresentation();return;
        }
        if(!save&&(geometry==null||sourceContext==null||phase==Phase.CAMERA_ERROR||phase==Phase.REVIEW)){
            calibration=(entryCalibration==null?calibration:entryCalibration).awaitingVerification("CAMERA_GEOMETRY_UNAVAILABLE");finishPresentation();return;
        }
        if(phase==Phase.COMMITTING||phase==Phase.CANCELLING){if(!save){phase=Phase.CANCELLING;pendingSave=null;}return;}
        if(save&&(cameraStep||phase!=Phase.EDITING||!controlsReady()||!editable.getAsBoolean()||overlay.isManipulating()
                ||TvSetupCalibration.validation(overlay.corners(),geometry,density())!=null))return;
        saveInputs();pendingSave=save?overlay.corners():null;
        resetWaitingBudget();
        phase=save?Phase.COMMITTING:Phase.CANCELLING;overlay.setEditing(false);hideKeyboard();
        restoreToolbarMetrics();
        applyVisibility();armTimeout();requestGeometry();preview.post(this::reconcile);
    }
    private void finishExit(){
        boolean save=phase==Phase.COMMITTING;
        if(save&&!controlsReady())return;
        if(sourceContext!=null&&!TvEditDraft.sameCoordinates(sourceContext,geometry)){phase=Phase.REVIEW;applyVisibility();render();return;}
        if(photoMode){
            if(save)try{
                org.json.JSONArray points=new org.json.JSONArray();for(Point2 p:pendingSave)points.put(new JSONObject().put("x",p.x).put("y",p.y));
                com.example.alpr_v1.experiment.Final180PhotoRoi roi=com.example.alpr_v1.experiment.Final180PhotoRoi.prepare(points,selectionForInput());
                com.example.alpr_v1.experiment.Final180PhotoRoiApproval approval=com.example.alpr_v1.experiment.Final180PhotoRoiApproval.capture(
                        roi,selectionForInput(),calibration.approval(),"OPERATOR_CONFIRMED");
                if(!preferences.edit().putString(com.example.alpr_v1.experiment.Final180PhotoRoi.PREFERENCE,roi.toJson().toString())
                        .putString(com.example.alpr_v1.experiment.Final180PhotoRoiApproval.PREFERENCE,approval.toJson().toString()).commit())
                    throw new java.io.IOException("PHOTO_APPROVAL_WRITE_FAILED");
                photoLoadKey="";photoApproval=approval;diagnostic("photo_saved","");
            }catch(Exception invalid){phase=Phase.EDITING;overlay.setCorners(pendingSave);overlay.setEditing(true);configurePanel();applyVisibility();render();
                android.util.Log.w("ALPR_TV_SETUP",invalid.getMessage(),invalid);
                Toast.makeText(activity,CalibrationOperatorText.preparationError(activity,invalid.getMessage()),Toast.LENGTH_LONG).show();return;}
            calibration=entryCalibration==null?calibration:entryCalibration;finishPresentation(save?EditorExit.SAVED:EditorExit.CANCELLED);
            if(save)Toast.makeText(activity,R.string.f6_roi_saved,Toast.LENGTH_LONG).show();return;
        }
        if(save){
            String validation=TvSetupCalibration.validation(pendingSave,geometry,density());
            if(validation!=null){phase=Phase.EDITING;overlay.setCorners(pendingSave);overlay.setEditing(true);configurePanel();applyVisibility();render();return;}
            if(entryCalibration!=null&&entryCalibration.approval()!=null&&sameApprovedCorners(pendingSave,entryCalibration.corners)
                    &&TvEditDraft.sameCoordinates(entryCalibration.reference,geometry)){
                calibration=entryCalibration.revalidated(geometry,currentCameraState().readiness,android.os.SystemClock.elapsedRealtimeNanos(),Instant.now().toString());
                if("READY".equals(calibration.status(geometry))){draft=null;preferences.edit().remove(TvEditDraft.PREFERENCE).apply();diagnostic("tv_saved_unchanged","");finishPresentation(EditorExit.SAVED);return;}
            }
            calibration=TvSetupCalibration.confirm(pendingSave,geometry,density(),Instant.now().toString());
            String approvedRecord=snapshot(System.currentTimeMillis()).toString();
            calibration=calibration.withStoredApproval(approvedRecord);
            SharedPreferences.Editor savedEditor=preferences.edit().putString(STORED,approvedRecord).remove(TvEditDraft.PREFERENCE);
            draft=null;
            if(cameraState.applied!=null)savedEditor.putString(CameraSetupSnapshot.PREFERENCE,cameraState.applied.toJson().toString());
            if(!savedEditor.commit()){calibration=entryCalibration;phase=Phase.EDITING;overlay.setCorners(pendingSave);overlay.setEditing(true);configurePanel();render();return;}
            persistedApprovalExpected=true;
            diagnostic("tv_saved","");
        }else if(entryCalibration!=null){
            calibration=entryCalibration;
            if(entryGeometry!=null&&!entryGeometry.sameGeometry(geometry))calibration=calibration.awaitingVerification("CAMERA_GEOMETRY_CHANGED");
        }
        finishPresentation(save?EditorExit.SAVED:EditorExit.CANCELLED);
        if(save)Toast.makeText(activity,R.string.tv_editor_saved,Toast.LENGTH_SHORT).show();
    }
    private void finishPresentation(){
        finishPresentation(EditorExit.CANCELLED);
    }
    private void finishPresentation(EditorExit reason){
        diagnostic("editor_closed",photoApprovalFailure);photoRequest++;
        if(photoApproval==null)photoLoadKey="";
        if(exitQuestion!=null){exitQuestion.dismiss();exitQuestion=null;}
        entryEditorPoints=null;
        photoMode=false;overlay.setAnalysisBounds(false);
        pendingMenuMode=null;resumeSource=null;
        phase=Phase.NORMAL;resetWaitingBudget();pendingSave=null;back.setEnabled(false);overlay.setEditing(false);
        restoreToolbarMetrics();
        MaterialToolbar toolbar=find(R.id.top_app_bar);if(toolbarTitle!=null)toolbar.setTitle(toolbarTitle);
        toolbar.setNavigationIcon(toolbarNavigation);toolbar.setNavigationOnClickListener(null);
        for(Map.Entry<Integer,Boolean> item:menuVisibility.entrySet()){
            android.view.MenuItem action=toolbar.getMenu().findItem(item.getKey());if(action!=null)action.setVisible(item.getValue());}
        applyVisibility();
        presentStoredOutline();controlsRefresh.run();
        editorExit.accept(reason);
    }
    private static boolean sameApprovedCorners(List<Point2> a,List<Point2> b){
        if(a.size()!=b.size())return false;
        for(int i=0;i<a.size();i++)if(Float.compare(a.get(i).x,b.get(i).x)!=0||Float.compare(a.get(i).y,b.get(i).y)!=0)return false;
        return true;
    }
    private void restoreToolbarMetrics(){
        MaterialToolbar toolbar=find(R.id.top_app_bar);
        if(toolbarHeight>0&&toolbar.getLayoutParams().height!=toolbarHeight){toolbar.getLayoutParams().height=toolbarHeight;toolbar.requestLayout();}
        TextView text=toolbarTitleView(toolbar);if(text!=null&&toolbarTitlePixels>0){text.setSingleLine(true);text.setMaxLines(1);text.setTextSize(TypedValue.COMPLEX_UNIT_PX,toolbarTitlePixels);}
    }
    public void close(){requestExit(false);}
    public void onBackground(){
        pendingMenuMode=null;
        if(!editing())return;saveDraft();rollbackCamera();saveInputs();calibration=(entryCalibration==null?calibration:entryCalibration).awaitingVerification("ACTIVITY_PAUSED_REQUIRES_CHECK");
        finishPresentation(EditorExit.BACKGROUND);
    }
    private static TextView toolbarTitleView(MaterialToolbar toolbar){
        for(int i=0;i<toolbar.getChildCount();i++)if(toolbar.getChildAt(i) instanceof TextView){
            TextView child=(TextView)toolbar.getChildAt(i);if(android.text.TextUtils.equals(child.getText(),toolbar.getTitle()))return child;}
        return null;
    }
    private void hideKeyboard(){View focus=currentInput();if(focus==null)focus=activity.getCurrentFocus();if(focus!=null){InputMethodManager ime=(InputMethodManager)activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);if(ime!=null)ime.hideSoftInputFromWindow(focus.getWindowToken(),0);focus.clearFocus();}}

    private void render(){
        if(!editing())return;applyVisibility();
        boolean referenceEditor=reference.enabled()&&!calculatorMode&&!cameraStep&&(phase==Phase.EDITING||phase==Phase.WAITING&&overlay.placedCount()==4);
        boolean ready=phase==Phase.EDITING&&presentationReady();
        boolean waiting=!calculatorMode&&phase==Phase.WAITING;
        boolean cameraError=!calculatorMode&&phase==Phase.CAMERA_ERROR;
        status.setVisibility(keyboardForm()||referenceEditor?View.GONE:View.VISIBLE);
        if(referenceEditor)textIfChanged(status,editorState());
        // Error recovery owns the space; disabled corner controls/statistics are not useful here.
        corners.setVisibility(!calculatorMode&&!cameraStep&&!cameraError?View.VISIBLE:View.GONE);
        coverage.setVisibility(!calculatorMode&&!cameraStep&&!cameraError&&!compactScreen()&&fontScale()<=1.2f?View.VISIBLE:View.GONE);
        clear.setVisibility(!calculatorMode&&!cameraStep&&!cameraError?View.VISIBLE:View.GONE);
        confirm.setVisibility(!calculatorMode&&!cameraStep&&!cameraError?View.VISIBLE:View.GONE);
        next.setVisibility(!calculatorMode&&cameraStep&&!cameraError?View.VISIBLE:View.GONE);
        next.setEnabled(phase==Phase.CAMERA_SETUP&&presentationReady()&&controlsReady()&&!zoomDragging&&editable.getAsBoolean());
        ((MaterialButton)find(R.id.tv_setup_details)).setText(cameraError?R.string.tv_editor_details:R.string.tv_editor_margins);
        find(R.id.tv_setup_details).setVisibility(!calculatorMode&&(cameraError||!cameraStep&&(compactScreen()||fontScale()>1.2f))?View.VISIBLE:View.GONE);
        find(R.id.tv_setup_waiting).setVisibility(keyboardForm()?View.GONE:waiting?View.VISIBLE:View.INVISIBLE);
        retry.setVisibility(phase==Phase.REVIEW||phase==Phase.CAMERA_ERROR?View.VISIBLE:View.GONE);
        retry.setText(phase==Phase.REVIEW?R.string.tv_editor_restart:R.string.tv_editor_retry);
        clear.setEnabled(!waiting&&phase!=Phase.COMMITTING&&phase!=Phase.CANCELLING);
        corners.setEnabled(ready);
        binding=true;
        for(int i=0;i<4;i++){
            MaterialButton button=find(cornerIds[i]);button.setEnabled(ready);
            boolean selected=i==overlay.selectedCorner(),placed=overlay.cornerPlaced(i);
            button.setTextColor(color(!ready?R.color.alpr_text_secondary:selected?R.color.alpr_background:placed?R.color.alpr_success:R.color.alpr_accent));
            button.setBackgroundTintList(ColorStateList.valueOf(color(!ready?R.color.alpr_surface:selected?R.color.alpr_primary:placed?R.color.alpr_card_green:R.color.alpr_surface)));
            button.setContentDescription(activity.getString(cornerNames[i])+(placed?" · ustawiony":" · do wskazania"));
        }
        corners.check(cornerIds[overlay.selectedCorner()]);binding=false;
        if(calculatorMode){status.setText(fontScale()>1.2f||compactScreen()?R.string.tv_distance_description_compact:R.string.tv_distance_description);instruction.setText("Szacunek kadrowania; cztery widoczne rogi są nadrzędne.");renderDistance();}
        else if(waiting){boolean frameMissing=geometryState==null||geometryState.availability==CameraGeometryProvider.Availability.WAITING_FRAME;
            status.setText(frameMissing?(fontScale()>1.2f?R.string.tv_editor_waiting_compact:R.string.tv_editor_waiting):
                    fontScale()>1.2f?R.string.tv_editor_mapping_wait_compact:R.string.tv_editor_mapping_wait);
            instruction.setText(frameMissing?R.string.tv_editor_wait_frame_hint:R.string.tv_editor_wait_mapping_hint);}
        else if(phase==Phase.REVIEW){status.setText(fontScale()>1.2f?"NIEAKTUALNY":"NIEAKTUALNY · sprawdź kadr");instruction.setText(R.string.tv_saved_frame_changed);}
        else if(phase==Phase.CAMERA_ERROR){status.setText(fontScale()>1.2f?R.string.tv_editor_mapping_error_compact:R.string.tv_editor_mapping_error_title);instruction.setText(R.string.tv_editor_camera_error);}
        else if(cameraStep){status.setText(R.string.tv_camera_step);}
        else if(!referenceEditor&&overlay.placedCount()<4){String name=fontScale()>1.2f?((MaterialButton)find(cornerIds[overlay.selectedCorner()])).getText().toString():activity.getString(cornerNames[overlay.selectedCorner()]);
            status.setText(activity.getString(R.string.tv_editor_step,overlay.placedCount()+1,name));instruction.setText(R.string.tv_editor_tap);}
        else if(!referenceEditor){status.setText(editorState());instruction.setText(R.string.tv_precision_instruction);}
        String validation=!calculatorMode&&!cameraStep&&ready&&overlay.placedCount()==4?TvSetupCalibration.validation(overlay.corners(),geometry,density()):null;
        String problem=cameraError?(fontScale()>1.2f?activity.getString("FRAME_NOT_AVAILABLE".equals(cameraFailureCode)||"CAMERA_UNAVAILABLE".equals(cameraFailureCode)?
                R.string.tv_editor_frame_error_short:R.string.tv_editor_mapping_error_short):cameraFailure):cameraStep&&"SAVED_FRAME_UNAVAILABLE".equals(cameraFailureCode)?cameraFailure:validation;
        if(!overlay.isManipulating()){error.setText(CameraOperatorPresentation.readableDetail(activity,problem));error.setVisibility(problem==null||problem.isEmpty()?View.GONE:View.VISIBLE);}
        confirm.setEnabled(!calculatorMode&&!cameraStep&&ready&&controlsReady()&&overlay.placedCount()==4&&!overlay.isManipulating()&&validation==null&&editable.getAsBoolean());
        if(!calculatorMode&&!cameraStep)renderCoverage();
        renderReferenceContent();renderCornerFeedback();
    }
    private void renderCoverage(){
        if(geometry==null||overlay.placedCount()!=4||phase!=Phase.EDITING||TvSetupCalibration.validation(overlay.corners(),geometry,density())!=null){coverage.setText(R.string.tv_editor_coverage_empty);return;}
        List<Point2> points=overlay.corners();double l=Double.POSITIVE_INFINITY,t=l,r=Double.NEGATIVE_INFINITY,b=r,area=0;
        for(int i=0;i<4;i++){Point2 a=points.get(i),c=points.get((i+1)%4);double[] p=geometry.normalizedToView(a.x,a.y),q=geometry.normalizedToView(c.x,c.y);
            area+=p[0]*q[1]-q[0]*p[1];l=Math.min(l,p[0]);r=Math.max(r,p[0]);t=Math.min(t,p[1]);b=Math.max(b,p[1]);}
        double[] u=geometry.usableViewRect();double w=u[2]-u[0],h=u[3]-u[1];
        coverage.setText(String.format(Locale.getDefault(),"Zajętość %.0f%%\nL %.0f%% · P %.0f%% · G %.0f%% · D %.0f%%",Math.abs(area)/2/(w*h)*100,(l-u[0])/w*100,(u[2]-r)/w*100,(t-u[1])/h*100,(u[3]-b)/h*100));
    }

    private void bindInputs(){
        binding=true;
        EditText[] edits={diagonal,width,height,aspectW,aspectH,margin,measured};String[] keys={"diagonal","width","height","aspect_w","aspect_h","margin","measured"};String[] defaults={"","","","16","9","5",""};
        for(int i=0;i<edits.length;i++){EditText edit=edits[i];edit.setTag(keys[i]);edit.setText(fields.optString(keys[i],defaults[i]));edit.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int n){}public void afterTextChanged(Editable e){}
            public void onTextChanged(CharSequence s,int a,int b,int c){if(!binding){saveInputs();renderDistance();revealFocusedField();}}});
            edit.setOnFocusChangeListener((v,focused)->{if(focused){lastInput=edit;revealFocusedField();}});
            edit.setOnEditorActionListener((view,action,key)->{
                if(action!=EditorInfo.IME_ACTION_NEXT&&action!=EditorInfo.IME_ACTION_DONE)return false;
                EditText[] all={diagonal,width,height,aspectW,aspectH,margin,measured};boolean next=false;
                for(EditText candidate:all){if(next&&candidate.isShown()){candidate.requestFocus();revealFocusedField();return true;}if(candidate==view)next=true;}
                hideKeyboard();return true;
            });}
        units.check("SIZE_CM".equals(inputMode)?R.id.tv_units_size:R.id.tv_units_diagonal);
        ratios.check("4".equals(aspectW.getText().toString())&&"3".equals(aspectH.getText().toString())?R.id.tv_ratio_classic:
                "16".equals(aspectW.getText().toString())&&"9".equals(aspectH.getText().toString())?R.id.tv_ratio_wide:R.id.tv_ratio_custom);
        binding=false;
        units.addOnButtonCheckedListener((group,id,checked)->{if(checked&&!binding){inputMode=id==R.id.tv_units_size?"SIZE_CM":"DIAGONAL_IN";updateInputVisibility();saveInputs();renderDistance();}});
        ratios.addOnButtonCheckedListener((group,id,checked)->{if(!checked||binding)return;binding=true;
            if(id==R.id.tv_ratio_wide){aspectW.setText("16");aspectH.setText("9");}else if(id==R.id.tv_ratio_classic){aspectW.setText("4");aspectH.setText("3");}
            binding=false;updateInputVisibility();saveInputs();renderDistance();});
        updateInputVisibility();
    }
    private void updateInputVisibility(){
        find(R.id.tv_diagonal_container).setVisibility("DIAGONAL_IN".equals(inputMode)?View.VISIBLE:View.GONE);
        find(R.id.tv_width_container).setVisibility("SIZE_CM".equals(inputMode)?View.VISIBLE:View.GONE);
        find(R.id.tv_height_container).setVisibility("SIZE_CM".equals(inputMode)?View.VISIBLE:View.GONE);
        find(R.id.tv_custom_ratio_fields).setVisibility(ratios.getCheckedButtonId()==R.id.tv_ratio_custom?View.VISIBLE:View.GONE);
        for(EditText edit:new EditText[]{diagonal,width,height,aspectW,aspectH,margin,measured})edit.setImeOptions(edit==measured?EditorInfo.IME_ACTION_DONE:EditorInfo.IME_ACTION_NEXT);
        for(int groupId:new int[]{R.id.tv_distance_units,R.id.tv_distance_ratios}){
            MaterialButtonToggleGroup group=find(groupId);for(int i=0;i<group.getChildCount();i++){
                MaterialButton button=(MaterialButton)group.getChildAt(i);boolean selected=button.getId()==group.getCheckedButtonId();
                button.setTextColor(color(selected?R.color.alpr_background:R.color.alpr_accent));
                button.setBackgroundTintList(ColorStateList.valueOf(color(selected?R.color.alpr_primary:R.color.alpr_surface)));}}
    }
    private void saveInputs(){try{for(EditText edit:new EditText[]{diagonal,width,height,aspectW,aspectH,margin,measured})fields.put((String)edit.getTag(),edit.getText().toString());fields.put("mode",inputMode);preferences.edit().putString(INPUTS,fields.toString()).apply();}catch(Exception ignored){}}
    private static double number(EditText edit){return Double.parseDouble(edit.getText().toString().trim().replace(',','.'));}
    private TvDistanceCalculator.Inputs inputs(){double m=number(margin)/100;return "SIZE_CM".equals(inputMode)?TvDistanceCalculator.Inputs.sizeCm(number(width),number(height),m):TvDistanceCalculator.Inputs.diagonal(number(diagonal),number(aspectW),number(aspectH),m);}
    private Double measured(){try{double d=number(measured);return d>0&&Double.isFinite(d)?d:null;}catch(Exception e){return null;}}
    private TvDistanceCalculator.Estimate cameraEstimate(TvDistanceCalculator.Inputs input){
        if(geometry==null)return TvDistanceCalculator.unavailable(distanceGeometryReason());
        return metadata==null?TvDistanceCalculator.unavailable(metadataReason.isEmpty()?"Brak metadanych aktywnej kamery":metadataReason):TvDistanceCalculator.fromCamera(input,metadata,geometry);}
    private void renderDistance(){
        if(!calculatorMode)return;
        try{TvDistanceCalculator.Inputs input=inputs();TvDistanceCalculator.Estimate estimate=cameraEstimate(input);
            String text=estimate.available()?String.format(Locale.getDefault(),"≈ %.2f m\nEfektywny kadr aktywnej kamery\n",estimate.distanceM):"Nie można wiarygodnie obliczyć odległości.\n"+estimate.reason+"\n";
            Double measuredDistance=measured();if(measuredDistance!=null){TvDistanceCalculator.Estimate ratio="READY".equals(calibration.status(geometry))?TvDistanceCalculator.fromMeasured(input,measuredDistance,calibration.corners,geometry):TvDistanceCalculator.unavailable("Potwierdź kalibrację TV, aby użyć proporcji obrazu");
                text+=ratio.available()?String.format(Locale.getDefault(),"≈ %.2f m · proporcja z pomiaru %.2f m\n",ratio.distanceM,measuredDistance):ratio.reason+"\n";}
            if(metadata!=null){double focus=metadata.optDouble("minimum_focus_distance_diopters",Double.NaN);if(Double.isFinite(focus)&&focus>0)text+=String.format(Locale.getDefault(),"Osobno: min. odległość ostrzenia ≈ %.2f m (CameraCharacteristics)\n",1/focus);}
            result.setText(text+"Szacunek centralnej geometrii; niepewność niezmierzona. Sprawdź cztery rogi na żywo. Wynik nie gwarantuje ostrości ani czytelności tablic.");
        }catch(Exception error){String cameraReason=geometry==null?distanceGeometryReason():metadata==null?metadataReason:"";
            result.setText(activity.getString(R.string.tv_distance_input_error)+(cameraReason.isEmpty()?"":"\nNie można wiarygodnie obliczyć odległości.\n"+cameraReason));}
    }
    public JSONObject snapshot(long wallMillis){
        TvDistanceCalculator.Inputs input=null;TvDistanceCalculator.Estimate estimate;
        try{input=inputs();estimate=cameraEstimate(input);}catch(Exception e){estimate=TvDistanceCalculator.unavailable("Wprowadź wymiary TV i poprawny margines");}
        Double measuredDistance=measured();if(input!=null&&!estimate.available()&&measuredDistance!=null&&"READY".equals(calibration.status(geometry)))estimate=TvDistanceCalculator.fromMeasured(input,measuredDistance,calibration.corners,geometry);
        return calibration.snapshot(geometry,input,measuredDistance,estimate,Instant.ofEpochMilli(wallMillis).toString());
    }
    /** Immutable live mapper; observation workers must capture it with their buffer. */
    public CameraPreviewGeometry observationGeometry(){return geometry;}
    /** Preparation only: live sessions keep their frozen mapper and existing invalidation gates. */
    public void revalidateForCamera(CameraSetupReadiness camera){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Calibration revalidation requires UI");
        if(camera==null||editing()||!editable.getAsBoolean())return;
        TvSetupCalibration next=calibration;
        if(persistedApprovalExpected){
            try{
                JSONObject stored=new JSONObject(preferences.getString(STORED,"")),original=next.approval();
                if(!com.example.alpr_v1.setup.SetupJson.verified(stored)||original==null
                        ||!stored.getString("digest_sha256").equals(original.getString("digest_sha256")))next=next.invalidated("INVALID_STORED_CALIBRATION");
            }catch(org.json.JSONException invalid){next=next.invalidated("INVALID_STORED_CALIBRATION");}
        }
        next=next.revalidated(geometry,camera,SystemClock.elapsedRealtimeNanos(),Instant.now().toString());
        if(next!=calibration){calibration=next;presentStoredOutline();renderOutlineControl();}
    }
    /** Existing generic telemetry envelope; never a new manual approval or an archive schema. */
    public JSONObject revalidationEvidence(CameraSetupReadiness camera){
        revalidateForCamera(camera);
        TvSetupCalibration c=calibration;CameraPreviewGeometry g=geometry;
        if(!"READY".equals(c.status(g))||camera==null||!camera.ready()
                ||!camera.frameFreshAt(SystemClock.elapsedRealtimeNanos())||g.bindingGeneration!=camera.bindingGeneration)return null;
        JSONObject original=c.approval();
        if(original==null||!com.example.alpr_v1.setup.SetupJson.verified(original))return null;
        try{return new JSONObject().put("validation_scope","camera_configuration_and_source_geometry")
                .put("physical_alignment_verified",false).put("revalidated_at_utc",Instant.now().toString())
                .put("original_calibration_snapshot",original).put("original_digest_sha256",original.getString("digest_sha256"))
                .put("confirmed_at_utc",c.confirmedAt).put("validated_source_geometry",g.toJson())
                .put("applied_camera_setup",camera.applied.toJson()).put("camera_binding_generation",camera.bindingGeneration)
                .put("camera_setup_revision",camera.revision).put("frame_received_elapsed_nanos",camera.lastFrameElapsedNanos)
                .put("frame_sensor_timestamp_nanos",camera.frameTimestampNanos>0?camera.frameTimestampNanos:JSONObject.NULL);
        }catch(org.json.JSONException error){throw new IllegalStateException("CALIBRATION_REVALIDATION_EVIDENCE_INVALID",error);}
    }
    private CameraSetupControl.State currentCameraState(){
        if(cameraControls==null)return cameraState;
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("CAMERA_SETUP_SNAPSHOT_STALE: capture camera setup on UI before preparation");
        CameraSetupControl.State current=cameraControls.state();
        // Keep the existing worker snapshot coherent at START; editor transitions stay listener-owned.
        if(!editing())cameraState=current;
        return current;
    }
    public com.example.alpr_v1.setup.TvBboxCrop selectionForInput(){
        CameraSetupControl.State current=currentCameraState();
        revalidateForCamera(current.readiness);
        return selectionForInput(current.ready()?current.applied:null);
    }
    public com.example.alpr_v1.setup.TvBboxCrop selectionForInput(CameraSetupSnapshot confirmedSetup){
        CameraPreviewGeometry g=geometry;TvSetupCalibration c=calibration;
        if(confirmedSetup==null)throw new IllegalStateException("CAMERA_SETUP_SNAPSHOT_MISSING");
        JSONObject confirmed=c.snapshot(g,null,null,TvDistanceCalculator.unavailable("NOT_USED_FOR_TV_BBOX_INPUT"),c.confirmedAt);
        return com.example.alpr_v1.setup.TvBboxCrop.prepare(confirmed,g,confirmedSetup);
    }
    public String bboxPreparationStatus(){
        CameraSetupControl.State current=currentCameraState();
        revalidateForCamera(current.readiness);
        return bboxPreparationStatus(current.ready()?current.applied:null,current.reason);
    }
    public String bboxPreparationStatus(CameraSetupSnapshot confirmedSetup,String cameraReason){
        if(!"READY".equals(calibration.status(geometry)))return confirmedSetup==null&&cameraReason!=null&&!cameraReason.isEmpty()
                ?cameraReason:"TV: kalibracja wymaga sprawdzenia · "+calibration.reason(geometry);
        if(confirmedSetup==null)return cameraReason==null||cameraReason.isEmpty()?"Brak potwierdzonej kamery i zoomu":cameraReason;
        if(geometry==null||!geometry.cameraId.equals(confirmedSetup.selection.logicalCameraId)
                ||!java.util.Objects.equals(geometry.physicalCameraId,confirmedSetup.selection.physicalCameraId)
                ||Math.abs(geometry.zoomRatio-confirmedSetup.zoomRatio)>=1e-5)
            return com.example.alpr_v1.camera.CameraSetupReadiness.Reason.CAMERA_SETUP_GEOMETRY_MISMATCH.description();
        if(java.time.Instant.parse(calibration.confirmedAt).toEpochMilli()>System.currentTimeMillis())return "Potwierdzenie kalibracji ma przyszłą datę · sprawdź zegar telefonu";
        try{selectionForInput(confirmedSetup);}catch(RuntimeException error){return "Nieprawidłowy BBOX TV · sprawdź i zatwierdź narożniki";}
        return "READY";
    }
    /** Retaining loaded models is not camera admission; START still requires a fresh confirmation. */
    public boolean matchesPreparedGeometry(com.example.alpr_v1.setup.TvBboxCrop selected){
        CameraPreviewGeometry g=geometry;TvSetupCalibration c=calibration;
        if(selected==null||g==null||!"READY".equals(c.status(g))||!selected.geometry.sameGeometry(g))return false;
        try{return java.time.Instant.parse(c.confirmedAt).toEpochMilli()<=System.currentTimeMillis()
                &&selected.calibrationDigest.equals(c.snapshot(g,null,null,TvDistanceCalculator.unavailable("NOT_USED_FOR_TV_BBOX_INPUT"),c.confirmedAt).getString("digest_sha256"));
        }catch(RuntimeException|org.json.JSONException invalid){return false;}
    }
    public com.example.alpr_v1.setup.TvBboxCrop.Frame openInput(com.example.alpr_v1.setup.TvBboxCrop frozen,
            com.example.alpr_v1.vision.CameraImageConverter.Result converted){
        CameraPreviewGeometry g=geometry;
        if(g==null||converted.cameraCropRect==null||converted.bufferWidth!=g.bufferWidth||converted.bufferHeight!=g.bufferHeight
                ||converted.rotationDegrees!=g.rotationDegrees)throw new IllegalArgumentException("TV_BBOX_CAMERA_FRAME_CHANGED");
        try{
            org.json.JSONArray crop=g.toJson().getJSONArray("crop_rect_px");android.graphics.Rect actual=converted.cameraCropRect;
            if(crop.getDouble(0)!=actual.left||crop.getDouble(1)!=actual.top||crop.getDouble(2)!=actual.right||crop.getDouble(3)!=actual.bottom)
                throw new IllegalArgumentException("TV_BBOX_CAMERA_CROP_CHANGED");
        }catch(org.json.JSONException error){throw new IllegalArgumentException(error);}
        if(!"READY".equals(calibration.status(g)))throw new IllegalArgumentException("TV_BBOX_CALIBRATION_STALE");
        return frozen.open(converted.bitmap,g,cameraState.applied);
    }
    public boolean matchesInputSelection(com.example.alpr_v1.setup.TvBboxCrop selected){
        if(selected==null)return false;
        try{com.example.alpr_v1.setup.TvBboxCrop current=selectionForInput();
            return selected.sameSelection(current)&&selected.calibrationDigest.equals(current.calibrationDigest);
        }catch(RuntimeException error){return false;}
    }
    public JSONObject freezeInputSelection(com.example.alpr_v1.setup.TvBboxCrop selected){
        if(!matchesInputSelection(selected))throw new IllegalStateException("TV_BBOX_SELECTION_CHANGED");
        frozenDigest=selected.calibrationDigest;frozenGeometry=geometry;invalidationSent=false;return selected.calibrationJson();
    }
    public JSONObject freeze(long wallMillis){saveInputs();JSONObject snapshot=snapshot(wallMillis);frozenDigest=snapshot.optString("digest_sha256");frozenGeometry="READY".equals(snapshot.optString("status"))?geometry:null;invalidationSent=false;return snapshot;}
    public void resumeSession(JSONObject frozen){if(invalidationSent||frozen==null||!"READY".equals(frozen.optString("status")))return;invalidationSent=true;frozenDigest=frozen.optString("digest_sha256");calibration=calibration.awaitingVerification("ACTIVITY_RESUMED_REQUIRES_GEOMETRY_CHECK");overlay.setCorners(Collections.emptyList());emitInvalidation("ACTIVITY_RESUMED_REQUIRES_GEOMETRY_CHECK");}
    private void emitInvalidation(String reason){try{event.accept("tv_setup_calibration_invalidated",new JSONObject().put("digest_sha256",frozenDigest).put("reason",reason));}catch(Exception ignored){}}
    private int color(int resource){
        if(reference!=null&&reference.enabled()){
            if(resource==R.color.alpr_accent||resource==R.color.alpr_primary)resource=R.color.tv_ref_accent;
            else if(resource==R.color.alpr_background)resource=R.color.tv_ref_background;
            else if(resource==R.color.alpr_surface)resource=R.color.tv_ref_surface;
            else if(resource==R.color.alpr_text_primary)resource=R.color.tv_ref_text;
            else if(resource==R.color.alpr_text_secondary)resource=R.color.tv_ref_secondary;
        }
        return ContextCompat.getColor(activity,resource);
    }
    private String geometryFailureMessage(){
        if(geometryState==null||geometryState.availability==CameraGeometryProvider.Availability.WAITING_FRAME)
            return activity.getString(R.string.tv_editor_no_analysis_frame);
        if("CAMERA_INFO_UNAVAILABLE".equals(geometryState.reasonCode))return activity.getString(R.string.tv_editor_camera_identity_missing);
        if("FRAME_METADATA_UNAVAILABLE".equals(geometryState.reasonCode))return activity.getString(R.string.tv_editor_frame_geometry_missing);
        if(geometryState.availability==CameraGeometryProvider.Availability.INVALID_TRANSFORM)
            return activity.getString(R.string.tv_editor_invalid_mapping);
        return activity.getString(R.string.tv_editor_mapping_not_ready);
    }
    private String geometryDetails(){
        String header=cameraFailure+"\n\nBuild "+com.example.alpr_v1.BuildConfig.GIT_COMMIT+"\n"+com.example.alpr_v1.BuildConfig.BUILT_AT_UTC
                +"\nŻądanie kamery: "+cameraState.requested+" · zoom "+cameraState.requestedZoom
                +"\nPotwierdzona kamera: "+(cameraState.applied==null?"N/A":cameraState.applied.selection+" · zoom "+cameraState.applied.zoomRatio)
                +"\nDostępne ścieżki kamer: "+cameraState.choices.size();
        return geometryState==null?header:header+"\n"+geometryState.availability+" · "+geometryState.reasonCode+"\n"+geometryState.detail+"\n\n"+geometryState.diagnostics;
    }
    private String distanceGeometryReason(){
        if(geometryState==null||geometryState.availability==CameraGeometryProvider.Availability.WAITING_FRAME)
            return "Brak klatki z aktywnej kamery. Wynik pojawi się po udostępnieniu geometrii i metadanych.";
        if(geometryState.availability==CameraGeometryProvider.Availability.INVALID_TRANSFORM)
            return "Brak wiarygodnej geometrii aktualnego kadru kamery.";
        return "Oczekiwanie na aktualne przeliczenie kadru kamery.";
    }
    private boolean compactScreen(){return activity.getResources().getDisplayMetrics().heightPixels/density()<=640;}
    private boolean landscape(){if(imeVisible)return activity.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        return root.getWidth()>0&&root.getHeight()>0?root.getWidth()>root.getHeight():
            activity.getResources().getDisplayMetrics().widthPixels>activity.getResources().getDisplayMetrics().heightPixels;}
    private static boolean sameLayout(ConstraintLayout.LayoutParams a,ConstraintLayout.LayoutParams b){
        return a.width==b.width&&a.height==b.height&&a.topToTop==b.topToTop&&a.topToBottom==b.topToBottom
                &&a.bottomToTop==b.bottomToTop&&a.bottomToBottom==b.bottomToBottom&&a.startToStart==b.startToStart
                &&a.startToEnd==b.startToEnd&&a.endToStart==b.endToStart&&a.endToEnd==b.endToEnd
                &&a.bottomMargin==b.bottomMargin&&a.topMargin==b.topMargin;
    }
    private void topMargin(View view,int value){ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)view.getLayoutParams();
        int pixels=dp(value);if(lp.topMargin!=pixels){lp.topMargin=pixels;view.setLayoutParams(lp);}}
    private int dp(int value){return Math.round(value*density());}
    private float density(){return activity.getResources().getDisplayMetrics().density;}
    private float fontScale(){return activity.getResources().getConfiguration().fontScale;}
    private static void textIfChanged(TextView view,String text){if(!text.contentEquals(view.getText()))view.setText(text);}
}
