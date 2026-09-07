package com.lumi.app.image;

import android.content.Context;
import java.io.IOException;

/**
 * DALL-E 이미지 생성 클라이언트 (샘플, 실제 엔드포인트/키 필요)
 */
public class DalleClient {
    private final Context appContext;
    public DalleClient(Context context) {
        this.appContext = context.getApplicationContext();
    }
    public String generateAndSave(String prompt) throws IOException {
        // TODO: 실제 DALL-E API 연동 구현 (OpenAI API 키 필요)
        throw new IOException("DALL-E 연동 미구현");
    }
}
