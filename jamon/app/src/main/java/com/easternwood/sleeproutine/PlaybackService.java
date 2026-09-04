package com.easternwood.sleeproutine;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
public class PlaybackService extends Service {
    static final String ACTION_PLAY = "com.easternwood.sleeproutine.PLAY";
    static final String ACTION_SET_VOLUME = "com.easternwood.sleeproutine.SET_VOLUME";
    static final String ACTION_STOP = "com.easternwood.sleeproutine.STOP";
    static final String ACTION_TOGGLE = "com.easternwood.sleeproutine.TOGGLE";
    private static final String CHANNEL_ID = "jamon_sleep_playback";
    static final String EXTRA_SOUND_INDEX = "service_sound_index";
    static final String EXTRA_SOUND_NAMES = "service_sound_names";
    static final String EXTRA_SOUND_RESOURCES = "service_sound_resources";
    static final String EXTRA_VOLUME = "service_volume";
    static final String EXTRA_VOLUMES = "service_volumes";
    private static final int NOTIFICATION_ID = 203;
    private final List<MediaPlayer> players = new ArrayList<>();
    private String soundNames;

    private Notification buildNotification() {
        Intent intent = new Intent(this, (Class<?>) MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int i = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent activity = PendingIntent.getActivity(this, 0, intent, i);
        Intent intent2 = new Intent(this, (Class<?>) PlaybackService.class);
        intent2.setAction(ACTION_STOP);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.playing))
                .setContentText(this.soundNames)
                .setContentIntent(activity)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_notification,
                        getString(R.string.notification_stop),
                        PendingIntent.getService(this, 1, intent2, i))
                        .build())
                .build();
    }

    private void createChannel() {
        NotificationChannel notificationChannel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW);
        notificationChannel.setDescription(getString(R.string.playing));
        notificationChannel.setSound(null, null);
        notificationChannel.enableVibration(false);
        ((NotificationManager) getSystemService(NotificationManager.class)).createNotificationChannel(notificationChannel);
    }

    private String joinNames(String[] strArr) {
        if (strArr == null || strArr.length == 0) {
            return getString(R.string.sound_brown);
        }
        StringBuilder sb = new StringBuilder();
        for (String str : strArr) {
            if (str != null && !str.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(" + ");
                }
                sb.append(str);
            }
        }
        return sb.length() == 0 ? getString(R.string.sound_brown) : sb.toString();
    }

    private void play(int[] iArr, float[] fArr) {
        releasePlayers();
        int i = 0;
        while (i < iArr.length) {
            MediaPlayer mediaPlayerCreate = MediaPlayer.create(this, iArr[i]);
            if (mediaPlayerCreate != null) {
                mediaPlayerCreate.setLooping(true);
                mediaPlayerCreate.setWakeMode(this, 1);
                float fMax = Math.max(0.02f, Math.min(1.0f, (fArr == null || i >= fArr.length) ? 0.55f : fArr[i]));
                mediaPlayerCreate.setVolume(fMax, fMax);
                this.players.add(mediaPlayerCreate);
            }
            i++;
        }
        if (this.players.isEmpty()) {
            stopSelf();
            return;
        }
        Iterator<MediaPlayer> it = this.players.iterator();
        while (it.hasNext()) {
            it.next().start();
        }
    }

    private void releasePlayers() {
        for (MediaPlayer mediaPlayer : this.players) {
            try {
                mediaPlayer.stop();
            } catch (RuntimeException e) {
            }
            mediaPlayer.release();
        }
        this.players.clear();
    }

    private void setVolume(int i, float f) {
        if (i < 0 || i >= this.players.size()) {
            return;
        }
        float fMax = Math.max(0.02f, Math.min(1.0f, f));
        this.players.get(i).setVolume(fMax, fMax);
    }

    private void stopPlayback() {
        releasePlayers();
        stopForeground(true);
    }

    private void toggle() {
        if (this.players.isEmpty()) {
            return;
        }
        boolean zIsPlaying = this.players.get(0).isPlaying();
        for (MediaPlayer mediaPlayer : this.players) {
            if (zIsPlaying && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            } else if (!zIsPlaying && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
        }
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override // android.app.Service
    public void onDestroy() {
        stopPlayback();
        super.onDestroy();
    }

    @Override // android.app.Service
    public int onStartCommand(Intent intent, int i, int i2) {
        String action = intent == null ? ACTION_PLAY : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopPlayback();
            stopSelf();
            return 2;
        }
        if (ACTION_TOGGLE.equals(action)) {
            toggle();
            return 2;
        }
        if (ACTION_SET_VOLUME.equals(action)) {
            if (intent != null) {
                setVolume(intent.getIntExtra(EXTRA_SOUND_INDEX, -1), intent.getFloatExtra(EXTRA_VOLUME, 0.55f));
            }
            return 2;
        }
        int[] intArrayExtra = intent == null ? null : intent.getIntArrayExtra(EXTRA_SOUND_RESOURCES);
        String[] stringArrayExtra = intent == null ? null : intent.getStringArrayExtra(EXTRA_SOUND_NAMES);
        float[] floatArrayExtra = intent != null ? intent.getFloatArrayExtra(EXTRA_VOLUMES) : null;
        if (intArrayExtra == null || intArrayExtra.length == 0) {
            intArrayExtra = new int[]{R.raw.brown_noise};
            stringArrayExtra = new String[]{getString(R.string.sound_brown)};
            floatArrayExtra = new float[]{0.6f};
        }
        this.soundNames = joinNames(stringArrayExtra);
        startForeground(NOTIFICATION_ID, buildNotification());
        play(intArrayExtra, floatArrayExtra);
        return 2;
    }
}
