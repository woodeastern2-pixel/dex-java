package com.lumora.app.collect;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

import com.lumora.app.data.ContextLogEntity;
import com.lumora.app.data.LumoraRepository;
import com.lumora.app.data.LumoraSettings;

/**
 * 위치는 주기적 추적이 아니라 "현재 마지막 알려진 위치"를 가끔 한 번 읽어
 * "회사/집 같은 자주 가는 좌표인지" 판단할 때 사용. 외부 전송 없음.
 */
public class LocationContextCollector {

    public static boolean hasPermission(Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public static void snapshot(Context ctx) {
        LumoraSettings s = new LumoraSettings(ctx);
        if (!s.isCollectLocation() || !s.isLocationConsented() || !hasPermission(ctx)) return;
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return;
            Location loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) return;
            String value = String.format(java.util.Locale.US, "%.4f,%.4f",
                    loc.getLatitude(), loc.getLongitude());
            new LumoraRepository(ctx).log(ContextLogEntity.T_LOCATION, value);
        } catch (Throwable ignored) {
        }
    }
}
