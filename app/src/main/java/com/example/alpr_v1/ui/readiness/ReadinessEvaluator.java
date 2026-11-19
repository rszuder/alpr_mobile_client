package com.example.alpr_v1.ui.readiness;

import com.example.alpr_v1.R;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.camera.*;
import com.example.alpr_v1.continuity.SceneHandlingMode;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.inference.RuntimeBackendFactory;
import com.example.alpr_v1.inference.RuntimeExecutionEvidence;
import com.example.alpr_v1.model.*;
import com.example.alpr_v1.setup.*;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.example.alpr_v1.ui.readiness.ReadinessFinding.Domain.*;
import static com.example.alpr_v1.ui.readiness.ReadinessFinding.Status.*;

/** Runs on a worker. Reads contracts and persisted evidence; never calls a START/preparation path. */
public final class ReadinessEvaluator {
    private final Map<String,String> integrity=new ConcurrentHashMap<>();
    public void invalidateFiles() { integrity.clear(); }

    public ReadinessResult evaluate(ReadinessSnapshot s) {
        List<ReadinessFinding> out=new ArrayList<>();
        boolean autoZoom=s.flag(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,false);
        if(s.research()) {
            check(s,out,"autozoom_lock","Automatyczne przybliżenie wymaga Lock",
                    "Włącz utrzymywanie obiektu dla dynamicznej obsługi sceny.",
                    ReadinessTarget.field(4,R.id.settings_research_lock_switch,"Przejdź do Lock"),
                    "ConfigurationRequirements.requireAutoZoomLock",()->ConfigurationRequirements.requireAutoZoomLock(autoZoom,
                            s.flag(SettingsActivity.KEY_RESEARCH_LOCK_ENABLED,true),SceneHandlingMode.fromWireName(s.text(
                                    SettingsActivity.KEY_SCENE_HANDLING_MODE,SceneHandlingMode.DYNAMIC_CONTINUITY.wireName()))));
            if(s.tv())check(s,out,"tv_fixed_zoom","Wyłącz automatyczne przybliżenie",
                    "Analiza obszaru TV wymaga stałego przybliżenia.",
                    ReadinessTarget.field(4,R.id.settings_research_autozoom_switch,"Przejdź do Autozoom"),
                    "ConfigurationRequirements.requireTvFixedZoom",()->ConfigurationRequirements.requireTvFixedZoom(autoZoom));
            if(s.photo()) {
                boolean consistent=check(s,out,"photo_input","Ustaw analizę całego obszaru fotografii",
                        "Ten profil zapisu wymaga strategii R0 i wejścia z obszaru TV. Etap pojazdów pozostaje wyłączony przez R0.",
                        !s.tv()?ReadinessTarget.field(0,R.id.settings_tv_input_mode,"Wybierz wejście TV")
                                :ReadinessTarget.field(0,R.id.settings_roi_r0,"Przejdź do strategii"),
                        "ConfigurationRequirements.requirePhotoInput",()->ConfigurationRequirements.requirePhotoInput(s.tv(),s.policy(),s.policy().usesVehicleCascade()));
                // The two independent controls must both be reported, even before either is corrected.
                if(!consistent&&!s.tv()&&s.policy().usesVehicleCascade())
                    check(s,out,"photo_strategy","Wybierz analizę całego obrazu",
                            "Profil fotografii wymaga strategii R0, bez etapu wykrywania pojazdów.",
                            ReadinessTarget.field(0,R.id.settings_roi_r0,"Przejdź do strategii"),
                            "ConfigurationRequirements.requirePhotoInput",()->ConfigurationRequirements.requirePhotoInput(true,s.policy(),true));
            }
        }
        if(s.tv()||s.photo())reviewGeometry(s,out);
        for(ModelRole role:ModelRole.values()) {
            boolean enabled=role!=ModelRole.VEHICLE||(s.research()?s.policy().usesVehicleCascade():s.flag("vehicle_cascade_enabled",false));
            ReadinessSnapshot.Stage stage=s.stages.get(role);
            if(!enabled||role==ModelRole.VEHICLE&&!s.research()&&(stage==null||stage.model==null))continue;
            reviewModel(s,out,role,stage);
        }
        if(s.research()&&s.basePackage!=null) {
            try {
                String observed=RuntimeExecutionEvidence.sha256(s.basePackage.sourceArchive());
                check(s,out,"base_package_hash","Zmieniono bazowy pakiet modeli",
                        "Archiwum pakietu różni się od zainstalowanego źródła. Sprawdź pakiet przed badaniem.",
                        new ReadinessTarget(2,R.id.settings_import_alpr_package,ReadinessTarget.Action.PACKAGES,null,"Sprawdź pakiet"),
                        "ConfigurationRequirements.requirePackageHash",()->ConfigurationRequirements.requirePackageHash(observed,s.basePackage.sourceSha256()));
            } catch(IOException unavailable) {
                add(s,out,"base_package_evidence",CONFIG,ADVISORY,"Archiwum źródłowe pakietu jest niedostępne",
                        "Zainstalowane modele są oceniane osobno. Dotychczasowa reguła nie blokuje START z tego powodu.",false,null,"ResearchExecutionConfig.basePackageShaEvidence");
            }
        }
        reviewDevice(s,out);
        return new ReadinessResult(s.identity,s.goal(),out);
    }
    public ReadinessResult updateDevice(ReadinessSnapshot s,ReadinessResult config){
        Set<String> live=new HashSet<>(Arrays.asList("camera_permission","camera_problem","camera_verification","focus","session_preparation","thermal"));
        List<ReadinessFinding> out=new ArrayList<>();
        for(ReadinessFinding finding:config.findings)if(!live.contains(finding.issueId))out.add(finding);
        reviewDevice(s,out);return new ReadinessResult(s.identity,s.goal(),out);
    }
    private void reviewDevice(ReadinessSnapshot s,List<ReadinessFinding> out){
        ReadinessSnapshot.Device d=s.device;
        if(!d.permission)add(s,out,"camera_permission",DEVICE,NEEDS_CHANGE,"Zezwól na używanie kamery",
                "Uprawnienie jest wymagane do pracy z podglądem.",true,ReadinessTarget.preview(),"MainActivity.experimentStartBlocker.PERMISSION");
        if(!d.cameraProblem.isEmpty())add(s,out,"camera_problem",DEVICE,NEEDS_CHANGE,"Sprawdź kamerę w podglądzie",
                d.cameraProblem,true,ReadinessTarget.preview(),"CameraSetupReadiness");
        else if(!d.cameraVerified)add(s,out,"camera_verification",DEVICE,NOT_CHECKABLE_YET,"Potwierdzenie kamery i obrazu",
                "Po powrocie do podglądu sprawdzimy działanie i świeżość obrazu oraz zgodność zapisanej geometrii.",false,null,"CameraSetupReadiness.frameFreshAt");
        if(ConfigurationRequirements.focusBlocksStart(s.research()&&s.flag(Final180PhotoRoi.ENABLED,false),d.focusRequested,d.focusHeld))
            add(s,out,"focus",DEVICE,NEEDS_CHANGE,"Potwierdź zażądaną ostrość",
                    "W podglądzie dokończ ustawianie ostrości lub wyłącz jej utrzymywanie.",true,
                    new ReadinessTarget(1,0,ReadinessTarget.Action.FOCUS,null,"Przejdź do ostrości"),"ConfigurationRequirements.focusBlocksStart");
        if(!d.preparationVerified)add(s,out,"session_preparation",SESSION_PREPARATION,NOT_CHECKABLE_YET,
                s.research()?"Przygotowanie modeli i zapisu":"Uruchomienie wybranych modeli",
                "Przygotowanie nastąpi po START. Sam brak przygotowanej sesji nie wymaga ponownego importowania modeli.",false,null,"MainActivity.preparedExperimentStart / pipeline");
        if(s.research()&&!d.thermalVerified)add(s,out,"thermal",DEVICE,NOT_CHECKABLE_YET,"Warunek temperatury",
                "Temperaturę i jej stabilność sprawdzimy przy uruchamianiu.",false,null,"MainActivity.prestartThermalReady");
    }
    private void reviewModel(ReadinessSnapshot s,List<ReadinessFinding> out,ModelRole role,ReadinessSnapshot.Stage stage) {
        String key="model_"+role.wireName(),name=role==ModelRole.VEHICLE?"pojazdów":role==ModelRole.PLATE?"tablic":"znaków";
        if(stage==null||stage.model==null) {
            add(s,out,key,CONFIG,NEEDS_CHANGE,"Wybierz model "+name,
                    role==ModelRole.VEHICLE?"Wybrana strategia badawcza wymaga aktywnego modelu pojazdów.":"Ten etap jest wymagany do rozpoznawania tablic.",
                    true,ReadinessTarget.model(role,false),"ModelRegistry.getActive / MobileAlprEngine.required / resolveResearchStage");return;
        }
        if(stage.availabilityPending) {
            add(s,out,key+"_selection",DEVICE,NOT_CHECKABLE_YET,"Dostępność wariantu modelu "+name,
                    "Wybór automatyczny wymaga potwierdzenia dostępności runtime przy uruchamianiu.",false,null,"AutoTuneManager.chosenVariant");return;
        }
        ModelVariant variant=stage.variant;
        if(variant==null) {
            add(s,out,key+"_variant",CONFIG,NEEDS_CHANGE,"Wybierz wariant modelu "+name,
                    s.research()?"Badanie wymaga jawnie wybranego wariantu, bez AUTO. "+stage.selectionError:stage.selectionError,
                    true,ReadinessTarget.model(role,true),"ResearchVariantSelection.requirePinned / AutoTuneManager.chosenVariant");return;
        }
        Boolean available=RuntimeBackendFactory.observedAvailability(variant.runtime());
        if(Boolean.FALSE.equals(available))add(s,out,key+"_runtime",CONFIG,NEEDS_CHANGE,"Wybierz dostępny wariant modelu "+name,
                "Wybrany runtime jest niedostępny w tej aplikacji.",true,ReadinessTarget.model(role,true),"RuntimeBackendFactory.observedAvailability");
        else if(available==null)add(s,out,key+"_runtime",DEVICE,NOT_CHECKABLE_YET,"Dostępność runtime modelu "+name,
                "Sprawdzimy ją przy uruchamianiu; podsumowanie nie ładuje bibliotek modelu.",false,null,"RuntimeBackendFactory.observedAvailability");
        check(s,out,key+"_decoder","Sprawdź wariant modelu "+name,
                "Metadane wariantu nie spełniają kontraktu dekodera aplikacji.",ReadinessTarget.model(role,true),
                "ModelConfigurationRequirements.requireOutput",()->ModelConfigurationRequirements.requireOutput(role,variant.output(stage.model.manifest().output())));
        check(s,out,key+"_input","Sprawdź wejście wariantu modelu "+name,
                "Metadane wejścia nie spełniają wymagań wybranego runtime.",ReadinessTarget.model(role,true),
                "ModelConfigurationRequirements.requireRuntimeInput",()->ModelConfigurationRequirements.requireRuntimeInput(variant.runtime(),variant.input(stage.model.manifest().input())));
        if(s.research()) {
            String executionKey=role==ModelRole.VEHICLE?SettingsActivity.KEY_RESEARCH_MP_EXECUTION:role==ModelRole.PLATE?SettingsActivity.KEY_RESEARCH_MT_EXECUTION:SettingsActivity.KEY_RESEARCH_MZ_EXECUTION;
            check(s,out,key+"_execution","Wybierz wykonanie modelu "+name,
                    "W badaniu wybierz CPU lub GPU. GPU jest dostępne dla wariantów TFLite; AUTO nie jest zatwierdzonym wykonaniem.",
                    ReadinessTarget.execution(role),"ResearchExecutionChoice.resolveApproved",()->ResearchExecutionChoice.fromWireName(s.text(executionKey,"auto")).resolveApproved(variant));
            check(s,out,key+"_files","Sprawdź pliki modelu "+name,
                    "Pliki wariantu są niedostępne lub różnią się od zainstalowanego manifestu.",ReadinessTarget.model(role,false),
                    "ModelConfigurationRequirements.requireResearchArtifacts",()->checkArtifacts(stage.model,variant));
        } else {
            check(s,out,key+"_files","Sprawdź pliki modelu "+name,"Nie można odczytać plików wybranego wariantu.",
                    ReadinessTarget.model(role,false),"RuntimeExecutionEvidence.safeFile / InferenceBackend.open",()->{
                        for(String file:variant.files())try {
                            File actual=RuntimeExecutionEvidence.safeFile(stage.model,file);
                            if(!actual.isFile()||!actual.canRead())throw new IllegalStateException("MODEL_FILE_UNAVAILABLE");
                        } catch(IOException invalid) { throw new IllegalStateException(invalid); }
                    });
        }
    }
    private void checkArtifacts(InstalledModel model,ModelVariant variant) {
        StringBuilder key=new StringBuilder(model.directory().toString()).append(model.fingerprint()).append(variant.id()).append(variant.sha256());
        for(String f:variant.files()) { File p=model.resolve(f);key.append('|').append(p.length()).append(':').append(p.lastModified()); }
        String cached=integrity.get(key.toString());
        if(cached==null) {
            try { ModelConfigurationRequirements.requireResearchArtifacts(model,variant);cached=""; }
            catch(RuntimeException invalid) { cached=invalid.getMessage()==null?invalid.toString():invalid.getMessage(); }
            if(integrity.size()>64)integrity.clear();integrity.put(key.toString(),cached);
        }
        if(!cached.isEmpty())throw new IllegalStateException(cached);
    }
    private void reviewGeometry(ReadinessSnapshot s,List<ReadinessFinding> out) {
        TvSetupCalibration calibration=TvSetupCalibration.restore(s.text("tv_setup_calibration_v1",""));
        JSONObject approval=calibration.approval();
        boolean tvApproved=approval!=null&&SetupJson.verified(approval)&&"READY".equals(approval.optString("status"))&&!calibration.corners.isEmpty();
        if(!tvApproved)add(s,out,"tv_approval",CONFIG,NEEDS_CHANGE,"Zatwierdź narożniki ekranu TV",
                "Brakuje prawidłowego zatwierdzenia TV. Kalibrator pozwala wybrać kamerę i ustawić cztery narożniki.",true,ReadinessTarget.tv(),"TvSetupCalibration.restore / TvSetupController.open");
        if(tvApproved){
            check(s,out,"tv_confirmation_time","Sprawdź datę zatwierdzenia TV",
                    "Zatwierdzenie ma przyszłą datę. Sprawdź zegar telefonu lub zatwierdź narożniki ponownie.",ReadinessTarget.tv(),
                    "TvSetupCalibration.confirmationTimeValid",()->{
                        if(!calibration.confirmationTimeValid(java.time.Instant.now().toString()))throw new IllegalStateException("CONFIRMATION_TIME_IN_FUTURE");
                    });
            CameraSetupSnapshot requested=CameraSetupSnapshot.restore(s.text(CameraSetupSnapshot.PREFERENCE,""));
            if(requested!=null)check(s,out,"tv_camera_configuration","Dopasuj kalibrację do wybranej kamery",
                    "Zapisane narożniki TV dotyczą innej kamery lub innego przybliżenia. Otwórz kalibrator i zatwierdź aktualny obraz.",
                    ReadinessTarget.tv(),"TvBboxCrop.prepare / camera_setup",()->TvBboxCrop.prepare(approval,calibration.reference,requested));
        }
        if(!s.photo())return;
        String saved=s.text(Final180PhotoRoi.PREFERENCE,"");
        if(saved.isEmpty()) {
            add(s,out,"photo_area",CONFIG,NEEDS_CHANGE,"Nie ustawiono obszaru fotografii",
                    tvApproved?"Otwórz kalibrator i zatwierdź obszar fotografii wewnątrz TV.":"Najpierw zatwierdź narożniki TV. Po powrocie będzie można ustawić obszar fotografii.",
                    true,tvApproved?ReadinessTarget.photo():ReadinessTarget.tv(),"Final180PhotoRoiApproval.resolve");return;
        }
        if(!tvApproved) {
            add(s,out,"photo_area",CONFIG,NEEDS_CHANGE,"Obszar fotografii wymaga zatwierdzenia TV",
                    "Najpierw popraw kalibrację TV; potem sprawdzimy zapisany obszar fotografii.",true,ReadinessTarget.tv(),"Final180PhotoRoiApproval.reviewAgainst");return;
        }
        check(s,out,"photo_area","Sprawdź obszar fotografii","Zapisany obszar nie ma zgodnego zatwierdzenia dla tej kalibracji TV.",
                ReadinessTarget.photo(),"Final180PhotoRoiApproval.resolve / reviewAgainst",()->{
                    // An authentic persisted source is an anchor for config review, never live camera evidence.
                    CameraPreviewGeometry source=calibration.reference;
                    CameraSetupSnapshot setup=new CameraSetupSnapshot(new CameraSelection(source.cameraId,source.physicalCameraId),(float)source.zoomRatio);
                    TvBboxCrop anchor=TvBboxCrop.prepare(approval,source,setup);
                    Final180PhotoRoiApproval.resolve(saved,s.text(Final180PhotoRoiApproval.PREFERENCE,""),anchor,approval,s.sessionsRoot)
                            .reviewAgainst(source,approval);
                });
    }
    private static boolean check(ReadinessSnapshot s,List<ReadinessFinding> out,String id,String title,String explanation,
                                 ReadinessTarget target,String source,Runnable predicate) {
        try { predicate.run();return true; }
        catch(RuntimeException invalid) {
            add(s,out,id,CONFIG,NEEDS_CHANGE,title,explanation,true,target,source);return false;
        }
    }
    private static void add(ReadinessSnapshot s,List<ReadinessFinding> out,String id,ReadinessFinding.Domain domain,
                            ReadinessFinding.Status status,String title,String explanation,boolean blocks,ReadinessTarget target,String source) {
        out.add(new ReadinessFinding(id,domain,status,title,explanation,blocks,target,source,s.identity));
    }
}
