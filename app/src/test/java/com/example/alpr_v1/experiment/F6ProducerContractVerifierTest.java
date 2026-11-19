package com.example.alpr_v1.experiment;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Contract semantics, independent of the stress fixture's number of rows or ZIP size. */
public class F6ProducerContractVerifierTest {
    JSONObject notRun()throws Exception{return Final180EvidenceTest.notRunMp(1);}

    @Test public void allRequiredNotRunFieldsAreChecked()throws Exception{
        JSONObject good=notRun();F6ProducerContractVerifier.mp(good);
        for(String key:F6ProducerContractVerifier.keys(good)){
            JSONObject bad=new JSONObject(good.toString());bad.remove(key);
            Exception e=assertThrows("Missing "+key,Exception.class,()->F6ProducerContractVerifier.mp(bad));
            assertTrue(e.toString(),e.toString().contains("MP_OBSERVATION_FIELDS_MISSING"));
        }
    }
    @Test public void notRunIsDistinctFromExecutedZero()throws Exception{
        for(String key:new String[]{"raw_count","rejected_class_count","rejected_class_ids","not_run_reason"}){
            JSONObject bad=notRun();bad.put(key,key.equals("rejected_class_ids")?new JSONArray():key.equals("not_run_reason")?"":0);
            assertThrows(key,Exception.class,()->F6ProducerContractVerifier.mp(bad));
        }
        JSONObject executed=notRun().put("execution_status","EXECUTED").put("not_run_reason",JSONObject.NULL)
                .put("raw_count",0).put("rejected_class_count",0).put("rejected_class_ids",new JSONArray());
        F6ProducerContractVerifier.mp(executed);
    }
    @Test public void typesAndStampDomainsAreNotCoerced()throws Exception{
        for(Object wrong:new Object[]{"1",1.5,true,-1,JSONObject.NULL}){
            JSONObject bad=notRun().put("trace_frame_id",wrong);
            assertThrows(Exception.class,()->F6ProducerContractVerifier.mp(bad));
        }
        for(String fault:new String[]{"missing","extra","float","domain"}){
            JSONObject bad=notRun(),stamp=bad.getJSONObject("source_stamp");
            if(fault.equals("missing"))stamp.remove("source_sequence");
            if(fault.equals("extra"))stamp.put("guessed_id",1);
            if(fault.equals("float"))stamp.put("source_timestamp_nanos",1.5);
            if(fault.equals("domain"))stamp.put("source_timestamp_domain","WALL_CLOCK");
            assertThrows(fault,Exception.class,()->F6ProducerContractVerifier.mp(bad));
        }
        assertEquals(9007199254740994L,notRun().getJSONObject("source_stamp").getLong("source_timestamp_nanos"));
    }
    @Test public void executedPopulationAndRoiGeometryMustBeConsistent()throws Exception{
        JSONObject good=notRun().put("execution_status","EXECUTED").put("not_run_reason",JSONObject.NULL)
                .put("raw_count",3).put("rejected_class_count",1).put("rejected_class_ids",new JSONArray().put(9));
        JSONObject roi=new JSONObject("{left:1,top:2,right:30,bottom:40,entity_id:1,vehicle_track_id:1}");
        good.getJSONArray("selected_rois").put(roi);F6ProducerContractVerifier.mp(good);
        for(String fault:new String[]{"count","raw","class-type","roi-inverted","roi-type","roi-id"}){
            JSONObject bad=new JSONObject(good.toString());
            if(fault.equals("count"))bad.put("rejected_class_count",0);
            if(fault.equals("raw"))bad.put("raw_count",0);
            if(fault.equals("class-type"))bad.put("rejected_class_ids",new JSONArray().put("9"));
            JSONObject selected=bad.getJSONArray("selected_rois").getJSONObject(0);
            if(fault.equals("roi-inverted"))selected.put("right",0);
            if(fault.equals("roi-type"))selected.put("bottom",40.5);
            if(fault.equals("roi-id"))selected.remove("entity_id");
            assertThrows(fault,Exception.class,()->F6ProducerContractVerifier.mp(bad));
        }
    }
    @Test public void failedMpDoesNotAcquireInventedCounts()throws Exception{
        JSONObject failed=notRun().put("execution_status","FAILED");
        for(String key:new String[]{"not_run_reason","raw_count","rejected_class_count","rejected_class_ids"})failed.remove(key);
        F6ProducerContractVerifier.mp(failed); // Existing FAILED contract does not require a decoded population.
        failed.put("execution_status","UNKNOWN");assertThrows(Exception.class,()->F6ProducerContractVerifier.mp(failed));
    }
}
