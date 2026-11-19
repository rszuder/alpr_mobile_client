package com.example.alpr_v1.ui.readiness;

public final class ReadinessFinding {
    public enum Domain { CONFIG, DEVICE, SESSION_PREPARATION }
    public enum Status { OK, NEEDS_CHANGE, NOT_CHECKABLE_YET, ADVISORY, IN_PROGRESS, NOT_APPLICABLE }
    public final String issueId,title,explanation,evidenceSource,snapshotIdentity;
    public final Domain domain;
    public final Status status;
    public final boolean blocksByExistingRule;
    public final ReadinessTarget target;

    public ReadinessFinding(String id,Domain domain,Status status,String title,String explanation,
                            boolean blocks,ReadinessTarget target,String source,String snapshot) {
        issueId=id;this.domain=domain;this.status=status;this.title=title;this.explanation=explanation;
        blocksByExistingRule=blocks;this.target=target;evidenceSource=source;snapshotIdentity=snapshot;
    }
}
