package com.example.alpr_v1.ui.readiness;

import java.util.*;

public final class ReadinessResult {
    public enum State {
        READY("Gotowe do rozpoczęcia"), NEEDS_CHANGE("Wymaga ustawienia"), DEFERRED("Sprawdzimy przy uruchamianiu");
        public final String label;State(String label){this.label=label;}
    }
    public final String snapshotIdentity,goal;
    public final List<ReadinessFinding> findings;
    public final State state;
    public ReadinessResult(String identity,String goal,List<ReadinessFinding> findings) {
        snapshotIdentity=identity;this.goal=goal;
        List<ReadinessFinding> sorted=new ArrayList<>(findings);
        sorted.sort(Comparator.comparingInt(f->priority(f.status)));this.findings=Collections.unmodifiableList(sorted);
        state=sorted.stream().anyMatch(f->f.status==ReadinessFinding.Status.NEEDS_CHANGE)?State.NEEDS_CHANGE
                :sorted.stream().anyMatch(f->f.status==ReadinessFinding.Status.NOT_CHECKABLE_YET||f.status==ReadinessFinding.Status.IN_PROGRESS)?State.DEFERRED:State.READY;
    }
    private static int priority(ReadinessFinding.Status status) {
        return status==ReadinessFinding.Status.NEEDS_CHANGE?0:status==ReadinessFinding.Status.NOT_CHECKABLE_YET?1:status==ReadinessFinding.Status.ADVISORY?2:3;
    }
    public ReadinessFinding finding(String id) { for(ReadinessFinding f:findings)if(f.issueId.equals(id))return f;return null; }
    public List<ReadinessFinding> visibleFindings() {
        List<ReadinessFinding> out=new ArrayList<>();for(ReadinessFinding f:findings)
            if(f.status!=ReadinessFinding.Status.OK&&f.status!=ReadinessFinding.Status.NOT_APPLICABLE)out.add(f);return out;
    }
    public String counts() {
        long changes=findings.stream().filter(f->f.status==ReadinessFinding.Status.NEEDS_CHANGE).count();
        long later=findings.stream().filter(f->f.status==ReadinessFinding.Status.NOT_CHECKABLE_YET).count();
        return (changes>0?"Do ustawienia: "+changes:"Ustawienia sprawdzone")+(later>0?" · Do potwierdzenia: "+later:"");
    }
}
