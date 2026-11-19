package com.example.alpr_v1;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Size;
import android.widget.ArrayAdapter;



import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;


import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.experiment.ResearchExecutionChoice;
import com.example.alpr_v1.camera.AnalysisResolutionProfile;
import com.example.alpr_v1.capture.CropCapacityPolicy;
import com.example.alpr_v1.capture.CaptureDirectoryStore;
import com.example.alpr_v1.logging.AppLog;
import com.example.alpr_v1.inference.RuntimeBackendFactory;
import com.example.alpr_v1.model.AlprPackageImporter;
import com.example.alpr_v1.model.InstalledAlprPackage;
import com.example.alpr_v1.model.InstalledModel;
import com.example.alpr_v1.model.ModelImportResult;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.model.ModelRole;
import com.example.alpr_v1.model.ModelVariant;
import com.example.alpr_v1.pipeline.RecognitionProfile;
import com.example.alpr_v1.ui.ModelStatusFormatter;
import com.example.alpr_v1.ui.readiness.*;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.camera.CameraResolutionCatalog;
import com.example.alpr_v1.camera.CameraResolutionSelection;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;


import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Osobny ekran konfiguracji. Zmiany są stosowane przez MainActivity po powrocie. */
public final class SettingsActivity extends AppCompatActivity {
    public static final String PREFERENCES = "alpr_ui";
    public static final String EXTRA_CAMERA_OBSERVATION="camera_observation";
    public static final String EXTRA_MT_FRAME_WIDTH="mt_analysis_frame_width",EXTRA_MT_FRAME_HEIGHT="mt_analysis_frame_height";
    public static final String EXTRA_THERMAL_ENABLED="readiness_thermal_enabled";
    public static final String EXTRA_RETURN_TO_SUMMARY="readiness_result_owner";
    public static final String KEY_REVISION = "settings_revision";

    public static final String KEY_ANALYSIS_RESOLUTION_SELECTION =
            "analysis_resolution_selection";

    public static final String KEY_EXPERIMENT_MODE_ENABLED =
            "experiment_mode_enabled";

    public static final String KEY_EXPERIMENT_ROI_POLICY =
            "experiment_roi_budget_policy";
    public static final String KEY_TV_INPUT_MODE = "tv_research_input_mode_v1";
    public static final String KEY_EXPERIMENT_TYPE = "experiment_type";
    public static final String KEY_EXPERIMENT_VARIANT = "experiment_variant";
    public static final String KEY_RESEARCH_MP_EXECUTION = "research_mp_execution";
    public static final String KEY_RESEARCH_MT_EXECUTION = "research_mt_execution";
    public static final String KEY_RESEARCH_MZ_EXECUTION = "research_mz_execution";
    public static final String KEY_RESEARCH_LOCK_ENABLED = "research_lock_enabled";
    public static final String KEY_RESEARCH_AUTOZOOM_ENABLED = "research_autozoom_enabled";
    public static final String KEY_EXPERIMENT_SERIES_ID = "experiment_series_id";
    public static final String KEY_EXPERIMENT_SCENARIO_ID = "experiment_scenario_id";
    public static final String KEY_EXPERIMENT_REPLICATE_INDEX = "experiment_replicate_index";
    public static final String KEY_EXPERIMENT_NOTES = "experiment_notes";
    public static final String KEY_SCENE_HANDLING_MODE = "scene_handling_mode";

