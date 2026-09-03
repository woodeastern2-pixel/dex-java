#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def replace_once(path, old, new):
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one marker, found {count}: {old!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


build = root / "app/build.gradle"
replace_once(build, "versionCode 28", "versionCode 29")
replace_once(build, 'versionName "0.5.1"', 'versionName "0.5.2"')
replace_once(
    build,
    "def admobAppId = project.findProperty('ADMOB_APP_ID') ?: 'ca-app-pub-3940256099942544~3347511713'\n"
    "def admobBannerId = project.findProperty('ADMOB_BANNER_ID') ?: 'ca-app-pub-3940256099942544/9214589741'",
    "def admobReleaseAppId = project.findProperty('ADMOB_APP_ID') ?: 'ca-app-pub-9360550840761530~7315527975'\n"
    "def admobReleaseBannerId = project.findProperty('ADMOB_BANNER_ID') ?: 'ca-app-pub-9360550840761530/9750119626'\n"
    "def admobTestAppId = 'ca-app-pub-3940256099942544~3347511713'\n"
    "def admobTestBannerId = 'ca-app-pub-3940256099942544/9214589741'",
)
replace_once(build, 'resValue "string", "admob_app_id", admobAppId', 'resValue "string", "admob_app_id", admobReleaseAppId')
replace_once(build, 'buildConfigField "String", "ADMOB_BANNER_ID", "\\\"${admobBannerId}\\\""', 'buildConfigField "String", "ADMOB_BANNER_ID", "\\\"${admobReleaseBannerId}\\\""')
replace_once(
    build,
    "    buildTypes {\n        release {",
    "    buildTypes {\n"
    "        debug {\n"
    "            resValue \"string\", \"admob_app_id\", admobTestAppId\n"
    '            buildConfigField "String", "ADMOB_BANNER_ID", "\\"${admobTestBannerId}\\""\n'
    "        }\n"
    "        release {",
)

ads = root / "app/src/main/java/com/easternwood/dolbomon/AdsManager.java"
ads.write_text("""package com.easternwood.dolbomon;

import android.app.Activity;
import android.view.ViewGroup;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public final class AdsManager {
    private AdsManager() {}

    public static void initialize(Activity activity, Runnable onReady) {
        ConsentInformation info = UserMessagingPlatform.getConsentInformation(activity);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        info.requestConsentInfoUpdate(activity, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity,
                        formError -> startAdsIfAllowed(activity, info, onReady)),
                requestError -> startAdsIfAllowed(activity, info, onReady));
    }

    private static void startAdsIfAllowed(Activity activity, ConsentInformation info, Runnable onReady) {
        if (!info.canRequestAds()) return;
        MobileAds.initialize(activity, status -> activity.runOnUiThread(onReady));
    }

    public static void attachBanner(Activity activity, ViewGroup parent) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        AdView adView = new AdView(activity);
        adView.setAdSize(AdSize.BANNER);
        adView.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
        parent.addView(adView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        adView.loadAd(new AdRequest.Builder().build());
    }
}
""", encoding="utf-8")

readme = root / "README.md"
replace_once(
    readme,
    "# 돌봄온 (DolbomOn)\n",
    "# 돌봄온 (DolbomOn)\n\n"
    "## v0.5.2 출시 준비\n\n"
    "- versionCode 29 / versionName 0.5.2\n"
    "- AdMob 프로덕션 앱 및 배너 광고 단위 적용\n"
    "- Debug 빌드는 Google 테스트 광고 ID만 사용\n"
    "- UMP 동의 상태에서 광고 요청이 허용된 경우에만 배너 로드\n",
)
replace_once(
    readme,
    "AdMob ID는 Gradle project property로 교체할 수 있습니다.",
    "Release 빌드는 돌봄온 AdMob 프로덕션 ID를 사용하며 Gradle project property로 교체할 수 있습니다. Debug 빌드는 항상 Google 공식 테스트 광고 ID를 사용합니다.",
)
replace_once(
    readme,
    "별도 값이 없을 때는 Google 공식 테스트 광고 ID를 사용합니다.",
    "프로덕션 ID는 공개 식별자이며 업로드 키와 비밀번호 같은 서명 비밀정보는 저장소에 포함하지 않습니다.",
)

print("DolbomOn v0.5.2 release configuration applied")
