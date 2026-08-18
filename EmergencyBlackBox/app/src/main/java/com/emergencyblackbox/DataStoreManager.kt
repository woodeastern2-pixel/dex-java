package com.emergencyblackbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class CameraLens { FRONT, BACK }
enum class VideoQualityPreset { P480, P720, P1080, P4K }
enum class FrameRatePreset(val fps: Int) { FPS24(24), FPS30(30), FPS60(60) }
enum class VideoFormatPreset { MP4_H264, MP4_H265 }
enum class AudioModePreset { VIDEO_ONLY, VIDEO_AUDIO }
enum class RecordingIndicatorPreset { NONE, STATUSBAR_SOS }

data class AppSettings(
    val cameraLens: CameraLens = CameraLens.BACK,
    val videoQuality: VideoQualityPreset = VideoQualityPreset.P1080,
    val frameRate: FrameRatePreset = FrameRatePreset.FPS30,
    val videoFormat: VideoFormatPreset = VideoFormatPreset.MP4_H264,
    val audioMode: AudioModePreset = AudioModePreset.VIDEO_AUDIO,
    val showDateTime: Boolean = true,
    val showGps: Boolean = true,
    val showAddress: Boolean = true,
    val showBattery: Boolean = true,
    val recordingIndicator: RecordingIndicatorPreset = RecordingIndicatorPreset.STATUSBAR_SOS,
    val recordingInProgress: Boolean = false,
    val lastRecordingPath: String = ""
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "emergency_blackbox_settings")

object DataStoreManager {
    private val KEY_CAMERA_LENS = stringPreferencesKey("camera_lens")
    private val KEY_VIDEO_QUALITY = stringPreferencesKey("video_quality")
    private val KEY_FRAME_RATE = stringPreferencesKey("frame_rate")
    private val KEY_VIDEO_FORMAT = stringPreferencesKey("video_format")
    private val KEY_AUDIO_MODE = stringPreferencesKey("audio_mode")
    private val KEY_SHOW_DATE_TIME = booleanPreferencesKey("show_date_time")
    private val KEY_SHOW_GPS = booleanPreferencesKey("show_gps")
    private val KEY_SHOW_ADDRESS = booleanPreferencesKey("show_address")
    private val KEY_SHOW_BATTERY = booleanPreferencesKey("show_battery")
    private val KEY_RECORDING_INDICATOR = stringPreferencesKey("recording_indicator")
    private val KEY_RECORDING_IN_PROGRESS = booleanPreferencesKey("recording_in_progress")
    private val KEY_LAST_RECORDING_PATH = stringPreferencesKey("last_recording_path")

    fun settingsFlow(context: Context): Flow<AppSettings> {
        return context.dataStore.data.map { prefs ->
            AppSettings(
                cameraLens = prefs[KEY_CAMERA_LENS]?.let { CameraLens.valueOf(it) } ?: CameraLens.BACK,
                videoQuality = prefs[KEY_VIDEO_QUALITY]?.let { VideoQualityPreset.valueOf(it) } ?: VideoQualityPreset.P1080,
                frameRate = prefs[KEY_FRAME_RATE]?.let { FrameRatePreset.valueOf(it) } ?: FrameRatePreset.FPS30,
                videoFormat = prefs[KEY_VIDEO_FORMAT]?.let { VideoFormatPreset.valueOf(it) } ?: VideoFormatPreset.MP4_H264,
                audioMode = prefs[KEY_AUDIO_MODE]?.let { AudioModePreset.valueOf(it) } ?: AudioModePreset.VIDEO_AUDIO,
                showDateTime = prefs[KEY_SHOW_DATE_TIME] ?: true,
                showGps = prefs[KEY_SHOW_GPS] ?: true,
                showAddress = prefs[KEY_SHOW_ADDRESS] ?: true,
                showBattery = prefs[KEY_SHOW_BATTERY] ?: true,
                recordingIndicator = prefs[KEY_RECORDING_INDICATOR]?.let {
                    RecordingIndicatorPreset.valueOf(it)
                } ?: RecordingIndicatorPreset.STATUSBAR_SOS,
                recordingInProgress = prefs[KEY_RECORDING_IN_PROGRESS] ?: false,
                lastRecordingPath = prefs[KEY_LAST_RECORDING_PATH] ?: ""
            )
        }
    }

    suspend fun updateSettings(context: Context, newSettings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CAMERA_LENS] = newSettings.cameraLens.name
            prefs[KEY_VIDEO_QUALITY] = newSettings.videoQuality.name
            prefs[KEY_FRAME_RATE] = newSettings.frameRate.name
            prefs[KEY_VIDEO_FORMAT] = newSettings.videoFormat.name
            prefs[KEY_AUDIO_MODE] = newSettings.audioMode.name
            prefs[KEY_SHOW_DATE_TIME] = newSettings.showDateTime
            prefs[KEY_SHOW_GPS] = newSettings.showGps
            prefs[KEY_SHOW_ADDRESS] = newSettings.showAddress
            prefs[KEY_SHOW_BATTERY] = newSettings.showBattery
            prefs[KEY_RECORDING_INDICATOR] = newSettings.recordingIndicator.name
            prefs[KEY_RECORDING_IN_PROGRESS] = newSettings.recordingInProgress
            prefs[KEY_LAST_RECORDING_PATH] = newSettings.lastRecordingPath
        }
    }

    suspend fun markRecordingState(context: Context, inProgress: Boolean, lastPath: String = "") {
        context.dataStore.edit { prefs ->
            prefs[KEY_RECORDING_IN_PROGRESS] = inProgress
            if (lastPath.isNotBlank()) {
                prefs[KEY_LAST_RECORDING_PATH] = lastPath
            }
        }
    }
}
