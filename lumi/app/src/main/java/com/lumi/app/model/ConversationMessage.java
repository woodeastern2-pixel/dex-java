package com.lumi.app.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "conversation_message")
public class ConversationMessage {

    /** 텍스트만 있는 일반 메시지. */
    public static final String ATTACH_NONE = "none";
    /** 사용자/루미가 공유한 사진. attachmentUri = content:// 또는 https://. */
    public static final String ATTACH_IMAGE = "image";
    /** 사용자가 녹음한 음성 메모. */
    public static final String ATTACH_VOICE = "voice";
    /** 루미가 공유하는 외부 링크. */
    public static final String ATTACH_LINK = "link";
    /** 루미가 먼저 보낸 프로액티브(알림성) 메시지. */
    public static final String ATTACH_NUDGE = "nudge";

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String sender;

    @NonNull
    public String content;

    @NonNull
    public String mood;

    public long timestamp;

    @NonNull
    @ColumnInfo(name = "attachment_type", defaultValue = "none")
    public String attachmentType = ATTACH_NONE;

    @Nullable
    @ColumnInfo(name = "attachment_uri")
    public String attachmentUri;

    /** 음성 길이(초), 링크 제목, 캡션 등 부가 정보. */
    @Nullable
    @ColumnInfo(name = "attachment_meta")
    public String attachmentMeta;

    public ConversationMessage(@NonNull String sender, @NonNull String content, @NonNull String mood, long timestamp) {
        this.sender = sender;
        this.content = content;
        this.mood = mood;
        this.timestamp = timestamp;
    }

    @Ignore
    public ConversationMessage(@NonNull String sender,
                               @NonNull String content,
                               @NonNull String mood,
                               long timestamp,
                               @NonNull String attachmentType,
                               @Nullable String attachmentUri,
                               @Nullable String attachmentMeta) {
        this(sender, content, mood, timestamp);
        this.attachmentType = attachmentType;
        this.attachmentUri = attachmentUri;
        this.attachmentMeta = attachmentMeta;
    }

    public boolean hasAttachment() {
        return attachmentType != null && !ATTACH_NONE.equals(attachmentType);
    }
}