package com.example.alpr_v1.ui;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Keeps app-owned dialogs immersive without replacing their existing show/dismiss listeners. */
public final class ImmersiveDialogBuilder extends MaterialAlertDialogBuilder {
    public ImmersiveDialogBuilder(Context context){super(context);}
    public ImmersiveDialogBuilder(Context context,int theme){super(context,theme);}
    @NonNull @Override public AlertDialog create(){
        AlertDialog dialog=super.create();
        CameraWindowUi.keepImmersive(dialog.getWindow());
        return dialog;
    }
}
