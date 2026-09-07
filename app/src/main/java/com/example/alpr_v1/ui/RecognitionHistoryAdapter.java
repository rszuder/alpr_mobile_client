package com.example.alpr_v1.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.alpr_v1.R;
import com.example.alpr_v1.capture.RecognitionHistoryItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class RecognitionHistoryAdapter
        extends RecyclerView.Adapter<RecognitionHistoryAdapter.Holder> {
    public interface Listener {
        void onOpen(RecognitionHistoryItem item);
    }

    private final List<RecognitionHistoryItem> items = new ArrayList<>();
    private final Listener listener;

    public RecognitionHistoryAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setItems(List<RecognitionHistoryItem> replacement) {
        List<RecognitionHistoryItem> previous = new ArrayList<>(items);
        List<RecognitionHistoryItem> next = new ArrayList<>(replacement);
        DiffUtil.DiffResult difference = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previous.size();
            }

            @Override
            public int getNewListSize() {
                return next.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return previous.get(oldPosition).historyId.equals(next.get(newPosition).historyId);
            }

            @Override
            public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return false;
            }
        }, false);
        items.clear();
        items.addAll(next);
        difference.dispatchUpdatesTo(this);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).historyId.hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_recognition_history,
                parent,
                false
        ));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private final PlateCropView preview;
        private final TextView number;
        private final TextView time;
        private final TextView confidence;
        private final TextView observations;

        Holder(View itemView) {
            super(itemView);
            preview = itemView.findViewById(R.id.history_preview);
            number = itemView.findViewById(R.id.history_number);
            time = itemView.findViewById(R.id.history_time);
            confidence = itemView.findViewById(R.id.history_confidence);
            observations = itemView.findViewById(R.id.history_observations);
        }

        void bind(RecognitionHistoryItem item, Listener listener) {
            Context context = itemView.getContext();
            preview.setPlate(item.previewBitmap, item.characters);
            preview.setBoxesVisible(true);
            number.setText(item.text.isEmpty() ? context.getString(R.string.result_placeholder) : item.text);
            time.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(
                    new Date(item.capturedAtMillis)
            ));
            confidence.setText(context.getString(
                    R.string.history_confidence_format,
                    percent(item.confidence)
            ));
            observations.setText(context.getResources().getQuantityString(
                    R.plurals.history_observation_count,
                    Math.max(1, item.observations),
                    Math.max(1, item.observations)
            ));
            itemView.setOnClickListener(view -> listener.onOpen(item));
            itemView.setContentDescription(context.getString(
                    R.string.history_item_description,
                    item.text,
                    context.getString(
                            R.string.history_detail_meta,
                            time.getText(),
                            percent(item.confidence)
                    ),
                    observations.getText()
            ));
        }

        private static int percent(double confidence) {
            return (int) Math.round(Math.max(0.0, Math.min(1.0, confidence)) * 100.0);
        }
    }
}
