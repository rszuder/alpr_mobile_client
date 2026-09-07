package com.example.alpr_v1.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.alpr_v1.MainActivity;
import com.example.alpr_v1.SettingsActivity;
import com.example.alpr_v1.experiment.*;
import com.example.alpr_v1.model.ModelRegistry;
import com.example.alpr_v1.pipeline.RoiBudgetPolicy;
import com.example.alpr_v1.metrics.ResearchArchive;
import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class ResearchAutoCollectionInstrumentedTest {
    @Test public void singleStartCollectsAutomaticallySurvivesRecreationAndTimerBuildsFrozenArchive() throws Exception {
        org.junit.Assume.assumeTrue("Requires camera and installed models: -e liveResearch true",
                "true".equals(InstrumentationRegistry.getArguments().getString("liveResearch")));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(new ModelRegistry(context).hasCompleteAlprComposition());
        SharedPreferences preferences=context.getSharedPreferences("alpr_ui",Context.MODE_PRIVATE);
        String[] keys={SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,
                SettingsActivity.KEY_EXPERIMENT_TYPE,SettingsActivity.KEY_EXPERIMENT_VARIANT,
                SettingsActivity.KEY_EXPERIMENT_SERIES_ID,SettingsActivity.KEY_EXPERIMENT_REPLICATE_INDEX,
                SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,SettingsActivity.KEY_RESEARCH_LOCK_ENABLED,
                SettingsActivity.KEY_SCENE_HANDLING_MODE,SettingsActivity.KEY_REVISION};
        Map<String,?> previous=preferences.getAll();
        preferences.edit().putBoolean(SettingsActivity.KEY_EXPERIMENT_MODE_ENABLED,true)
                .putString(SettingsActivity.KEY_EXPERIMENT_TYPE,"roi_budget")
                .putString(SettingsActivity.KEY_EXPERIMENT_VARIANT,RoiBudgetPolicy.FULL_FRAME.wireName())
                .putString(SettingsActivity.KEY_EXPERIMENT_ROI_POLICY,RoiBudgetPolicy.FULL_FRAME.wireName())
                .putString(SettingsActivity.KEY_EXPERIMENT_SERIES_ID,"TEST-AUTO-COLLECTION")
                .putString(SettingsActivity.KEY_SCENE_HANDLING_MODE,"dynamic_continuity")
                .putBoolean(SettingsActivity.KEY_RESEARCH_AUTOZOOM_ENABLED,false)
                .putBoolean(SettingsActivity.KEY_RESEARCH_LOCK_ENABLED,false).commit();
        AtomicReference<ResearchSessionViewModel> retained=new AtomicReference<>();
        File created=null;
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> retained.set((ResearchSessionViewModel)get(activity,"researchSessions")));
            await(()->retained.get().ready(),10000);
            scenario.onActivity(activity -> {
                set(activity,"experimentTimerConfig",TimerConfig.of(true,15));
                invoke(activity,"startCamera",new Class<?>[]{boolean.class},true);
                assertTrue(retained.get().experiment.isRunning());
                assertTrue(retained.get().store().accepting());
                assertTrue((Boolean)get(activity,"collectionActive"));
                invoke(activity,"toggleCollection",new Class<?>[0]);
                assertTrue(retained.get().store().accepting());
            });
            ResearchSessionViewModel original=retained.get();
            String id=original.experiment.sessionId();
            ResearchExecutionConfig frozen=original.experiment.frozenExecutionConfig();
            created=original.store().directory();
            Thread.sleep(1500);
            scenario.recreate();
            scenario.onActivity(activity -> {
                ResearchSessionViewModel recreated=(ResearchSessionViewModel)get(activity,"researchSessions");
                assertSame(original,recreated); assertEquals(id,recreated.experiment.sessionId());
                assertSame(frozen,recreated.experiment.frozenExecutionConfig());
                assertTrue(recreated.store().accepting());
                assertTrue((Boolean)get(activity,"collectionActive"));
            });
            await(()->!original.experiment.isRunning() && !original.finalizing(),45000);
            File archive=new File(created,"final/"+id+".alprsession");
            assertTrue(archive.isFile()); assertFalse(original.store().accepting());
            ResearchArchive.verifyEntryHashes(archive);
            JSONObject session=new JSONObject(new String(Files.readAllBytes(new File(created,"session.json").toPath()),StandardCharsets.UTF_8));
            assertEquals("timer",session.getString("completion_reason"));
            assertEquals(original.experiment.durationMillis(),(session.getLong("finished_elapsed_nanos")
                    -session.getLong("started_elapsed_nanos"))/1_000_000L);
            assertEquals("automatic",session.getString("collection_mode"));
            assertEquals(frozen.plate.variantId,session.getJSONObject("execution").getJSONObject("stages")
                    .getJSONObject("mt").getString("variant_id"));
            assertTrue("Camera must produce auditable MT work",session.getLong("attempts_seen")>0);
            File qa=new File(context.getExternalFilesDir(null),"research-qa-auto.alprsession");
            Files.copy(archive.toPath(),qa.toPath(),StandardCopyOption.REPLACE_EXISTING);
        } finally {
            SharedPreferences.Editor restore=preferences.edit();
            for (String key:keys) {
                Object value=previous.get(key);
                if (value instanceof Boolean) restore.putBoolean(key,(Boolean)value);
                else if (value instanceof Integer) restore.putInt(key,(Integer)value);
                else if (value instanceof String) restore.putString(key,(String)value);
                else restore.remove(key);
            }
            restore.commit();
            if (created != null && retained.get()!=null && !retained.get().finalizing()) {
                assertTrue(created.getCanonicalPath().startsWith(ResearchSessionStore.sessionsRoot(context).getCanonicalPath()+File.separator));
                try (java.util.stream.Stream<Path> paths=Files.walk(created.toPath())) {
                    for (Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
                }
            }
        }
    }
    private interface Condition { boolean ready(); }
    private static void await(Condition condition,long timeout) throws Exception {
        long until=SystemClock.elapsedRealtime()+timeout;
        while (!condition.ready() && SystemClock.elapsedRealtime()<until) Thread.sleep(100);
        assertTrue("Timed out",condition.ready());
    }
    private static Object get(Object object,String name) {
        try { Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object); }
        catch (Exception error) {throw new AssertionError(error);}
    }
    private static void set(Object object,String name,Object value) {
        try { Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value); }
        catch (Exception error) {throw new AssertionError(error);}
    }
    private static void invoke(Object object,String name,Class<?>[] types,Object... values) {
        try { Method method=object.getClass().getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(object,values); }
        catch (Exception error) {throw new AssertionError(error);}
    }
}
