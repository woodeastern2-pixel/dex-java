package com.lumi.app.image;

import android.content.Context;
import java.io.IOException;

/**
 * Stable Diffusion 이미지 생성 클라이언트 (샘플, 실제 엔드포인트/키 필요)
 */
public class StableDiffusionClient {
    private final Context appContext;
    public StableDiffusionClient(Context context) {
        this.appContext = context.getApplicationContext();
    }
    public String generateAndSave(String prompt) throws IOException {
        // TODO: 실제 Stable Diffusion API 연동 구현 (엔드포인트, 인증 등 필요)
        throw new IOException("Stable Diffusion 연동 미구현");
    }
}
