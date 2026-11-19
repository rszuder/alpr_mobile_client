package com.example.alpr_v1.experiment;

import org.json.JSONObject;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Read-only summary of the configuration frozen with the selected archive, never current settings. */
public final class ResearchSessionSummary {
    public final File archive;
    public final JSONObject session;
    public final JSONObject execution;
    public final Instant date;
    public final boolean creationDate, complete, metadataAvailable, approximateDuration;
    public final long durationMillis;

    public ResearchSessionSummary(File archive, JSONObject session) {
        this.archive = archive;
        this.session = copy(session);
        metadataAvailable = this.session.length() > 0;
        JSONObject execution = this.session.optJSONObject("execution");
        this.execution = execution == null ? new JSONObject() : execution;
        Instant start = instant(this.session.optString("started_at", ""));
        creationDate = start == null;
        date = start == null ? instant(this.session.optString("created_at", "")) : start;
        complete = this.session.optBoolean("collection_complete", false);
        long first = this.session.optLong("started_elapsed_nanos", -1L);
        long last = this.session.optLong("finished_elapsed_nanos", -1L);
        Instant end = instant(this.session.optString("finished_at", ""));
        if (first >= 0 && last >= first) {
            durationMillis = (last - first) / 1_000_000L; approximateDuration = false;
        } else if (complete && !"process_interrupted".equals(this.session.optString("completion_reason"))
                && start != null && end != null && !end.isBefore(start)) {
            durationMillis = java.time.Duration.between(start, end).toMillis(); approximateDuration = true;
        } else { durationMillis = -1L; approximateDuration = false; }
    }

    public String value(String key) {
        String frozen = execution.optString(key, "");
        return frozen.isEmpty() || "null".equals(frozen) ? clean(session.optString(key, "")) : frozen;
    }

