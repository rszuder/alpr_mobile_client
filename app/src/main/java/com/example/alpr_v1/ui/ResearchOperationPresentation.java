package com.example.alpr_v1.ui;

import com.example.alpr_v1.experiment.ResearchOperationState;

/** Operator-facing projection of the existing receipt. No invented overall percentage. */
public final class ResearchOperationPresentation {
    public final boolean visible,active,determinate;
    public final String title,detail;
    public final int percent;
    private ResearchOperationPresentation(boolean visible,boolean active,boolean determinate,String title,String detail,int percent){
        this.visible=visible;this.active=active;this.determinate=determinate;this.title=title;this.detail=detail;this.percent=percent;
    }
    public static ResearchOperationPresentation from(ResearchOperationState.Snapshot s){
        if(s==null||"INVENTORY".equals(s.stage))return new ResearchOperationPresentation(false,false,false,"","",0);
        boolean active=s.active();String title;
        if(active)title=stage(s.stage);
        else switch(s.status){
            case "FAILED":title="Zapis nie powiódł się — sprawdź szczegóły";break;
            case "INTERRUPTED":title="Operacja została przerwana";break;
            case "CANCELLED":title="Operacja anulowana";break;
            case "SAVED_UNVERIFIED":title="Dokument zapisany — brak potwierdzenia odczytu";break;
            case "SUCCEEDED":
                title="PREPARING".equals(s.stage)?"Przygotowano sesję":
                        "VERIFYING_DOCUMENT".equals(s.stage)||"COPYING_DOCUMENT".equals(s.stage)?"Eksport zapisany i sprawdzony":
                                "RECOVERING".equals(s.stage)?"Odzyskano kopię sesji — sprawdź kompletność":"Archiwum sesji gotowe";
                break;
            default:title="Stan operacji wymaga sprawdzenia";
        }
        boolean known=active&&s.total>0;
        int percent=known?(int)Math.max(0,Math.min(100,100.0*s.done/s.total)):0;
        String detail=known?percent+"% · "+s.done+" / "+s.total+" B":active?"START będzie dostępny po zakończeniu operacji.":"Szczegóły zawierają wynik i dane sesji.";
        return new ResearchOperationPresentation(true,active,known,title,detail,percent);
    }
    private static String stage(String stage){switch(stage){
        case "PREPARING":return "Przygotowywanie sesji";
        case "QUEUE_DRAIN":return "Domykanie zadań w locie";
        case "FINALIZING_JSONL":return "Zapisywanie dzienników";
        case "BUILDING_ARCHIVE":return "Tworzenie archiwum sesji";
        case "VALIDATING_ARCHIVE":return "Sprawdzanie archiwum sesji";
        case "COPYING_DOCUMENT":return "Eksportowanie dokumentu";
        case "VERIFYING_DOCUMENT":return "Sprawdzanie zapisanego dokumentu";
        case "RECOVERING":return "Odzyskiwanie sesji na kopii";
        default:return "Trwa operacja sesji";
    }}
}
