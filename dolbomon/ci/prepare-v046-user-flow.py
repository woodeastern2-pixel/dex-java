#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])

build = root / 'app/build.gradle'
s = build.read_text()
marker = "    implementation 'com.google.android.ump:user-messaging-platform:4.0.0'"
deps = "\n    androidTestImplementation 'androidx.test.ext:junit:1.2.1'\n    androidTestImplementation 'androidx.test:core:1.6.1'\n    androidTestImplementation 'androidx.test:runner:1.6.2'"
if 'androidx.test.ext:junit' not in s:
    s = s.replace(marker, marker + deps)
build.write_text(s)

record = root / 'app/src/main/java/com/easternwood/dolbomon/RecordActivity.java'
rs = record.read_text()
hook = '''        buildUi();
        if (BuildConfig.DEBUG && getIntent().getBooleanExtra("visual_photos", false)) {
            try {
                File d = new File(getFilesDir(), "photos"); d.mkdirs();
                for (int i = 1; i <= 2; i++) {
                    File f = new File(d, "visual_" + i + ".png");
                    Bitmap b = Bitmap.createBitmap(320, 220, Bitmap.Config.ARGB_8888);
                    b.eraseColor(i == 1 ? Color.rgb(151, 220, 215) : Color.rgb(247, 199, 112));
                    try (FileOutputStream o = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
                    b.recycle();
                    photoPaths.add(f.getAbsolutePath());
                }
            } catch (Exception ignored) {}
        }
'''
if 'getBooleanExtra("visual_photos", false)' not in rs:
    rs = rs.replace('        buildUi();\n', hook, 1)
record.write_text(rs)

test_dir = root / 'app/src/androidTest/java/com/easternwood/dolbomon'
test_dir.mkdir(parents=True, exist_ok=True)
(test_dir / 'ShareIntentDeviceTest.java').write_text(r'''package com.easternwood.dolbomon;
import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class ShareIntentDeviceTest {
  private ArrayList<String> photos(Context c) throws Exception {
    File d=new File(c.getFilesDir(),"photos"); d.mkdirs();
    ArrayList<String> out=new ArrayList<>();
    for(int i=0;i<2;i++){
      File f=new File(d,"share-test-"+i+".jpg");
      try(FileOutputStream o=new FileOutputStream(f)){o.write(new byte[]{1,2,3,4});}
      out.add(f.getAbsolutePath());
    }
    return out;
  }

  @Test public void smsEmailKakaoKeepBodyAndPhotos() throws Exception {
    Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
    ArrayList<String> photos=photos(c);
    String body="홍길동 어르신의 전달문 테스트입니다.";

    DatabaseHelper.Guardian sms=new DatabaseHelper.Guardian();
    sms.method="SMS"; sms.phone="01012345678";
    Intent si=ShareHelper.buildIntent(c,sms,"제목",body,photos);
    assertEquals(Intent.ACTION_SEND_MULTIPLE,si.getAction());
    assertEquals(body,si.getStringExtra(Intent.EXTRA_TEXT));
    assertEquals(body,si.getStringExtra("sms_body"));
    assertNotNull(si.getClipData());
    assertEquals(2,si.getClipData().getItemCount());
    assertEquals(2,si.getParcelableArrayListExtra(Intent.EXTRA_STREAM).size());

    DatabaseHelper.Guardian email=new DatabaseHelper.Guardian();
    email.method="EMAIL"; email.email="test@example.com";
    Intent ei=ShareHelper.buildIntent(c,email,"제목",body,photos);
    assertEquals(body,ei.getStringExtra(Intent.EXTRA_TEXT));
    assertNotNull(ei.getClipData());
    assertEquals(2,ei.getClipData().getItemCount());

    DatabaseHelper.Guardian kakao=new DatabaseHelper.Guardian();
    kakao.method="KAKAO";
    Intent ki=ShareHelper.buildIntent(c,kakao,"제목",body,photos);
    assertEquals(body,ki.getStringExtra(Intent.EXTRA_TEXT));
    assertNotNull(ki.getClipData());
    assertEquals(2,ki.getClipData().getItemCount());
  }
}
''')
