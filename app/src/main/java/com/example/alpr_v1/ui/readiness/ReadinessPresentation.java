package com.example.alpr_v1.ui.readiness;

import android.content.Context;
import com.example.alpr_v1.R;
import com.example.alpr_v1.model.ModelRole;
import java.util.*;

/** Transient visual locations, separate from actionable navigation and START admission. */
public final class ReadinessPresentation {
    public static final class Marker {
        private final Set<String> changes=new LinkedHashSet<>(),later=new LinkedHashSet<>();
        private void add(ReadinessFinding finding){
            (finding.status==ReadinessFinding.Status.NEEDS_CHANGE?changes:later).add(finding.issueId);
        }
        public void merge(Marker other){changes.addAll(other.changes);later.addAll(other.later);}
        public int changes(){return changes.size();}
        public int later(){return later.size();}
        public int color(){return changes.isEmpty()?R.color.options_accent:R.color.alpr_warning;}
        public String label(Context context,int section){
            return changes.isEmpty() ? "" : context.getString(R.string.guidance_change);
        }
    }
    public final ReadinessResult result;
    public final boolean research;
    private final Map<Integer,Marker> sections=new LinkedHashMap<>(),controls=new LinkedHashMap<>();
    public ReadinessPresentation(ReadinessResult result,boolean research){
        this.result=result;this.research=research;
        if(result==null)return;
        for(ReadinessFinding f:result.visibleFindings()){
            if(f.status!=ReadinessFinding.Status.NEEDS_CHANGE&&f.status!=ReadinessFinding.Status.NOT_CHECKABLE_YET&&f.status!=ReadinessFinding.Status.IN_PROGRESS)continue;
            if(f.target!=null){mark(f,f.target.sectionId,f.target.controlId);continue;}
            if("camera_verification".equals(f.issueId))mark(f,1,0);
            else if("session_preparation".equals(f.issueId)){mark(f,2,0);if(research)mark(f,3,0);}
            else for(ModelRole role:ModelRole.values()){
                String prefix="model_"+role.wireName();
                if(f.issueId.equals(prefix+"_runtime")||f.issueId.equals(prefix+"_selection"))mark(f,2,ReadinessTarget.model(role,true).controlId);
            }
        }
    }
    private void mark(ReadinessFinding finding,int section,int control){
        if(section>=0&&section<5)sections.computeIfAbsent(section,k->new Marker()).add(finding);
        if(control!=0)controls.computeIfAbsent(control,k->new Marker()).add(finding);
    }
    public Marker section(int index){return sections.get(index);}
    public Map<Integer,Marker> controls(){return Collections.unmodifiableMap(controls);}
    public String headline() {
        if (result == null) return "Sprawdzanie ustawień…";
        return result.state == ReadinessResult.State.NEEDS_CHANGE ? "Wymaga ustawienia" : "Ustawienia są poprawne.";
    }
    public String summary() {
        if (result == null) return "Oceniamy aktualne wartości formularza";
        for (ReadinessFinding f : result.findings) if (f.status == ReadinessFinding.Status.NEEDS_CHANGE)
            return f.title + ". " + (f.target == null ? "Otwórz szczegóły poniżej." : f.target.label + ".");
        List<String> steps = new ArrayList<>();
        if (result.finding("camera_verification") != null) steps.add("Kamerę sprawdzimy po włączeniu podglądu.");
        if (result.finding("session_preparation") != null) steps.add(research
                ? "Modele i zapis przygotujemy podczas uruchamiania analizy."
                : "Modele przygotujemy podczas uruchamiania analizy.");
        if (result.finding("thermal") != null) steps.add("Warunek temperatury sprawdzimy podczas uruchamiania.");
        if (steps.isEmpty()) for (ReadinessFinding f : result.visibleFindings())
            if (f.status == ReadinessFinding.Status.NOT_CHECKABLE_YET) steps.add(f.explanation);
        return steps.isEmpty() ? "Brak ustawień wymagających poprawienia." : String.join(" ", steps);
    }
    /** Main shares the interpretation; operational camera/session messages keep priority. */
    public String mainText() {
        if (result == null) return headline();
        if (result.state == ReadinessResult.State.NEEDS_CHANGE) {
            String detail="Otwórz Opcje";
            for(ReadinessFinding finding:result.findings)if(finding.status==ReadinessFinding.Status.NEEDS_CHANGE){
                String id=finding.issueId;
                if(id.startsWith("model_plate"))detail="Model MT · Opcje";
                else if(id.startsWith("model_character"))detail="Model MZ · Opcje";
                else if(id.startsWith("model_vehicle"))detail="Model MP · Opcje";
                else if(id.startsWith("tv_"))detail="Kalibracja TV · Opcje";
                else if(id.equals("photo_area"))detail="Obszar zdjęć · Opcje";
                break;
            }
            return headline()+"\n"+detail;
        }
        if (result.state == ReadinessResult.State.DEFERRED)
            return "Ustawienia poprawne\nSprawdzenie przy uruchamianiu";
        return headline();
    }
    public String title(Context context,ReadinessFinding finding){
        if("camera_verification".equals(finding.issueId))return context.getString(R.string.options_camera_title);
        if("session_preparation".equals(finding.issueId))return context.getString(research?R.string.guidance_models_and_save:R.string.options_models_title);
        return finding.title;
    }
    public String caption(Context context,ReadinessFinding finding){
        if(finding.status==ReadinessFinding.Status.NEEDS_CHANGE){
            String section=finding.target==null?"":context.getString(sectionTitle(finding.target.sectionId))+" · ";
            return section+context.getString(R.string.guidance_change);
        }
        if(finding.status==ReadinessFinding.Status.NOT_CHECKABLE_YET||finding.status==ReadinessFinding.Status.IN_PROGRESS)
            return context.getString(finding.domain==ReadinessFinding.Domain.SESSION_PREPARATION||finding.issueId.startsWith("model_")?R.string.guidance_after_start:R.string.guidance_later);
        return context.getString(R.string.guidance_information);
    }
    public static int sectionTitle(int index){
        int[] titles={R.string.options_mode_title,R.string.options_camera_title,R.string.options_models_title,R.string.options_session_title,R.string.options_advanced_title};
        return titles[Math.max(0,Math.min(4,index))];
    }
}
