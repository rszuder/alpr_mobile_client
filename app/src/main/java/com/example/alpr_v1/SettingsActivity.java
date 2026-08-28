package com.example.alpr_v1;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Size;
import android.widget.ArrayAdapter;



import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import com.example.alpr_v1.autotune.AutoTuneManager;
import com.example.alpr_v1.camera.AnalysisResolutionProfile;
import com.example.alpr_v1.capture.CropCapacityPolicy;
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
    public static final String KEY_REVISION = "settings_revision";

    public static final String KEY_ANALYSIS_RESOLUTION_SELECTION =
            "analysis_resolution_selection";

    public static final String KEY_EXPERIMENT_MODE_ENABLED =
            "experiment_mode_enabled";

    public static final String KEY_EXPERIMENT_ROI_POLICY =
            "experiment_roi_budget_policy";
    public static final String KEY_EXPERIMENT_SERIES_ID = "experiment_series_id";
    public static final String KEY_EXPERIMENT_SCENARIO_ID = "experiment_scenario_id";
    public static final String KEY_EXPERIMENT_REPLICATE_INDEX = "experiment_replicate_index";
    public static final String KEY_EXPERIMENT_NOTES = "experiment_notes";
    public static final String KEY_SCENE_HANDLING_MODE = "scene_handling_mode";

    private static final String LOG_TAG = "SettingsActivity";

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
    private MaterialButton importButton;
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
    private ModelRole pendingImportRole;

    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                ModelRole expectedRole = pendingImportRole;
                pendingImportRole = null;
                if (uri != null) importModel(uri, expectedRole);
            }
    );

    private final ActivityResultLauncher<Uri> directoryPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri == null) return;
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                    preferences.edit().putString("capture_directory_uri", uri.toString()).apply();
                    markChanged();
                    refreshStoragePath();
                } catch (SecurityException error) {
                    Toast.makeText(this, R.string.capture_directory_error, Toast.LENGTH_LONG).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        applySystemInsets();

        preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        modelRegistry = new ModelRegistry(this);
        packageImporter = new AlprPackageImporter(this, modelRegistry);
        autoTuneManager = new AutoTuneManager(this);

        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        modelStatusSummary = findViewById(R.id.settings_model_status_summary);
        vehicleModelStatus = findViewById(R.id.settings_model_status_vehicle);
        plateModelStatus = findViewById(R.id.settings_model_status_plate);
        characterModelStatus = findViewById(R.id.settings_model_status_character);
        storagePath = findViewById(R.id.settings_storage_path);
        progress = findViewById(R.id.settings_progress);
        importButton = findViewById(R.id.settings_import_model);
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

        configureProfileControls();
        configureResolutionControls();
        configureCropControls();
        configureSceneHandlingControls();
        configureRoiBudgetControls();
        configureExperimentIdentity();
        configurePipelineControls();
        refreshModelStatus();
        refreshStoragePath();
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


        CameraResolutionCatalog catalog =
                new CameraResolutionCatalog(
                        this
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
        SceneHandlingMode effective = experimentEnabled
                ? SceneHandlingMode.STRICT_SCENE_BOUNDARY : stored;
        group.check(effective == SceneHandlingMode.STRICT_SCENE_BOUNDARY
                ? R.id.settings_scene_strict
                : R.id.settings_scene_dynamic);
        setSceneHandlingControlsEnabled(group, !experimentEnabled);
        group.addOnButtonCheckedListener((ignored, checkedId, isChecked) -> {
            if (!isChecked || preferences.getBoolean(
                    KEY_EXPERIMENT_MODE_ENABLED,
                    false
            )) {
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

    private void refreshSceneHandlingControls() {
        MaterialButtonToggleGroup group = findViewById(
                R.id.settings_scene_handling_group
        );
        TextView summary = findViewById(R.id.settings_scene_handling_summary);
        boolean experimentEnabled = preferences.getBoolean(
                KEY_EXPERIMENT_MODE_ENABLED,
                false
        );
        if (experimentEnabled) group.check(R.id.settings_scene_strict);
        setSceneHandlingControlsEnabled(group, !experimentEnabled);
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

        importButton.setOnClickListener(view -> launchModelImport(null));
        vehicleNode.setOnClickListener(view -> showNodeActions(ModelRole.VEHICLE));
        plateNode.setOnClickListener(view -> showNodeActions(ModelRole.PLATE));
        characterNode.setOnClickListener(view -> showNodeActions(ModelRole.CHARACTER));
        restoreBaseButton.setOnClickListener(view -> restoreBaseComposition());
        findViewById(R.id.settings_select_storage).setOnClickListener(view -> {
            String stored = preferences.getString("capture_directory_uri", "");
            Uri initial = stored.isEmpty() ? null : Uri.parse(stored);
            directoryPicker.launch(initial);
        });
    }

    private void launchModelImport(ModelRole expectedRole) {
        pendingImportRole = expectedRole;
        modelPicker.launch(new String[]{
                "application/zip", "application/octet-stream", "application/x-zip-compressed"
        });
    }

    private void showNodeActions(ModelRole role) {
        CharSequence[] actions = new CharSequence[]{
                getString(R.string.settings_node_choose_model),
                getString(R.string.settings_node_import_model, roleLabel(role))
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_node_actions_title, roleLabel(role)))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showCompositionModel(role);
                    } else {
                        launchModelImport(role);
                    }
                })
                .setNegativeButton(R.string.menu_close, null)
                .show();
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
            if (!ModelRegistry.isExecutable(model)) {
                labels[index + offset] = labels[index + offset] + " · brak runtime'u";
            }
            if (active != null && active.storageId().equals(model.storageId())) {
                selected = index + offset;
            }
        }
        final int[] choice = {Math.max(0, selected)};
        new MaterialAlertDialogBuilder(this)
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
                    if (!ModelRegistry.isExecutable(selectedModel)) {
                        Toast.makeText(
                                this,
                                R.string.settings_model_not_executable,
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }
                    if (active == null || !active.storageId().equals(selectedModel.storageId())) {
                        modelRegistry.activate(selectedModel);
                        markChanged();
                        refreshModelStatus();
                    }
                    showVariantSelection(role, selectedModel);
                })
                .setNegativeButton(R.string.menu_close, null)
                .show();
    }

    private void showVariantSelection(ModelRole role, InstalledModel model) {
        List<ModelVariant> variants = model.manifest().variants();
        CharSequence[] labels = new CharSequence[variants.size() + 1];
        labels[0] = getString(R.string.settings_variant_auto);
        String pinned = autoTuneManager.pinnedVariantId(model);
        int selected = 0;
        for (int index = 0; index < variants.size(); index++) {
            ModelVariant variant = variants.get(index);
            String label = variant.id() + " · " + variant.runtime().wireName()
                    + " · " + variant.precision().toUpperCase(java.util.Locale.ROOT);
            if (!RuntimeBackendFactory.isRuntimeAvailable(variant.runtime())) {
                label += " · " + getString(
                        R.string.settings_variant_unavailable,
                        RuntimeBackendFactory.unavailableReason(variant.runtime())
                );
            }
            labels[index + 1] = label;
            if (variant.id().equals(pinned)) selected = index + 1;
        }
        final int[] choice = {selected};
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.settings_select_variant_title, roleLabel(role)))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> choice[0] = which)
                .setPositiveButton(R.string.settings_apply, (dialog, ignored) -> {
                    if (choice[0] == 0) {
                        autoTuneManager.clearPinnedVariant(model);
                    } else {
                        ModelVariant variant = variants.get(choice[0] - 1);
                        if (!RuntimeBackendFactory.isRuntimeAvailable(variant.runtime())) {
                            Toast.makeText(
                                    this,
                                    RuntimeBackendFactory.unavailableReason(variant.runtime()),
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }
                        autoTuneManager.pinVariant(model, variant.id());
                    }
                    markChanged();
                    refreshModelStatus();
                })
                .setNegativeButton(R.string.menu_close, null)
                .show();
    }

    private void restoreBaseComposition() {
        modelRegistry.reload();
        InstalledAlprPackage base = modelRegistry.getBasePackage();
        if (base == null) return;
        autoTuneManager.clearPinnedVariant(base.vehicleModel());
        autoTuneManager.clearPinnedVariant(base.plateModel());
        autoTuneManager.clearPinnedVariant(base.characterModel());
        modelRegistry.restoreBasePackage();
        markChanged();
        refreshModelStatus();
        Toast.makeText(this, R.string.settings_composition_restored, Toast.LENGTH_LONG).show();
    }

    private static String roleLabel(ModelRole role) {
        return role == ModelRole.VEHICLE ? "MP"
                : role == ModelRole.PLATE ? "MT" : "MZ";
    }

    private void importModel(Uri uri, ModelRole expectedRole) {
        setBusy(true);
        AppLog.info(this, LOG_TAG, "Rozpoczęto import modelu z ekranu opcji");
        backgroundExecutor.execute(() -> {
            try {
                ModelImportResult result = packageImporter.importPackage(uri);
                String name;
                String successMessage;
                if (expectedRole != null) {
                    InstalledModel model = result.isCompletePackage()
                            ? modelForRole(result.completePackage(), expectedRole)
                            : result.singleModel();
                    if (model == null) {
                        throw new IllegalArgumentException(getString(
                                R.string.settings_node_role_missing,
                                roleLabel(expectedRole)
                        ));
                    }
                    if (model.manifest().role() != expectedRole) {
                        throw new IllegalArgumentException(getString(
                                R.string.settings_node_role_mismatch,
                                roleLabel(model.manifest().role()),
                                roleLabel(expectedRole)
                        ));
                    }
                    if (!ModelRegistry.isExecutable(model)) {
                        modelRegistry.reload();
                        throw new IllegalArgumentException(getString(
                                R.string.settings_model_not_executable
                        ));
                    }
                    modelRegistry.activate(model);
                    name = model.manifest().name();
                    successMessage = getString(
                            R.string.settings_node_imported,
                            roleLabel(expectedRole),
                            name
                    );
                } else if (result.isCompletePackage()) {
                    InstalledAlprPackage completePackage = result.completePackage();
                    modelRegistry.activate(completePackage);
                    name = completePackage.manifest().name();
                    int message = completePackage.vehicleModel() != null
                            && !ModelRegistry.isExecutable(completePackage.vehicleModel())
                            ? R.string.settings_import_complete_partial_runtime
                            : R.string.settings_import_complete;
                    successMessage = getString(message, name);
                } else {
                    InstalledModel model = result.singleModel();
                    if (ModelRegistry.isExecutable(model)) modelRegistry.activate(model);
                    else modelRegistry.reload();
                    name = model.manifest().name();
                    successMessage = getString(R.string.settings_import_partial, name);
                }
                markChanged();
                AppLog.info(this, LOG_TAG, "Zaimportowano model: " + name);
                runOnUiThread(() -> {
                    setBusy(false);
                    refreshModelStatus();
                    Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                AppLog.error(this, LOG_TAG, "Import odrzucony: " + error.getMessage(), error);
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(
                            this,
                            getString(R.string.settings_import_rejected, error.getMessage()),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        });
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
        modelStatusSummary.setTextColor(getColor(
                modelRegistry.hasRequiredPipeline()
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
                                RoiBudgetPolicy.TWO_ROI.wireName()
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
        String roleName = roleLabel(role);
        InstalledModel model = modelRegistry.getActive(role);
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
        ModelVariant variant = autoTuneManager.chosenVariant(model);
        String runtime = variant.runtime().wireName().toUpperCase(java.util.Locale.ROOT);
        String precision = variant.precision().toUpperCase(java.util.Locale.ROOT);
        button.setText(getString(
                R.string.settings_node_active_lines,
                roleName,
                runtime,
                precision
        ));
        button.setContentDescription(
                roleName + ": " + model.manifest().modelId()
                        + ", " + variant.id()
                        + ", " + (autoTuneManager.isVariantPinned(model) ? "ręczny" : "auto")
        );
    }

    private boolean hasPinnedVariant() {
        return autoTuneManager.isVariantPinned(modelRegistry.getActive(ModelRole.VEHICLE))
                || autoTuneManager.isVariantPinned(modelRegistry.getActive(ModelRole.PLATE))
                || autoTuneManager.isVariantPinned(modelRegistry.getActive(ModelRole.CHARACTER));
    }

    private void refreshStoragePath() {
        String stored = preferences.getString("capture_directory_uri", "");
        if (stored.isEmpty()) {
            storagePath.setText(R.string.settings_storage_unset);
            return;
        }
        Uri uri = Uri.parse(stored);
        String label = uri.getLastPathSegment();
        storagePath.setText(getString(
                R.string.settings_storage_selected,
                label == null || label.isEmpty() ? uri.toString() : label
        ));
    }

    private void saveString(String key, String value) {
        if (value.equals(preferences.getString(key, ""))) return;
        preferences.edit().putString(key, value).apply();
        markChanged();
    }

    private void markChanged() {
        int revision = preferences.getInt(KEY_REVISION, 0);
        preferences.edit().putInt(KEY_REVISION, revision + 1).apply();
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        importButton.setEnabled(!busy);
    }

    private void applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    @Override
    protected void onPause() {
        persistExperimentIdentity();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }

    private void configureExperimentIdentity() {
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
        if (experimentSeriesId == null) return;
        String series = normalizedText(experimentSeriesId, defaultExperimentSeriesId());
        String scenario = normalizedText(experimentScenarioId, "live_camera");
        String notes = normalizedText(experimentNotes, "");
        int replicate = 1;
        try {
            replicate = Math.max(1, Integer.parseInt(
                    normalizedText(experimentReplicateIndex, "1")
            ));
        } catch (NumberFormatException ignored) {
            // Niepoprawna wartość wraca do pierwszego powtórzenia.
        }

        boolean changed = !series.equals(preferences.getString(KEY_EXPERIMENT_SERIES_ID, ""))
                || !scenario.equals(preferences.getString(KEY_EXPERIMENT_SCENARIO_ID, ""))
                || replicate != preferences.getInt(KEY_EXPERIMENT_REPLICATE_INDEX, 1)
                || !notes.equals(preferences.getString(KEY_EXPERIMENT_NOTES, ""));
        experimentSeriesId.setText(series);
        experimentScenarioId.setText(scenario);
        experimentReplicateIndex.setText(String.valueOf(replicate));
        preferences.edit()
                .putString(KEY_EXPERIMENT_SERIES_ID, series)
                .putString(KEY_EXPERIMENT_SCENARIO_ID, scenario)
                .putInt(KEY_EXPERIMENT_REPLICATE_INDEX, replicate)
                .putString(KEY_EXPERIMENT_NOTES, notes)
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

        /*
         * Jednorazowa migracja wersji, którą właśnie zbudowaliśmy.
         * Stara wartość roi_budget_policy staje się WYŁĄCZNIE
         * wariantem eksperymentalnym.
         */
        if (!preferences.contains(
                KEY_EXPERIMENT_ROI_POLICY
        )) {
            String legacy =
                    preferences.getString(
                            "roi_budget_policy",
                            RoiBudgetPolicy.TWO_ROI.wireName()
                    );

            preferences.edit()
                    .putString(
                            KEY_EXPERIMENT_ROI_POLICY,
                            legacy
                    )
                    .remove(
                            "roi_budget_policy"
                    )
                    .apply();
        }

        boolean experimentEnabled =
                preferences.getBoolean(
                        KEY_EXPERIMENT_MODE_ENABLED,
                        false
                );

        RoiBudgetPolicy selected =
                RoiBudgetPolicy.fromWireName(
                        preferences.getString(
                                KEY_EXPERIMENT_ROI_POLICY,
                                RoiBudgetPolicy.TWO_ROI.wireName()
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

                    markChanged();
                    refreshNodeBadges();
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
                    refreshNodeBadges();
                }
        );
    }
}
