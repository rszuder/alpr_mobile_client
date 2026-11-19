package com.example.alpr_v1.ui.readiness;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import com.example.alpr_v1.R;
import com.google.android.material.button.MaterialButton;
import java.util.*;
import java.util.function.Consumer;

/** Compact summary above the five existing sections, with at most three initial findings. */
public final class ReadinessSummaryView {
    private final Activity activity;
    private final TextView state,counts;
    private final LinearLayout findings;
    private final MaterialButton all;
    private final Consumer<ReadinessFinding> navigate;
    private ReadinessPresentation current;
    private final Set<String> details=new LinkedHashSet<>();
    private boolean expanded;
    public ReadinessSummaryView(Activity activity,Bundle saved,Consumer<ReadinessFinding> navigate) {
        this.activity=activity;this.navigate=navigate;
        state=activity.findViewById(R.id.readiness_state);counts=activity.findViewById(R.id.readiness_counts);
        findings=activity.findViewById(R.id.readiness_findings);all=activity.findViewById(R.id.readiness_show_all);
        expanded=saved!=null&&saved.getBoolean("readiness_all");
        if(saved!=null&&saved.getStringArrayList("readiness_details")!=null)details.addAll(saved.getStringArrayList("readiness_details"));
        all.setOnClickListener(v->{expanded=!expanded;render(current);});ViewCompat.setAccessibilityHeading(state,true);
    }
    public void save(Bundle out) { out.putBoolean("readiness_all",expanded);out.putStringArrayList("readiness_details",new ArrayList<>(details)); }
    public void render(ReadinessPresentation presentation) {
        current=presentation;ReadinessResult result=presentation.result;
        activity.findViewById(R.id.readiness_progress).setVisibility(result==null?View.VISIBLE:View.GONE);
        state.setText(presentation.headline());
        state.setTextColor(activity.getColor(result!=null&&result.state==ReadinessResult.State.NEEDS_CHANGE?R.color.alpr_warning:result!=null&&result.state==ReadinessResult.State.READY?R.color.alpr_success:R.color.options_muted));
        counts.setText(presentation.summary());
        if(result==null){findings.setVisibility(View.INVISIBLE);if(all.getVisibility()==View.VISIBLE)all.setVisibility(View.INVISIBLE);return;}
        findings.setVisibility(View.VISIBLE);findings.removeAllViews();all.setVisibility(View.GONE);
        ((TextView)activity.findViewById(R.id.settings_research_configuration_summary)).setText(result.goal);
        List<ReadinessFinding> rows=result.visibleFindings();
        for(ReadinessFinding f:rows.subList(0,expanded?rows.size():Math.min(3,rows.size()))) {
            LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(0,dp(12),0,dp(4));
            row.setTag(f.issueId);
            int color=f.status==ReadinessFinding.Status.NEEDS_CHANGE?R.color.alpr_warning:
                    f.status==ReadinessFinding.Status.ADVISORY?R.color.options_muted:R.color.options_accent;
            LinearLayout heading=new LinearLayout(activity);heading.setOrientation(LinearLayout.HORIZONTAL);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout label=new LinearLayout(activity);label.setOrientation(LinearLayout.VERTICAL);
            TextView title=text(presentation.title(activity,f),14,color);title.setTypeface(null,Typeface.BOLD);label.addView(title);
            TextView caption=text(presentation.caption(activity,f),12,color);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(3);label.addView(caption,cp);
            heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            MaterialButton toggle=new MaterialButton(activity);
            toggle.setTag("details:"+f.issueId);toggle.setAllCaps(false);toggle.setTextSize(12);toggle.setTextColor(activity.getColor(R.color.options_accent));
            toggle.setMinHeight(dp(48));toggle.setMinimumHeight(dp(48));toggle.setMinWidth(dp(88));toggle.setMinimumWidth(dp(88));
            toggle.setInsetTop(0);toggle.setInsetBottom(0);toggle.setPadding(dp(8),0,dp(8),0);
            toggle.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));toggle.setStrokeWidth(0);toggle.setElevation(0);
            heading.addView(toggle,new LinearLayout.LayoutParams(-2,-2));row.addView(heading,new LinearLayout.LayoutParams(-1,-2));
            TextView explanation=text(f.explanation,13,R.color.options_muted);
            explanation.setTag("explanation:"+f.issueId);explanation.setVisibility(details.contains(f.issueId)?View.VISIBLE:View.GONE);
            LinearLayout.LayoutParams desc=new LinearLayout.LayoutParams(-1,-2);desc.topMargin=dp(3);row.addView(explanation,desc);
            Runnable detailState=()->{
                boolean open=details.contains(f.issueId);toggle.setText(open?R.string.guidance_hide_details:R.string.guidance_details);
                toggle.setContentDescription(activity.getString(R.string.guidance_details_accessibility,toggle.getText(),f.title));
                ViewCompat.setStateDescription(toggle,activity.getString(open?R.string.options_section_expanded:R.string.options_section_collapsed));
                explanation.setVisibility(open?View.VISIBLE:View.GONE);
            };
            toggle.setOnClickListener(v->{if(!details.remove(f.issueId))details.add(f.issueId);detailState.run();});detailState.run();
            if(f.target!=null) {
                MaterialButton action=new MaterialButton(activity);action.setText(f.target.label);action.setTag("action:"+f.issueId);
                action.setTextSize(13);action.setAllCaps(false);action.setMinHeight(dp(48));action.setMinimumHeight(dp(48));
                action.setInsetTop(0);action.setInsetBottom(0);action.setCornerRadius(dp(8));action.setElevation(0);
                action.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(R.color.options_surface)));
                action.setTextColor(activity.getColor(color));action.setStrokeColor(ColorStateList.valueOf(activity.getColor(color)));action.setStrokeWidth(dp(1));
                action.setContentDescription(f.title+". "+f.target.label);
                action.setOnClickListener(v->navigate.accept(f));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);row.addView(action,p);
            }
            findings.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        if(rows.size()>3) { all.setVisibility(View.VISIBLE);all.setText(expanded?"Pokaż mniej":"Pokaż wszystkie ("+rows.size()+")"); }
    }
    private TextView text(String value,int size,int color) { TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(activity.getColor(color));return t; }
    private int dp(int value) { return Math.round(value*activity.getResources().getDisplayMetrics().density); }
}
