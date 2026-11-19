package com.example.alpr_v1.experiment;

import org.json.JSONObject;
import java.io.File;
import java.util.*;
import java.util.function.Consumer;

/** Small control/receipt journal beside the existing session registry; never research evidence.
 * Every callback must present its own id. A terminal or old callback cannot finish another task. */
public final class ResearchOperationState {
    public static final class Snapshot {
        public final String id,sessionId,stage,status,error,detail;
        public final long done,total,updatedWallMillis;
        Snapshot(String id,String session,String stage,String status,long done,long total,String detail,String error,long wall){
            this.id=id;sessionId=session;this.stage=stage;this.status=status;this.done=done;this.total=total;this.detail=detail;this.error=error;updatedWallMillis=wall;
        }
        public boolean active(){return "ACTIVE".equals(status);}
        public JSONObject toJson(){try{return new JSONObject().put("schema","alpr.research_operation_receipt.v1").put("operation_id",id).put("session_id",sessionId)
                .put("stage",stage).put("status",status).put("done",done).put("total",total).put("detail",detail)
                .put("error",error).put("updated_wall_millis",updatedWallMillis);}catch(Exception e){throw new IllegalStateException(e);}}
        public String description(){return "Operacja: "+id+"\nSesja: "+sessionId+" · "+label(stage)+" · "+status+(total>0?" · "+done+"/"+total:"")
                +"\nOstatnia zmiana: "+java.time.Instant.ofEpochMilli(updatedWallMillis)
                +(detail.isEmpty()?"":"\n"+detail)+(error.isEmpty()?"":"\n"+error);}
    }
    private static final Map<String,Snapshot> PROCESS_ACTIVE=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<String> BLOCKING=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<String> DOCUMENTS=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final File directory;
    private String key(String id){return directory.getAbsolutePath()+"/"+id;}
    private final Consumer<Snapshot> changed;
    private final Map<String,Snapshot> active=new LinkedHashMap<>();
    private Snapshot latest;
    private volatile String persistenceError="";
    private final Map<String,Snapshot> recent=new LinkedHashMap<>();
    public ResearchOperationState(File directory,Consumer<Snapshot> changed){this.directory=directory;this.changed=changed;}
    /** Inventory only. A dead process is INTERRUPTED; its work is never assumed to have succeeded. */
    public synchronized void restore()throws Exception{
        File[] files=directory.listFiles((d,n)->n.endsWith(".json"));if(files==null)return;
        for(File f:files){try{
            JSONObject j=new JSONObject(ResearchSessionStore.read(f));if(PROCESS_ACTIVE.containsKey(key(j.getString("operation_id"))))continue;String state=j.getString("status");
            Snapshot s=new Snapshot(j.getString("operation_id"),j.optString("session_id"),j.getString("stage"),
                    state.equals("ACTIVE")?"INTERRUPTED":state,j.optLong("done"),j.optLong("total"),j.optString("detail"),
                    state.equals("ACTIVE")?"PROCESS_ENDED_DURING_"+j.getString("stage"):j.optString("error"),j.getLong("updated_wall_millis"));
            if(state.equals("ACTIVE"))persist(s);
            remember(s);
            if(latest==null||s.updatedWallMillis>latest.updatedWallMillis)latest=s;
        }catch(Exception invalid){/* Keep unreadable original; the session registry remains authoritative. */}}
        if(latest!=null)changed.accept(latest);
    }
    public synchronized String begin(String session,String stage,String detail){
        String id=UUID.randomUUID().toString();Snapshot s=new Snapshot(id,session==null?"":session,stage,"ACTIVE",0,0,detail==null?"":detail,"",System.currentTimeMillis());
        active.put(id,s);PROCESS_ACTIVE.put(key(id),s);
        if(stage.equals("VALIDATING_ARCHIVE"))DOCUMENTS.add(key(id));
        if(stage.equals("PREPARING")||stage.equals("QUEUE_DRAIN")||stage.equals("RECOVERING"))BLOCKING.add(key(id));
        publish(s);return id;
    }
    public synchronized boolean update(String id,String stage,long done,long total,String detail){
        Snapshot old=active.get(id);if(old==null)return false;
        Snapshot s=new Snapshot(id,old.sessionId,stage,"ACTIVE",done,total,detail==null?old.detail:detail,"",System.currentTimeMillis());
        active.put(id,s);PROCESS_ACTIVE.put(key(id),s);publish(s);return true;
    }
    public synchronized boolean bindSession(String id,String session){
        Snapshot old=active.get(id);if(old==null||!old.sessionId.isEmpty())return false;
        Snapshot s=new Snapshot(id,session,old.stage,old.status,old.done,old.total,old.detail,old.error,System.currentTimeMillis());
        active.put(id,s);PROCESS_ACTIVE.put(key(id),s);publish(s);return true;
    }
    public synchronized boolean end(String id,String status,String detail,Throwable error){
        Snapshot old=active.remove(id);if(old==null)return false;
        PROCESS_ACTIVE.remove(key(id));BLOCKING.remove(key(id));DOCUMENTS.remove(key(id));
        publish(new Snapshot(id,old.sessionId,old.stage,status,old.done,old.total,detail==null?old.detail:detail,chain(error),System.currentTimeMillis()));return true;
    }
    public synchronized Snapshot latest(){
        Snapshot newest=null;String winner="";
        for(Map.Entry<String,Snapshot> e:PROCESS_ACTIVE.entrySet())if(e.getKey().startsWith(directory.getAbsolutePath()+"/"))
            if(newest==null||priority(e.getKey())>priority(winner)||priority(e.getKey())==priority(winner)&&e.getValue().updatedWallMillis>=newest.updatedWallMillis){newest=e.getValue();winner=e.getKey();}
        return newest==null?latest:newest;
    }
    private static int priority(String key){return BLOCKING.contains(key)?2:DOCUMENTS.contains(key)?1:0;}
    public synchronized List<Snapshot> active(){
        List<Snapshot> result=new ArrayList<>();for(Map.Entry<String,Snapshot> e:PROCESS_ACTIVE.entrySet())if(e.getKey().startsWith(directory.getAbsolutePath()+"/"))result.add(e.getValue());return result;
    }
    public boolean hasBlockingWork(){for(String key:BLOCKING)if(key.startsWith(directory.getAbsolutePath()+"/"))return true;return false;}
    public boolean hasDocumentWork(){for(String key:DOCUMENTS)if(key.startsWith(directory.getAbsolutePath()+"/"))return true;return false;}
    /** UI/admission guard. Inventory is read-only and never locks configuration. */
    public static boolean hasActiveWriteWork(File directory){
        String prefix=directory.getAbsolutePath()+"/";
        for(Map.Entry<String,Snapshot> e:PROCESS_ACTIVE.entrySet())
            if(e.getKey().startsWith(prefix)&&e.getValue().active()&&!"INVENTORY".equals(e.getValue().stage))return true;
        return false;
    }
    public boolean hasWriteWork(){return hasActiveWriteWork(directory);}
    /** Do not let a routine history inventory replace the last useful write receipt. */
    public synchronized Snapshot presentation(){
        Snapshot running=latest();
        if(running!=null&&running.active()&&!"INVENTORY".equals(running.stage))return running;
        Snapshot result=null;
        for(Snapshot s:recent.values())if(!"INVENTORY".equals(s.stage)&&(result==null||s.updatedWallMillis>=result.updatedWallMillis))result=s;
        return result;
    }
    private void remember(Snapshot s){recent.put(s.id,s);if(recent.size()>12){Snapshot oldest=Collections.min(recent.values(),Comparator.comparingLong(v->v.updatedWallMillis));recent.remove(oldest.id);}}
    public synchronized String historyDescription(){
        StringBuilder b=new StringBuilder();Snapshot current=latest();if(current!=null)b.append("Bieżący / ostatni stan:\n").append(current.description());
        List<Snapshot> list=new ArrayList<>(recent.values());list.sort((a,c)->Long.compare(c.updatedWallMillis,a.updatedWallMillis));
        for(Snapshot record:list)if(current==null||!record.id.equals(current.id))b.append("\n\n").append(record.description());
        if(!persistenceError.isEmpty())b.append("\n\n").append(persistenceError);
        return b.length()==0?"Brak zapisanych operacji":b.toString();
    }
    private void publish(Snapshot s){latest=s;remember(s);persist(s);changed.accept(s);}
    private void persist(Snapshot s){try{ResearchSessionStore.atomicText(new File(directory,s.id+".json"),s.toJson().toString(2));}
        catch(Exception|Error error){persistenceError="Nie zapisano trwale stanu operacji "+s.id+": "+error;android.util.Log.e("RESEARCH_OPERATION",persistenceError,error);}}
    public static String chain(Throwable e){StringBuilder b=new StringBuilder();Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        while(e!=null&&seen.add(e)){if(b.length()>0)b.append(" <- ");b.append(e);e=e.getCause();}return b.toString();}
    private static String label(String stage){switch(stage){case "INVENTORY":return "Odczyt historii";case "PREPARING":return "Przygotowanie sesji";
        case "QUEUE_DRAIN":return "Domykanie zadań w locie";case "FINALIZING_JSONL":return "Finalizacja dzienników";
        case "BUILDING_ARCHIVE":return "Budowa ZIP";case "VALIDATING_ARCHIVE":return "Sprawdzanie archiwum";
        case "COPYING_DOCUMENT":return "Zapis dokumentu";case "VERIFYING_DOCUMENT":return "Sprawdzanie zapisanego dokumentu";
        case "RECOVERING":return "Odzyskiwanie na kopii";default:return stage;}}
}
