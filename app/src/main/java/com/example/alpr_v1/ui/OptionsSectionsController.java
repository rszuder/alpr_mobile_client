package com.example.alpr_v1.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.drawable.RotateDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import com.example.alpr_v1.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.example.alpr_v1.ui.readiness.ReadinessPresentation;
import org.json.JSONObject;
import java.util.function.Supplier;

/** UI-only accordion. A preference/status refresh never starts a layout animation. */
public final class OptionsSectionsController {
    private static final String SAVED_SECTION="options_open_section";
    private static final int[] HEADERS={R.id.options_mode_header,R.id.options_camera_header,R.id.options_models_header,R.id.options_session_header,R.id.options_advanced_header};
    private static final int[] BODIES={R.id.options_mode_body,R.id.options_camera_body,R.id.options_models_body,R.id.options_session_body,R.id.options_advanced_body};
    private static final int[] CARDS={R.id.options_mode_card,R.id.options_camera_card,R.id.options_models_card,R.id.options_session_card,R.id.options_advanced_card};
    private static final int[] HELP={R.id.options_mode_help,R.id.options_camera_help,R.id.options_models_help,R.id.options_session_help,R.id.options_advanced_help};
    private static final int[] TITLES={R.string.options_mode_title,R.string.options_camera_title,R.string.options_models_title,R.string.options_session_title,R.string.options_advanced_title};
    private final Activity activity;
    private final Supplier<JSONObject> configuration;
    private final NestedScrollView scroll;
    private final RotateDrawable[] arrows=new RotateDrawable[5];
    private int expanded;
    private long generation;
    private ValueAnimator animation;
    private ViewTreeObserver.OnPreDrawListener alignment;
    private View revealTarget;
    private Runnable afterReveal;
    private ReadinessPresentation readiness;
    private ReadinessPresentation lastReadiness;
    private final java.util.Map<View,android.graphics.drawable.Drawable> fieldForegrounds=new java.util.IdentityHashMap<>();
    private final java.util.Map<View,CharSequence> fieldTooltips=new java.util.IdentityHashMap<>();

    public OptionsSectionsController(Activity activity,Bundle saved,Supplier<JSONObject> configuration){
        this.activity=activity;this.configuration=configuration;scroll=activity.findViewById(R.id.settings_scroll);
        expanded=saved==null?-1:saved.getInt(SAVED_SECTION,-1);if(expanded< -1||expanded>=5)expanded=-1;
        for(int i=0;i<HEADERS.length;i++){
            final int section=i;MaterialButton header=activity.findViewById(HEADERS[i]);
            header.setMaxLines(Integer.MAX_VALUE);header.setEllipsize(null);
            RotateDrawable arrow=new RotateDrawable();arrow.setDrawable(activity.getDrawable(R.drawable.ic_options_expand));
            arrow.setFromDegrees(0);arrow.setToDegrees(180);arrows[i]=arrow;header.setIcon(arrow);
            header.setOnClickListener(v->select(expanded==section?-1:section));ViewCompat.setAccessibilityHeading(header,true);
            activity.findViewById(HELP[i]).setOnClickListener(v->showHelp(section));
        }
        for(int id:new int[]{R.id.settings_experiment_switch,R.id.settings_f6_enabled,R.id.settings_research_lock_switch,R.id.settings_research_autozoom_switch}){
            MaterialSwitch toggle=activity.findViewById(id);View row=(View)toggle.getParent();
            row.setOnClickListener(v->{if(toggle.isEnabled())toggle.performClick();});
        }
        applyFinalVisibility();refresh();
    }
    public void save(Bundle out){out.putInt(SAVED_SECTION,expanded);}
    public int expandedSection(){return expanded;}
    public boolean animating(){return animation!=null;}
    public void setReadiness(ReadinessPresentation presentation){readiness=presentation;if(presentation.result!=null)lastReadiness=presentation;refresh();}

    /** A readiness destination overrides header alignment only for this navigation request. */
    public void revealControl(int section,int control,Runnable shown){
        if(section<0||section>=5)return;
        select(section);revealTarget=activity.findViewById(control);afterReveal=shown;
        if(animation==null)align(generation);
    }
    public void showSummary(){
        select(-1);scroll.post(()->{if(expanded<0)scroll.scrollTo(0,0);});
    }

