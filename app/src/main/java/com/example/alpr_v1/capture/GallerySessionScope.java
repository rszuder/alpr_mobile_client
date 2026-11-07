package com.example.alpr_v1.capture;

import com.example.alpr_v1.metrics.ResearchArchive;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Read model only. Never owns a run, restores RAM crops, or writes human decisions. */
public final class GallerySessionScope {
    public final String sessionId, state, countSource, missingReason;
    public final Long savedCount;

    private GallerySessionScope(String id,String state,Long count,String source,String reason) {
        this.sessionId=id;this.state=state;this.savedCount=count;this.countSource=source;this.missingReason=reason;
    }
    public static GallerySessionScope known(String id,String state,long count,String source) {
        if(id==null||id.isEmpty()||count<0)throw new IllegalArgumentException("Invalid gallery session count");
        return new GallerySessionScope(id,state,count,source,"");
    }
    public static GallerySessionScope unavailable(String id,String state,String reason) {
        return new GallerySessionScope(id==null?"":id,state==null?"UNKNOWN":state,null,"UNAVAILABLE",reason);
    }
    public boolean contains(String id) {return !sessionId.isEmpty()&&sessionId.equals(id);}
    public String countText(){return savedCount==null?"N/A":Long.toString(savedCount);}
    public String stateText(){
        switch(state){case "PREPARED":return "Przygotowana";case "RUNNING":return "Bieżąca";
            case "FINALIZING":return "Zapis w toku";case "COMPLETED":return "Zakończona";
            case "PARTIAL":return "Niekompletna";case "ERROR":return "Błąd zapisu";default:return "Podglądy RAM / stan N/A";}
    }
    public String label(){return (sessionId.isEmpty()?"Brak wybranej sesji":sessionId)+" · "+stateText();}
    public String header(int retained,int reviewed,int limit){
        return label()+"\nZapisane obrazy tej sesji: "+countText()
                +(savedCount==null?" — "+missingReason:"")
                +"\nPodglądy tej sesji w RAM: "+retained+" (limit "+limit+")"
                +"\nZweryfikowane podglądy RAM: "+reviewed+" z "+retained;
    }

    /** Local immutable archive is checked off the UI thread; missing metadata is never zero. */
    public static GallerySessionScope readStored(File directory) {
        String id=directory.getName(),state="UNKNOWN";
        try {
            File sidecar=new File(directory,"session.json");
            if(sidecar.isFile()){
                JSONObject hint=new JSONObject(read(new FileInputStream(sidecar),1024*1024));
                if(!id.equals(hint.getString("session_id")))throw new IOException("Niezgodna tożsamość sesji");
                state=hint.getString("state");
            }
            File archive=new File(directory,"final/"+id+".alprsession");
            if(!archive.isFile())return unavailable(id,state,"Archiwum zapisu niedostępne");
            ResearchArchive.verifyEntryHashes(archive);
            try(ZipFile zip=new ZipFile(archive)){
                Set<String> names=new HashSet<>();long images=0;
                Enumeration<? extends ZipEntry> entries=zip.entries();
                while(entries.hasMoreElements()){
                    ZipEntry entry=entries.nextElement();
                    if(!names.add(entry.getName()))throw new IOException("Powtórzony wpis archiwum");
                    if(entry.getName().startsWith("samples/crops/")&&entry.getName().endsWith(".jpg"))images++;
                }
                JSONObject session=new JSONObject(read(zip.getInputStream(required(zip,"session.json")),1024*1024));
                JSONObject manifest=new JSONObject(read(zip.getInputStream(required(zip,"manifest.json")),4*1024*1024));
                if(!id.equals(session.getString("session_id"))||session.getLong("crop_count")!=images
                        ||manifest.getLong("crop_count")!=images)throw new IOException("Sprzeczny licznik zapisów");
                String annotations=read(zip.getInputStream(required(zip,"samples/annotations.jsonl")),32*1024*1024);
                Set<String> captures=new HashSet<>();
                for(String line:annotations.split("\n"))if(!line.trim().isEmpty()){
                    JSONObject row=new JSONObject(line);String capture=row.getString("capture_id");
                    if(!id.equals(row.getString("session_id"))||!captures.add(capture)
                            ||!names.contains("samples/crops/"+capture+".jpg"))throw new IOException("Niezgodny zapis próbki");
                }
                if(captures.size()!=images)throw new IOException("Niepełny indeks obrazów");
                return known(id,session.getString("state"),images,"VERIFIED_SESSION_ARCHIVE");
            }
        }catch(Exception error){return unavailable(id,state,"Nie można potwierdzić zapisów: "+error.getClass().getSimpleName());}
    }
    private static ZipEntry required(ZipFile zip,String name)throws IOException{
        ZipEntry entry=zip.getEntry(name);if(entry==null)throw new IOException("Missing "+name);return entry;
    }
    private static String read(InputStream input,int maximum)throws IOException{
        try(InputStream in=input;ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){if(bytes.size()+n>maximum)throw new IOException("Metadata budget");bytes.write(buffer,0,n);}
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }
}
