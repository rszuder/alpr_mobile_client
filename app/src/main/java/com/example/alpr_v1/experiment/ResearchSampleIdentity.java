package com.example.alpr_v1.experiment;

/** Stable research identity never derived from recognized text. */
public final class ResearchSampleIdentity {
    public final String attemptId, subjectKey;
    public final long sceneGeneration, entityId, vehicleTrackId, plateTrackId;
    public ResearchSampleIdentity(String attemptId, String sessionId, long sceneGeneration,
                                  long entityId, long vehicleTrackId, long plateTrackId) {
        this.attemptId = attemptId; this.sceneGeneration = sceneGeneration;
        this.entityId = entityId; this.vehicleTrackId = vehicleTrackId; this.plateTrackId = plateTrackId;
        subjectKey = subjectKey(sessionId,sceneGeneration,entityId,plateTrackId,attemptId);
    }
    public static String subjectKey(String session,long scene,long entity,long track,String attempt) {
        return session + "/sg-" + scene + (entity > 0 ? "/entity-" + entity
                : track > 0 ? "/track-" + track : "/attempt-" + attempt);
    }
}