    public String dateText(ZoneId zone) {
        if (date == null) return "Data nieznana";
        return (creationDate ? "Utworzono " : "") + DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm:ss",
                Locale.forLanguageTag("pl-PL")).withZone(zone).format(date);
    }

    public String durationText() {
        if (durationMillis < 0) return "Czas nieustalony";
        long seconds = durationMillis / 1_000L;
        String value = seconds < 60 ? seconds + " s"
                : seconds < 3_600 ? String.format(Locale.ROOT,"%d min %02d s",seconds/60,seconds%60)
                : String.format(Locale.ROOT,"%d h %02d min %02d s",seconds/3600,(seconds/60)%60,seconds%60);
        if (durationMillis > 0 && durationMillis < 1_000) value = "< 1 s";
        return approximateDuration ? "≈ " + value : value;
    }

    public static List<ResearchSessionSummary> load(List<File> archives) {
        List<ResearchSessionSummary> result = new ArrayList<>();
        for (File file : archives) result.add(new ResearchSessionSummary(file, readMetadata(file)));
        result.sort(Comparator.comparing((ResearchSessionSummary item) -> item.date,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(item -> item.archive.getName()));
        return result;
    }

    public File directory(){return archive.getParentFile().getParentFile();}
    public boolean hasArchive(){return archive.isFile();}
    public String partialReason(){
        if(complete)return "";
        if(!metadataAvailable)return "Metadane nieczytelne lub niedostępne; zachowano katalog: "+directory();
        List<String> reasons=new ArrayList<>();
        for(String key:new String[]{"last_write_error","completion_reason"}){String value=session.optString(key);if(!value.isEmpty())reasons.add(value);}
        for(String key:new String[]{"scene_evidence_complete","mt_detection_roles_complete"})
            if(session.has(key)&&!session.optBoolean(key))reasons.add((key.equals("mt_detection_roles_complete")?"Niepełny dowód wskazań MT":"Niepełny dowód scen")+" ("+key+"=false)");
        JSONObject f6=session.optJSONObject("f6_evidence");if(f6!=null&&!f6.optBoolean("complete"))reasons.add("Zapis F6 częściowy (f6_evidence.complete=false)");
        if(!hasArchive())reasons.add("Brak zamkniętego archiwum; zachowano pliki sesji");
        return reasons.isEmpty()?"Nie potwierdzono kompletności zapisu":String.join("; ",reasons);
    }
    public boolean hasF6Evidence(){return Final180Evidence.enabled(session);}

    /** On-demand diagnostics of the selected, closed archive. Call on the history IO executor.
     * Never recovers, finalizes, changes completion flags, or uses current camera preferences. */
    public String evidenceBreakdown(){
        if(!hasF6Evidence())return "";
        if(!hasArchive())return "Rozliczenie F6: nieustalone — brak zamkniętego archiwum. Pliki sesji zachowano.";
        long size=archive.length(),modified=archive.lastModified();
        try(ZipFile zip=new ZipFile(archive)){
            JSONObject saved=Final180ArchiveValidator.json(zip,"session.json");
            String id=session.getString("session_id");
            Final180ArchiveValidator.require(id.equals(saved.getString("session_id"))&&Final180Evidence.enabled(saved),"History session identity");
            JSONObject hashes=Final180ArchiveValidator.json(zip,"manifest.json").getJSONObject("entry_sha256");
            diagnosticChecksum(zip,hashes,"session.json");
            long[] events=new long[9],roles=new long[5],frames=new long[5];
            String[] names={"MT_STARTED","MT_RETURNED","MT_DECODED","MT_FAILED","MZ_STARTED","MZ_RETURNED","MZ_FAILED","FRAME_OPENED","FRAME_FINISHED"};
            long[] sequence={0};
            diagnosticRows(zip,hashes,Final180Evidence.FILE,row->{
                Final180ArchiveValidator.require(id.equals(row.getString("session_id"))&&Final180Evidence.SCHEMA.equals(row.getString("schema")),"Lifecycle identity");
                Final180ArchiveValidator.require(row.getLong("event_sequence")==++sequence[0],"Lifecycle sequence");
                String event=row.getString("event");for(int i=0;i<names.length;i++)if(names[i].equals(event))events[i]++;
            });
            diagnosticRows(zip,hashes,F5aEvidence.ROLES,row->{
                Final180ArchiveValidator.require(id.equals(row.getString("session_id"))&&"alpr.mt_detection_roles.v1".equals(row.getString("schema")),"Role identity");
                String proof=row.getString("role_proof_status");
                Final180ArchiveValidator.require("COMPLETE".equals(proof)||"INCOMPLETE".equals(proof),"Role proof status");
                roles["COMPLETE".equals(proof)?0:1]++;
                if(row.getBoolean("stale_or_cancelled"))roles["scene_superseded".equals(row.getString("cancel_reason"))?2:3]++;
                if(!row.optString("execution_error").isEmpty()||!row.optString("processing_error").isEmpty())roles[4]++;
            });
            diagnosticRows(zip,hashes,TvBboxEvidence.INDEX,row->{
                Final180ArchiveValidator.require(id.equals(row.getString("session_id"))&&TvBboxEvidence.SCHEMA.equals(row.getString("schema")),"Frame identity");
                frames[0]++;
                for(int i=0;i<2;i++){
                    JSONObject image=row.getJSONObject(i==0?"source_image":"crop_image");
                    String status=image.getString("status");
                    if("WRITTEN".equals(status)&&!image.isNull("entry")&&zip.getEntry(image.getString("entry"))!=null)frames[i+1]++;
                    else frames[3]++;
                }
                if(!row.optString("processing_error").isEmpty())frames[4]++;
            });
            Final180ArchiveValidator.require(size==archive.length()&&modified==archive.lastModified(),"Archive changed during history read");
            JSONObject f6=saved.optJSONObject("f6_evidence");
            return "Rozliczenie zapisanych dowodów F6\n"
                    +"MT rozpoczęte / zwrócone / zdekodowane: "+events[0]+" / "+events[1]+" / "+events[2]+"\n"
                    +"MZ rozpoczęte / zwrócone: "+events[4]+" / "+events[5]+"\n"
                    +"Błędy backendu MT / MZ: "+events[3]+" / "+events[6]+"\n"
                    +"Dowód ról MT COMPLETE / INCOMPLETE: "+roles[0]+" / "+roles[1]+"\n"
                    +"Unieważnione po zmianie sceny (scene_superseded): "+roles[2]+"\n"
                    +"Inne anulowania MT: "+roles[3]+"; rekordy ról z błędem: "+roles[4]+"\n"
                    +"Klatki otwarte / domknięte: "+events[7]+" / "+events[8]+"\n"
                    +"Obrazy wg indeksu — pełne / crop: "+frames[1]+" / "+frames[2]+"; brak lub niezapisane: "+frames[3]+"\n"
                    +"Klatki z błędem przetwarzania: "+frames[4]+"\n"
                    +"Straty kolejki próbek / telemetrii: "+counter(saved,"dropped_sample_count")+" / "+counter(saved,"dropped_telemetry_count")+"\n"
                    +"Straty dziennika F6 / błędy projekcji: "+counter(f6,"lifecycle_loss_count")+" / "+counter(f6,"projection_error_count")+"\n"
                    +"Nieustalona utrata dowodów: "+(f6==null||!f6.has("unlocated_loss_possible")?"nieustalono":f6.getBoolean("unlocated_loss_possible")?"możliwa":"nie zadeklarowano")+"\n"
                    +"Unieważnienie wyniku nie oznacza utraty obrazu. Niepełny FINAL pozostaje niepełny.\n"
                    +"Sprawdzono sumy dzienników; liczby obrazów pochodzą z indeksu. To nie jest pełna kontrola bajtów obrazów ani ocena fotografii.";
        }catch(Exception error){return "Rozliczenie F6: nieustalone — "+ResearchOperationState.chain(error)+"\nNie zastąpiono brakujących dowodów zerami; sesja i jej status pozostają bez zmian.";}
    }
    private static String counter(JSONObject value,String key){
        Object number=value==null?null:value.opt(key);
        return (number instanceof Integer||number instanceof Long)&&((Number)number).longValue()>=0?number.toString():"nieustalono";
    }
    private static void diagnosticRows(ZipFile zip,JSONObject hashes,String name,Final180ArchiveValidator.JsonRow consumer)throws Exception{
        diagnosticChecksum(zip,hashes,name);
        Final180ArchiveValidator.rows(zip,name,consumer);
    }
    private static void diagnosticChecksum(ZipFile zip,JSONObject hashes,String name)throws Exception{
        java.util.zip.ZipEntry entry=zip.getEntry(name);
        Final180ArchiveValidator.require(entry!=null,"Missing "+name);
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        java.util.zip.CRC32 crc=new java.util.zip.CRC32();
        try(InputStream in=zip.getInputStream(entry)){
            byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){digest.update(buffer,0,count);crc.update(buffer,0,count);}
        }
        StringBuilder actual=new StringBuilder();for(byte b:digest.digest())actual.append(String.format(Locale.ROOT,"%02x",b&255));
        Final180ArchiveValidator.require(actual.toString().equals(hashes.getString(name))&&crc.getValue()==entry.getCrc(),"History journal checksum: "+name);
    }
    /** Durable registry inventory only. Never finalizes or recovers a session while listing it. */
    public static List<ResearchSessionSummary> catalog(File root){
        List<ResearchSessionSummary> result=new ArrayList<>();File[] dirs=root.listFiles(File::isDirectory);
        if(dirs!=null)for(File dir:dirs){
            File[] archives=new File(dir,"final").listFiles((d,n)->n.endsWith(".alprsession"));
            if(archives!=null&&archives.length>0){for(File archive:archives)result.add(new ResearchSessionSummary(archive,readMetadata(archive)));}
            else try{File metadata=new File(dir,"session.json");if(!metadata.isFile()||metadata.length()>2L*1024*1024)throw new java.io.IOException("SESSION_METADATA_UNAVAILABLE");
                JSONObject json=new JSONObject(new String(Files.readAllBytes(metadata.toPath()),StandardCharsets.UTF_8));
                result.add(new ResearchSessionSummary(new File(dir,"final/"+json.getString("session_id")+".alprsession"),json));
            }catch(Exception invalid){result.add(new ResearchSessionSummary(new File(dir,"final/"+dir.getName()+".alprsession"),null));}
        }
        result.sort(Comparator.comparing((ResearchSessionSummary item)->item.date,Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }
    private static JSONObject copy(JSONObject source){try{return source==null?new JSONObject():new JSONObject(source.toString());}catch(Exception invalid){throw new IllegalArgumentException(invalid);}}
    private static JSONObject readMetadata(File archive) {
        // An intact archive remains selectable even if the companion metadata file was lost.
        try (ZipFile zip = new ZipFile(archive)) {
            java.util.zip.ZipEntry entry = zip.getEntry("session.json");
            if (entry == null) return new JSONObject();
            try (InputStream input = zip.getInputStream(entry); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1){if(bytes.size()+count>2*1024*1024)throw new java.io.IOException("SESSION_METADATA_TOO_LARGE");bytes.write(buffer, 0, count);}
                return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            }
        } catch (Exception ignored) { return new JSONObject(); }
    }

    private static Instant instant(String value) {
        try { return Instant.parse(value); } catch (Exception ignored) { return null; }
    }
    private static String clean(String value) { return "null".equals(value) ? "" : value; }
}
