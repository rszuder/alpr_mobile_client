package com.example.alpr_v1.experiment;

import android.app.Application;
import android.os.SystemClock;
import com.example.alpr_v1.metrics.MetricsCollector;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** Retained production owner and deadline with a deterministic scheduler, no device/session data. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class AnalysisTimerRetainedTest {
    ResearchSessionViewModel owner;
    MetricsCollector metrics;
    final List<Runnable> callbacks=new ArrayList<>();
    final List<String> expired=new ArrayList<>();
    androidx.lifecycle.Observer<String> observer;
    @Before public void setup()throws Exception{
        owner=new ResearchSessionViewModel(RuntimeEnvironment.getApplication());metrics=new MetricsCollector();
        Field field=ResearchSessionViewModel.class.getDeclaredField("timer");field.setAccessible(true);
        field.set(owner,new ExperimentDeadline(SystemClock::elapsedRealtimeNanos,(task,delay)->{callbacks.add(task);return ()->{};}));
        observer=expired::add;owner.timerExpired.observeForever(observer);
    }
    @After public void close()throws Exception{
        owner.timerExpired.removeObserver(observer);owner.onCleared();
        Field field=ResearchSessionViewModel.class.getDeclaredField("finalizer");field.setAccessible(true);
        ((ExecutorService)field.get(owner)).awaitTermination(5,TimeUnit.SECONDS);
    }
    void start(TimerConfig config){
        long wall=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtimeNanos();
        metrics.startMeasurementSession(wall,elapsed,System.nanoTime());owner.startOrdinaryTimer(config,wall,elapsed,metrics);
    }
    void advance(int seconds){ShadowSystemClock.advanceBy(Duration.ofSeconds(seconds));}
    void deliver(){callbacks.get(callbacks.size()-1).run();ShadowLooper.shadowMainLooper().idle();}
    @Test public void ordinaryDeadlineEndsAdmissionWithoutUiTickOrResearchArchive(){
        start(TimerConfig.of(true,5));String id=owner.timerState().id;
        assertTrue(owner.timerState().running);assertTrue(metrics.isMeasurementSessionActive());
        assertEquals(ExperimentSession.State.IDLE,owner.experiment.state());assertNull(owner.store());
        advance(6);assertFalse(metrics.isMeasurementSessionActive());deliver();
        assertFalse(owner.timerState().running);assertTrue(owner.timerState().expired);assertEquals(Collections.singletonList(id),expired);
        deliver();assertEquals(1,expired.size());assertEquals(ExperimentSession.State.IDLE,owner.experiment.state());assertNull(owner.store());
    }
    @Test public void offKeepsPresetAndNeverSchedulesAutomaticStop(){
        start(TimerConfig.of(false,300));assertEquals(-1,owner.timerState().deadlineElapsedNanos);
        assertEquals(300,owner.timerState().durationSeconds);assertTrue(callbacks.isEmpty());advance(600);
        assertTrue(metrics.isMeasurementSessionActive());assertTrue(owner.timerState().running);
    }
    @Test public void reattachingPresentationNeverExtendsDeadline(){
        start(TimerConfig.of(true,60));long at=owner.timerState().deadlineElapsedNanos;String id=owner.timerState().id;
        advance(12);owner.armTimer(metrics);owner.armTimer(metrics);
        assertEquals(at,owner.timerState().deadlineElapsedNanos);assertEquals(id,owner.timerState().id);assertEquals(1,callbacks.size());
    }
    @Test public void stopAndNewRunRejectTheOldCallbackAndGiveFullPresetAgain(){
        start(TimerConfig.of(true,5));Runnable old=callbacks.get(0);String first=owner.timerState().id;
        advance(2);owner.closeMeasurement(ExperimentSession.CompletionReason.MANUAL,metrics);
        assertFalse(owner.timerState().running);assertFalse(owner.timerState().expired);start(TimerConfig.of(true,5));
        assertNotEquals(first,owner.timerState().id);long deadline=owner.timerState().deadlineElapsedNanos;
        assertEquals(SystemClock.elapsedRealtimeNanos()+5_000_000_000L,deadline);
        advance(3);old.run();assertTrue(owner.timerState().running);assertTrue(metrics.isMeasurementSessionActive());
        advance(2);deliver();assertTrue(owner.timerState().expired);assertEquals(1,expired.size());
    }
    @Test public void researchUsesItsFrozenDeadlineAndDoesNotInheritOrdinaryState(){
        start(TimerConfig.of(true,5));Runnable old=callbacks.get(0);owner.closeMeasurement(ExperimentSession.CompletionReason.MANUAL,metrics);
        ExperimentSession.Prepared prepared=owner.experiment.prepare("pipeline_comparison","SYNTHETIC",TimerConfig.of(true,60),ThermalConfig.disabled(),ExperimentIdentity.defaults(),null);
        assertTrue(owner.experiment.startPrepared(prepared,System.currentTimeMillis(),SystemClock.elapsedRealtimeNanos()));
        metrics.startMeasurementSession();owner.armTimer(metrics);long deadline=owner.experiment.timerDeadlineElapsedNanos();
        advance(7);old.run();assertTrue(owner.experiment.isRunning());assertEquals(deadline,owner.timerState().deadlineElapsedNanos);
        advance(54);deliver();assertFalse(owner.experiment.isRunning());assertEquals(ExperimentSession.CompletionReason.TIMER,owner.experiment.completionReason());
        assertEquals(60_000,owner.experiment.durationMillis());assertEquals(prepared.sessionId,expired.get(0));
    }
    @Test public void lateStopUsesActualExpiredStateAndErrorCancelsDeadline(){
        start(TimerConfig.of(true,5));advance(7);owner.closeMeasurement(ExperimentSession.CompletionReason.MANUAL,metrics);
        assertTrue(owner.timerState().expired);assertFalse(owner.timerState().running);
        start(TimerConfig.of(true,5));Runnable callback=callbacks.get(callbacks.size()-1);
        owner.closeMeasurement(ExperimentSession.CompletionReason.ERROR,metrics);advance(7);callback.run();
        assertFalse(owner.timerState().expired);assertTrue(expired.isEmpty());
    }
}
