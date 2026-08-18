package com.emergencyblackbox

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    suspend fun startRecording(
        settings: AppSettings,
        outputFile: File,
        onStarted: () -> Unit,
        onFinalized: (VideoRecordEvent.Finalize) -> Unit
    ) {
        val provider = getCameraProvider()

        val targetQuality = when (settings.videoQuality) {
            VideoQualityPreset.P480 -> Quality.SD
            VideoQualityPreset.P720 -> Quality.HD
            VideoQualityPreset.P1080 -> Quality.FHD
            VideoQualityPreset.P4K -> Quality.UHD
        }

        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(targetQuality, Quality.FHD, Quality.HD, Quality.SD),
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
        )

        val preferredSelector = if (settings.cameraLens == CameraLens.FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val selectedCamera = when {
            provider.hasCamera(preferredSelector) -> preferredSelector
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> throw IllegalStateException("사용 가능한 카메라를 찾을 수 없습니다.")
        }

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()

        val localVideoCapture = VideoCapture.withOutput(recorder)

        provider.unbindAll()
        provider.bindToLifecycle(
            lifecycleOwner,
            selectedCamera,
            localVideoCapture
        )
        videoCapture = localVideoCapture

        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        val pending = localVideoCapture.output.prepareRecording(context, outputOptions)

        val preparedRecording = if (settings.audioMode == AudioModePreset.VIDEO_AUDIO && hasAudioPermission()) {
            pending.withAudioEnabled()
        } else {
            pending
        }

        activeRecording = preparedRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> onStarted()
                is VideoRecordEvent.Finalize -> onFinalized(event)
            }
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    fun isRecording(): Boolean = activeRecording != null

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun getCameraProvider(): ProcessCameraProvider {
        return suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { continuation.resume(it) }
                        .onFailure { continuation.resumeWithException(it) }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    }
}
