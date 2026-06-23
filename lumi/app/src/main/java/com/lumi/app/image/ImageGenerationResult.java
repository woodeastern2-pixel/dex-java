package com.lumi.app.image;

public class ImageGenerationResult {
    public final boolean success;
    public final String imagePath;
    public final String userFacingMessage;
    public final String errorMessage;
    public final String prompt;

    private ImageGenerationResult(boolean success,
                                  String imagePath,
                                  String userFacingMessage,
                                  String errorMessage,
                                  String prompt) {
        this.success = success;
        this.imagePath = imagePath;
        this.userFacingMessage = userFacingMessage;
        this.errorMessage = errorMessage;
        this.prompt = prompt;
    }

    public static ImageGenerationResult ok(String imagePath, String userFacingMessage, String prompt) {
        return new ImageGenerationResult(true, imagePath, userFacingMessage, null, prompt);
    }

    public static ImageGenerationResult fail(String userFacingMessage, String errorMessage, String prompt) {
        return new ImageGenerationResult(false, null, userFacingMessage, errorMessage, prompt);
    }
}