package com.emergencyblackbox

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.location.Geocoder
import android.net.Uri
import android.os.BatteryManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Transformer
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.common.collect.ImmutableList
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WatermarkProcessor(
    private val context: Context,
    private val locationClient: FusedLocationProviderClient
) {
    suspend fun buildWatermarkLines(settings: AppSettings): List<String> {
        val lines = mutableListOf<String>()

        if (settings.showDateTime) {
            lines += SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        }

        if (settings.showGps) {
            val location = getCurrentLocationSafely()
            if (location != null) {
                lines += "${location.first}, ${location.second}"

                if (settings.showAddress) {
                    val address = getAddress(location.first, location.second)
                    if (address.isNotBlank()) {
                        lines += address
                    }
                }
            }
        }

        if (settings.showBattery) {
            val battery = getBatteryPercent()
            if (battery >= 0) {
                lines += "배터리 ${battery}%"
            }
        }

        return lines
    }

    suspend fun writeSidecarFile(videoFile: File, lines: List<String>) {
        if (lines.isEmpty()) return

        withContext(Dispatchers.IO) {
            val sidecar = File(videoFile.parentFile, videoFile.nameWithoutExtension + ".watermark.txt")
            sidecar.writeText(lines.joinToString(separator = "\n"))
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    suspend fun burnWatermarkIntoVideo(
        inputFile: File,
        exportFile: File,
        finalFile: File,
        lines: List<String>,
        showDateTime: Boolean,
        recordingStartedAtEpochMs: Long
    ) {
        withContext(Dispatchers.IO) {
            exportFile.parentFile?.mkdirs()
            finalFile.parentFile?.mkdirs()
            if (exportFile.exists()) {
                exportFile.delete()
            }
        }

        if (lines.isEmpty()) {
            withContext(Dispatchers.IO) {
                inputFile.copyTo(finalFile, overwrite = true)
            }
            return
        }

        val overlaySettings = OverlaySettings.Builder()
            .setBackgroundFrameAnchor(1f, 1f)
            .setOverlayFrameAnchor(1f, 1f)
            .setScale(0.63f, 0.63f)
            .build()

        val overlay = createDynamicTextOverlay(
            sourceLines = lines,
            overlaySettings = overlaySettings,
            showDateTime = showDateTime,
            recordingStartedAtEpochMs = recordingStartedAtEpochMs
        )

        val overlayEffect = OverlayEffect(ImmutableList.of(overlay))
        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(inputFile)))
            .setEffects(Effects(emptyList<AudioProcessor>(), listOf<Effect>(overlayEffect)))
            .build()

        suspendCancellableCoroutine<Unit> { continuation ->
            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: androidx.media3.transformer.ExportResult
                    ) {
                        continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: androidx.media3.transformer.ExportResult,
                        exportException: ExportException
                    ) {
                        continuation.resumeWithException(exportException)
                    }
                })
                .build()

            continuation.invokeOnCancellation {
                transformer.cancel()
            }

            transformer.start(editedMediaItem, exportFile.absolutePath)
        }

        withContext(Dispatchers.IO) {
            exportFile.copyTo(finalFile, overwrite = true)
        }
    }

    private fun createDynamicTextOverlay(
        sourceLines: List<String>,
        overlaySettings: OverlaySettings,
        showDateTime: Boolean,
        recordingStartedAtEpochMs: Long
    ): TextOverlay {
        val staticLines = if (showDateTime && sourceLines.isNotEmpty()) {
            sourceLines.drop(1)
        } else {
            sourceLines
        }
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        return object : TextOverlay() {
            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
                return overlaySettings
            }

            override fun getText(presentationTimeUs: Long): SpannableString {
                val elapsedSeconds = (presentationTimeUs / 1_000_000L).coerceAtLeast(0L)
                val dateLine = if (showDateTime) {
                    dateFormatter.format(Date(recordingStartedAtEpochMs + elapsedSeconds * 1000L))
                } else {
                    null
                }

                val text = buildString {
                    if (dateLine != null) {
                        append(dateLine)
                    }
                    staticLines.forEach { line ->
                        if (isNotEmpty()) append('\n')
                        append(line)
                    }
                }

                return SpannableString(text).apply {
                    setSpan(
                        ForegroundColorSpan(Color.WHITE),
                        0,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    val firstLineEnd = indexOf('\n').let { if (it == -1) length else it }
                    if (firstLineEnd > 0) {
                        setSpan(
                            AbsoluteSizeSpan(36, true),
                            0,
                            firstLineEnd,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    if (firstLineEnd < length) {
                        setSpan(
                            AbsoluteSizeSpan(30, true),
                            firstLineEnd,
                            length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocationSafely(): Pair<Double, Double>? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return null

        return suspendCancellableCoroutine { continuation ->
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(30_000)
                .build()

            locationClient.getCurrentLocation(request, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(location.latitude to location.longitude)
                    } else {
                        locationClient.lastLocation
                            .addOnSuccessListener { last ->
                                if (last != null) {
                                    continuation.resume(last.latitude to last.longitude)
                                } else {
                                    continuation.resume(null)
                                }
                            }
                            .addOnFailureListener {
                                continuation.resume(null)
                            }
                    }
                }
                .addOnFailureListener {
                    locationClient.lastLocation
                        .addOnSuccessListener { last ->
                            if (last != null) {
                                continuation.resume(last.latitude to last.longitude)
                            } else {
                                continuation.resume(null)
                            }
                        }
                        .addOnFailureListener {
                            continuation.resume(null)
                        }
                }
        }
    }

    private suspend fun getAddress(lat: Double, lon: Double): String {
        return withContext(Dispatchers.IO) {
            runCatching {
                val geocoder = Geocoder(context, Locale.KOREA)
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                addresses?.firstOrNull()?.getAddressLine(0).orEmpty()
            }.getOrDefault("")
        }
    }

    private fun getBatteryPercent(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            -1
        }
    }
}
