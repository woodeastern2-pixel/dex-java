package com.emergencyblackbox

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.camera.video.VideoRecordEvent
import com.google.android.gms.location.LocationServices
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecordingService : LifecycleService() {
    private data class RecordingPaths(
        val rawFile: File,
        val exportFile: File,
        val finalFile: File
    )

    companion object {
        const val ACTION_START = "com.emergencyblackbox.action.START"
        const val ACTION_STOP = "com.emergencyblackbox.action.STOP"
        const val ACTION_TOGGLE = "com.emergencyblackbox.action.TOGGLE"
        const val ACTION_RECOVER = "com.emergencyblackbox.action.RECOVER"

        const val EXTRA_TRIGGER = "extra_trigger"
        private const val CHANNEL_ID = "emergency_blackbox_channel"
        private const val NOTIFICATION_ID = 911
        private const val TAG = "EmergencyBlackBox"

        @Volatile
        var isRecording: Boolean = false

        fun start(context: Context, action: String, trigger: String = "manual") {
            val intent = Intent(context, RecordingService::class.java).apply {
                this.action = action
                putExtra(EXTRA_TRIGGER, trigger)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var cameraManager: CameraManager
    private lateinit var watermarker: WatermarkProcessor
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var recordingDao: RecordingEventDao

    private var currentEventId: Long = 0L
    private var currentRecordingStartedAtEpochMs: Long = 0L
    private var currentRecordingPaths: RecordingPaths? = null

    override fun onCreate() {
        super.onCreate()
        cameraManager = CameraManager(this, this)
        watermarker = WatermarkProcessor(this, LocationServices.getFusedLocationProviderClient(this))
        recordingDao = AppDatabase.get(this).recordingEventDao()

        createNotificationChannel()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EmergencyBlackBox::RecordingWakelock")
    }

    override fun onDestroy() {
        if (wakeLock.isHeld) wakeLock.release()
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val action = intent?.action ?: ACTION_TOGGLE
        scope.launch {
            when (action) {
                ACTION_START -> startRecordingInternal(forceToast = true)
                ACTION_STOP -> stopRecordingInternal(forceToast = true)
                ACTION_TOGGLE -> {
                    if (isRecording || cameraManager.isRecording()) {
                        stopRecordingInternal(forceToast = true)
                    } else {
                        startRecordingInternal(forceToast = true)
                    }
                }
                ACTION_RECOVER -> {
                    val settings = DataStoreManager.settingsFlow(this@RecordingService).first()
                    recoverUnfinishedRawFiles(settings)
                    if (settings.recordingInProgress && !isRecording) {
                        startRecordingInternal(forceToast = false)
                    }
                }
            }
        }
        return START_STICKY
    }

    private suspend fun startRecordingInternal(forceToast: Boolean) {
        if (isRecording || cameraManager.isRecording()) return
        if (!hasCameraPermission()) {
            Log.e(TAG, "startRecordingInternal failed: camera permission missing")
            Toast.makeText(this, "카메라 권한이 없어 녹화할 수 없습니다.", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        runCatching {
            val settings = DataStoreManager.settingsFlow(this).first()
            val paths = createRecordingPaths()
            currentRecordingPaths = paths

            if (!wakeLock.isHeld) {
                wakeLock.acquire(8 * 60 * 60 * 1000L)
            }

            startForegroundSafely(settings)

            currentEventId = recordingDao.insert(
                RecordingEvent(
                    filePath = paths.finalFile.absolutePath,
                    startedAtEpochMs = System.currentTimeMillis(),
                    status = "recording"
                )
            )
            currentRecordingStartedAtEpochMs = System.currentTimeMillis()

            DataStoreManager.markRecordingState(this, inProgress = true, lastPath = paths.finalFile.absolutePath)

            cameraManager.startRecording(
                settings = settings,
                outputFile = paths.rawFile,
                onStarted = {
                    isRecording = true
                    if (forceToast) {
                        Toast.makeText(this, "녹화가 시작되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                },
                onFinalized = { finalize ->
                    scope.launch {
                        isRecording = false

                        recordingDao.markCompleted(
                            id = currentEventId,
                            endedAt = System.currentTimeMillis(),
                            status = if (finalize.hasError()) "error_${finalize.error}" else "completed"
                        )

                        if (finalize.hasError()) {
                            val msg = "녹화 실패: ${finalize.error}"
                            Log.e(TAG, "Video finalize error=${finalize.error} cause=${finalize.cause?.message}")
                            Toast.makeText(this@RecordingService, msg, Toast.LENGTH_SHORT).show()
                        }

                        DataStoreManager.markRecordingState(this@RecordingService, inProgress = false)

                        val lines = watermarker.buildWatermarkLines(settings)
                        currentRecordingPaths?.let { currentPaths ->
                            val processed = runCatching {
                                watermarker.burnWatermarkIntoVideo(
                                    inputFile = currentPaths.rawFile,
                                    exportFile = currentPaths.exportFile,
                                    finalFile = currentPaths.finalFile,
                                    lines = lines,
                                    showDateTime = settings.showDateTime,
                                    recordingStartedAtEpochMs = currentRecordingStartedAtEpochMs
                                )
                                watermarker.writeSidecarFile(currentPaths.finalFile, lines)
                                true
                            }.getOrElse {
                                false
                            }

                            if (!processed) {
                                copyRawToFinalFallback(currentPaths)
                            }

                            // If transformer output is missing/empty, keep at least raw recording as final.
                            if (!currentPaths.finalFile.exists() || currentPaths.finalFile.length() <= 0L) {
                                copyRawToFinalFallback(currentPaths)
                            }

                            scanMedia(currentPaths.finalFile)
                        }

                        if (wakeLock.isHeld) wakeLock.release()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            )
        }.onFailure { throwable ->
            Log.e(TAG, "startRecordingInternal exception", throwable)
            DataStoreManager.markRecordingState(this, inProgress = false)
            if (wakeLock.isHeld) wakeLock.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            val reason = throwable.message?.take(60)?.ifBlank { null }
            val errorType = throwable::class.java.simpleName
            val message = if (reason != null) {
                "녹화를 시작할 수 없습니다. [$errorType] $reason"
            } else {
                "녹화를 시작할 수 없습니다. [$errorType]"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    private suspend fun stopRecordingInternal(forceToast: Boolean) {
        if (!isRecording && !cameraManager.isRecording()) return

        cameraManager.stopRecording()
        if (forceToast) {
            Toast.makeText(this, "녹화가 종료되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (isRecording) {
            start(this, ACTION_RECOVER, "task_removed")
        }
        super.onTaskRemoved(rootIntent)
    }

    private suspend fun recoverUnfinishedRawFiles(settings: AppSettings) {
        val root = File(Environment.getExternalStorageDirectory(), "Android/media/com.emergencyblackbox")
        val protectedDir = File(root, "Protected")
        val exportDir = File(root, "Export")
        val videoDir = File(root, "Video")

        if (!protectedDir.exists()) return

        val rawFiles = protectedDir.listFiles { file ->
            file.isFile && file.extension.equals("mp4", ignoreCase = true)
        }?.sortedByDescending { it.lastModified() }.orEmpty()

        rawFiles.take(3).forEach { raw ->
            val final = File(videoDir, raw.name)
            if (final.exists() && final.length() > 0L) return@forEach

            val export = File(exportDir, raw.name)
            val lines = watermarker.buildWatermarkLines(settings)
            val restored = runCatching {
                watermarker.burnWatermarkIntoVideo(
                    inputFile = raw,
                    exportFile = export,
                    finalFile = final,
                    lines = lines,
                    showDateTime = settings.showDateTime,
                    recordingStartedAtEpochMs = raw.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                )
                watermarker.writeSidecarFile(final, lines)
                true
            }.getOrElse {
                false
            }

            if (!restored) {
                copyRawToFinalFallback(RecordingPaths(rawFile = raw, exportFile = export, finalFile = final))
            }
        }
    }

    private suspend fun copyRawToFinalFallback(paths: RecordingPaths) {
        withContext(Dispatchers.IO) {
            runCatching {
                paths.finalFile.parentFile?.mkdirs()
                paths.rawFile.copyTo(paths.finalFile, overwrite = true)
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun createRecordingPaths(): RecordingPaths {
        val root = File(
            Environment.getExternalStorageDirectory(),
            "Android/media/com.emergencyblackbox"
        )
        val videoDir = File(root, "Video")
        val protectedDir = File(root, "Protected")
        val exportDir = File(root, "Export")

        if (!videoDir.exists()) videoDir.mkdirs()
        if (!protectedDir.exists()) protectedDir.mkdirs()
        if (!exportDir.exists()) exportDir.mkdirs()

        val fileName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".mp4"
        return RecordingPaths(
            rawFile = File(protectedDir, fileName),
            exportFile = File(exportDir, fileName),
            finalFile = File(videoDir, fileName)
        )
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergency BlackBox Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundSafely(settings: AppSettings) {
        val notification = buildNotification(settings.recordingIndicator, true)
        val types = computeForegroundServiceTypes(settings)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
    }

    private fun computeForegroundServiceTypes(settings: AppSettings): Int {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (settings.audioMode == AudioModePreset.VIDEO_AUDIO && hasAudioPermission()) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if ((settings.showGps || settings.showAddress) && hasLocationPermission()) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        return types
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun scanMedia(file: File) {
        if (!file.exists()) return
        MediaScannerConnection.scanFile(
            this,
            arrayOf(file.absolutePath),
            arrayOf("video/mp4")
        ) { path, uri ->
            Log.d(TAG, "Media scan completed path=$path uri=$uri")
        }
    }

    private fun buildNotification(
        indicator: RecordingIndicatorPreset,
        isRecordingNow: Boolean
    ): Notification {
        val title = if (indicator == RecordingIndicatorPreset.STATUSBAR_SOS && isRecordingNow) {
            "SOS start"
        } else {
            "Emergency BlackBox"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(title)
            .setContentText(null)
            .setStyle(NotificationCompat.BigTextStyle().bigText(""))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun VideoRecordEvent.Finalize.hasError(): Boolean {
        return error != VideoRecordEvent.Finalize.ERROR_NONE
    }
}
