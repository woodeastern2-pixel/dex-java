package com.lumi.app.ui;

import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lumi.app.R;
import com.lumi.app.model.ConversationMessage;

import java.io.IOException;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface ImageActionListener {
        void onSaveImageRequested(String uriString);
        void onShareImageRequested(String uriString);
        void onRegenerateImageRequested(String prompt);
        void onSpeakTextRequested(String text);
        void onStopSpeakRequested();
    }

    private static final int TYPE_USER = 1;
    private static final int TYPE_LUMI = 2;

    private final List<ConversationMessage> items;
    private final ImageActionListener imageActionListener;
    private MediaPlayer activePlayer;
    private int expandedLumiTextPosition = RecyclerView.NO_POSITION;
    private int expandedUserTextPosition = RecyclerView.NO_POSITION;

    public ChatAdapter(List<ConversationMessage> items, ImageActionListener imageActionListener) {
        this.items = items;
        this.imageActionListener = imageActionListener;
    }

    public List<ConversationMessage> getItems() {
        return items;
    }

    public void setMessages(List<ConversationMessage> messages) {
        items.clear();
        if (messages != null) items.addAll(messages);
        expandedLumiTextPosition = RecyclerView.NO_POSITION;
        expandedUserTextPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public void addMessage(ConversationMessage message) {
        items.add(message);
        expandedLumiTextPosition = RecyclerView.NO_POSITION;
        expandedUserTextPosition = RecyclerView.NO_POSITION;
        notifyItemInserted(items.size() - 1);
    }

    public void replaceLastUserPreview(ConversationMessage real) {
        for (int i = items.size() - 1; i >= 0; i--) {
            ConversationMessage m = items.get(i);
            if ("user".equals(m.sender) && m.id == 0) {
                items.set(i, real);
                expandedUserTextPosition = RecyclerView.NO_POSITION;
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void updateLastLumiPreview(String content) {
        for (int i = items.size() - 1; i >= 0; i--) {
            ConversationMessage m = items.get(i);
            if ("lumi".equals(m.sender) && m.id == 0) {
                m.content = content == null ? "" : content;
                expandedLumiTextPosition = RecyclerView.NO_POSITION;
                notifyItemChanged(i);
                return;
            }
        }
    }

    public boolean replaceLastLumiPreview(ConversationMessage real) {
        for (int i = items.size() - 1; i >= 0; i--) {
            ConversationMessage m = items.get(i);
            if ("lumi".equals(m.sender) && m.id == 0) {
                items.set(i, real);
                expandedLumiTextPosition = RecyclerView.NO_POSITION;
                notifyItemChanged(i);
                return true;
            }
        }
        return false;
    }

    public void releasePlayer() {
        if (activePlayer != null) {
            try { activePlayer.release(); } catch (Exception ignored) {}
            activePlayer = null;
        }
    }

    public void collapseTextActionPanels() {
        int oldLumi = expandedLumiTextPosition;
        int oldUser = expandedUserTextPosition;
        expandedLumiTextPosition = RecyclerView.NO_POSITION;
        expandedUserTextPosition = RecyclerView.NO_POSITION;
        notifyChangedDistinct(oldLumi, oldUser);
    }

    @Override
    public int getItemViewType(int position) {
        return "user".equals(items.get(position).sender) ? TYPE_USER : TYPE_LUMI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            View view = inflater.inflate(R.layout.item_chat_user, parent, false);
            return new UserHolder(view);
        }
        View view = inflater.inflate(R.layout.item_chat_lumi, parent, false);
        return new LumiHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ConversationMessage message = items.get(position);
        if (holder instanceof UserHolder) {
            bindUser((UserHolder) holder, message, position);
        } else {
            bindLumi((LumiHolder) holder, message, position);
        }
    }

    private void bindUser(UserHolder h, ConversationMessage m, int position) {
        if (m.content != null && !m.content.isEmpty()) {
            h.bubble.setText(m.content);
            h.bubble.setVisibility(View.VISIBLE);
        } else {
            h.bubble.setVisibility(View.GONE);
        }
        boolean hasText = m.content != null && !m.content.trim().isEmpty();
        h.textActions.setVisibility(hasText && expandedUserTextPosition == position ? View.VISIBLE : View.GONE);
        h.copyButton.setOnClickListener(null);
        h.bubble.setOnClickListener(null);
        h.bubble.setOnLongClickListener(null);
        if (hasText) {
            h.copyButton.setOnClickListener(v -> copyText(v.getContext(), m.content));
            h.bubble.setOnClickListener(v -> toggleUserTextActions(position));
            h.bubble.setOnLongClickListener(v -> {
                copyText(v.getContext(), m.content);
                return true;
            });
        }
        h.image.setVisibility(View.GONE);
        h.voice.setVisibility(View.GONE);
        h.voice.setOnClickListener(null);
        h.image.setOnClickListener(null);
        if (ConversationMessage.ATTACH_IMAGE.equals(m.attachmentType) && m.attachmentUri != null) {
            h.image.setVisibility(View.VISIBLE);
            try { h.image.setImageURI(Uri.parse(m.attachmentUri)); } catch (Exception ignored) {}
            h.image.setOnClickListener(v -> openExternal(v.getContext(), Uri.parse(m.attachmentUri), "image/*"));
        } else if (ConversationMessage.ATTACH_VOICE.equals(m.attachmentType)) {
            h.voice.setVisibility(View.VISIBLE);
            String label;
            if (m.content != null && !m.content.isEmpty()) {
                label = h.itemView.getContext().getString(R.string.chat_voice_transcript, m.content);
            } else {
                int seconds = parseIntSafe(m.attachmentMeta, 0);
                label = h.itemView.getContext().getString(R.string.chat_voice_caption, seconds);
            }
            h.voice.setText(label);
            if (m.content != null && !m.content.isEmpty()) {
                h.bubble.setVisibility(View.GONE);
            }
            if (m.attachmentUri != null) {
                h.voice.setOnClickListener(v -> playVoice(v.getContext(), m.attachmentUri));
            }
        }
    }

    private void bindLumi(LumiHolder h, ConversationMessage m, int position) {
        h.bubble.setText(m.content);
        boolean hasText = m.content != null && !m.content.isEmpty();
        h.bubble.setVisibility(hasText ? View.VISIBLE : View.GONE);
        h.textActions.setVisibility(hasText && expandedLumiTextPosition == position ? View.VISIBLE : View.GONE);
        h.copyButton.setOnClickListener(null);
        h.speakButton.setOnClickListener(null);
        h.speakButton.setOnLongClickListener(null);
        h.bubble.setOnClickListener(null);
        h.bubble.setOnLongClickListener(null);
        if (hasText) {
            h.copyButton.setOnClickListener(v -> copyText(v.getContext(), m.content));
            h.bubble.setOnClickListener(v -> toggleLumiTextActions(position));
            h.bubble.setOnLongClickListener(v -> {
                copyText(v.getContext(), m.content);
                return true;
            });
            if (imageActionListener != null) {
                h.speakButton.setOnClickListener(v -> imageActionListener.onSpeakTextRequested(m.content));
                h.speakButton.setOnLongClickListener(v -> {
                    imageActionListener.onStopSpeakRequested();
                    return true;
                });
            }
        }
        h.moodTag.setText("· " + MoodPalette.label(m.mood));
        int color = MoodPalette.color(h.itemView.getContext(), m.mood);
        h.moodTag.setTextColor(color);
        if (h.avatar != null && h.avatar.getBackground() != null) {
            h.avatar.getBackground().setTint(color);
            h.avatar.getBackground().setAlpha(190);
        }
        h.link.setVisibility(View.GONE);
        h.link.setOnClickListener(null);
        h.image.setVisibility(View.GONE);
        h.image.setImageDrawable(null);
        h.image.setOnClickListener(null);
        h.imageActions.setVisibility(View.GONE);
        h.saveButton.setOnClickListener(null);
        h.shareButton.setOnClickListener(null);
        h.regenButton.setOnClickListener(null);
        if (ConversationMessage.ATTACH_LINK.equals(m.attachmentType) && m.attachmentUri != null) {
            h.link.setVisibility(View.VISIBLE);
            String label = m.attachmentMeta != null && !m.attachmentMeta.isEmpty()
                    ? "🔗 " + m.attachmentMeta
                    : "🔗 " + m.attachmentUri;
            h.link.setText(label);
            h.link.setOnClickListener(v -> openExternal(v.getContext(), Uri.parse(m.attachmentUri), null));
        } else if (ConversationMessage.ATTACH_IMAGE.equals(m.attachmentType) && m.attachmentUri != null) {
            renderLumiImage(h.image, m.attachmentUri);
            h.imageActions.setVisibility(View.VISIBLE);
            if (imageActionListener != null) {
                String prompt = m.attachmentMeta == null ? "" : m.attachmentMeta;
                h.saveButton.setOnClickListener(v -> imageActionListener.onSaveImageRequested(m.attachmentUri));
                h.shareButton.setOnClickListener(v -> imageActionListener.onShareImageRequested(m.attachmentUri));
                h.regenButton.setOnClickListener(v -> imageActionListener.onRegenerateImageRequested(prompt));
            }
        }
    }

    private void renderLumiImage(ImageView view, String uriString) {
        view.setVisibility(View.VISIBLE);
        try {
            Uri uri = Uri.parse(uriString);
            String scheme = uri.getScheme();
            if ("file".equalsIgnoreCase(scheme) && uri.getPath() != null) {
                android.graphics.Bitmap bm = BitmapFactory.decodeFile(uri.getPath());
                if (bm != null) {
                    view.setImageBitmap(bm);
                } else {
                    view.setVisibility(View.GONE);
                    return;
                }
            } else if ("content".equalsIgnoreCase(scheme)) {
                view.setImageURI(uri);
            } else {
                // 다른 스키마(http 등) 은 이 구현에서는 아직 지원하지 않는다. 숨김.
                view.setVisibility(View.GONE);
                return;
            }
            view.setOnClickListener(v -> openExternal(v.getContext(), Uri.parse(uriString), "image/*"));
        } catch (Throwable ignored) {
            view.setVisibility(View.GONE);
        }
    }

    private void playVoice(Context ctx, String uri) {
        releasePlayer();
        try {
            activePlayer = new MediaPlayer();
            activePlayer.setDataSource(ctx, Uri.parse(uri));
            activePlayer.setOnCompletionListener(mp -> releasePlayer());
            activePlayer.prepare();
            activePlayer.start();
        } catch (IOException | IllegalStateException e) {
            Toast.makeText(ctx, "재생할 수 없어요: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            releasePlayer();
        }
    }

    private void openExternal(Context ctx, Uri uri, String mime) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            if (mime != null) i.setDataAndType(uri, mime); else i.setData(uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(ctx, "열 수 없는 항목이에요", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyText(Context context, String text) {
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(context, context.getString(R.string.chat_export_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        ClipData data = ClipData.newPlainText("lumi_message", text);
        clipboard.setPrimaryClip(data);
        Toast.makeText(context, context.getString(R.string.chat_text_copied), Toast.LENGTH_SHORT).show();
    }

    private void toggleLumiTextActions(int position) {
        int oldLumi = expandedLumiTextPosition;
        int oldUser = expandedUserTextPosition;
        expandedUserTextPosition = RecyclerView.NO_POSITION;
        expandedLumiTextPosition = (oldLumi == position) ? RecyclerView.NO_POSITION : position;
        notifyChangedDistinct(oldLumi, oldUser, expandedLumiTextPosition);
    }

    private void toggleUserTextActions(int position) {
        int oldLumi = expandedLumiTextPosition;
        int oldUser = expandedUserTextPosition;
        expandedLumiTextPosition = RecyclerView.NO_POSITION;
        expandedUserTextPosition = (oldUser == position) ? RecyclerView.NO_POSITION : position;
        notifyChangedDistinct(oldLumi, oldUser, expandedUserTextPosition);
    }

    private void notifyChangedDistinct(int... positions) {
        java.util.HashSet<Integer> unique = new java.util.HashSet<>();
        for (int position : positions) {
            if (position != RecyclerView.NO_POSITION) {
                unique.add(position);
            }
        }
        for (Integer position : unique) {
            notifyItemChanged(position);
        }
    }

    private int parseIntSafe(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class UserHolder extends RecyclerView.ViewHolder {
        TextView bubble;
        LinearLayout textActions;
        TextView copyButton;
        ImageView image;
        TextView voice;
        UserHolder(View itemView) {
            super(itemView);
            bubble = itemView.findViewById(R.id.userBubble);
            textActions = itemView.findViewById(R.id.userTextActions);
            copyButton = itemView.findViewById(R.id.userCopyButton);
            image = itemView.findViewById(R.id.userAttachmentImage);
            voice = itemView.findViewById(R.id.userVoiceChip);
        }
    }

    static class LumiHolder extends RecyclerView.ViewHolder {
        TextView bubble;
        LinearLayout textActions;
        TextView copyButton;
        TextView speakButton;
        TextView moodTag;
        View avatar;
        TextView link;
        ImageView image;
        LinearLayout imageActions;
        TextView saveButton;
        TextView shareButton;
        TextView regenButton;
        LumiHolder(View itemView) {
            super(itemView);
            bubble = itemView.findViewById(R.id.lumiBubble);
            textActions = itemView.findViewById(R.id.lumiTextActions);
            copyButton = itemView.findViewById(R.id.lumiCopyButton);
            speakButton = itemView.findViewById(R.id.lumiSpeakButton);
            moodTag = itemView.findViewById(R.id.lumiMoodTag);
            avatar = itemView.findViewById(R.id.lumiAvatar);
            link = itemView.findViewById(R.id.lumiAttachmentLink);
            image = itemView.findViewById(R.id.lumiAttachmentImage);
            imageActions = itemView.findViewById(R.id.lumiImageActions);
            saveButton = itemView.findViewById(R.id.imageSaveButton);
            shareButton = itemView.findViewById(R.id.imageShareButton);
            regenButton = itemView.findViewById(R.id.imageRegenButton);
        }
    }
}