    private void select(int next){
        generation++;long ticket=generation;
        revealTarget=null;afterReveal=null;
        cancelAlignment();
        if(animation!=null){animation.cancel();animation=null;}
        View focus=activity.getCurrentFocus();
        if(focus!=null&&expanded>=0&&descendant(focus,activity.findViewById(BODIES[expanded]))){
            View header=activity.findViewById(HEADERS[next>=0?next:expanded]);
            header.setFocusableInTouchMode(true);header.requestFocus();
            InputMethodManager keyboard=(InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(focus.getWindowToken(),0);
        }
        int[] heights=new int[5],targets=new int[5],levels=new int[5];float[] opacity=new float[5];
        for(int i=0;i<5;i++){
            View body=activity.findViewById(BODIES[i]);int height=body.getLayoutParams().height;
            heights[i]=body.getVisibility()==View.GONE?0:height>=0?height:body.getHeight();
            levels[i]=arrows[i].getLevel();opacity[i]=heights[i]==0?0:body.getAlpha();
        }
        expanded=next;
        boolean animate=ValueAnimator.areAnimatorsEnabled()&&activity.findViewById(HEADERS[0]).getWidth()>0;
        if(!animate){applyFinalVisibility();refresh();align(ticket);return;}
        if(next>=0){
            View body=activity.findViewById(BODIES[next]);body.setVisibility(View.VISIBLE);
            int width=activity.findViewById(HEADERS[next]).getWidth();
            body.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            targets[next]=body.getMeasuredHeight();
        }
        for(int i=0;i<5;i++){
            View body=activity.findViewById(BODIES[i]);
            if(heights[i]>0||targets[i]>0){body.setVisibility(View.VISIBLE);setHeight(body,heights[i]);}
            body.setImportantForAccessibility(i==next?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        animation=ValueAnimator.ofFloat(0,1);animation.setDuration(200);animation.setInterpolator(new FastOutSlowInInterpolator());
        animation.addUpdateListener(value->{
            if(ticket!=generation)return;float f=(float)value.getAnimatedValue();
            for(int i=0;i<5;i++){
                View body=activity.findViewById(BODIES[i]);
                if(heights[i]>0||targets[i]>0){setHeight(body,Math.round(heights[i]+(targets[i]-heights[i])*f));body.setAlpha(opacity[i]+((i==expanded?1:0)-opacity[i])*f);}
                arrows[i].setLevel(Math.round(levels[i]+((i==expanded?10000:0)-levels[i])*f));
            }
        });
        animation.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator value){
            if(ticket!=generation)return;animation=null;applyFinalVisibility();align(ticket);
        }});
        animation.start();refresh();
    }
    private void applyFinalVisibility(){
        for(int i=0;i<5;i++){
            View body=activity.findViewById(BODIES[i]);body.setVisibility(expanded==i?View.VISIBLE:View.GONE);
            setHeight(body,ViewGroup.LayoutParams.WRAP_CONTENT);body.setAlpha(1);
            body.setImportantForAccessibility(expanded==i?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            arrows[i].setLevel(expanded==i?10000:0);
        }
    }
    private void align(long ticket){
        if(expanded<0)return;final int section=expanded;
        cancelAlignment();
        // A post() can run before the layout requested by WRAP_CONTENT has completed.
        // Align after measuring the collapsed previous section, including after manual scrolling.
        alignment=new ViewTreeObserver.OnPreDrawListener(){@Override public boolean onPreDraw(){
            scroll.getViewTreeObserver().removeOnPreDrawListener(this);
            if(alignment==this)alignment=null;
            if(ticket!=generation||expanded!=section||activity.isDestroyed()||activity.isFinishing())return true;
            View header=revealTarget!=null?revealTarget:activity.findViewById(HEADERS[section]);
            if(revealTarget instanceof MaterialSwitch&&revealTarget.getParent() instanceof View)header=(View)revealTarget.getParent();
            int[] headerAt=new int[2],viewportAt=new int[2];
            header.getLocationInWindow(headerAt);scroll.getLocationInWindow(viewportAt);
            int top=headerAt[1]-viewportAt[1],bottom=top+header.getHeight();
            int delta=top<scroll.getPaddingTop()?top-scroll.getPaddingTop():Math.max(0,bottom-(scroll.getHeight()-scroll.getPaddingBottom()));
            if(delta!=0)scroll.scrollBy(0,delta);
            if(revealTarget!=null){
                View target=revealTarget;Runnable action=afterReveal;revealTarget=null;afterReveal=null;
                target.setFocusableInTouchMode(true);target.requestFocus();
                View indicator=header;android.graphics.drawable.Drawable previous=indicator.getForeground();
                android.graphics.drawable.GradientDrawable outline=new android.graphics.drawable.GradientDrawable();
                outline.setColor(android.graphics.Color.TRANSPARENT);outline.setCornerRadius(8*activity.getResources().getDisplayMetrics().density);
                outline.setStroke(Math.max(1,Math.round(activity.getResources().getDisplayMetrics().density)),activity.getColor(R.color.options_accent));
                indicator.setForeground(outline);
                indicator.postDelayed(()->{if(indicator.getForeground()==outline){indicator.setForeground(fieldForegrounds.containsKey(indicator)?fieldForegrounds.get(indicator):previous);renderReadinessFields();}},1200);
                target.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
                if(action!=null)action.run();
            }
            return true;
        }};
        scroll.getViewTreeObserver().addOnPreDrawListener(alignment);
        scroll.postInvalidateOnAnimation();
    }
    private void cancelAlignment(){
        if(alignment!=null){
            if(scroll.getViewTreeObserver().isAlive())scroll.getViewTreeObserver().removeOnPreDrawListener(alignment);
            alignment=null;
        }
    }
    public void stop(){
        generation++;cancelAlignment();revealTarget=null;afterReveal=null;
        if(animation!=null){animation.cancel();animation=null;}applyFinalVisibility();
    }
    private static void setHeight(View body,int height){ViewGroup.LayoutParams p=body.getLayoutParams();if(p.height!=height){p.height=height;body.setLayoutParams(p);}}
    private static boolean descendant(View view,View parent){for(View child=view;child!=null;child=child.getParent() instanceof View?(View)child.getParent():null)if(child==parent)return true;return false;}

    public void refresh(){
        MaterialSwitch research=activity.findViewById(R.id.settings_experiment_switch);boolean experiment=research.isChecked();
        for(int id:new int[]{R.id.options_research_identity,R.id.options_research_execution,R.id.options_research_advanced})activity.findViewById(id).setVisibility(experiment?View.VISIBLE:View.GONE);
        state(R.id.settings_experiment_switch,R.id.settings_experiment_state);state(R.id.settings_f6_enabled,R.id.settings_f6_state);
        state(R.id.settings_research_lock_switch,R.id.settings_lock_state);state(R.id.settings_research_autozoom_switch,R.id.settings_autozoom_state);
        JSONObject config=configuration.get();
        String mode=ResearchConfigurationText.isF6(config)?"Badanie na fotografiach":text(R.id.settings_experiment_type);
        String[] summaries={experiment?mode+" · "+strategy():activity.getString(R.string.options_normal_mode),
                text(R.id.settings_resolution_dropdown),experiment?execution(config):"MP, MT i MZ · osobne ustawienia CPU",
                experiment?"Powtórzenie "+text(R.id.settings_experiment_replicate_index)+" · "+text(R.id.settings_experiment_series_id):"Galeria i lokalizacja plików",
                "Profil: "+profile()};
        for(int i=0;i<5;i++){
            MaterialButton header=activity.findViewById(HEADERS[i]);String title=activity.getString(TITLES[i]),sub=summaries[i];
            ReadinessPresentation.Marker marker=readiness==null?null:readiness.section(i);String status=marker==null?"":marker.label(activity,i);
            ReadinessPresentation.Marker previous=readiness!=null&&readiness.result==null&&lastReadiness!=null?lastReadiness.section(i):null;
            String statusLayout=previous==null?status:previous.label(activity,i);
            String open=activity.getString(expanded==i?R.string.options_section_expanded:R.string.options_section_collapsed);
            SpannableString value=new SpannableString(title+"\n"+sub+(statusLayout.isEmpty()?"":"\n"+statusLayout));value.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),0,title.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            value.setSpan(new RelativeSizeSpan(.8125f),title.length()+1,title.length()+1+sub.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if(marker!=null && marker.changes()>0){
                int start=title.length()+sub.length()+2;
                value.setSpan(new ForegroundColorSpan(activity.getColor(marker.color())),0,title.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                value.setSpan(new ForegroundColorSpan(activity.getColor(marker.color())),start,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                value.setSpan(new RelativeSizeSpan(.75f),start,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            else if(previous!=null && !statusLayout.isEmpty()){int start=title.length()+sub.length()+2;value.setSpan(new ForegroundColorSpan(android.graphics.Color.TRANSPARENT),start,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);value.setSpan(new RelativeSizeSpan(.75f),start,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
            header.setText(value);header.setContentDescription(title+". "+sub+(status.isEmpty()?"":". "+status)+". "+open);
            ViewCompat.setStateDescription(header,open+(status.isEmpty()?"":". "+status));
            MaterialCardView card=activity.findViewById(CARDS[i]);card.setTag(R.id.readiness_section_marker,marker);
            card.setStrokeWidth(Math.round((marker==null||marker.changes()==0?1:2)*activity.getResources().getDisplayMetrics().density));
            card.setStrokeColor(activity.getColor(marker!=null&&marker.changes()>0?marker.color():expanded==i?R.color.options_accent:R.color.options_outline));
            header.setIconTint(android.content.res.ColorStateList.valueOf(activity.getColor(marker==null||marker.changes()==0?R.color.options_accent:marker.color())));
        }
        renderReadinessFields();
        int hint="R1".equals(strategy())?R.string.options_strategy_r1:"R2".equals(strategy())?R.string.options_strategy_r2:R.string.options_strategy_r0;
        ((TextView)activity.findViewById(R.id.options_strategy_hint)).setText(hint);
        ((TextView)activity.findViewById(R.id.options_camera_observation)).setText(cameraSummary());
    }
    private void renderReadinessFields(){
        java.util.Map<View,ReadinessPresentation.Marker> desired=new java.util.IdentityHashMap<>();
        if(readiness!=null)for(java.util.Map.Entry<Integer,ReadinessPresentation.Marker> entry:readiness.controls().entrySet()){
            if(entry.getValue().changes()==0)continue;
            View control=activity.findViewById(entry.getKey());if(control==null)continue;
            View owner=control;
            if(control instanceof MaterialSwitch&&control.getParent() instanceof View)owner=(View)control.getParent();
            else if(control.getParent() instanceof com.google.android.material.button.MaterialButtonToggleGroup)owner=(View)control.getParent();
            else for(android.view.ViewParent p=control.getParent();p instanceof View;p=p.getParent()){
                if(p instanceof com.google.android.material.textfield.TextInputLayout){owner=(View)p;break;}
                if(p==activity.findViewById(R.id.options_content))break;
            }
            if(!fieldForegrounds.containsKey(owner)){fieldForegrounds.put(owner,owner.getForeground());fieldTooltips.put(owner,owner.getTooltipText());}
            desired.computeIfAbsent(owner,v->new ReadinessPresentation.Marker()).merge(entry.getValue());
        }
        for(java.util.Map.Entry<View,android.graphics.drawable.Drawable> entry:fieldForegrounds.entrySet()){
            View owner=entry.getKey();ReadinessPresentation.Marker marker=desired.get(owner);owner.setTag(R.id.readiness_field_marker,marker);
            if(marker==null){owner.setForeground(entry.getValue());owner.setTooltipText(fieldTooltips.get(owner));continue;}
            android.graphics.drawable.GradientDrawable outline=new android.graphics.drawable.GradientDrawable();
            outline.setColor(android.graphics.Color.TRANSPARENT);outline.setCornerRadius(8*activity.getResources().getDisplayMetrics().density);
            outline.setStroke(Math.max(1,Math.round(2*activity.getResources().getDisplayMetrics().density)),activity.getColor(marker.color()));
            owner.setForeground(outline);owner.setTooltipText(marker.label(activity,-1));
        }
    }
    private void state(int switchId,int textId){
        MaterialSwitch toggle=activity.findViewById(switchId);TextView status=activity.findViewById(textId);
        String label=activity.getString(toggle.isChecked()?R.string.options_on:R.string.options_off);status.setText(label);
        status.setTextColor(activity.getColor(!toggle.isEnabled()?R.color.options_muted:toggle.isChecked()?R.color.options_accent:R.color.options_muted));
        ((View)toggle.getParent()).setEnabled(toggle.isEnabled());ViewCompat.setStateDescription(toggle,label);
    }
    private String text(int id){CharSequence s=((TextView)activity.findViewById(id)).getText();return s==null?"":s.toString();}
    private String strategy(){for(int id:new int[]{R.id.settings_roi_r0,R.id.settings_roi_r1,R.id.settings_roi_r2})if(((MaterialButton)activity.findViewById(id)).isChecked())return text(id);return "—";}
    private String profile(){for(int id:new int[]{R.id.settings_profile_fast,R.id.settings_profile_balanced,R.id.settings_profile_accurate})if(((MaterialButton)activity.findViewById(id)).isChecked())return text(id);return "—";}
    private String execution(JSONObject config){
        JSONObject stages=config==null?null:config.optJSONObject("stages"),mt=stages==null?null:stages.optJSONObject("mt"),mz=stages==null?null:stages.optJSONObject("mz");
        if(mt!=null&&mz!=null&&!mt.has("unavailable_reason")&&!mz.has("unavailable_reason")){
            if(mt.optString("runtime").equals(mz.optString("runtime"))&&mt.optString("precision").equals(mz.optString("precision"))&&mt.optInt("cpu_threads")==mz.optInt("cpu_threads")&&mt.optBoolean("gpu")==mz.optBoolean("gpu"))
                return "MT/MZ "+mt.optString("precision").toUpperCase(java.util.Locale.ROOT)+" · "+(mt.optBoolean("gpu")?"GPU":"CPU ×"+mt.optInt("cpu_threads"));
            return "MT "+mt.optString("precision").toUpperCase(java.util.Locale.ROOT)+" · MZ "+mz.optString("precision").toUpperCase(java.util.Locale.ROOT);
        }
        return activity.getString(R.string.options_execution_unavailable);
    }
    private String cameraSummary(){
        String raw=text(R.id.settings_resolution_capabilities);
        java.util.regex.Matcher preview=java.util.regex.Pattern.compile("Preview[^\\n]*",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(raw);
        java.util.regex.Matcher analysis=java.util.regex.Pattern.compile("(?:ImageAnalysis|Analysis|Analiza)[^\\n]*",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(raw);
        String result=preview.find()?preview.group():"";if(analysis.find())result+=(result.isEmpty()?"":"\n")+analysis.group();
        return result.isEmpty()?activity.getString(R.string.options_camera_wait):activity.getString(R.string.options_camera_observation,result);
    }
    private void showHelp(int section){
        String value;
        switch(section){
            case 0:value=ResearchConfigurationText.title(configuration.get())+"\n"+ResearchConfigurationText.details(configuration.get())+"\n\n"+activity.getString(R.string.settings_experiment_description)+"\n\n"+activity.getString(R.string.research_profile_explanation)+"\n\n"+activity.getString(R.string.settings_roi_budget_description);break;
            case 1:value=text(R.id.settings_resolution_capabilities);break;
            case 2:value=text(R.id.settings_model_status_summary)+"\n\nMP: "+text(R.id.settings_model_status_vehicle)+"\n\nMT: "+text(R.id.settings_model_status_plate)+"\n\nMZ: "+text(R.id.settings_model_status_character)+"\n\n"+text(R.id.settings_research_mp_summary)+"\n"+text(R.id.settings_research_mt_summary)+"\n"+text(R.id.settings_research_mz_summary)+"\n"+text(R.id.settings_cpu_threads_hint);break;
            case 3:value=activity.getString(R.string.settings_experiment_identity_description)+"\n\n"+activity.getString(R.string.settings_crop_description);break;
            default:value=activity.getString(R.string.options_extra_help)+"\n\n"+text(R.id.settings_scene_handling_summary);
        }
        new ImmersiveDialogBuilder(activity).setTitle(activity.getString(TITLES[section])).setMessage(value).setPositiveButton(android.R.string.ok,null).show();
    }
}
