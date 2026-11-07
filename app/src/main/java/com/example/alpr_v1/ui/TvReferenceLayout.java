package com.example.alpr_v1.ui;

import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import com.example.alpr_v1.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

/** Presentation confined to the TV calibration wizard; never owns the main-screen controls. */
final class TvReferenceLayout {
    private final AppCompatActivity activity;
    private final ConstraintLayout root;
    private final View preview,panel;
    private final MaterialToolbar toolbar;
    private final Drawable rootBackground,panelBackground,toolbarBackground;
    private final TextView instruction;
    private final ViewGroup instructionParent;
    private final int instructionIndex;
    private final ViewGroup.LayoutParams instructionLayout;
    private final int[] panelPadding;
    private final List<ButtonAppearance> buttons=new ArrayList<>();
    private final List<TextView> labels=new ArrayList<>();
    private final List<ColorStateList> labelColors=new ArrayList<>();
    private final List<Float> labelSizes=new ArrayList<>();
    private String windowKey="";
    private boolean enabled,preciseExpanded;
    private int lastSectionIcon;

    private static final class ButtonAppearance {
        final MaterialButton view;
        final ColorStateList text,tint,iconTint,stroke;
        final Drawable icon;
        final int radius;
        ButtonAppearance(MaterialButton view){this.view=view;text=view.getTextColors();tint=view.getBackgroundTintList();
            iconTint=view.getIconTint();stroke=view.getStrokeColor();radius=view.getCornerRadius();icon=view.getIcon();}
        void restore(){view.setTextColor(text);view.setBackgroundTintList(tint);view.setIconTint(iconTint);
            view.setStrokeColor(stroke);view.setCornerRadius(radius);view.setIcon(icon);}
    }
    TvReferenceLayout(AppCompatActivity activity){
        this.activity=activity;root=find(R.id.main);preview=find(R.id.camera_preview);panel=find(R.id.tv_setup_panel);toolbar=find(R.id.top_app_bar);
        rootBackground=root.getBackground();panelBackground=panel.getBackground();toolbarBackground=toolbar.getBackground();
        panelPadding=new int[]{panel.getPaddingLeft(),panel.getPaddingTop(),panel.getPaddingRight(),panel.getPaddingBottom()};
        instruction=find(R.id.tv_setup_instruction);instructionParent=(ViewGroup)instruction.getParent();
        instructionIndex=instructionParent.indexOfChild(instruction);instructionLayout=instruction.getLayoutParams();
        for(int id:new int[]{R.id.tv_setup_cancel,R.id.tv_setup_confirm,R.id.tv_setup_next,R.id.tv_setup_retry,R.id.tv_setup_clear})buttons.add(new ButtonAppearance(find(id)));
        for(int id:new int[]{R.id.tv_setup_title,R.id.tv_setup_status,R.id.tv_setup_instruction,R.id.tv_setup_coverage,R.id.tv_camera_caption,R.id.tv_camera_detail,R.id.tv_camera_zoom_value,R.id.tv_camera_fov}){
            TextView label=find(id);labels.add(label);labelColors.add(label.getTextColors());labelSizes.add(label.getTextSize());}
        find(R.id.tv_ref_precise_toggle).setOnClickListener(v->{preciseExpanded=!preciseExpanded;updateExpansion();});
    }
    boolean enabled(){return enabled;}
    private String currentWindowKey(){return root.getWidth()+":"+root.getHeight()+":"+root.getPaddingLeft()+":"+root.getPaddingTop()+":"+root.getPaddingRight()+":"+root.getPaddingBottom()+":"+font();}
    boolean windowChanged(){return !windowKey.equals(currentWindowKey());}
    boolean geometryLayoutPending(){return preview.isLayoutRequested()||find(R.id.tv_ref_frame).isLayoutRequested();}
    void setEnabled(boolean value){
        if(enabled==value)return;enabled=value;
        if(value){
            preciseExpanded=false;
            ((ViewGroup)instruction.getParent()).removeView(instruction);
            ((ViewGroup)find(R.id.tv_ref_instruction_slot)).addView(instruction,new LinearLayout.LayoutParams(-1,-2));
            // New drawables preserve the originals; setBackgroundColor would mutate a saved ColorDrawable.
            root.setBackground(new ColorDrawable(color(R.color.tv_ref_background)));
            panel.setBackground(new ColorDrawable(color(R.color.tv_ref_background)));
            toolbar.setBackground(new ColorDrawable(color(R.color.tv_ref_surface)));
            for(int i=0;i<labels.size();i++)labels.get(i).setTextColor(color(i<2?R.color.tv_ref_text:R.color.tv_ref_secondary));
            for(ButtonAppearance saved:buttons)styleButton(saved.view,saved.view.getId()==R.id.tv_setup_confirm||saved.view.getId()==R.id.tv_setup_next);
            ((MaterialButton)find(R.id.tv_setup_clear)).setIconResource(R.drawable.ic_delete_24);
            ((MaterialButton)find(R.id.tv_setup_confirm)).setIconResource(R.drawable.ic_tv_ref_check);
        }else{
            ((ViewGroup)instruction.getParent()).removeView(instruction);
            instructionParent.addView(instruction,Math.min(instructionIndex,instructionParent.getChildCount()),instructionLayout);instruction.setMinLines(2);
            for(int id:new int[]{R.id.tv_ref_header,R.id.tv_ref_frame,R.id.tv_ref_instruction_card,R.id.tv_ref_precise_toggle,R.id.tv_ref_lens_choices,R.id.tv_corner_coordinate_header,R.id.tv_corner_nudge_controls})find(id).setVisibility(View.GONE);
            find(R.id.tv_ref_section_icon).setVisibility(View.VISIBLE);find(R.id.tv_ref_section_title).setVisibility(View.VISIBLE);
            find(R.id.tv_camera_lens).setVisibility(View.VISIBLE);
            for(int id:new int[]{R.id.tv_camera_available_hint,R.id.tv_camera_input_mode,R.id.tv_camera_rectification_hint})find(id).setVisibility(View.VISIBLE);
            root.setBackground(rootBackground);panel.setBackground(panelBackground);toolbar.setBackground(toolbarBackground);
            panel.setPadding(panelPadding[0],panelPadding[1],panelPadding[2],panelPadding[3]);
            for(ButtonAppearance saved:buttons)saved.restore();
            for(int i=0;i<labels.size();i++){labels.get(i).setTextColor(labelColors.get(i));labels.get(i).setTextSize(TypedValue.COMPLEX_UNIT_PX,labelSizes.get(i));}
        }
    }
    /** Stable bounds within the wizard; ordinary preview and calculator use their existing layout. */
    void layout(boolean cameraStep,boolean landscape){
        if(!enabled)return;windowKey=currentWindowKey();
        find(R.id.tv_ref_header).setVisibility(View.VISIBLE);find(R.id.tv_ref_frame).setVisibility(View.VISIBLE);
        find(R.id.tv_corner_coordinate_header).setVisibility(cameraStep?View.GONE:View.VISIBLE);
        find(R.id.tv_ref_section_icon).setVisibility(cameraStep?View.VISIBLE:View.GONE);find(R.id.tv_ref_section_title).setVisibility(cameraStep?View.VISIBLE:View.GONE);
        textIfChanged(find(R.id.tv_ref_section_title),font()>1.3f&&!cameraStep?"Rogi TV":activity.getString(cameraStep?R.string.tv_ref_camera:R.string.tv_ref_corners));
        find(R.id.tv_ref_section_badge).setVisibility(font()>1.3f&&cameraStep?View.GONE:View.VISIBLE);
        int sectionIcon=cameraStep?R.drawable.ic_camera_preview_32:R.drawable.ic_auto_focus_zoom_24;
        if(lastSectionIcon!=sectionIcon){lastSectionIcon=sectionIcon;((android.widget.ImageView)find(R.id.tv_ref_section_icon)).setImageResource(sectionIcon);}
        int usable=Math.max(dp(240),root.getHeight()-root.getPaddingTop()-root.getPaddingBottom());
        int toolbarHeight=Math.max(toolbar.getHeight(),toolbar.getLayoutParams().height);
        int headerHeight=dp(Math.max(56,landscape||font()>1.3f?28*font()+8:48*font()));View header=find(R.id.tv_ref_header);
        if(header.getLayoutParams().height!=headerHeight){header.getLayoutParams().height=headerHeight;header.requestLayout();}
        View frame=find(R.id.tv_ref_frame);ConstraintLayout.LayoutParams fp=new ConstraintLayout.LayoutParams((ConstraintLayout.LayoutParams)frame.getLayoutParams());
        ConstraintLayout.LayoutParams pp=new ConstraintLayout.LayoutParams((ConstraintLayout.LayoutParams)preview.getLayoutParams());
        ConstraintLayout.LayoutParams editor=new ConstraintLayout.LayoutParams((ConstraintLayout.LayoutParams)panel.getLayoutParams());
        if(landscape){
            int side=Math.min(dp(360),Math.round((root.getWidth()-root.getPaddingLeft()-root.getPaddingRight())*.45f));
            fp.width=0;fp.height=0;fp.endToEnd=ConstraintLayout.LayoutParams.UNSET;fp.endToStart=R.id.tv_setup_panel;
            fp.topToBottom=R.id.tv_ref_header;fp.bottomToTop=ConstraintLayout.LayoutParams.UNSET;fp.bottomToBottom=ConstraintLayout.LayoutParams.PARENT_ID;fp.bottomMargin=dp(12);
            editor.width=side;editor.height=0;editor.startToStart=ConstraintLayout.LayoutParams.UNSET;editor.endToEnd=ConstraintLayout.LayoutParams.PARENT_ID;
            editor.topToBottom=R.id.tv_ref_header;editor.bottomToBottom=ConstraintLayout.LayoutParams.PARENT_ID;
        }else{
            int footer=Math.max(dp(56),find(R.id.tv_setup_actions).getMeasuredHeight());
            // Reserve a usable scrolling area even with two large-font footer buttons.
            int wanted=Math.round((usable-toolbarHeight)*.46f)-dp(16),available=usable-toolbarHeight-headerHeight-footer-dp(112);
            fp.width=0;fp.height=Math.max(dp(96),Math.min(wanted,available));fp.endToStart=ConstraintLayout.LayoutParams.UNSET;fp.endToEnd=ConstraintLayout.LayoutParams.PARENT_ID;
            fp.bottomToTop=fp.bottomToBottom=ConstraintLayout.LayoutParams.UNSET;fp.bottomMargin=0;
            editor.width=0;editor.height=0;editor.startToStart=editor.endToEnd=ConstraintLayout.LayoutParams.PARENT_ID;
            editor.topToBottom=R.id.tv_ref_frame;editor.bottomToBottom=ConstraintLayout.LayoutParams.PARENT_ID;
        }
        int verticalMargin=!cameraStep&&usable<dp(640)&&font()>1.3f?8:12;
        editor.topToTop=ConstraintLayout.LayoutParams.UNSET;editor.topMargin=dp(verticalMargin);editor.bottomMargin=dp(verticalMargin);
        editor.leftMargin=editor.rightMargin=dp(16);editor.setMarginStart(dp(16));editor.setMarginEnd(dp(16));
        pp.width=pp.height=0;pp.startToStart=pp.endToEnd=R.id.tv_ref_frame;pp.endToStart=ConstraintLayout.LayoutParams.UNSET;
        pp.topToTop=R.id.tv_ref_frame;pp.topToBottom=ConstraintLayout.LayoutParams.UNSET;pp.bottomToTop=ConstraintLayout.LayoutParams.UNSET;pp.bottomToBottom=R.id.tv_ref_frame;
        pp.topMargin=0;pp.bottomMargin=dp(2);pp.leftMargin=pp.rightMargin=dp(2);pp.setMarginStart(dp(2));pp.setMarginEnd(dp(2));
        apply(frame,fp);apply(preview,pp);apply(panel,editor);panel.setPadding(0,0,0,0);
        LinearLayout.LayoutParams body=(LinearLayout.LayoutParams)find(R.id.tv_setup_body_scroll).getLayoutParams();
        if(body.height!=0||body.weight!=1){body.height=0;body.weight=1;find(R.id.tv_setup_body_scroll).setLayoutParams(body);}
        find(R.id.tv_camera_lens).setVisibility(View.GONE);find(R.id.tv_ref_lens_choices).setVisibility(View.VISIBLE);
        for(int id:new int[]{R.id.tv_camera_available_hint,R.id.tv_camera_input_mode,R.id.tv_camera_rectification_hint})find(id).setVisibility(View.GONE);
    }
    void content(boolean cameraStep,TvSetupController.Phase phase){
        if(!enabled)return;boolean editor=!cameraStep&&phase==TvSetupController.Phase.EDITING;
        textIfChanged(find(R.id.tv_ref_section_badge),editor?"DRAFT":"Kalibracja");
        find(R.id.tv_ref_instruction_card).setVisibility(editor?View.VISIBLE:View.GONE);find(R.id.tv_ref_precise_toggle).setVisibility(editor?View.VISIBLE:View.GONE);
        find(R.id.tv_corner_nudge_controls).setVisibility(editor?View.VISIBLE:View.GONE);
        if(editor){
            find(R.id.tv_setup_status).setVisibility(View.GONE);
            textIfChanged(find(R.id.tv_ref_instruction_heading),font()>1.3f?"Dopasuj rogi TV":activity.getString(R.string.tv_ref_instruction_title));
            if(font()>1.3f){instruction.setMinLines(1);textIfChanged(instruction,"Przeciągnij uchwyty i zatwierdź.");}
            find(R.id.tv_setup_title).setVisibility(View.VISIBLE);((TextView)find(R.id.tv_setup_title)).setTextSize(16);updateExpansion();
        }else find(R.id.tv_corner_choices).setVisibility(View.GONE);
    }
    private void updateExpansion(){
        if(enabled&&find(R.id.tv_ref_precise_toggle).isShown())find(R.id.tv_corner_choices).setVisibility(preciseExpanded?View.VISIBLE:View.GONE);
    }
    private void styleButton(MaterialButton button,boolean filled){
        int[][] states={new int[]{-android.R.attr.state_enabled},new int[]{}};
        ColorStateList text=new ColorStateList(states,new int[]{color(R.color.tv_ref_secondary),color(filled?R.color.tv_ref_background:R.color.tv_ref_accent)});
        button.setTextColor(text);button.setIconTint(text);button.setCornerRadius(dp(24));button.setStrokeColor(ColorStateList.valueOf(color(R.color.tv_ref_border)));
        button.setBackgroundTintList(new ColorStateList(states,new int[]{color(R.color.tv_ref_surface),color(filled?R.color.tv_ref_accent:R.color.tv_ref_background)}));
    }
    private static void apply(View view,ConstraintLayout.LayoutParams value){ConstraintLayout.LayoutParams old=(ConstraintLayout.LayoutParams)view.getLayoutParams();
        if(old.width!=value.width||old.height!=value.height||old.topToTop!=value.topToTop||old.topToBottom!=value.topToBottom||old.bottomToTop!=value.bottomToTop||old.bottomToBottom!=value.bottomToBottom||old.startToStart!=value.startToStart||old.endToEnd!=value.endToEnd||old.endToStart!=value.endToStart||old.topMargin!=value.topMargin||old.bottomMargin!=value.bottomMargin||old.leftMargin!=value.leftMargin||old.rightMargin!=value.rightMargin)view.setLayoutParams(value);}
    @SuppressWarnings("unchecked") private <T extends View>T find(int id){return (T)activity.findViewById(id);}
    private static void textIfChanged(TextView view,String text){if(!text.contentEquals(view.getText()))view.setText(text);}
    private int color(int id){return ContextCompat.getColor(activity,id);}
    private float font(){return activity.getResources().getConfiguration().fontScale;}
    private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
