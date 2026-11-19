package com.example.alpr_v1.tracking;

import com.example.alpr_v1.continuity.*;
import com.example.alpr_v1.domain.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DynamicPlateGeometryGateTest {
    private static final long S=1_000_000_000L;
    private static final DynamicVehicleIdentity.Stamp OWNER=new DynamicVehicleIdentity.Stamp(1,1,1,1,1);
    private static final NormalizedBounds BODY=new NormalizedBounds(.1f,.1f,.9f,.9f);
    private static ContinuityStamp source(long millis){return new ContinuityStamp(1,1,0,millis+1,S+millis*1_000_000L,SourceTimestampDomain.CAMERAX_SENSOR);}
    private static NormalizedQuad quad(float l,float t,float r,float b){return new NormalizedQuad(new float[]{l,t,r,t,r,b,l,b});}
    private static NormalizedQuad full(){return quad(.4f,.6f,.6f,.65f);}
    private static DynamicPlateGeometryGate.Decision admit(DynamicPlateGeometryGate gate,long millis,NormalizedQuad q) {
        return gate.evaluate(OWNER,source(millis),q.bounds(),q,BODY,true);
    }
    private static DynamicPlateGeometryGate seeded() {
        DynamicPlateGeometryGate gate=new DynamicPlateGeometryGate();
        DynamicPlateGeometryGate.Decision first=admit(gate,0,full());assertTrue(first.accepted());gate.commit(first);return gate;
    }
    @Test public void smallBorderJitterUsesTheFullReferenceInsteadOfCroppingIt() {
        DynamicPlateGeometryGate gate=seeded();NormalizedQuad border=quad(.4f,.604f,.6f,.646f);
        DynamicPlateGeometryGate.Decision decision=admit(gate,100,border);
        assertEquals(DynamicPlateGeometryGate.Reason.ACCEPTED_REFERENCE,decision.reason);assertTrue(decision.accepted());
        assertEquals(1f,DynamicPlateGeometryGate.coverage(full(),decision.quad),.0001f);
        assertEquals(.6f,decision.quad.bounds().top,.0001f);assertEquals(.65f,decision.quad.bounds().bottom,.0001f);
        assertTrue(gate.commit(decision));
        assertEquals(DynamicPlateGeometryGate.Reason.PARTIAL_REFERENCE,admit(gate,200,quad(.4f,.6f,.58f,.65f)).reason);
    }
    @Test public void referenceFallbackCannotHideMissingRowsOrAnUnrelatedDetectorBox() {
        DynamicPlateGeometryGate gate=new DynamicPlateGeometryGate();NormalizedQuad two=quad(.4f,.5f,.55f,.6f);
        gate.commit(admit(gate,0,two));
        assertEquals(DynamicPlateGeometryGate.Reason.PARTIAL_REFERENCE,admit(gate,100,quad(.4f,.5f,.55f,.55f)).reason);
        DynamicPlateGeometryGate one=seeded();NormalizedQuad shifted=quad(.7f,.604f,.9f,.646f);
        assertFalse(admit(one,100,shifted).accepted());
    }
    @Test public void partialQuadIsRejectedEvenWhenItsDetectorBoxAlsoShrinks() {
        DynamicPlateGeometryGate gate=seeded();
        DynamicPlateGeometryGate.Decision partial=admit(gate,1000,quad(.4f,.6f,.5f,.65f));
        assertEquals(DynamicPlateGeometryGate.Reason.PARTIAL_REFERENCE,partial.reason);assertEquals(.5f,partial.referenceCoverage,.0001f);
        assertTrue(admit(gate,2000,full()).accepted());
    }
    @Test public void repeatedSmallCorrectionsCannotGraduallyEatTheFullReference() {
        DynamicPlateGeometryGate gate=seeded();
        DynamicPlateGeometryGate.Decision small=admit(gate,100,quad(.4f,.6f,.596f,.65f));
        assertTrue(small.accepted());gate.commit(small);
        DynamicPlateGeometryGate.Decision next=admit(gate,200,quad(.4f,.6f,.588f,.65f));
        assertEquals(DynamicPlateGeometryGate.Reason.PARTIAL_REFERENCE,next.reason);
        assertEquals(.94f,next.referenceCoverage,.0001f);
    }
    @Test public void fullMeasurementCanExpandAnInitiallySmallReference() {
        DynamicPlateGeometryGate gate=new DynamicPlateGeometryGate();
        DynamicPlateGeometryGate.Decision first=admit(gate,0,quad(.4f,.6f,.5f,.65f));gate.commit(first);
        DynamicPlateGeometryGate.Decision expanded=admit(gate,100,full());assertTrue(expanded.accepted());gate.commit(expanded);
        assertEquals(DynamicPlateGeometryGate.Reason.PARTIAL_REFERENCE,admit(gate,200,quad(.4f,.6f,.5f,.65f)).reason);
    }
    @Test public void matchingQuadBoundsAreRequiredBeforeTheFirstReference() {
        DynamicPlateGeometryGate gate=new DynamicPlateGeometryGate();NormalizedQuad part=quad(.4f,.6f,.5f,.65f);
        assertEquals(DynamicPlateGeometryGate.Reason.BBOX_MISMATCH,
                gate.evaluate(OWNER,source(0),full().bounds(),part,BODY,true).reason);
    }
    @Test public void oversizedAndOutsideVehicleMeasurementsAreRejected() {
        DynamicPlateGeometryGate gate=seeded();
        assertEquals(DynamicPlateGeometryGate.Reason.EXCESSIVE_EXPANSION,admit(gate,100,quad(.2f,.4f,.8f,.8f)).reason);
        NormalizedQuad outside=quad(.01f,.02f,.09f,.06f);
        assertEquals(DynamicPlateGeometryGate.Reason.OUTSIDE_VEHICLE,admit(gate,200,outside).reason);
    }
    @Test public void realPixelReferencesInterpolateOnlyBetweenSupportedSamples() {
        DynamicPlateGeometryGate gate=seeded();
        gate.observe(OWNER,source(100),BODY,quad(.41f,.6f,.61f,.65f));
        gate.observe(OWNER,source(300),BODY,quad(.43f,.6f,.63f,.65f));
        NormalizedQuad middle=quad(.42f,.6f,.62f,.65f);
        assertTrue(gate.evaluate(OWNER,source(200),middle.bounds(),middle,null,true).accepted());
        gate.observe(OWNER,source(1000),BODY,middle);
        assertEquals(DynamicPlateGeometryGate.Reason.WAITING_FOR_REFERENCE,
                gate.evaluate(OWNER,source(600),middle.bounds(),middle,null,true).reason);
    }
    @Test public void missingPixelsCannotBeRelabeledAsCurrentGeometryWithoutMeasuredMp() {
        DynamicPlateGeometryGate gate=seeded();
        assertEquals(DynamicPlateGeometryGate.Reason.WAITING_FOR_REFERENCE,
                gate.evaluate(OWNER,source(1000),full().bounds(),full(),null,true).reason);
    }
    @Test public void currentMeasuredVehiclePairMovesTheReferenceDuringBootstrap() {
        DynamicPlateGeometryGate gate=seeded();NormalizedBounds moved=new NormalizedBounds(.12f,.1f,.92f,.9f);
        NormalizedQuad plate=quad(.42f,.6f,.62f,.65f);
        assertTrue(gate.evaluate(OWNER,source(1000),plate.bounds(),plate,moved,true).accepted());
        assertEquals(DynamicPlateGeometryGate.Reason.PARTIAL_REFERENCE,
                gate.evaluate(OWNER,source(1100),quad(.42f,.6f,.52f,.65f).bounds(),quad(.42f,.6f,.52f,.65f),moved,true).reason);
    }
    @Test public void reversedWindingAndTwoRowPlatesRemainSupported() {
        NormalizedQuad reversed=new NormalizedQuad(new float[]{.4f,.6f,.4f,.65f,.6f,.65f,.6f,.6f});
        assertEquals(1,DynamicPlateGeometryGate.coverage(full(),reversed),.0001f);
        assertTrue(admit(new DynamicPlateGeometryGate(),0,quad(.4f,.5f,.55f,.6f)).accepted());
        assertFalse(DynamicPlateGeometryGate.valid(new NormalizedQuad(new float[]{.4f,.6f,.6f,.65f,.6f,.6f,.4f,.65f})));
    }
    @Test public void rotatedAndPerspectiveReferencesUsePolygonCoverageInsteadOfAspectLimits() {
        DynamicPlateGeometryGate gate=seeded();
        NormalizedQuad perspective=new NormalizedQuad(new float[]{.42f,.55f,.61f,.63f,.58f,.68f,.4f,.6f});
        assertTrue(DynamicPlateGeometryGate.valid(perspective));
        gate.observe(OWNER,source(100),BODY,perspective);
        assertTrue(gate.evaluate(OWNER,source(100),perspective.bounds(),perspective,BODY,true).accepted());
    }
    @Test public void sceneChangesAndRevokedOwnersCannotReuseTheReference() {
        DynamicPlateGeometryGate gate=seeded();
        assertEquals(DynamicPlateGeometryGate.Reason.STALE_OR_UNOWNED,
                gate.evaluate(OWNER,source(100),full().bounds(),full(),BODY,false).reason);
        ContinuityStamp newer=new ContinuityStamp(2,2,0,500,3*S,SourceTimestampDomain.CAMERAX_SENSOR);
        gate.observe(OWNER,newer,BODY,null);
        assertEquals(DynamicPlateGeometryGate.Reason.STALE_OR_UNOWNED,admit(gate,200,full()).reason);
    }
    @Test public void contextChangeBetweenDecisionAndCommitCannotInstallOldGeometry() {
        DynamicPlateGeometryGate gate=new DynamicPlateGeometryGate();
        DynamicPlateGeometryGate.Decision decision=admit(gate,0,full());assertTrue(decision.accepted());
        gate.observe(OWNER,new ContinuityStamp(2,2,0,500,3*S,SourceTimestampDomain.CAMERAX_SENSOR),BODY,null);
        assertFalse(gate.commit(decision));
    }
}
