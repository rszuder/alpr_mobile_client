package com.example.alpr_v1.ui.readiness;

import android.os.Bundle;
import java.util.UUID;

/** Navigation state only; never stored in research preferences or serialized into a session. */
public final class ReadinessReturnContext {
    public static final String EXTRA="readiness_return_context", EXTRA_ACTION="readiness_action", EXTRA_MODE="readiness_calibrator_mode";
    public final String origin="OPTIONS_READINESS",returnMode="SETTINGS_SUMMARY";
    public final String issueId,requestInstanceId;
    public final int sectionId,controlId,scrollAnchor;
    public ReadinessReturnContext(String issueId,int section,int control,int scroll) {
        this(issueId,section,control,scroll,UUID.randomUUID().toString());
    }
    private ReadinessReturnContext(String issue,int section,int control,int scroll,String request) {
        issueId=issue;sectionId=section;controlId=control;scrollAnchor=scroll;requestInstanceId=request;
    }
    public Bundle toBundle() {
        Bundle b=new Bundle();b.putString("origin",origin);b.putString("returnMode",returnMode);b.putString("issueId",issueId);
        b.putString("requestInstanceId",requestInstanceId);b.putInt("sectionId",sectionId);b.putInt("controlId",controlId);b.putInt("scrollAnchor",scrollAnchor);return b;
    }
    public static ReadinessReturnContext fromBundle(Bundle b) {
        if(b==null||!"OPTIONS_READINESS".equals(b.getString("origin"))||!"SETTINGS_SUMMARY".equals(b.getString("returnMode")))return null;
        String issue=b.getString("issueId"),request=b.getString("requestInstanceId");
        if(issue==null||request==null)return null;
        return new ReadinessReturnContext(issue,b.getInt("sectionId"),b.getInt("controlId"),b.getInt("scrollAnchor"),request);
    }
}
