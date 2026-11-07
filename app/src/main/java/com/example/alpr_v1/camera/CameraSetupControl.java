package com.example.alpr_v1.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Preparation UI port. Production uses the same CameraX controller as analysis. */
public interface CameraSetupControl {
    final class Choice {
        public final CameraSelection selection;
        public final String label,detail;
        public Choice(CameraSelection selection,String label,String detail){this.selection=selection;this.label=label;this.detail=detail;}
        @Override public String toString(){return label;}
    }
    final class State {
        public final List<Choice> choices;
        public final CameraSelection requested;
        public final float requestedZoom,minZoom,maxZoom;
        public final CameraSetupSnapshot applied;
        public final boolean loading;
        public final String reason;
        public final long revision;
        public State(List<Choice> choices,CameraSelection requested,float requestedZoom,float min,float max,
                CameraSetupSnapshot applied,boolean loading,String reason,long revision){
            this.choices=Collections.unmodifiableList(new ArrayList<>(choices));this.requested=requested;this.requestedZoom=requestedZoom;
            minZoom=min;maxZoom=max;this.applied=applied;this.loading=loading;this.reason=reason==null?"":reason;this.revision=revision;
        }
        public boolean ready(){return applied!=null&&!loading;}
        public static State unavailable(){return new State(Collections.emptyList(),CameraSelection.defaultBack(),1,1,1,null,false,"Uruchom podgląd, aby odczytać dostępne obiektywy",0);}
    }
    State state();
    void setListener(Consumer<State> listener);
    void apply(CameraSelection selection,float zoom);
}
