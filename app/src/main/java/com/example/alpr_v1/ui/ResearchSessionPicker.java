package com.example.alpr_v1.ui;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.example.alpr_v1.R;
import com.example.alpr_v1.experiment.ResearchSessionSummary;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public final class ResearchSessionPicker {
    private ResearchSessionPicker() { }
    public static AlertDialog show(Context context, List<ResearchSessionSummary> sessions, Consumer<File> selected) {
        ResearchSessionExportAdapter adapter = new ResearchSessionExportAdapter(sessions);
        return new MaterialAlertDialogBuilder(context).setTitle(R.string.session_export_title)
                .setAdapter(adapter,(dialog,index) -> selected.accept(adapter.getItem(index).archive))
                .setNegativeButton(R.string.menu_close,null).show();
    }
}
