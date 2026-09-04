package com.easternwood.sleeproutine;

import java.util.Arrays;
import java.util.List;

/* JADX INFO: loaded from: classes.dex */
final class SoundCatalog {
    static final List<Sound> ALL = Arrays.asList(
            new Sound("brown", R.string.sound_brown, R.string.sound_brown_desc, R.raw.brown_noise, R.drawable.sound_brown, false, false),
            new Sound("rain", R.string.sound_rain, R.string.sound_rain_desc, R.raw.rain_soft, R.drawable.sound_rain, false, true),
            new Sound("fan", R.string.sound_fan, R.string.sound_fan_desc, R.raw.fan_soft, R.drawable.sound_fan, false, true),
            new Sound("waves", R.string.sound_waves, R.string.sound_waves_desc, R.raw.night_waves, R.drawable.sound_waves, false, true),
            new Sound("forest", R.string.sound_forest, R.string.sound_forest_desc, R.raw.quiet_forest, R.drawable.sound_forest, false, true),
            new Sound("pink", R.string.sound_pink, R.string.sound_pink_desc, R.raw.pink_noise, R.drawable.sound_pink, true, false),
            new Sound("white", R.string.sound_white, R.string.sound_white_desc, R.raw.white_noise, R.drawable.sound_white, true, false),
            new Sound("ocean", R.string.sound_ocean, R.string.sound_ocean_desc, R.raw.deep_ocean, R.drawable.sound_ocean, true, true),
            new Sound("fireplace", R.string.sound_fireplace, R.string.sound_fireplace_desc, R.raw.fireplace, R.drawable.sound_fireplace, true, true),
            new Sound("stream", R.string.sound_stream, R.string.sound_stream_desc, R.raw.mountain_stream, R.drawable.sound_stream, true, true),
            new Sound("thunder", R.string.sound_thunder, R.string.sound_thunder_desc, R.raw.distant_thunder, R.drawable.sound_thunder, true, true),
            new Sound("crickets", R.string.sound_crickets, R.string.sound_crickets_desc, R.raw.night_crickets, R.drawable.sound_crickets, true, true),
            new Sound("train", R.string.sound_train, R.string.sound_train_desc, R.raw.train_cabin, R.drawable.sound_train, true, true)
    );

    static final class Sound {
        final boolean actualRecording;
        final int descriptionRes;

        /* JADX INFO: renamed from: id */
        final String id;
        final int imageRes;
        final int nameRes;
        final boolean pro;
        final int rawRes;

        Sound(String str, int i, int i2, int i3, int imageRes, boolean z, boolean z2) {
            this.id = str;
            this.nameRes = i;
            this.descriptionRes = i2;
            this.rawRes = i3;
            this.imageRes = imageRes;
            this.pro = z;
            this.actualRecording = z2;
        }
    }

    private SoundCatalog() {
    }

    static Sound byId(String str) {
        for (Sound sound : ALL) {
            if (sound.id.equals(str)) {
                return sound;
            }
        }
        return ALL.get(0);
    }

    static Sound byRaw(int i) {
        for (Sound sound : ALL) {
            if (sound.rawRes == i) {
                return sound;
            }
        }
        return ALL.get(0);
    }
}
