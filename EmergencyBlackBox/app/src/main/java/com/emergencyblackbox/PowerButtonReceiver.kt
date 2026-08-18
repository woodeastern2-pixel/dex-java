package com.emergencyblackbox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerButtonReceiver : BroadcastReceiver() {
    companion object {
        private const val WINDOW_MS = 1_800L
        private val screenOffClicks = ArrayDeque<Long>()
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_SCREEN_OFF) return

        val now = System.currentTimeMillis()
        screenOffClicks.addLast(now)

        while (screenOffClicks.isNotEmpty() && now - screenOffClicks.first() > WINDOW_MS) {
            screenOffClicks.removeFirst()
        }

        if (screenOffClicks.size >= 3) {
            // Android 정책상 전원버튼 직접 감지는 제한되므로 화면 OFF 연속 이벤트 기반으로 최대한 동작시킨다.
            RecordingService.start(context, RecordingService.ACTION_TOGGLE, "power_button_triple")
            screenOffClicks.clear()
        }
    }
}
