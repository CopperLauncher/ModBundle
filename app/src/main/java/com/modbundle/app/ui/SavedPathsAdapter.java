package com.modbundle.app.ui;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

@Deprecated
public final class SavedPathsAdapter extends RecyclerView.Adapter<SavedPathsAdapter.ViewHolder> {
    @Override
    public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        throw new UnsupportedOperationException("SavedPathsAdapter is deprecated and should not be used.");
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        throw new UnsupportedOperationException("SavedPathsAdapter is deprecated and should not be used.");
    }

    @Override
    public int getItemCount() { return 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View itemView) {
            super(itemView);
        }
    }
}
