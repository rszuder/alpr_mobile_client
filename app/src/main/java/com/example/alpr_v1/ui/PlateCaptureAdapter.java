package com.example.alpr_v1.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alpr_v1.R;
import com.example.alpr_v1.capture.CapturedPlateItem;
import com.example.alpr_v1.pipeline.PlateCharacter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Adapter poziomej galerii cropów zarejestrowanych w sesji. */
public final class PlateCaptureAdapter
        extends RecyclerView.Adapter<PlateCaptureAdapter.Holder> {
    public interface SelectionListener {
        void onSelectionChanged(CapturedPlateItem item, boolean selected);
        void onVerificationChanged(
                CapturedPlateItem item,
                CapturedPlateItem.VerificationStatus status
        );
        void onCorrectionRequested(CapturedPlateItem item);
    }

    private final List<CapturedPlateItem> items = new ArrayList<>();
    private final SelectionListener selectionListener;
    private int visibleSlots = 1;

    public PlateCaptureAdapter(SelectionListener selectionListener) {
        this.selectionListener = selectionListener;
        setHasStableIds(true);
    }

    public void setItems(List<CapturedPlateItem> newItems) {
        List<CapturedPlateItem> previousItems = new ArrayList<>(items);
        List<CapturedPlateItem> replacementItems = new ArrayList<>(newItems);
        Collections.reverse(replacementItems);
        DiffUtil.DiffResult difference = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return previousItems.size(); }

            @Override
            public int getNewListSize() { return replacementItems.size(); }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return previousItems.get(oldPosition).captureId.equals(
                        replacementItems.get(newPosition).captureId
                );
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                // Elementy przechowują zmienny stan zapisu; ponowne związanie odświeża checkbox.
                return false;
            }
        }, false);
        items.clear();
        items.addAll(replacementItems);
        difference.dispatchUpdatesTo(this);
    }



    @Override
    public long getItemId(int position) {
        return items.get(position).captureId.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_plate_result,
                parent,
                false
        );
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position), selectionListener);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        private final PlateCropView crop;
        private final TextView number;
        private final TextView date;
        private final TextView meta;
        private final TextView inference;
        private final TextView characters;
        private final MaterialCheckBox save;
        private final TextView verificationStatus;
        private final MaterialButton verifyAccept;
        private final MaterialButton verifyReject;
        private final MaterialButton verifyEdit;

        Holder(View itemView) {
            super(itemView);
            crop = itemView.findViewById(R.id.plate_crop);
            number = itemView.findViewById(R.id.plate_number);
            date = itemView.findViewById(R.id.plate_date);
            meta = itemView.findViewById(R.id.plate_meta);
            inference = itemView.findViewById(R.id.plate_inference);
            characters = itemView.findViewById(R.id.plate_characters);
            save = itemView.findViewById(R.id.plate_save);
            verificationStatus = itemView.findViewById(R.id.plate_verification_status);
            verifyAccept = itemView.findViewById(R.id.plate_verify_accept);
            verifyReject = itemView.findViewById(R.id.plate_verify_reject);
            verifyEdit = itemView.findViewById(R.id.plate_verify_edit);
        }

        void bind(CapturedPlateItem item, SelectionListener listener) {
            Context context = itemView.getContext();
            crop.setPlate(item.bitmap, item.characters);
            number.setText(item.text.isEmpty()
                    ? context.getString(R.string.result_placeholder)
                    : item.text);
            date.setText(new SimpleDateFormat(
                    "yyyy-MM-dd  HH:mm:ss.SSS", Locale.getDefault()
            ).format(new Date(item.capturedAtMillis)));
            String state = context.getString(item.confirmed
                    ? R.string.plate_state_confirmed
                    : R.string.plate_state_preliminary);
            CharSequence metaText = coloredMeta(context, item, state);
            meta.setText(metaText);
            inference.setText(item.timing == null
                    ? context.getString(R.string.inference_time_unavailable)
                    : context.getString(
                            R.string.inference_time_format,
                            item.timing.totalMilliseconds(),
                            item.timing.characterInferenceNanos / 1_000_000.0
                    ));
            CharSequence details = characterDetails(context, item.characters);
            characters.setText(details);
            boolean stored = item.saveState == CapturedPlateItem.SaveState.SAVED;
            boolean saving = item.saveState == CapturedPlateItem.SaveState.SAVING;
            bindVerification(context, item, listener, saving);

            save.setOnCheckedChangeListener(null);
            save.setChecked(item.selectedForSave);
            save.setEnabled(!stored && !saving);
            save.setText(null);
            save.setContentDescription(context.getString(
                    stored
                            ? R.string.crop_saved
                            : saving ? R.string.crop_saving : R.string.crop_select_action
            ));
            save.setOnCheckedChangeListener((button, checked) -> {
                item.selectedForSave = checked;
                listener.onSelectionChanged(item, checked);
            });
            itemView.setContentDescription(
                    (item.text.isEmpty()
                            ? context.getString(R.string.plate_waiting_for_characters)
                            : item.text)
                            + ". " + metaText + ". " + date.getText() + ". " + details
                            + ". " + verificationStatus.getText()
            );
        }

        private void bindVerification(
                Context context,
                CapturedPlateItem item,
                SelectionListener listener,
                boolean saving
        ) {
            verifyAccept.setOnClickListener(null);
            verifyReject.setOnClickListener(null);
            verifyEdit.setOnClickListener(null);
            boolean accepted = item.verificationStatus
                    == CapturedPlateItem.VerificationStatus.ACCEPTED;
            boolean rejected = item.verificationStatus
                    == CapturedPlateItem.VerificationStatus.REJECTED;
            verifyAccept.setChecked(accepted);
            verifyReject.setChecked(rejected);
            verifyAccept.setEnabled(!item.text.isEmpty() && !saving);
            verifyReject.setEnabled(!saving);
            verifyEdit.setEnabled(!saving);
            int statusColor = R.color.alpr_text_muted;
            if (accepted || item.verificationStatus
                    == CapturedPlateItem.VerificationStatus.CORRECTED) {
                statusColor = R.color.alpr_success;
            } else if (rejected) {
                statusColor = R.color.alpr_warning;
            }
            verificationStatus.setTextColor(ContextCompat.getColor(context, statusColor));
            switch (item.verificationStatus) {
                case ACCEPTED:
                    verificationStatus.setText(R.string.verification_accepted);
                    break;
                case REJECTED:
                    verificationStatus.setText(R.string.verification_rejected);
                    break;
                case CORRECTED:
                    verificationStatus.setText(context.getString(
                            R.string.verification_corrected,
                            item.groundTruthText
                    ));
                    break;
                case NOT_REVIEWED:
                default:
                    verificationStatus.setText(R.string.verification_not_reviewed);
                    break;
            }
            verifyAccept.setOnClickListener(view -> listener.onVerificationChanged(
                    item,
                    accepted
                            ? CapturedPlateItem.VerificationStatus.NOT_REVIEWED
                            : CapturedPlateItem.VerificationStatus.ACCEPTED
            ));
            verifyReject.setOnClickListener(view -> listener.onVerificationChanged(
                    item,
                    rejected
                            ? CapturedPlateItem.VerificationStatus.NOT_REVIEWED
                            : CapturedPlateItem.VerificationStatus.REJECTED
            ));
            verifyEdit.setOnClickListener(view -> listener.onCorrectionRequested(item));
        }

        private static CharSequence coloredMeta(
                Context context,
                CapturedPlateItem item,
                String state
        ) {
            SpannableStringBuilder value = new SpannableStringBuilder();
            value.append("MT ");
            appendColored(
                    value,
                    percent(item.plateConfidence) + "%",
                    ContextCompat.getColor(context, R.color.alpr_primary)
            );
            value.append(" · MZ ");
            appendColored(
                    value,
                    percent(item.recognitionConfidence) + "%",
                    ContextCompat.getColor(context, R.color.alpr_success)
            );
            value.append(" · ");
            appendColored(
                    value,
                    state,
                    ContextCompat.getColor(
                            context,
                            item.confirmed ? R.color.alpr_success : R.color.alpr_warning
                    )
            );
            return value;
        }

        private static CharSequence characterDetails(
                Context context,
                List<PlateCharacter> characters
        ) {
            if (characters == null || characters.isEmpty()) {
                return context.getString(R.string.plate_waiting_for_characters);
            }
            SpannableStringBuilder details = new SpannableStringBuilder();
            for (int index = 0; index < characters.size(); index++) {
                PlateCharacter character = characters.get(index);
                if (details.length() > 0) {
                    if (index % 2 == 0) details.append('\n');
                    else details.append("   ");
                }
                int lineStart = details.length();
                int confidence = percent(character.confidence);
                String line = context.getString(
                        R.string.plate_character_format,
                        character.label,
                        Math.round((character.left + character.right) * 50f),
                        Math.round((character.top + character.bottom) * 50f),
                        confidence
                );
                details.append(line);
                int labelStart = lineStart;
                int labelEnd = Math.min(details.length(), labelStart + character.label.length());
                details.setSpan(
                        new ForegroundColorSpan(ContextCompat.getColor(context, R.color.alpr_primary)),
                        labelStart,
                        labelEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                String confidenceToken = confidence + "%";
                int tokenOffset = line.lastIndexOf(confidenceToken);
                if (tokenOffset >= 0) {
                    details.setSpan(
                            new ForegroundColorSpan(ContextCompat.getColor(context, R.color.alpr_success)),
                            lineStart + tokenOffset,
                            lineStart + tokenOffset + confidenceToken.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                }
            }
            return details;
        }

        private static void appendColored(
                SpannableStringBuilder destination,
                String text,
                int color
        ) {
            int start = destination.length();
            destination.append(text);
            destination.setSpan(
                    new ForegroundColorSpan(color),
                    start,
                    destination.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        private static int percent(double confidence) {
            return (int) Math.round(Math.max(0.0, Math.min(1.0, confidence)) * 100.0);
        }
    }
}
