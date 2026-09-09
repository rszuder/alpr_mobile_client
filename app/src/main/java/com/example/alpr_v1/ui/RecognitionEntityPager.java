package com.example.alpr_v1.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.example.alpr_v1.R;
import com.example.alpr_v1.capture.RecognitionHistoryItem;
import com.example.alpr_v1.capture.RecognitionHistoryObservation;
import com.example.alpr_v1.capture.ObservationTelemetry;
import com.example.alpr_v1.pipeline.CropInferenceTiming;
import com.example.alpr_v1.pipeline.ModelRuntimeSummary;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Fixed preview is owned by the dialog; only entity pages scroll horizontally/vertically. */
public final class RecognitionEntityPager extends LinearLayout {
    private final List<EntityPage> pages = new ArrayList<>();
    private final List<TextView> badges = new ArrayList<>();
    private final HorizontalScrollView badgeScroll;
    private final LinearLayout badgeRow;
    private final RecyclerView pager;
    private final LinearLayoutManager layout;
    private final TextView position;
    private RecognitionHistoryItem item;
    private final int blue, violet, green, muted, foreground, surface;
    private static final class EntityPage {
        final long entity, scene;
        final List<RecognitionHistoryObservation> observations = new ArrayList<>();
        EntityPage(long entity, long scene) { this.entity = entity; this.scene = scene; }
        String label() { return entity > 0 ? "P" + entity : "Bez encji"; }
    }
    public RecognitionEntityPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        blue = color(R.color.alpr_primary); violet = color(R.color.alpr_secondary);
        green = color(R.color.alpr_success); muted = color(R.color.alpr_text_secondary);
        foreground = color(R.color.alpr_text_primary); surface = color(R.color.alpr_card);
        badgeScroll = new HorizontalScrollView(context);
        badgeScroll.setHorizontalScrollBarEnabled(false);
        badgeRow = column(); badgeRow.setOrientation(HORIZONTAL);
        badgeScroll.addView(badgeRow, new ViewGroup.LayoutParams(-2, -2));
        addView(badgeScroll, new LayoutParams(-1, -2));
        position = text("", 12, muted, false);
        position.setGravity(Gravity.CENTER); position.setPadding(0, dp(6), 0, dp(8));
        position.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(position);
        pager = new RecyclerView(context); pager.setId(R.id.history_detail_pager);
        layout = new LinearLayoutManager(context, RecyclerView.HORIZONTAL, false);
        pager.setLayoutManager(layout);
        pager.setOverScrollMode(OVER_SCROLL_NEVER);
        pager.setItemAnimator(null);
        PagerSnapHelper snap = new PagerSnapHelper(); snap.attachToRecyclerView(pager);
        pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView recycler, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    View page = snap.findSnapView(layout);
                    if (page != null) selectBadge(layout.getPosition(page));
                }
            }
        });
        addView(pager, new LayoutParams(-1, 0, 1));
    }
    public void setItem(RecognitionHistoryItem item) {
        this.item = item;
        pages.clear(); badges.clear(); badgeRow.removeAllViews();
        LinkedHashMap<String, EntityPage> grouped = new LinkedHashMap<>();
        for (RecognitionHistoryObservation observation : item.observationRecords()) {
            String key = observation.sceneGeneration + ":" + observation.entityId
                    + (observation.entityId == 0 ? ":" + observation.plateTrackId : "");
            EntityPage page = grouped.computeIfAbsent(key,
                    ignored -> new EntityPage(observation.entityId, observation.sceneGeneration));
            page.observations.add(observation);
        }
        pages.addAll(grouped.values());
        if (pages.isEmpty()) pages.add(new EntityPage(item.entityId, 0));
        for (int index = 0; index < pages.size(); index++) {
            final int pageIndex = index;
            EntityPage page = pages.get(index);
            TextView badge = text(page.label(), 14, blue, true);
            badge.setGravity(Gravity.CENTER); badge.setMinWidth(dp(68)); badge.setMinHeight(dp(48));
            badge.setPadding(dp(14), 0, dp(14), 0);
            badge.setContentDescription("Encja " + (page.entity > 0 ? page.entity : "bez przypisania")
                    + ", scena " + page.scene);
            badge.setFocusable(true); badge.setOnClickListener(view -> {
                pager.smoothScrollToPosition(pageIndex); selectBadge(pageIndex);
            });
            LayoutParams params = new LayoutParams(-2, -2); params.setMarginEnd(dp(8));
            badgeRow.addView(badge, params); badges.add(badge);
        }
        pager.setAdapter(new RecyclerView.Adapter<PageHolder>() {
            @NonNull @Override public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                ScrollView scroll = new ScrollView(getContext()); scroll.setFillViewport(true);
                scroll.setLayoutParams(new RecyclerView.LayoutParams(-1, -1));
                scroll.setClipToPadding(false); scroll.setPadding(0, 0, 0, dp(4));
                return new PageHolder(scroll);
            }
            @Override public void onBindViewHolder(@NonNull PageHolder holder, int index) {
                ScrollView scroll = (ScrollView) holder.itemView; scroll.removeAllViews();
                scroll.addView(entityContent(pages.get(index))); scroll.scrollTo(0, 0);
            }
            @Override public int getItemCount() { return pages.size(); }
        });
        selectBadge(0);
    }
    private static final class PageHolder extends RecyclerView.ViewHolder {
        PageHolder(View view) { super(view); }
    }
    private void selectBadge(int selected) {
        if (selected < 0 || selected >= pages.size()) return;
        for (int index = 0; index < badges.size(); index++) {
            TextView badge = badges.get(index); boolean active = selected == index;
            badge.setSelected(active); badge.setTextColor(active ? color(R.color.alpr_background) : blue);
            badge.setBackground(shape(active ? blue : surface, blue, 24));
        }
        position.setText(pages.size() == 1 ? "1 encja · obserwacje poniżej"
                : "‹   Encja " + (selected + 1) + " z " + pages.size() + "   ›   Przesuń w bok");
        TextView badge = badges.get(selected);
        badgeScroll.post(() -> badgeScroll.smoothScrollTo(Math.max(0, badge.getLeft() - dp(12)), 0));
    }
    private LinearLayout entityContent(EntityPage page) {
        LinearLayout root = column(); root.setPadding(dp(2), 0, dp(2), 0);
        TextView heading = text(page.label() + "  ·  " + Math.max(1, page.observations.size()) + " obs.", 19, blue, true);
        heading.setPadding(dp(4), dp(4), 0, dp(10)); root.addView(heading);
        if (page.observations.isEmpty()) {
            LinearLayout legacy = card(); legacy.addView(text("Zapis archiwalny", 15, foreground, true));
            timings(legacy, item.timing);
            legacy.addView(text("Ten zapis nie zawiera szczegółowych obserwacji encji.", 13, muted, false));
            root.addView(legacy);
        }
        for (int index = 0; index < page.observations.size(); index++) {
            RecognitionHistoryObservation observation = page.observations.get(index);
            LinearLayout card = card();
            card.addView(text("OBSERWACJA " + (index + 1) + "  ·  "
                    + new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date(observation.capturedAtMillis)), 12, blue, true));
            TextView reading = text(observation.text.isEmpty() ? "—" : observation.text, 21, foreground, true);
            reading.setTypeface(Typeface.MONOSPACE, Typeface.BOLD); reading.setPadding(0, dp(8), 0, dp(6)); card.addView(reading);
            card.addView(text(observation.confirmed ? "●  Potwierdzony odczyt" : "●  Odczyt wstępny", 12,
                    observation.confirmed ? green : color(R.color.alpr_warning), true));
            pair(card, "Pewność odczytu", value(observation.confidence * 100, "%"),
                    "Pewność MT", value(observation.plateConfidence * 100, "%"), green);
            section(card, "CZASY MODELI", violet); timings(card, observation.timing);
            ObservationTelemetry telemetry = observation.telemetry;
            section(card, "ZASOBY PRZY ODCZYCIE", blue);
            LinearLayout resources = row();
            metric(resources, "FPS", value(telemetry == null ? Double.NaN : telemetry.cameraFps, ""), blue);
            metric(resources, "TMP · bateria", value(telemetry == null ? Double.NaN : telemetry.batteryTemperatureC, "°C"), color(R.color.alpr_warning));
            metric(resources, "CPU · aplikacja", value(telemetry == null ? Double.NaN : telemetry.cpuPercent, "%"), green);
            card.addView(resources);
            section(card, "MODELE I KADR", violet);
            field(card, "Rozdzielczość", observation.sourceWidth + " × " + observation.sourceHeight);
            field(card, "Źródło", observation.captureSource == null ? "—" : observation.captureSource);
            if (telemetry == null || telemetry.models.isEmpty()) card.addView(text("Brak zapisanych wariantów modeli", 12, muted, false));
            else for (ModelRuntimeSummary model : telemetry.models) {
                TextView modelView = text(model.stage + "  ·  " + model.variantId + "\n" + model.modelName
                        + "\n" + model.precision + "  ·  " + model.backend, 12,
                        "MP".equals(model.stage) ? blue : "MT".equals(model.stage) ? violet : green, false);
                modelView.setPadding(dp(10), dp(8), dp(10), dp(8));
                modelView.setBackground(shape(color(R.color.alpr_background), color(R.color.alpr_outline), 10));
                LayoutParams modelParams = new LayoutParams(-1, -2); modelParams.topMargin = dp(6); card.addView(modelView, modelParams);
            }
            section(card, "TOŻSAMOŚĆ OBSERWACJI", muted);
            pair(card, "Track pojazdu", id(observation.vehicleTrackId), "Track tablicy", id(observation.plateTrackId), blue);
            pair(card, "Scena / epoka", observation.sceneGeneration + " / " + observation.visualEpoch,
                    "Klatka", Long.toString(observation.frameId), muted);
            field(card, "Transformacja kamery", Long.toString(observation.cameraTransformGeneration));
            if (observation.associationReason != null && !observation.associationReason.isEmpty())
                field(card, "Przypisanie", observation.associationReason);
            LayoutParams params = new LayoutParams(-1, -2); params.bottomMargin = dp(12); root.addView(card, params);
        }
        return root;
    }
    private void timings(LinearLayout parent, CropInferenceTiming timing) {
        LinearLayout values = row();
        metric(values, "MP", duration(timing == null ? -1 : timing.vehicleInferenceNanos), blue);
        metric(values, "MT", duration(timing == null ? -1 : timing.plateInferenceNanos), violet);
        metric(values, "MZ", duration(timing == null ? -1 : timing.characterInferenceNanos), green);
        parent.addView(values);
        field(parent, "Potok do odczytu", duration(timing == null ? -1 : timing.pipelineToObservationNanos));
    }
    private void pair(LinearLayout parent, String a, String av, String b, String bv, int accent) {
        LinearLayout row = row(); metric(row, a, av, accent); metric(row, b, bv, accent); parent.addView(row);
    }
    private void metric(LinearLayout row, String name, String value, int accent) {
        LinearLayout box = column(); box.setPadding(dp(9), dp(9), dp(7), dp(9));
        box.setBackground(shape((accent & 0x00ffffff) | 0x18000000, (accent & 0x00ffffff) | 0x55000000, 12));
        box.addView(text(name, 11, muted, false)); box.addView(text(value, 16, accent, true));
        LayoutParams params = new LayoutParams(0, -2, 1); params.setMarginEnd(dp(5)); row.addView(box, params);
    }
    private void field(LinearLayout card, String name, String value) {
        LinearLayout row = row();
        TextView label = text(name, 12, muted, false); row.addView(label, new LayoutParams(0, -2, 1));
        TextView content = text(value, 12, foreground, false); content.setGravity(Gravity.END);
        content.setTextIsSelectable(true); row.addView(content, new LayoutParams(0, -2, 1.4f)); card.addView(row);
    }
    private void section(LinearLayout card, String name, int accent) {
        TextView title = text(name, 11, accent, true); title.setLetterSpacing(.08f);
        title.setPadding(0, dp(18), 0, dp(5)); card.addView(title);
    }
    private LinearLayout card() {
        LinearLayout card = column(); card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(shape(surface, color(R.color.alpr_outline), 18)); return card;
    }
    private LinearLayout column() { LinearLayout box = new LinearLayout(getContext()); box.setOrientation(VERTICAL); return box; }
    private LinearLayout row() { LinearLayout box = column(); box.setOrientation(HORIZONTAL); box.setPadding(0, dp(6), 0, dp(3)); return box; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(getContext()); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return text;
    }
    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(radius)); shape.setStroke(dp(1), stroke); return shape;
    }
    private int color(int resource) { return getContext().getColor(resource); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String id(long id) { return id > 0 ? Long.toString(id) : "—"; }
    private static String duration(long nanos) { return nanos < 0 ? "—" : value(nanos / 1_000_000.0, " ms"); }
    private static String value(double number, String unit) { return Double.isFinite(number) && number >= 0
            ? String.format(Locale.forLanguageTag("pl-PL"), "%.1f", number) + unit : "—"; }
}
