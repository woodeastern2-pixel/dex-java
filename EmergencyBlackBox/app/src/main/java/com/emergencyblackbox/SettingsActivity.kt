package com.emergencyblackbox

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    private lateinit var acCamera: MaterialAutoCompleteTextView
    private lateinit var acQuality: MaterialAutoCompleteTextView
    private lateinit var acFrame: MaterialAutoCompleteTextView
    private lateinit var acFormat: MaterialAutoCompleteTextView
    private lateinit var acAudio: MaterialAutoCompleteTextView
    private lateinit var acIndicator: MaterialAutoCompleteTextView
    private lateinit var cbDateTime: CheckBox
    private lateinit var cbGps: CheckBox
    private lateinit var cbAddress: CheckBox
    private lateinit var cbBattery: CheckBox
    private lateinit var tvWatermarkPreview: TextView
    private lateinit var btnSave: MaterialButton

    private val cameraItems = listOf("전면 카메라", "후면 카메라")
    private val qualityItems = listOf("480P", "720P", "1080P", "4K")
    private val frameItems = listOf("24fps", "30fps", "60fps")
    private val formatItems = listOf("MP4(H264)", "MP4(H265)")
    private val audioItems = listOf("영상만", "영상+음성")
    private val indicatorItems = listOf("표시 안함", "상태바 SOS 표시")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        bindViews()
        setupSpinners()

        lifecycleScope.launch {
            bindCurrentSettings()
        }

        listOf(cbDateTime, cbGps, cbAddress, cbBattery).forEach { checkBox ->
            checkBox.setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        btnSave.setOnClickListener {
            lifecycleScope.launch {
                saveSettings()
                Toast.makeText(this@SettingsActivity, "설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindViews() {
        acCamera = findViewById(R.id.acCamera)
        acQuality = findViewById(R.id.acQuality)
        acFrame = findViewById(R.id.acFrame)
        acFormat = findViewById(R.id.acFormat)
        acAudio = findViewById(R.id.acAudio)
        acIndicator = findViewById(R.id.acIndicator)
        cbDateTime = findViewById(R.id.cbDateTime)
        cbGps = findViewById(R.id.cbGps)
        cbAddress = findViewById(R.id.cbAddress)
        cbBattery = findViewById(R.id.cbBattery)
        tvWatermarkPreview = findViewById(R.id.tvWatermarkPreview)
        btnSave = findViewById(R.id.btnSave)
    }

    private fun setupSpinners() {
        acCamera.setSimpleItems(cameraItems)
        acQuality.setSimpleItems(qualityItems)
        acFrame.setSimpleItems(frameItems)
        acFormat.setSimpleItems(formatItems)
        acAudio.setSimpleItems(audioItems)
        acIndicator.setSimpleItems(indicatorItems)
    }

    private suspend fun bindCurrentSettings() {
        val settings = DataStoreManager.settingsFlow(this).first()

        acCamera.setText(if (settings.cameraLens == CameraLens.FRONT) cameraItems[0] else cameraItems[1], false)
        acQuality.setText(
            when (settings.videoQuality) {
                VideoQualityPreset.P480 -> qualityItems[0]
                VideoQualityPreset.P720 -> qualityItems[1]
                VideoQualityPreset.P1080 -> qualityItems[2]
                VideoQualityPreset.P4K -> qualityItems[3]
            },
            false
        )
        acFrame.setText(
            when (settings.frameRate) {
                FrameRatePreset.FPS24 -> frameItems[0]
                FrameRatePreset.FPS30 -> frameItems[1]
                FrameRatePreset.FPS60 -> frameItems[2]
            },
            false
        )
        acFormat.setText(if (settings.videoFormat == VideoFormatPreset.MP4_H264) formatItems[0] else formatItems[1], false)
        acAudio.setText(if (settings.audioMode == AudioModePreset.VIDEO_ONLY) audioItems[0] else audioItems[1], false)
        acIndicator.setText(
            if (settings.recordingIndicator == RecordingIndicatorPreset.NONE) indicatorItems[0] else indicatorItems[1],
            false
        )

        cbDateTime.isChecked = settings.showDateTime
        cbGps.isChecked = settings.showGps
        cbAddress.isChecked = settings.showAddress
        cbBattery.isChecked = settings.showBattery
        updatePreview()
    }

    private suspend fun saveSettings() {
        val current = DataStoreManager.settingsFlow(this).first()

        val updated = current.copy(
            cameraLens = if (acCamera.text.toString() == cameraItems[0]) CameraLens.FRONT else CameraLens.BACK,
            videoQuality = when (acQuality.text.toString()) {
                qualityItems[0] -> VideoQualityPreset.P480
                qualityItems[1] -> VideoQualityPreset.P720
                qualityItems[2] -> VideoQualityPreset.P1080
                else -> VideoQualityPreset.P4K
            },
            frameRate = when (acFrame.text.toString()) {
                frameItems[0] -> FrameRatePreset.FPS24
                frameItems[1] -> FrameRatePreset.FPS30
                else -> FrameRatePreset.FPS60
            },
            videoFormat = if (acFormat.text.toString() == formatItems[0]) {
                VideoFormatPreset.MP4_H264
            } else {
                VideoFormatPreset.MP4_H265
            },
            audioMode = if (acAudio.text.toString() == audioItems[0]) {
                AudioModePreset.VIDEO_ONLY
            } else {
                AudioModePreset.VIDEO_AUDIO
            },
            recordingIndicator = if (acIndicator.text.toString() == indicatorItems[0]) {
                RecordingIndicatorPreset.NONE
            } else {
                RecordingIndicatorPreset.STATUSBAR_SOS
            },
            showDateTime = cbDateTime.isChecked,
            showGps = cbGps.isChecked,
            showAddress = cbAddress.isChecked,
            showBattery = cbBattery.isChecked
        )

        DataStoreManager.updateSettings(this, updated)
    }

    private fun MaterialAutoCompleteTextView.setSimpleItems(items: List<String>) {
        setAdapter(ArrayAdapter(this@SettingsActivity, android.R.layout.simple_list_item_1, items))
    }

    private fun updatePreview() {
        val lines = buildList {
            if (cbDateTime.isChecked) add("2026-06-04 15:32:11")
            if (cbGps.isChecked) add("37.4840, 127.0347")
            if (cbAddress.isChecked) add("서울특별시 서초구 양재동")
            if (cbBattery.isChecked) add("배터리 84%")
        }

        tvWatermarkPreview.text = if (lines.isEmpty()) {
            "워터마크가 표시되지 않습니다."
        } else {
            lines.joinToString("\n")
        }
    }
}