    private static final String LOG_TAG = "SettingsActivity";
    private static final String STATE_PENDING_IMPORT_ROLE = "pending_import_role";
    private static final String STATE_PENDING_IMPORT_CONTENT = "pending_import_content";

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private ModelRegistry modelRegistry;
    private AlprPackageImporter packageImporter;
    private AutoTuneManager autoTuneManager;
    private TextView modelStatusSummary;
    private TextView vehicleModelStatus;
    private TextView plateModelStatus;
    private TextView characterModelStatus;
    private TextView storagePath;
    private View progress;
    private MaterialButton importModelButton;
    private MaterialButton importPackageButton;
    private MaterialButton restoreBaseButton;
    private MaterialButton vehicleNode;
    private MaterialButton plateNode;
    private MaterialButton characterNode;
    private Chip vehicleBadge;
    private Chip plateBadge;
    private Chip characterBadge;
    private TextInputEditText experimentSeriesId;
    private TextInputEditText experimentScenarioId;
    private TextInputEditText experimentReplicateIndex;
    private TextInputEditText experimentNotes;
    private MaterialAutoCompleteTextView experimentType;
    private TextInputEditText experimentVariant;
    private ModelRole pendingImportRole;
    private AlprPackageImporter.ExpectedContent pendingImportContent;
    private boolean importInProgress;
    private com.example.alpr_v1.ui.OptionsSectionsController optionsSections;
    private boolean configurationBlocked;
    private int readinessDialogs;
    private ReadinessController readiness;
    private ReadinessSummaryView readinessView;
    private ReadinessReturnContext readinessNavigation;
    private final SharedPreferences.OnSharedPreferenceChangeListener readinessPreferences=(prefs,key)->{
        if(readiness!=null)runOnUiThread(()->readiness.refresh());
    };

    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                ModelRole expectedRole = pendingImportRole;
                AlprPackageImporter.ExpectedContent expectedContent = pendingImportContent;
                pendingImportRole = null;
                pendingImportContent = null;
                if (uri != null) importSelectedFile(uri, expectedRole, expectedContent);
                else if(readinessNavigation!=null)returnToReadinessSummary();
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_ALPR_v1_Options);
        super.onCreate(savedInstanceState);
        readinessNavigation=ReadinessReturnContext.fromBundle(savedInstanceState==null?null:savedInstanceState.getBundle("readiness_local_navigation"));
        if (researchWriteInProgress()) {
            configurationBlocked=true;
            Toast.makeText(this,R.string.main_session_work_blocked,Toast.LENGTH_LONG).show();
            returnBlockedReadiness();
            finish();return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        applySystemInsets();
        restorePendingImport(savedInstanceState);

        preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        modelRegistry = new ModelRegistry(this, true);
        packageImporter = new AlprPackageImporter(this, modelRegistry);
        autoTuneManager = new AutoTuneManager(this);

        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(view -> requestSettingsClose());
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        requestSettingsClose();
                    }
                }
        );
        modelStatusSummary = findViewById(R.id.settings_model_status_summary);
        vehicleModelStatus = findViewById(R.id.settings_model_status_vehicle);
        plateModelStatus = findViewById(R.id.settings_model_status_plate);
        characterModelStatus = findViewById(R.id.settings_model_status_character);
        storagePath = findViewById(R.id.settings_storage_path);
        progress = findViewById(R.id.settings_progress);
        importModelButton = findViewById(R.id.settings_import_model);
        importPackageButton = findViewById(R.id.settings_import_alpr_package);
        restoreBaseButton = findViewById(R.id.settings_restore_base_package);
        vehicleNode = findViewById(R.id.settings_node_vehicle);
        plateNode = findViewById(R.id.settings_node_plate);
        characterNode = findViewById(R.id.settings_node_character);
        vehicleBadge = findViewById(R.id.settings_node_vehicle_badge);
        plateBadge = findViewById(R.id.settings_node_plate_badge);
        characterBadge = findViewById(R.id.settings_node_character_badge);
        experimentSeriesId = findViewById(R.id.settings_experiment_series_id);
        experimentScenarioId = findViewById(R.id.settings_experiment_scenario_id);
        experimentReplicateIndex = findViewById(R.id.settings_experiment_replicate_index);
        experimentNotes = findViewById(R.id.settings_experiment_notes);
        experimentType = findViewById(R.id.settings_experiment_type);
        experimentVariant = findViewById(R.id.settings_experiment_variant);

        configureProfileControls();
        configureResolutionControls();
        configureCropControls();
        configureSceneHandlingControls();
        configureDynamicMtControls();
        configureRoiBudgetControls();
        configureExperimentIdentity();
        findViewById(R.id.settings_tv_calibrate).setOnClickListener(v -> requestTvSetup(com.example.alpr_v1.ui.TvSetupController.Mode.CALIBRATION));
        findViewById(R.id.settings_tv_distance).setOnClickListener(v -> requestTvSetup(com.example.alpr_v1.ui.TvSetupController.Mode.DISTANCE));
        refreshTvTools();
        configurePipelineControls();
        refreshModelStatus();
        refreshStoragePath();
        optionsSections=new com.example.alpr_v1.ui.OptionsSectionsController(this,savedInstanceState,
                ()->com.example.alpr_v1.ui.ResearchConfigurationText.planned(preferences,modelRegistry,autoTuneManager));
        readinessView=new ReadinessSummaryView(this,savedInstanceState,this::navigateReadiness);
        readiness=new ReadinessController(this::effectiveReadiness,result->{
            ReadinessPresentation presentation=new ReadinessPresentation(result,((com.google.android.material.materialswitch.MaterialSwitch)findViewById(R.id.settings_experiment_switch)).isChecked());
            readinessView.render(presentation);optionsSections.setReadiness(presentation);
        });
        for(String name:new String[]{PREFERENCES,"autotune","model_registry"})
            getSharedPreferences(name,MODE_PRIVATE).registerOnSharedPreferenceChangeListener(readinessPreferences);
        readinessNavigation=ReadinessReturnContext.fromBundle(savedInstanceState==null?null:savedInstanceState.getBundle("readiness_local_navigation"));
        MaterialToolbar readinessToolbar=findViewById(R.id.settings_toolbar);
        readinessToolbar.getMenu().add(0,R.id.readiness_back_summary,0,R.string.readiness_summary)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM);
        readinessToolbar.getMenu().findItem(R.id.readiness_back_summary).setVisible(readinessNavigation!=null);
        readinessToolbar.setOnMenuItemClickListener(item->{if(item.getItemId()!=R.id.readiness_back_summary)return false;returnToReadinessSummary();return true;});
        for(TextInputEditText field:new TextInputEditText[]{experimentSeriesId,experimentScenarioId,experimentReplicateIndex,experimentVariant,experimentNotes})
            field.addTextChangedListener(new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence s,int start,int count,int after){}
                public void onTextChanged(CharSequence s,int start,int before,int count){refreshOptionsLayout();if(readiness!=null)readiness.refresh();}
                public void afterTextChanged(android.text.Editable text){}
            });
        if(getIntent().hasExtra(ReadinessReturnContext.EXTRA))returnToReadinessSummary();
        readiness.refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(optionsSections!=null)optionsSections.save(outState);
        if(readinessView!=null)readinessView.save(outState);
        if(readinessNavigation!=null)outState.putBundle("readiness_local_navigation",readinessNavigation.toBundle());
        if (pendingImportRole != null) {
            outState.putString(STATE_PENDING_IMPORT_ROLE, pendingImportRole.wireName());
        }
        if (pendingImportContent != null) {
            outState.putString(STATE_PENDING_IMPORT_CONTENT, pendingImportContent.name());
        }
    }

    private void restorePendingImport(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        String role = savedInstanceState.getString(STATE_PENDING_IMPORT_ROLE, "");
        if (!role.isEmpty()) {
            try {
                pendingImportRole = ModelRole.fromWire(role);
            } catch (IllegalArgumentException ignored) {
                pendingImportRole = null;
            }
        }
        String content = savedInstanceState.getString(STATE_PENDING_IMPORT_CONTENT, "");
        if (!content.isEmpty()) {
            try {
                pendingImportContent = AlprPackageImporter.ExpectedContent.valueOf(content);
            } catch (IllegalArgumentException ignored) {
                pendingImportContent = null;
            }
        }
    }

    private void configureProfileControls() {
        MaterialButtonToggleGroup group = findViewById(R.id.settings_profile_group);
        RecognitionProfile selected = RecognitionProfile.fromWireName(preferences.getString(
                "recognition_profile", RecognitionProfile.BALANCED.wireName()
        ));
        int selectedId = selected == RecognitionProfile.FAST
                ? R.id.settings_profile_fast
                : selected == RecognitionProfile.ACCURATE
                ? R.id.settings_profile_accurate
                : R.id.settings_profile_balanced;
        group.check(selectedId);

        com.google.android.material.materialswitch.MaterialSwitch lockSwitch =
                findViewById(R.id.settings_research_lock_switch);
        com.google.android.material.materialswitch.MaterialSwitch autoZoomSwitch =
                findViewById(R.id.settings_research_autozoom_switch);
        lockSwitch.setChecked(preferences.getBoolean(
                KEY_RESEARCH_LOCK_ENABLED,
                true
        ));
        autoZoomSwitch.setChecked(preferences.getBoolean(
                KEY_RESEARCH_AUTOZOOM_ENABLED,
                false
        ));
        lockSwitch.setOnCheckedChangeListener((button, enabled) -> {
            preferences.edit().putBoolean(
                    KEY_RESEARCH_LOCK_ENABLED,
                    enabled
            ).apply();
            if (!enabled && autoZoomSwitch.isChecked()) {
                autoZoomSwitch.setChecked(false);
            }
            markChanged();
        });
        autoZoomSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (enabled && !lockSwitch.isChecked()) {
                button.setChecked(false);
                Toast.makeText(
                        this,
                        R.string.research_autozoom_requires_lock,
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
            preferences.edit().putBoolean(
                    KEY_RESEARCH_AUTOZOOM_ENABLED,
                    enabled
            ).apply();
            markChanged();
        });
        group.addOnButtonCheckedListener((ignored, checkedId, isChecked) -> {
            if (!isChecked) return;
            RecognitionProfile profile = checkedId == R.id.settings_profile_fast
                    ? RecognitionProfile.FAST
                    : checkedId == R.id.settings_profile_accurate
                    ? RecognitionProfile.ACCURATE
                    : RecognitionProfile.BALANCED;
            saveString("recognition_profile", profile.wireName());
        });
    }

    private void configureResolutionControls() {

        MaterialAutoCompleteTextView dropdown =
                findViewById(
                        R.id.settings_resolution_dropdown
                );

        TextView capabilities =
                findViewById(
                        R.id.settings_resolution_capabilities
                );


        com.example.alpr_v1.camera.CameraSetupSnapshot savedCamera=preferences.getBoolean(KEY_EXPERIMENT_MODE_ENABLED,false)
                &&"roi_budget".equals(preferences.getString(KEY_EXPERIMENT_TYPE,"roi_budget"))?
                com.example.alpr_v1.camera.CameraSetupSnapshot.restore(preferences.getString(com.example.alpr_v1.camera.CameraSetupSnapshot.PREFERENCE,"")):null;
        CameraResolutionCatalog catalog =
                new CameraResolutionCatalog(
                        this,savedCamera==null?null:savedCamera.selection
                );


        List<String> labels =
                new java.util.ArrayList<>();

        List<String> values =
                new java.util.ArrayList<>();


        labels.add(
                getString(
                        R.string.settings_resolution_auto
                )
        );

        values.add(
                CameraResolutionSelection.AUTO
        );


        for (Size size :
                catalog.resolutions()) {

            labels.add(
                    catalog.label(
                            size
                    )
            );

            values.add(
                    CameraResolutionCatalog.wireName(
                            size
                    )
            );
        }


        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        labels
                );

        dropdown.setAdapter(
                adapter
        );


        String selectedWire =
                currentResolutionWire(
                        catalog
                );


        int selectedIndex =
                values.indexOf(
                        selectedWire
                );

        if (selectedIndex < 0) {
            selectedIndex = 0;
        }


        dropdown.setText(
                labels.get(
                        selectedIndex
                ),
                false
        );


        dropdown.setOnClickListener(
                view ->
                        dropdown.showDropDown()
        );


        dropdown.setOnItemClickListener(
                (parent, view, position, id) -> {

                    if (position < 0
                            || position >= values.size()) {
                        return;
                    }

                    saveString(
                            KEY_ANALYSIS_RESOLUTION_SELECTION,
                            values.get(position)
                    );
                    describeCameraStreams(capabilities,values.get(position));
                }
        );


        if (catalog.resolutions().isEmpty()) {

            capabilities.setText(
                    R.string.settings_resolution_unavailable
            );

        } else {

            capabilities.setText(
                    R.string.settings_resolution_available
            );
        }
        describeCameraStreams(capabilities,selectedWire);
    }
    private void describeCameraStreams(TextView label,String requested){
        org.json.JSONObject observation=null;
        try{String raw=getIntent().getStringExtra(EXTRA_CAMERA_OBSERVATION);if(raw!=null)observation=new org.json.JSONObject(raw);}catch(Exception ignored){}
        label.setText("Wybrana rozdzielczość dla obu strumieni: "+requested+"\nOstatni odczyt przed otwarciem ustawień (zmiana wyboru wymaga powrotu do podglądu):\n"
                +com.example.alpr_v1.camera.CameraController.describeObservation(observation));
    }
    private String currentResolutionWire(
            CameraResolutionCatalog catalog
    ) {

        if (preferences.contains(
                KEY_ANALYSIS_RESOLUTION_SELECTION
        )) {

            return preferences.getString(
                    KEY_ANALYSIS_RESOLUTION_SELECTION,
                    CameraResolutionSelection.AUTO
            );
        }


        /*
         * Migracja starej konfiguracji.
         *
         * AUTO    -> auto
         * FAST    -> najbliższa standardowa 640x480
         * DISTANT -> najbliższa standardowa 1920x1080
         */
        AnalysisResolutionProfile legacy =
                AnalysisResolutionProfile.fromWireName(
                        preferences.getString(
                                "analysis_resolution_profile",
                                AnalysisResolutionProfile.AUTO.wireName()
                        )
                );


        if (legacy
                == AnalysisResolutionProfile.AUTO) {

            return CameraResolutionSelection.AUTO;
        }


        Size target =
                legacy
                        == AnalysisResolutionProfile.FAST

                        ? new Size(
                        640,
                        480
                )

                        : new Size(
                        1920,
                        1080
                );


        Size resolved =
                catalog.closestRegularTo(
                        target
                );


        if (resolved == null) {
            return CameraResolutionCatalog.wireName(
                    target
            );
        }


        return CameraResolutionCatalog.wireName(
                resolved
        );
    }

    private void configureCropControls() {
        MaterialButtonToggleGroup group = findViewById(R.id.settings_crop_group);
        String selected = CropCapacityPolicy.normalizeSetting(
                preferences.getString("crop_limit", CropCapacityPolicy.AUTO)
        );
        int selectedId;
        switch (selected) {
            case "10": selectedId = R.id.settings_crop_10; break;
            case "25": selectedId = R.id.settings_crop_25; break;
            case "50": selectedId = R.id.settings_crop_50; break;
            case "100": selectedId = R.id.settings_crop_100; break;
            default: selectedId = R.id.settings_crop_auto; break;
        }
        group.check(selectedId);
        group.addOnButtonCheckedListener((ignored, checkedId, isChecked) -> {
            if (!isChecked) return;
            String value = checkedId == R.id.settings_crop_10 ? "10"
                    : checkedId == R.id.settings_crop_25 ? "25"
                    : checkedId == R.id.settings_crop_50 ? "50"
                    : checkedId == R.id.settings_crop_100 ? "100"
                    : CropCapacityPolicy.AUTO;
            saveString("crop_limit", value);
        });
    }

    private void configureSceneHandlingControls() {
        MaterialButtonToggleGroup group = findViewById(
                R.id.settings_scene_handling_group
        );
        boolean experimentEnabled = preferences.getBoolean(
                KEY_EXPERIMENT_MODE_ENABLED,
                false
        );
        SceneHandlingMode stored = SceneHandlingMode.fromWireName(
                preferences.getString(
                        KEY_SCENE_HANDLING_MODE,
                        SceneHandlingMode.DYNAMIC_CONTINUITY.wireName()
                )
        );
        SceneHandlingMode effective = stored;
        group.check(effective == SceneHandlingMode.STRICT_SCENE_BOUNDARY
                ? R.id.settings_scene_strict
                : R.id.settings_scene_dynamic);
        setSceneHandlingControlsEnabled(group, true);
        group.addOnButtonCheckedListener((ignored, checkedId, isChecked) -> {
            if (!isChecked) {
                refreshSceneHandlingControls();
                return;
            }
            SceneHandlingMode selected = checkedId == R.id.settings_scene_strict
                    ? SceneHandlingMode.STRICT_SCENE_BOUNDARY
                    : SceneHandlingMode.DYNAMIC_CONTINUITY;
            saveString(KEY_SCENE_HANDLING_MODE, selected.wireName());
        });
        refreshSceneHandlingControls();
    }

    private void configureDynamicMtControls() {
        findViewById(R.id.settings_dynamic_mt).setOnClickListener(ignored -> {
            View form = getLayoutInflater().inflate(R.layout.dialog_dynamic_mt, null);
            int frameWidth=getIntent().getIntExtra(EXTRA_MT_FRAME_WIDTH,0),frameHeight=getIntent().getIntExtra(EXTRA_MT_FRAME_HEIGHT,0);
            int[] ids = {R.id.dynamic_mt_enter_width, R.id.dynamic_mt_enter_height,
                    R.id.dynamic_mt_keep_width, R.id.dynamic_mt_keep_height, R.id.dynamic_mt_primary_top};
            com.example.alpr_v1.acquisition.DynamicMtConfig config =
                    com.example.alpr_v1.acquisition.DynamicMtSettings.read(preferences);
            TextView frameInfo=form.findViewById(R.id.dynamic_mt_frame_info);
            boolean knownFrame=frameWidth>0&&frameHeight>0;
            frameInfo.setText(knownFrame?getString(R.string.dynamic_mt_frame_size,frameWidth,frameHeight):getString(R.string.dynamic_mt_frame_unknown));
            if(knownFrame&&!config.size.fitsFrame(frameWidth,frameHeight))
                frameInfo.append("\n"+getString(R.string.dynamic_mt_frame_invalid,frameWidth,frameHeight));
            int[] values = {config.size.enterWidth, config.size.enterHeight,
                    config.size.keepWidth, config.size.keepHeight, Math.round(config.primaryTopFraction * 100f)};
            for (int i = 0; i < ids.length; i++) ((android.widget.EditText) form.findViewById(ids[i])).setText(Integer.toString(values[i]));
            androidx.appcompat.app.AlertDialog dialog = new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
                    .setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_dynamic_mt_dialog))
                    .setTitle(R.string.settings_dynamic_mt_title).setView(form)
                    .setNegativeButton(R.string.menu_close, null)
                    .setNeutralButton(R.string.settings_dynamic_mt_defaults, null)
                    .setPositiveButton(R.string.settings_dynamic_mt_save, null).create();
            dialog.setOnShowListener(shown -> {
                dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener(button -> {
                    int[] defaults = {120, 80, 100, 64, 35};
                    for (int i = 0; i < ids.length; i++) ((android.widget.EditText) form.findViewById(ids[i])).setText(Integer.toString(defaults[i]));
                });
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(button -> {
                    try {
                        int[] entered = new int[ids.length];
                        for (int i = 0; i < ids.length; i++) entered[i] = Integer.parseInt(((android.widget.EditText) form.findViewById(ids[i])).getText().toString());
                        com.example.alpr_v1.acquisition.DynamicMtConfig next = new com.example.alpr_v1.acquisition.DynamicMtConfig(
                                new com.example.alpr_v1.acquisition.DynamicVehicleSizeGate.Config(entered[0], entered[1], entered[2], entered[3]),
                                entered[4] / 100f);
                        if(knownFrame&&!next.size.fitsFrame(frameWidth,frameHeight)) {
                            ((android.widget.EditText)form.findViewById(ids[entered[0]>frameWidth?0:1]))
                                    .setError(getString(R.string.dynamic_mt_frame_invalid,frameWidth,frameHeight));return;
                        }
                        com.example.alpr_v1.acquisition.DynamicMtSettings.save(preferences, next);
                        markChanged();
                        dialog.dismiss();
                    } catch (IllegalArgumentException invalid) {
                        ((android.widget.EditText) form.findViewById(ids[0])).setError(getString(R.string.settings_dynamic_mt_invalid));
                    }
                });
            });
            dialog.show();
        });
    }

    private void refreshSceneHandlingControls() {
        MaterialButtonToggleGroup group = findViewById(
                R.id.settings_scene_handling_group
        );
        TextView summary = findViewById(R.id.settings_scene_handling_summary);
        boolean experimentEnabled = preferences.getBoolean(
                KEY_EXPERIMENT_MODE_ENABLED,
                false
        );

        setSceneHandlingControlsEnabled(group, true);
        summary.setText(experimentEnabled
                ? R.string.settings_scene_mode_experiment_locked
                : R.string.settings_scene_mode_description);
    }

    private static void setSceneHandlingControlsEnabled(
            MaterialButtonToggleGroup group,
            boolean enabled
    ) {
        for (int index = 0; index < group.getChildCount(); index++) {
            group.getChildAt(index).setEnabled(enabled);
        }
    }

    private void configurePipelineControls() {

        vehicleBadge.setOnClickListener(view -> {
            /*
             * W trybie eksperymentalnym normalna konfiguracja jest
             * tylko do odczytu.
             */
            if (preferences.getBoolean(
                    KEY_EXPERIMENT_MODE_ENABLED,
                    false
            )) {
                refreshNodeBadges();
                return;
            }

            boolean enabled =
                    vehicleBadge.isChecked();

            preferences.edit()
                    .putBoolean(
                            "vehicle_cascade_enabled",
                            enabled
                    )
                    .apply();

            markChanged();
            refreshNodeBadges();
        });

        importModelButton.setOnClickListener(view -> launchModelImport(null));
        importPackageButton.setOnClickListener(view -> launchPackageImport());
        vehicleNode.setOnClickListener(view -> showNodeVariant(ModelRole.VEHICLE));
        plateNode.setOnClickListener(view -> showNodeVariant(ModelRole.PLATE));
        characterNode.setOnClickListener(view -> showNodeVariant(ModelRole.CHARACTER));
        restoreBaseButton.setOnClickListener(view -> restoreBaseComposition());
    }

    private void launchModelImport(ModelRole expectedRole) {
        pendingImportRole = expectedRole;
        pendingImportContent = AlprPackageImporter.ExpectedContent.SINGLE_MODEL;
        launchImportPicker();
    }

    private void launchPackageImport() {
        pendingImportRole = null;
        pendingImportContent = AlprPackageImporter.ExpectedContent.COMPLETE_PACKAGE;
        launchImportPicker();
    }

    private void launchImportPicker() {
        modelPicker.launch(new String[]{
                "application/zip", "application/octet-stream", "application/x-zip-compressed"
        });
    }

    private void showNodeActions(ModelRole role) {
        CharSequence[] actions = new CharSequence[]{
                getString(R.string.settings_node_choose_model),
                getString(R.string.settings_node_choose_variant),
                getString(R.string.settings_node_import_model, roleLabel(role))
        };
        trackReadinessDialog(new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
                .setTitle(getString(R.string.settings_node_actions_title, roleLabel(role)))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showCompositionModel(role);
                    } else if (which == 1) {
                        modelRegistry.reload();
                        InstalledModel model = modelRegistry.getActive(role);
                        if (model == null) {
                            Toast.makeText(this, getString(
                                    R.string.settings_no_active_model, roleLabel(role)
                            ), Toast.LENGTH_LONG).show();
                        } else {
                            showVariantSelection(role, model);
                        }
                    } else {
                        launchModelImport(role);
                    }
                })
                .setNegativeButton(R.string.menu_close, null)
                .show());
    }

    private void showNodeVariant(ModelRole role) {
        modelRegistry.reload();
        InstalledModel model = modelRegistry.getActive(role);
        if (model == null) showNodeActions(role);
        else showVariantSelection(role, model);
    }

    private void showCompositionModel(ModelRole role) {
        modelRegistry.reload();
        List<InstalledModel> models = modelRegistry.getInstalled(role);
        if (models.isEmpty()) {
            Toast.makeText(
                    this,
                    getString(R.string.settings_no_models_for_role, roleLabel(role)),
                    Toast.LENGTH_LONG
            ).show();
            return;
        }
        int offset = role == ModelRole.VEHICLE ? 1 : 0;
        CharSequence[] labels = new CharSequence[models.size() + offset];
        if (offset == 1) labels[0] = getString(R.string.settings_vehicle_none);
        InstalledModel active = modelRegistry.getActive(role);
        int selected = offset == 1 && active == null ? 0 : -1;
        for (int index = 0; index < models.size(); index++) {
            InstalledModel model = models.get(index);
            labels[index + offset] = model.manifest().name() + " · "
                    + model.manifest().modelId();
            if (!ModelRegistry.isExecutableForInspection(model)) {
                labels[index + offset] = labels[index + offset] + " · brak runtime'u";
            }
            if (active != null && active.storageId().equals(model.storageId())) {
                selected = index + offset;
            }
        }
        final int[] choice = {Math.max(0, selected)};
        trackReadinessDialog(new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
                .setTitle(getString(R.string.settings_select_model_title, roleLabel(role)))
                .setSingleChoiceItems(labels, choice[0], (dialog, which) -> choice[0] = which)
                .setPositiveButton(R.string.settings_apply, (dialog, ignored) -> {
                    if (role == ModelRole.VEHICLE && choice[0] == 0) {
                        InstalledModel previous = modelRegistry.getActive(ModelRole.VEHICLE);
                        autoTuneManager.clearPinnedVariant(previous);
                        modelRegistry.deactivateVehicle();
                        setVehicleCascadeEnabled(false);
                        markChanged();
                        refreshModelStatus();
                        return;
                    }
                    InstalledModel selectedModel = models.get(choice[0] - offset);
                    if (!ModelRegistry.isExecutableForInspection(selectedModel)) {
                        Toast.makeText(
                                this,
                                R.string.settings_model_not_executable,
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }
                    boolean changed = false;
                    if (active == null || !active.storageId().equals(selectedModel.storageId())) {
                        modelRegistry.activate(selectedModel);
                        changed = true;
                    }
                    if (role == ModelRole.VEHICLE
                            && !preferences.getBoolean("vehicle_cascade_enabled", false)) {
                        setVehicleCascadeEnabled(true);
                        changed = true;
                    }
                    if (changed) {
                        markChanged();
                        refreshModelStatus();
                    }
                    showVariantSelection(role, selectedModel);
                })
                .setNegativeButton(R.string.menu_close, null)
                .show());
    }

    private void showVariantSelection(ModelRole role, InstalledModel model) {
        // AUTO wariantu jest niedozwolone w trybie eksperymentalnym.
        final boolean researchMode = preferences.getBoolean(
                KEY_EXPERIMENT_MODE_ENABLED, false);
        List<ModelVariant> variants = model.manifest().variants();
        CharSequence[] labels = new CharSequence[variants.size() + 1];
        labels[0] = "Auto — wariant wybierany automatycznie";
        if (researchMode) {
            labels[0] = labels[0] + " · niedostępne w trybie eksperymentalnym";
        }
        String pinned = autoTuneManager.storedVariantId(model);
        // Brak przypięcia w EXP oznacza brak wyboru, a nie domyślne AUTO.
        int selected = researchMode ? -1 : 0;
        for (int index = 0; index < variants.size(); index++) {
            ModelVariant variant = variants.get(index);
            com.example.alpr_v1.model.ModelInputSpec input = variant.input(model.manifest().input());
            String label = ModelStatusFormatter.variantLabel(variant) + " · " + input.width() + "×" + input.height()
                    + "\n" + variant.id();
            if (Boolean.FALSE.equals(RuntimeBackendFactory.observedAvailability(variant.runtime()))) {
                label += " · " + getString(
                        R.string.settings_variant_unavailable,
                        "Backend jest niedostępny; wybierz inny wariant"
                );
            }
            labels[index + 1] = label;
            if (variant.id().equals(pinned)) selected = index + 1;
        }
        final int[] choice = {selected};
        final com.example.alpr_v1.autotune.NormalExecutionSettings normal = new com.example.alpr_v1.autotune.NormalExecutionSettings(this);
        final android.widget.Spinner cpu = new android.widget.Spinner(this);
        cpu.setTag("normal_cpu_threads");
        java.util.List<String> cpuLabels = new java.util.ArrayList<>();
        cpuLabels.add("Auto · CPU/GPU i wątki");
        for (int n = 1; n <= com.example.alpr_v1.autotune.NormalExecutionSettings.maximumCpuThreads(); n++)
            cpuLabels.add("CPU: " + com.example.alpr_v1.autotune.NormalExecutionSettings.threadLabel(n));
        cpu.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, cpuLabels) {
            private View style(View row) {
                TextView label = (TextView) row;
                label.setTextColor(getColor(R.color.alpr_text_primary));
                label.setBackgroundColor(getColor(R.color.alpr_card));
                label.setTextSize(14); label.setSingleLine(false); label.setMaxLines(3);
                label.setMinHeight(Math.round(48 * getResources().getDisplayMetrics().density));
                return row;
            }
            @Override public View getView(int position, View recycled, ViewGroup parent) { return style(super.getView(position,recycled,parent)); }
            @Override public View getDropDownView(int position, View recycled, ViewGroup parent) { return style(super.getDropDownView(position,recycled,parent)); }
        });
        int requested = normal.requestedThreads(model);
        final int maximum = com.example.alpr_v1.autotune.NormalExecutionSettings.maximumCpuThreads();
        if (requested < 0 || requested > maximum) {
            cpuLabels.add("Zapisano CPU: " + requested + " — wybierz dostępną wartość");
            ((ArrayAdapter<?>)cpu.getAdapter()).notifyDataSetChanged();
            cpu.setSelection(cpuLabels.size() - 1);
        } else cpu.setSelection(requested);
        android.widget.LinearLayout execution = new android.widget.LinearLayout(this);
        execution.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        execution.setPadding(padding, padding / 2, padding, padding / 2);
        TextView heading = new TextView(this); heading.setText("Wątki CPU");
        heading.setTextColor(getColor(R.color.alpr_text_primary)); heading.setTextSize(16); execution.addView(heading);
        execution.addView(cpu, new android.widget.LinearLayout.LayoutParams(-1, -2));
        TextView hint = new TextView(this); hint.setText("Ręczne wątki wybierają CPU. Zostanie zastosowane przy następnym uruchomieniu modelu.\nTo konfiguracja żądana; liczba pracujących wątków nie jest zmierzona.");
        hint.setTextSize(13); hint.setTextColor(getColor(R.color.alpr_text_muted)); execution.addView(hint);
        android.widget.Button modelAction = new android.widget.Button(this);
        modelAction.setText("Model / import");
        execution.addView(modelAction);
        execution.setVisibility(researchMode ? View.GONE : View.VISIBLE);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
                this, R.layout.item_model_variant_choice, labels
        ) {
            @Override public boolean areAllItemsEnabled() { return false; }

            @Override public boolean isEnabled(int position) {
                return position == 0
                        ? !researchMode
                        : !Boolean.FALSE.equals(RuntimeBackendFactory.observedAvailability(
                                variants.get(position - 1).runtime()));
            }

            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                row.setEnabled(isEnabled(position));
                row.setAlpha(isEnabled(position) ? 1f : .45f);
                return row;
            }
        };
        androidx.appcompat.app.AlertDialog variantDialog = new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
                .setTitle(researchMode ? getString(R.string.settings_select_variant_title, roleLabel(role)) : "Model i wykonanie " + roleLabel(role))
                .setView(researchMode ? execution : null)
                .setSingleChoiceItems(adapter, selected, (dialog, which) -> {
                    choice[0] = which;
                    ((androidx.appcompat.app.AlertDialog) dialog).getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                            .setEnabled(adapter.isEnabled(which));
                })
                .setPositiveButton(R.string.settings_apply, (dialog, ignored) -> {
                    if (researchWriteInProgress()) { configurationBlocked=true;returnBlockedReadiness();finish();return; }
                    if (!researchMode && cpu.getSelectedItemPosition() > maximum) return;
                    boolean variantChanged = !java.util.Objects.equals(pinned, choice[0] <= 0 ? "" : variants.get(choice[0] - 1).id());
                    if (choice[0] <= 0) {
                        if (researchMode) {
                            Toast.makeText(this,
                                    "W trybie eksperymentalnym wybierz jawny wariant MT/MZ (np. TFLite FP32).",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (choice[0] < 0) return;
                        if (variantChanged) autoTuneManager.clearPinnedVariant(model);
                    } else {
                        ModelVariant variant = variants.get(choice[0] - 1);
                        if (Boolean.FALSE.equals(RuntimeBackendFactory.observedAvailability(variant.runtime()))) {
                            Toast.makeText(
                                    this,
                                    "Backend jest niedostępny; wybierz inny wariant",
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }
                        if (variantChanged) autoTuneManager.pinInspectedVariant(model, variant.id());
                    }
                    if (!researchMode) normal.save(model, cpu.getSelectedItemPosition());
                    if (variantChanged) markChanged();
                    refreshModelStatus();
                    if (readiness != null) readiness.refresh();
                })
                .setNeutralButton(researchMode ? "Model / import" : null, (dialog, which) -> showNodeActions(role))
                .setNegativeButton(R.string.menu_close, null)
                .create();
        variantDialog.setOnShowListener(dialog -> {
            variantDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                    .setEnabled(choice[0] >= 0 && adapter.isEnabled(choice[0]));
        });
        // Keep commit/cancel outside the scrollable content, including at large system fonts.
        if (!researchMode) variantDialog.getListView().addFooterView(execution, null, false);
        modelAction.setOnClickListener(view -> { variantDialog.dismiss(); showNodeActions(role); });
        variantDialog.show();
        trackReadinessDialog(variantDialog);
    }

    private void restoreBaseComposition() {
        modelRegistry.reload();
        InstalledAlprPackage base = modelRegistry.getBasePackage();
        if (base == null) return;
        autoTuneManager.clearPinnedVariant(base.vehicleModel());
        autoTuneManager.clearPinnedVariant(base.plateModel());
        autoTuneManager.clearPinnedVariant(base.characterModel());
        modelRegistry.restoreBasePackage();
        setVehicleCascadeEnabled(
                base.vehicleModel() != null
                        && ModelRegistry.isExecutableForInspection(base.vehicleModel())
        );
        markChanged();
        refreshModelStatus();
        Toast.makeText(this, R.string.settings_composition_restored, Toast.LENGTH_LONG).show();
    }

    private static String roleLabel(ModelRole role) {
        return role == ModelRole.VEHICLE ? "MP"
                : role == ModelRole.PLATE ? "MT" : "MZ";
    }

    private void importSelectedFile(
            Uri uri,
            ModelRole expectedRole,
            AlprPackageImporter.ExpectedContent expectedContent
    ) {
        setBusy(true);
        AppLog.info(this, LOG_TAG, "Rozpoczęto import modelu mobilnego lub pakietu ALPR");
        backgroundExecutor.execute(() -> {
            try {
                ModelImportResult result = packageImporter.importPackage(uri, expectedContent);
                String name;
                String successMessage;
                if (expectedRole != null) {
                    InstalledModel model = result.singleModel();
                    if (model.manifest().role() != expectedRole) {
                        throw new IllegalArgumentException(getString(
                                R.string.settings_node_role_mismatch,
                                roleLabel(model.manifest().role()),
                                roleLabel(expectedRole)
                        ));
                    }
                    if (!ModelRegistry.isExecutableForInspection(model)) {
                        modelRegistry.reload();
                        throw new IllegalArgumentException(getString(
                                R.string.settings_model_not_executable
                        ));
                    }
                    modelRegistry.activate(model);
                    if (expectedRole == ModelRole.VEHICLE) {
                        setVehicleCascadeEnabled(true);
                    }
                    name = model.manifest().name();
                    successMessage = singleModelImportMessage(model, name);
                } else if (result.isCompletePackage()) {
                    InstalledAlprPackage completePackage = result.completePackage();
                    modelRegistry.activate(completePackage);
                    setVehicleCascadeEnabled(
                            completePackage.vehicleModel() != null
                                    && ModelRegistry.isExecutableForInspection(completePackage.vehicleModel())
                    );
                    name = completePackage.manifest().name();
                    int message = completePackage.vehicleModel() != null
                            && !ModelRegistry.isExecutableForInspection(completePackage.vehicleModel())
                            ? R.string.settings_import_complete_partial_runtime
                            : R.string.settings_import_complete;
                    successMessage = getString(message, name);
                } else {
                    InstalledModel model = result.singleModel();
                    if (ModelRegistry.isExecutableForInspection(model)) {
                        modelRegistry.activate(model);
                        if (model.manifest().role() == ModelRole.VEHICLE) {
                            setVehicleCascadeEnabled(true);
                        }
                    } else {
                        modelRegistry.reload();
                    }
                    name = model.manifest().name();
                    successMessage = singleModelImportMessage(model, name);
                }
                markChanged();
                AppLog.info(
                        this,
                        LOG_TAG,
                        (result.isCompletePackage()
                                ? "Zaimportowano pakiet ALPR: "
                                : "Zaimportowano model mobilny: ") + name
                );
                runOnUiThread(() -> {
                    setBusy(false);
                    refreshModelStatus();
                    Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                AppLog.error(this, LOG_TAG, "Import odrzucony: " + error.getMessage(), error);
                boolean pinnedResearchCandidate = BuildConfig.DEBUG
                        && expectedContent == AlprPackageImporter.ExpectedContent.SINGLE_MODEL
                        && (expectedRole == null || expectedRole == ModelRole.CHARACTER)
                        && error.getMessage() != null
                        && error.getMessage().contains("MZ_CLEAN_INT8_RESEARCH_IMPORT_NOT_APPROVED");
                runOnUiThread(() -> {
                    setBusy(false);
                    if (pinnedResearchCandidate) {
                        // Nothing is installed unless the operator explicitly agrees.
                        new com.example.alpr_v1.ui.ImmersiveDialogBuilder(this)
                                .setTitle("MZ-DAY INT8 — wyłącznie badawczo")
                                .setMessage("Ten pakiet ma status EXPLORATORY_ONLY. "
                                        + "Możesz go zainstalować jedynie w wersji DEBUG "
                                        + "i uruchamiać na CPU dla badań F4. "
                                        + "Plik musi mieć dokładnie przypięty SHA-256. "
                                        + "Nie oznacza to zatwierdzenia produkcyjnego, "
                                        + "potwierdzenia kalibracji ani final_test. "
                                        + "Czy świadomie zezwalasz na taki import?")
                                .setNegativeButton("Anuluj", null)
                                .setPositiveButton("Importuj badawczo", (dialog, which) ->
                                        importPinnedResearchMz(uri))
                                .show();
                    } else {
                        Toast.makeText(
                                this,
                                getString(R.string.settings_import_rejected, error.getMessage()),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
            }
        });
    }

    private void importPinnedResearchMz(Uri uri) {
        setBusy(true);
        AppLog.info(this, LOG_TAG, "MZ-DAY INT8: jawny import badawczy DEBUG/CPU");
        backgroundExecutor.execute(() -> {
            try {
                ModelImportResult result = packageImporter.importPinnedResearchMz(uri);
                InstalledModel model = result.singleModel();
                if (model == null || model.manifest().role() != ModelRole.CHARACTER
                        || !ModelRegistry.isExecutableForInspection(model)) {
                    throw new IllegalArgumentException("MZ_PINNED_RESEARCH_MODEL_NOT_EXECUTABLE");
                }
                modelRegistry.activate(model);
                markChanged();
                AppLog.info(this, LOG_TAG,
                        "Import badawczy MZ-DAY INT8 (EXPLORATORY_ONLY): "
                                + model.manifest().modelId());
                runOnUiThread(() -> {
                    setBusy(false);
                    refreshModelStatus();
                    Toast.makeText(this,
                            "MZ-DAY INT8 aktywny: DEBUG/CPU, RESEARCH_ONLY. "
                                    + "Nie używaj do final_test.",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                AppLog.error(this, LOG_TAG,
                        "Import badawczy MZ-DAY INT8 odrzucony: " + error.getMessage(), error);
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this,
                            "Import badawczy odrzucony: " + error.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String singleModelImportMessage(InstalledModel model, String name) {
        if (!ModelRegistry.isExecutableForInspection(model)) {
            return getString(
                    R.string.settings_import_model_stored,
                    roleLabel(model.manifest().role()),
                    name
            );
        }
        if (modelRegistry.hasCompleteAlprComposition()) {
            return getString(
                    R.string.settings_import_model_ready,
                    roleLabel(model.manifest().role()),
                    name
            );
        }
        boolean missingPlate = modelRegistry.getActive(ModelRole.PLATE) == null;
        boolean missingCharacter = modelRegistry.getActive(ModelRole.CHARACTER) == null;
        int message = missingPlate && missingCharacter
                ? R.string.settings_import_model_missing_both
                : missingPlate
                ? R.string.settings_import_model_missing_mt
                : R.string.settings_import_model_missing_mz;
        return getString(message, roleLabel(model.manifest().role()), name);
    }

    private static InstalledModel modelForRole(
            InstalledAlprPackage completePackage,
            ModelRole role
    ) {
        return role == ModelRole.VEHICLE
                ? completePackage.vehicleModel()
                : role == ModelRole.PLATE
                ? completePackage.plateModel()
                : completePackage.characterModel();
    }

    private void refreshModelStatus() {
        modelRegistry.reload();
        ModelStatusFormatter.Presentation status =
                ModelStatusFormatter.presentation(modelRegistry, autoTuneManager);
        modelStatusSummary.setText(status.summary);
        vehicleModelStatus.setText(status.vehicle);
        plateModelStatus.setText(status.plate);
        characterModelStatus.setText(status.character);
        if (preferences.getBoolean(KEY_EXPERIMENT_MODE_ENABLED, false)) {
            vehicleModelStatus.setText(researchModelDescription(ModelRole.VEHICLE));
            plateModelStatus.setText(researchModelDescription(ModelRole.PLATE));
            characterModelStatus.setText(researchModelDescription(ModelRole.CHARACTER));
        }
        modelStatusSummary.setTextColor(getColor(
                modelRegistry.hasCompleteAlprComposition()
                        ? R.color.alpr_success
                        : R.color.alpr_warning
        ));
        vehicleModelStatus.setTextColor(getColor(
                modelRegistry.getActive(ModelRole.VEHICLE) == null
                        ? R.color.alpr_text_muted
                        : R.color.alpr_text_secondary
        ));
        plateModelStatus.setTextColor(getColor(
                modelRegistry.getActive(ModelRole.PLATE) == null
                        ? R.color.alpr_warning
                        : R.color.alpr_text_secondary
        ));
        characterModelStatus.setTextColor(getColor(
                modelRegistry.getActive(ModelRole.CHARACTER) == null
                        ? R.color.alpr_warning
                        : R.color.alpr_text_secondary
        ));
        updateNode(vehicleNode, ModelRole.VEHICLE);
        updateNode(plateNode, ModelRole.PLATE);
        updateNode(characterNode, ModelRole.CHARACTER);
        refreshNodeBadges();
        restoreBaseButton.setEnabled(
                modelRegistry.getBasePackage() != null
                        && (modelRegistry.canRestoreBaseModels() || hasPinnedVariant())
        );
        refreshResearchExecutionControls();
        refreshResearchSummary();
    }

    private void refreshNodeBadges() {
        boolean hasVehicle =
                modelRegistry.getActive(ModelRole.VEHICLE) != null;

        boolean hasPlate =
                modelRegistry.getActive(ModelRole.PLATE) != null;

        boolean hasCharacter =
                modelRegistry.getActive(ModelRole.CHARACTER) != null;


        /*
         * NORMALNA konfiguracja użytkownika.
         * Tryb eksperymentalny nigdy jej nie zmienia.
         */
        boolean normalVehicleEnabled =
                hasVehicle
                        && preferences.getBoolean(
                        "vehicle_cascade_enabled",
                        false
                );


        /*
         * Oddzielny stan eksperymentu.
         */
        boolean experimentEnabled =
                preferences.getBoolean(
                        KEY_EXPERIMENT_MODE_ENABLED,
                        false
                );

        RoiBudgetPolicy experimentPolicy =
                RoiBudgetPolicy.fromWireName(
                        preferences.getString(
                                KEY_EXPERIMENT_ROI_POLICY,
                                preferences.getString("roi_budget_policy", RoiBudgetPolicy.TWO_ROI.wireName())
                        )
                );


        /*
         * Badge pokazuje stan EFEKTYWNIE wykonywany przez pipeline.
         *
         * EXP OFF:
         *   pokazujemy normalną konfigurację.
         *
         * EXP ON:
         *   R0 -> MP WYŁ.
         *   R1 -> MP WŁ.
         *   R2 -> MP WŁ.
         *
         * Normalna wartość vehicle_cascade_enabled nie jest zmieniana.
         */
        boolean effectiveVehicleEnabled =
                experimentEnabled
                        ? hasVehicle
                          && experimentPolicy.usesVehicleCascade()
                        : normalVehicleEnabled;


        updateStageBadge(
                vehicleBadge,
                ModelRole.VEHICLE,
                hasVehicle,
                effectiveVehicleEnabled,
                !experimentEnabled
        );
        boolean mpNotRequested = experimentEnabled ? !experimentPolicy.usesVehicleCascade()
                : !preferences.getBoolean("vehicle_cascade_enabled",false);
        if (!hasVehicle && mpNotRequested) {
            vehicleBadge.setText(R.string.settings_node_badge_disabled);
            vehicleBadge.setContentDescription("MP: wyłączony, niewymagany w tym trybie");
        }

        updateStageBadge(
                plateBadge,
                ModelRole.PLATE,
                hasPlate,
                hasPlate,
                false
        );

        updateStageBadge(
                characterBadge,
                ModelRole.CHARACTER,
                hasCharacter,
                hasCharacter,
                false
        );
    }

    private void updateStageBadge(
            Chip badge,
            ModelRole role,
            boolean available,
            boolean enabled,
            boolean userToggleable
    ) {
        badge.setChecked(enabled);
        badge.setEnabled(available);
        badge.setClickable(userToggleable && available);
        badge.setFocusable(userToggleable && available);
        badge.setText(!available
                ? R.string.settings_node_badge_missing
                : enabled
                ? R.string.settings_node_badge_enabled
                : R.string.settings_node_badge_disabled);
        badge.setContentDescription(getString(
                R.string.settings_node_badge_description,
                roleLabel(role),
                !available
                        ? getString(R.string.settings_node_badge_state_missing)
                        : enabled
                        ? getString(R.string.settings_node_badge_state_enabled)
                        : getString(R.string.settings_node_badge_state_disabled)
        ));
    }

    private void setVehicleCascadeEnabled(boolean enabled) {
        preferences.edit().putBoolean("vehicle_cascade_enabled", enabled).apply();
    }

    private void updateNode(MaterialButton button, ModelRole role) {
        button.setMaxLines(Integer.MAX_VALUE);
        button.setEllipsize(null);
        String roleName = roleLabel(role);
        InstalledModel model = modelRegistry.getActive(role);
        boolean researchMode = preferences.getBoolean(KEY_EXPERIMENT_MODE_ENABLED,false);
        boolean disabledMp = role == ModelRole.VEHICLE && (researchMode
                ? !RoiBudgetPolicy.fromWireName(preferences.getString(KEY_EXPERIMENT_ROI_POLICY, preferences.getString("roi_budget_policy", RoiBudgetPolicy.TWO_ROI.wireName()))).usesVehicleCascade()
                : !preferences.getBoolean("vehicle_cascade_enabled",false));
        if (disabledMp && model == null) {
            button.setText(roleName + "\nWyłączony" + (researchMode ? " przez R0" : " · opcjonalny"));
            button.setContentDescription(button.getText()); return;
        }
        if (model == null) {
            InstalledAlprPackage base = modelRegistry.getBasePackage();
            InstalledModel baseModel = base == null ? null : modelForRole(base, role);
            if (baseModel != null) {
                String runtime = baseModel.manifest().variants().isEmpty()
                        ? "--"
                        : baseModel.manifest().variants().get(0).runtime().wireName()
                        .toUpperCase(java.util.Locale.ROOT);
                button.setText(getString(
                        R.string.settings_node_inactive_lines,
                        roleName,
                        runtime
                ));
                button.setContentDescription(roleName + ": "
                        + baseModel.manifest().modelId() + ", " + runtime
                        + ", " + getString(R.string.settings_node_inactive));
            } else {
                button.setText(getString(
                        R.string.settings_node_missing_lines,
                        roleName
                ));
                button.setContentDescription(roleName + ": "
                        + getString(R.string.settings_node_missing));
            }
            return;
        }
        boolean research = preferences.getBoolean(KEY_EXPERIMENT_MODE_ENABLED, false);
        boolean enabled = role != ModelRole.VEHICLE || (research
                ? RoiBudgetPolicy.fromWireName(preferences.getString(KEY_EXPERIMENT_ROI_POLICY, preferences.getString("roi_budget_policy", RoiBudgetPolicy.TWO_ROI.wireName()))).usesVehicleCascade()
                : preferences.getBoolean("vehicle_cascade_enabled", false));
        String value;
        if (research) {
            ModelVariant variant = autoTuneManager.inspectedVariant(model);
            String key = role == ModelRole.VEHICLE ? KEY_RESEARCH_MP_EXECUTION : role == ModelRole.PLATE ? KEY_RESEARCH_MT_EXECUTION : KEY_RESEARCH_MZ_EXECUTION;
            ResearchExecutionChoice executionChoice;
            try { executionChoice = ResearchExecutionChoice.fromWireName(preferences.getString(key, "auto")); }
            catch (IllegalArgumentException invalid) { executionChoice = ResearchExecutionChoice.AUTO; }
            value = roleName + " · " + (variant == null ? "Wariant do sprawdzenia" : ModelStatusFormatter.variantLabel(variant)) + "\n" + executionChoice.label();
        }
        else {
            ModelVariant variant = autoTuneManager.inspectedVariant(model);
            value = roleName + " · " + (variant == null ? "Auto — wariant do sprawdzenia" : ModelStatusFormatter.variantLabel(variant))
                    + "\n" + autoTuneManager.normalExecutionLabel(model, variant);
        }
        if (!enabled) value += "\nWyłączony";
        button.setText(value);
        button.setContentDescription(value + ". Model i wykonanie: " + model.manifest().modelId());
    }

    private boolean hasPinnedVariant() {
        return !autoTuneManager.storedVariantId(modelRegistry.getActive(ModelRole.VEHICLE)).isEmpty()
                || !autoTuneManager.storedVariantId(modelRegistry.getActive(ModelRole.PLATE)).isEmpty()
                || !autoTuneManager.storedVariantId(modelRegistry.getActive(ModelRole.CHARACTER)).isEmpty();
    }

    private void refreshStoragePath() {
        storagePath.setText(getString(
                R.string.settings_storage_selected,
                CaptureDirectoryStore.RELATIVE_PATH
        ));
        refreshOptionsLayout();
    }

    private void saveString(String key, String value) {
        if (value.equals(preferences.getString(key, ""))) return;
        preferences.edit().putString(key, value).apply();
        markChanged();
    }

    private ReadinessSnapshot.IdentityDraft identityDraft(){
        return new ReadinessSnapshot.IdentityDraft(fieldText(experimentSeriesId),fieldText(experimentScenarioId),
                fieldText(experimentReplicateIndex),fieldText(experimentNotes),fieldText(experimentVariant),defaultExperimentSeriesId());
    }
    private static String fieldText(TextView field){return field==null||field.getText()==null?"":field.getText().toString();}
    private ReadinessSnapshot effectiveReadiness(long ticket){
        boolean permission=androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED;
        return ReadinessSnapshot.capture(preferences,modelRegistry,autoTuneManager,identityDraft(),
                com.example.alpr_v1.experiment.ResearchSessionStore.sessionsRoot(this),ReadinessSnapshot.Device.options(permission,configurationBlocked,
                        getIntent().getBooleanExtra(EXTRA_THERMAL_ENABLED,false)),ticket);
    }
    private void navigateReadiness(ReadinessFinding clicked){
        ReadinessResult current=readiness==null?null:readiness.current();
        ReadinessFinding finding=current==null?null:current.finding(clicked.issueId);
        if(finding==null||finding.target==null||!finding.snapshotIdentity.equals(clicked.snapshotIdentity))return;
        if(importInProgress){Toast.makeText(this,R.string.settings_import_in_progress_wait,Toast.LENGTH_LONG).show();return;}
        if(researchWriteInProgress()){configurationBlocked=true;returnBlockedReadiness();finish();return;}
        ReadinessTarget target=finding.target;
        readinessNavigation=new ReadinessReturnContext(finding.issueId,target.sectionId,target.controlId,
                ((androidx.core.widget.NestedScrollView)findViewById(R.id.settings_scroll)).getScrollY());
        ((MaterialToolbar)findViewById(R.id.settings_toolbar)).getMenu().findItem(R.id.readiness_back_summary).setVisible(true);
        if(target.action==ReadinessTarget.Action.TV_CORNERS||target.action==ReadinessTarget.Action.PHOTO_AREA){
            requestTvSetup(target.action==ReadinessTarget.Action.TV_CORNERS?com.example.alpr_v1.ui.TvSetupController.Mode.CALIBRATION:com.example.alpr_v1.ui.TvSetupController.Mode.F6_PHOTO);return;
        }
        if(target.action==ReadinessTarget.Action.PREVIEW||target.action==ReadinessTarget.Action.FOCUS){
            setResult(RESULT_OK,new android.content.Intent().putExtra(ReadinessReturnContext.EXTRA,readinessNavigation.toBundle())
                    .putExtra(ReadinessReturnContext.EXTRA_ACTION,target.action.name()));finish();return;
        }
        optionsSections.revealControl(target.sectionId,target.controlId,()->{
            switch(target.action){
                case MODEL:
                    if(modelRegistry.getInstalled(target.role).isEmpty())launchModelImport(target.role);
                    else showCompositionModel(target.role);break;
                case VARIANT:showNodeVariant(target.role);break;
                case EXECUTION:
                    MaterialAutoCompleteTextView dropdown=findViewById(target.controlId);
                    dropdown.setOnDismissListener(()->{dropdown.setOnDismissListener(null);if(readinessNavigation!=null)returnToReadinessSummary();});
                    dropdown.showDropDown();break;
                case PACKAGES:launchPackageImport();break;
                default:break;
            }
        });
    }
    private void trackReadinessDialog(androidx.appcompat.app.AlertDialog dialog){
        if(readinessNavigation==null)return;
        readinessDialogs++;
        dialog.setOnDismissListener(ignored->{
            readinessDialogs=Math.max(0,readinessDialogs-1);
            findViewById(R.id.settings_root).post(()->{
                if(readinessDialogs==0&&readinessNavigation!=null&&!importInProgress&&pendingImportRole==null&&pendingImportContent==null)
                    returnToReadinessSummary();
            });
        });
    }
    private void returnToReadinessSummary(){
        readinessNavigation=null;
        if(optionsSections==null)return;
        View focus=getCurrentFocus();if(focus!=null){
            android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(focus.getWindowToken(),0);
        }
        ((MaterialToolbar)findViewById(R.id.settings_toolbar)).getMenu().findItem(R.id.readiness_back_summary).setVisible(false);
        optionsSections.showSummary();if(readiness!=null)readiness.refresh(true);
    }
    private void returnBlockedReadiness(){
        ReadinessReturnContext context=readinessNavigation;
        if(context==null)context=ReadinessReturnContext.fromBundle(getIntent().getBundleExtra(ReadinessReturnContext.EXTRA));
        if(context==null)context=new ReadinessReturnContext("configuration_locked",-1,0,0);
        setResult(RESULT_CANCELED,new android.content.Intent().putExtra(ReadinessReturnContext.EXTRA,context.toBundle())
                .putExtra(ReadinessReturnContext.EXTRA_ACTION,"BLOCKED"));
    }
    private void refreshResearchSummary(){
        TextView summary=findViewById(R.id.settings_research_configuration_summary);
        if(summary==null||preferences==null||modelRegistry==null||autoTuneManager==null)return;
        summary.setText(effectiveReadiness(0).goal());
        refreshOptionsLayout();
    }
    private void refreshOptionsLayout(){if(optionsSections!=null)optionsSections.refresh();}
    private boolean researchWriteInProgress(){return com.example.alpr_v1.experiment.ResearchOperationState.hasActiveWriteWork(new java.io.File(getFilesDir(),"research/operations"));}
    @Override protected void onResume(){
        super.onResume();
        if(researchWriteInProgress()){
            configurationBlocked=true;
            Toast.makeText(this,R.string.main_session_work_blocked,Toast.LENGTH_LONG).show();returnBlockedReadiness();finish();return;
        }
        if(readiness!=null)readiness.resume();
    }
    private void markChanged() {
        refreshResearchSummary();
        int revision = preferences.getInt(KEY_REVISION, 0);
        preferences.edit().putInt(KEY_REVISION, revision + 1).apply();
    }

    private void setBusy(boolean busy) {
        importInProgress = busy;
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        importModelButton.setEnabled(!busy);
        importPackageButton.setEnabled(!busy);
        if(!busy&&readinessNavigation!=null)returnToReadinessSummary();
    }

    private void requestSettingsClose() {
        if (importInProgress) {
            Toast.makeText(
                    this,
                    R.string.settings_import_in_progress_wait,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }
        if(readinessNavigation!=null){returnToReadinessSummary();return;}
        finish();
    }

    private void applySystemInsets() {
        View root=findViewById(R.id.settings_root);
        com.example.alpr_v1.ui.CameraWindowUi.install(getWindow(),root);
        View scroll=findViewById(R.id.settings_scroll);int bottom=scroll.getPaddingBottom();
        com.example.alpr_v1.ui.CameraWindowUi.observeIme(root,state->{
            int extra=state.visible?Math.max(0,state.bottom-root.getPaddingBottom()):0;
            scroll.setPadding(scroll.getPaddingLeft(),scroll.getPaddingTop(),scroll.getPaddingRight(),bottom+extra);
            View focus=getCurrentFocus();if(state.visible&&focus!=null)focus.post(()->{
                if(focus==getCurrentFocus())focus.requestRectangleOnScreen(new android.graphics.Rect(0,0,focus.getWidth(),focus.getHeight()),true);
            });
        });
    }

    @Override
    protected void onPause() {
        persistExperimentIdentity();
        if(readiness!=null)readiness.suspend();
        super.onPause();
    }

    @Override protected void onStop(){
        if(optionsSections!=null)optionsSections.stop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if(optionsSections!=null)optionsSections.stop();
        if(readiness!=null)readiness.close();
        for(String name:new String[]{PREFERENCES,"autotune","model_registry"})
            getSharedPreferences(name,MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(readinessPreferences);
        // Import zapisuje duże archiwa. Natychmiastowe przerwanie executora
        // mogło pozostawić tylko pierwszy model kompletnego pakietu. Pozwalamy
        // już rozpoczętej transakcji zakończyć się również przy technicznym
        // odtworzeniu Activity.
        backgroundExecutor.shutdown();
        super.onDestroy();
    }

    private void requestTvSetup(com.example.alpr_v1.ui.TvSetupController.Mode mode) {
        if(!com.example.alpr_v1.ui.TvSetupAvailability.available(preferences.getBoolean(KEY_EXPERIMENT_MODE_ENABLED,false),preferences.getString(KEY_EXPERIMENT_TYPE,"roi_budget")))return;
        if(readinessNavigation==null&&getIntent().getBooleanExtra(EXTRA_RETURN_TO_SUMMARY,false)){
            int control=mode==com.example.alpr_v1.ui.TvSetupController.Mode.F6_PHOTO?R.id.settings_f6_photo_roi:
                    mode==com.example.alpr_v1.ui.TvSetupController.Mode.DISTANCE?R.id.settings_tv_distance:R.id.settings_tv_calibrate;
            readinessNavigation=new ReadinessReturnContext("tool_"+mode.name(),1,control,
                    ((androidx.core.widget.NestedScrollView)findViewById(R.id.settings_scroll)).getScrollY());
        }
        if(readinessNavigation!=null){
            if(importInProgress){Toast.makeText(this,R.string.settings_import_in_progress_wait,Toast.LENGTH_LONG).show();return;}
            android.content.Intent result=new android.content.Intent().putExtra(ReadinessReturnContext.EXTRA,readinessNavigation.toBundle())
                    .putExtra(ReadinessReturnContext.EXTRA_ACTION,"CALIBRATOR").putExtra(ReadinessReturnContext.EXTRA_MODE,mode.name());
            setResult(RESULT_OK,result);finish();return;
        }
        com.example.alpr_v1.ui.TvSetupNavigation.request(preferences,mode);
        requestSettingsClose();
    }
    private void refreshTvTools(){
        findViewById(R.id.settings_tv_input_container).setVisibility(preferences.getBoolean(KEY_EXPERIMENT_MODE_ENABLED,false)
                &&"roi_budget".equals(preferences.getString(KEY_EXPERIMENT_TYPE,"roi_budget"))?View.VISIBLE:View.GONE);
        findViewById(R.id.settings_tv_tools).setVisibility(
            com.example.alpr_v1.ui.TvSetupAvailability.available(preferences.getBoolean(KEY_EXPERIMENT_MODE_ENABLED,false),
                    preferences.getString(KEY_EXPERIMENT_TYPE,"roi_budget"))?View.VISIBLE:View.GONE);}

    private void configureExperimentIdentity() {
        com.google.android.material.materialswitch.MaterialSwitch f6=findViewById(R.id.settings_f6_enabled);
        refreshResearchSummary();
        f6.setChecked(preferences.getBoolean(com.example.alpr_v1.experiment.Final180PhotoRoi.ENABLED,false));
        f6.setOnCheckedChangeListener((button,checked)->{preferences.edit().putBoolean(com.example.alpr_v1.experiment.Final180PhotoRoi.ENABLED,checked).apply();markChanged();});
        findViewById(R.id.settings_f6_photo_roi).setOnClickListener(v->requestTvSetup(com.example.alpr_v1.ui.TvSetupController.Mode.F6_PHOTO));
        com.google.android.material.textfield.MaterialAutoCompleteTextView input=findViewById(R.id.settings_tv_input_mode);
        String[] labels={"Pełna klatka kamery (referencja)","Wycinek TV (BBOX) — próba nieoficjalna"};
        input.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,labels));
        input.setText(labels["TV_BBOX_CROP".equals(preferences.getString(KEY_TV_INPUT_MODE,"CAMERA_FULL_FRAME"))?1:0],false);
        input.setOnItemClickListener((parent,view,position,id)->{
            preferences.edit().putString(KEY_TV_INPUT_MODE,position==1?"TV_BBOX_CROP":"CAMERA_FULL_FRAME").apply();markChanged();
        });
        String[] typeWires = {
                "roi_budget",
                "runtime",
                "consensus",
                "continuity",
                "end_to_end"
        };
        String[] typeLabels = {
                "ROI / budżet obszarów",
                "Runtime / kwantyzacja",
                "Konsensus",
                "Ciągłość i recovery",
                "End-to-end"
        };
        String selectedType = preferences.getString(
                KEY_EXPERIMENT_TYPE,
                "roi_budget"
        );
        int selectedTypeIndex = 0;
        for (int index = 0; index < typeWires.length; index++) {
            if (typeWires[index].equals(selectedType)) selectedTypeIndex = index;
        }
        experimentType.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                typeLabels
        ));
        experimentType.setText(typeLabels[selectedTypeIndex], false);
        experimentType.setOnItemClickListener((parent, view, position, id) -> {
            preferences.edit().putString(
                    KEY_EXPERIMENT_TYPE,
                    typeWires[position]
            ).apply();
            refreshTvTools();
            markChanged();
        });
        experimentVariant.setText(preferences.getString(
                KEY_EXPERIMENT_VARIANT,
                ""
        ));
        experimentSeriesId.setText(preferences.getString(
                KEY_EXPERIMENT_SERIES_ID,
                defaultExperimentSeriesId()
        ));
        experimentScenarioId.setText(preferences.getString(
                KEY_EXPERIMENT_SCENARIO_ID,
                "live_camera"
        ));
        experimentReplicateIndex.setText(String.valueOf(preferences.getInt(
                KEY_EXPERIMENT_REPLICATE_INDEX,
                1
        )));
        experimentNotes.setText(preferences.getString(KEY_EXPERIMENT_NOTES, ""));
    }

    private void persistExperimentIdentity() {
        if(configurationBlocked||experimentSeriesId==null)return;
        if (experimentSeriesId == null) return;
        ReadinessSnapshot.IdentityDraft draft=identityDraft();
        String series=draft.series,scenario=draft.scenario,notes=draft.notes,variant=draft.variant;
        int replicate=draft.replicate;

        boolean changed = !series.equals(preferences.getString(KEY_EXPERIMENT_SERIES_ID, defaultExperimentSeriesId()))
                || !scenario.equals(preferences.getString(KEY_EXPERIMENT_SCENARIO_ID, "live_camera"))
                || replicate != preferences.getInt(KEY_EXPERIMENT_REPLICATE_INDEX, 1)
                || !notes.equals(preferences.getString(KEY_EXPERIMENT_NOTES, ""))
                || !variant.equals(preferences.getString(KEY_EXPERIMENT_VARIANT, ""));
        if (!changed) return;
        experimentSeriesId.setText(series);
        experimentScenarioId.setText(scenario);
        experimentReplicateIndex.setText(String.valueOf(replicate));
        preferences.edit()
                .putString(KEY_EXPERIMENT_SERIES_ID, series)
                .putString(KEY_EXPERIMENT_SCENARIO_ID, scenario)
                .putInt(KEY_EXPERIMENT_REPLICATE_INDEX, replicate)
                .putString(KEY_EXPERIMENT_NOTES, notes)
                .putString(KEY_EXPERIMENT_VARIANT, variant)
                .apply();
        if (changed) markChanged();
    }

    private static String normalizedText(TextInputEditText input, String fallback) {
        String value = input == null || input.getText() == null
                ? ""
                : input.getText().toString().trim();
        return value.isEmpty() ? fallback : value;
    }

    private static String defaultExperimentSeriesId() {
        return "ROI-" + new java.text.SimpleDateFormat(
                "yyyyMMdd",
                java.util.Locale.ROOT
        ).format(new java.util.Date());
    }

    private void refreshResearchExecutionControls() {
        configureResearchStageControl(
                ModelRole.VEHICLE,
                "MP",
                R.id.settings_research_mp_summary,
                R.id.settings_research_mp_execution,
                KEY_RESEARCH_MP_EXECUTION
        );
        configureResearchStageControl(
                ModelRole.PLATE,
                "MT",
                R.id.settings_research_mt_summary,
                R.id.settings_research_mt_execution,
                KEY_RESEARCH_MT_EXECUTION
        );
        configureResearchStageControl(
                ModelRole.CHARACTER,
                "MZ",
                R.id.settings_research_mz_summary,
                R.id.settings_research_mz_execution,
                KEY_RESEARCH_MZ_EXECUTION
        );
        refreshOptionsLayout();
    }

    private String researchModelDescription(ModelRole role) {
        if (role == ModelRole.VEHICLE && !RoiBudgetPolicy.fromWireName(preferences.getString(
                KEY_EXPERIMENT_ROI_POLICY, preferences.getString("roi_budget_policy", RoiBudgetPolicy.TWO_ROI.wireName()))).usesVehicleCascade())
            return getString(R.string.settings_research_stage_disabled,"MP");
        String key = role == ModelRole.VEHICLE ? KEY_RESEARCH_MP_EXECUTION
                : role == ModelRole.PLATE ? KEY_RESEARCH_MT_EXECUTION : KEY_RESEARCH_MZ_EXECUTION;
        ResearchExecutionChoice choice;
        try { choice = ResearchExecutionChoice.fromWireName(preferences.getString(key, "auto")); }
        catch (IllegalArgumentException invalid) { choice = ResearchExecutionChoice.AUTO; }
        return ModelStatusFormatter.describeResearch(modelRegistry.getActive(role), autoTuneManager, choice);
    }

    private void configureResearchStageControl(
            ModelRole role,
            String stageName,
            int summaryId,
            int dropdownId,
            String preferenceKey
    ) {
        TextView summary = findViewById(summaryId);
        MaterialAutoCompleteTextView dropdown = findViewById(dropdownId);
        dropdown.setSingleLine(false);dropdown.setMaxLines(2);
        dropdown.setTextColor(new android.content.res.ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},
                new int[]{getColor(R.color.alpr_text_muted),getColor(R.color.alpr_text_primary)}));
        RoiBudgetPolicy roiPolicy = RoiBudgetPolicy.fromWireName(
                preferences.getString(
                        KEY_EXPERIMENT_ROI_POLICY,
                        preferences.getString("roi_budget_policy", RoiBudgetPolicy.TWO_ROI.wireName())
                )
        );
        if (role == ModelRole.VEHICLE && !roiPolicy.usesVehicleCascade()) {
            summary.setText(getString(
                    R.string.settings_research_stage_disabled,
                    stageName
            ));
            dropdown.setEnabled(false);
            dropdown.setText("—", false);
            return;
        }

        InstalledModel model = modelRegistry.getActive(role);
        if (model == null) {
            summary.setText(getString(
                    R.string.settings_research_stage_missing,
                    stageName
            ));
            dropdown.setEnabled(false);
            dropdown.setText(ResearchExecutionChoice.AUTO.label(), false);
            return;
        }

        ModelVariant variant;
        try {
            variant = autoTuneManager.inspectedVariant(model);
            if (variant == null) throw new IllegalStateException("Dostępność wariantu sprawdzimy przy uruchamianiu");
        } catch (RuntimeException error) {
            summary.setText(getString(
                    R.string.settings_research_stage_missing,
                    stageName
            ));
            dropdown.setEnabled(false);
            return;
        }
        summary.setText(getString(
                R.string.settings_research_stage_summary,
                stageName,
                model.manifest().modelId(),
                variant.id(),
                variant.runtime().wireName()
        ));

        List<ResearchExecutionChoice> choices = new java.util.ArrayList<>(
                ResearchExecutionChoice.supportedFor(variant.runtime())
        );
        boolean explicitRequired=preferences.getBoolean(KEY_EXPERIMENT_MODE_ENABLED,false);
        if(explicitRequired)choices.remove(ResearchExecutionChoice.AUTO);
        ResearchExecutionChoice selected;
        try {
            selected = ResearchExecutionChoice.fromWireName(
                    preferences.getString(
                            preferenceKey,
                            ResearchExecutionChoice.AUTO.wireName()
                    )
            );
        } catch (IllegalArgumentException ignored) {
            selected = ResearchExecutionChoice.AUTO;
        }
        boolean selectedUnsupported = !choices.contains(selected);
        List<String> labels = new java.util.ArrayList<>();
        for (ResearchExecutionChoice choice : choices) labels.add(choice.label());
        dropdown.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                labels
        ){
            @Override public View getView(int position,View recycled,ViewGroup parent){
                TextView row=(TextView)super.getView(position,recycled,parent);
                row.setSingleLine(false);row.setMaxLines(2);
                row.setTextColor(getColor(R.color.alpr_text_primary));row.setBackgroundColor(getColor(R.color.alpr_card));
                row.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density));return row;
            }
        });
        dropdown.setText(
                explicitRequired&&selected==ResearchExecutionChoice.AUTO?getString(R.string.f25_explicit_execution_required):selectedUnsupported
                        ? getString(
                                R.string.research_execution_unavailable,
                                selected.label(),
                                variant.runtime().wireName()
                        )
                        : selected.label(),
                false
        );
        dropdown.setEnabled(true);
        dropdown.setOnItemClickListener((parent, view, position, id) -> {
            ResearchExecutionChoice choice = choices.get(position);
            preferences.edit().putString(
                    preferenceKey,
                    choice.wireName()
            ).apply();
            markChanged();
            refreshModelStatus();
        });
    }

    private void configureRoiBudgetControls() {
        com.google.android.material.materialswitch.MaterialSwitch experimentSwitch =
                findViewById(
                        R.id.settings_experiment_switch
                );

        View experimentOptions =
                findViewById(
                        R.id.settings_roi_experiment_options
                );

        MaterialButtonToggleGroup group =
                findViewById(
                        R.id.settings_roi_budget_group
                );

        // Read legacy policy without changing stored research data on entry.
        boolean experimentEnabled =
                preferences.getBoolean(
                        KEY_EXPERIMENT_MODE_ENABLED,
                        false
                );

        RoiBudgetPolicy selected =
                RoiBudgetPolicy.fromWireName(
                        preferences.getString(
                                KEY_EXPERIMENT_ROI_POLICY,
                                preferences.getString("roi_budget_policy", RoiBudgetPolicy.TWO_ROI.wireName())
                        )
                );

        experimentSwitch.setChecked(
                experimentEnabled
        );

        experimentOptions.setVisibility(
                experimentEnabled
                        ? View.VISIBLE
                        : View.GONE
        );

        int selectedId =
                selected == RoiBudgetPolicy.ONE_ROI
                        ? R.id.settings_roi_r1
                        : selected == RoiBudgetPolicy.TWO_ROI
                          ? R.id.settings_roi_r2
                          : R.id.settings_roi_r0;

        group.check(selectedId);

        experimentSwitch.setOnCheckedChangeListener(
                (button, enabled) -> {

                    preferences.edit()
                            .putBoolean(
                                    KEY_EXPERIMENT_MODE_ENABLED,
                                    enabled
                            )
                            .apply();

                    experimentOptions.setVisibility(
                            enabled
                                    ? View.VISIBLE
                                    : View.GONE
                    );

                    refreshTvTools();

                    markChanged();
                    refreshModelStatus();
                    refreshSceneHandlingControls();
                }
        );

        group.addOnButtonCheckedListener(
                (ignored, checkedId, isChecked) -> {
                    if (!isChecked) return;

                    RoiBudgetPolicy policy =
                            checkedId == R.id.settings_roi_r1
                                    ? RoiBudgetPolicy.ONE_ROI
                                    : checkedId == R.id.settings_roi_r2
                                      ? RoiBudgetPolicy.TWO_ROI
                                      : RoiBudgetPolicy.FULL_FRAME;

                    /*
                     * Bardzo ważne:
                     * NIE DOTYKAMY vehicle_cascade_enabled.
                     */
                    preferences.edit()
                            .putString(
                                    KEY_EXPERIMENT_ROI_POLICY,
                                    policy.wireName()
                            )
                            .apply();

                    markChanged();
                    refreshModelStatus();
                }
        );
    }
}
