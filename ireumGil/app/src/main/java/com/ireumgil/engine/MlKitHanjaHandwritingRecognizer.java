package com.ireumgil.engine;

import com.google.mlkit.common.MlKitException;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer;
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions;
import com.google.mlkit.vision.digitalink.recognition.Ink;
import com.ireumgil.ui.HandwritingCanvasView;

import java.util.ArrayList;
import java.util.List;

public class MlKitHanjaHandwritingRecognizer implements AutoCloseable {

    public interface Callback {
        void onModelDownloadStarted();
        void onRecognized(List<String> candidates);
        void onFailure(Exception error);
    }

    private final DigitalInkRecognitionModel model;
    private final DigitalInkRecognizer recognizer;
    private final RemoteModelManager modelManager;

    public MlKitHanjaHandwritingRecognizer() throws MlKitException {
        DigitalInkRecognitionModelIdentifier identifier =
                DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hani-TW");
        if (identifier == null) {
            throw new MlKitException("한자 손글씨 모델을 찾을 수 없습니다.", MlKitException.UNAVAILABLE);
        }
        model = DigitalInkRecognitionModel.builder(identifier).build();
        recognizer = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model).build());
        modelManager = RemoteModelManager.getInstance();
    }

    public void recognize(List<List<HandwritingCanvasView.InkPoint>> strokes, Callback callback) {
        Ink ink = buildInk(strokes);
        modelManager.isModelDownloaded(model)
                .addOnSuccessListener(downloaded -> {
                    if (downloaded) {
                        runRecognition(ink, callback);
                        return;
                    }
                    callback.onModelDownloadStarted();
                    modelManager.download(model, new DownloadConditions.Builder().build())
                            .addOnSuccessListener(ignored -> runRecognition(ink, callback))
                            .addOnFailureListener(callback::onFailure);
                })
                .addOnFailureListener(callback::onFailure);
    }

    private void runRecognition(Ink ink, Callback callback) {
        recognizer.recognize(ink)
                .addOnSuccessListener(result -> {
                    List<String> values = new ArrayList<>();
                    result.getCandidates().forEach(candidate -> values.add(candidate.getText()));
                    callback.onRecognized(values);
                })
                .addOnFailureListener(callback::onFailure);
    }

    private Ink buildInk(List<List<HandwritingCanvasView.InkPoint>> strokes) {
        Ink.Builder ink = Ink.builder();
        for (List<HandwritingCanvasView.InkPoint> points : strokes) {
            if (points.isEmpty()) {
                continue;
            }
            Ink.Stroke.Builder stroke = Ink.Stroke.builder();
            for (HandwritingCanvasView.InkPoint point : points) {
                stroke.addPoint(Ink.Point.create(point.x, point.y, point.timestamp));
            }
            ink.addStroke(stroke.build());
        }
        return ink.build();
    }

    @Override
    public void close() {
        recognizer.close();
    }
}
