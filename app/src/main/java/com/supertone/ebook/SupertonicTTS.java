package com.supertone.ebook;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;

/**
 * Supertonic TTS 엔진 (Android 버전)
 * 원본: https://github.com/supertone-inc/supertonic/tree/main/java
 */
public class SupertonicTTS {
    private static final String TAG = "SupertonicTTS";
    private OrtEnvironment ortEnv;
    private TextToSpeech textToSpeech;
    private Context context;
    private boolean initialized = false;
    private String onnxDir;
    
    // 모델 파일 경로 (assets 폴더)
    private static final String ASSETS_ONNX_DIR = "onnx";
    private static final String FILES_ONNX_DIR = "onnx";
    
    // 오디오 파라미터
    private static final int DEFAULT_TOTAL_STEP = 7; // Denoising steps (더 높을수록 품질 향상, 속도 저하)
    
    public SupertonicTTS(Context context) throws Exception {
        this.context = context;
        initialize();
    }
    
    private void initialize() throws Exception {
        try {
            ortEnv = OrtEnvironment.getEnvironment();
            
            // Check if models are in assets or files directory
            File filesOnnxDir = new File(context.getFilesDir(), FILES_ONNX_DIR);
            File filesVoiceStylesDir = new File(context.getFilesDir(), "voice_styles");
            
            // Check if models exist in files directory (downloaded)
            if (filesOnnxDir.exists() && filesOnnxDir.listFiles() != null && 
                filesOnnxDir.listFiles().length > 0) {
                Log.d(TAG, "Using models from files directory");
                onnxDir = filesOnnxDir.getAbsolutePath();
            } else {
                // Try to copy from assets (if they exist)
                try {
                    Log.d(TAG, "Copying model files from assets...");
                    SupertonicHelper.copyAssetsDir(context, ASSETS_ONNX_DIR, FILES_ONNX_DIR);
                    
                    // Copy voice style files
                    Log.d(TAG, "Copying voice style files from assets...");
                    SupertonicHelper.copyAssetsDir(context, "voice_styles", "voice_styles");
                } catch (Exception e) {
                    Log.w(TAG, "Assets not found, models must be downloaded", e);
                    throw new IllegalStateException("Model files not found. Please download models first.");
                }
                
                onnxDir = new File(context.getFilesDir(), FILES_ONNX_DIR).getAbsolutePath();
            }
            
            Log.d(TAG, "ONNX directory: " + onnxDir);
            
            // TTS 컴포넌트 로드
            textToSpeech = SupertonicHelper.loadTextToSpeech(onnxDir, false, ortEnv);
            
            initialized = true;
            Log.d(TAG, "Supertonic TTS 초기화 완료 (CPU 모드)");
        } catch (Exception e) {
            Log.e(TAG, "초기화 실패", e);
            throw e;
        }
    }
    
    /**
     * 텍스트를 오디오로 변환
     */
    public byte[] generate(String text, String voiceId) throws Exception {
        if (!initialized || textToSpeech == null) {
            throw new IllegalStateException("TTS 엔진이 초기화되지 않았습니다");
        }
        
        try {
            // 목소리 스타일 로드
            String voiceStylePath = getVoiceStylePath(voiceId);
            List<String> voiceStyles = Arrays.asList(voiceStylePath);
            SupertonicHelper.Style style = SupertonicHelper.loadVoiceStyle(voiceStyles, false, ortEnv);
            
            // 텍스트 리스트
            List<String> textList = Arrays.asList(text);
            
            // TTS 생성
            SupertonicHelper.TTSResult result = textToSpeech.call(textList, style, DEFAULT_TOTAL_STEP, ortEnv);
            
            // WAV 파일로 변환
            File tempWav = new File(context.getCacheDir(), "tts_output.wav");
            SupertonicHelper.writeWavFile(tempWav.getAbsolutePath(), result.wav, textToSpeech.sampleRate);
            
            // 바이트 배열로 읽기
            byte[] wavData = new byte[(int) tempWav.length()];
            FileInputStream fis = new FileInputStream(tempWav);
            fis.read(wavData);
            fis.close();
            
            // 정리
            style.close();
            
            return wavData;
        } catch (Exception e) {
            Log.e(TAG, "오디오 생성 실패", e);
            throw e;
        }
    }
    
    /**
     * 목소리 스타일 경로 가져오기
     */
    private String getVoiceStylePath(String voiceId) {
        // voice1, voice2, voice3, voice4 -> M1.json, F1.json, M2.json, F2.json 등
        // 실제 파일명은 assets/voice_styles/ 폴더에 있어야 함
        String[] voiceFiles = {"M1.json", "F1.json", "M2.json", "F2.json"};
        int voiceIndex = getVoiceIndex(voiceId);
        String fileName = voiceFiles[voiceIndex % voiceFiles.length];
        
        // Assets에서 복사된 파일 경로
        File voiceStyleFile = new File(context.getFilesDir(), "voice_styles/" + fileName);
        if (!voiceStyleFile.exists()) {
            // 기본값으로 첫 번째 목소리 사용
            voiceStyleFile = new File(context.getFilesDir(), "voice_styles/M1.json");
        }
        
        return voiceStyleFile.getAbsolutePath();
    }
    
    /**
     * 목소리 ID를 인덱스로 변환
     */
    private int getVoiceIndex(String voiceId) {
        switch (voiceId) {
            case "voice1": return 0;
            case "voice2": return 1;
            case "voice3": return 2;
            case "voice4": return 3;
            default: return 0;
        }
    }
    
    /**
     * 리소스 해제
     */
    public void release() {
        if (textToSpeech != null) {
            try {
                textToSpeech.close();
            } catch (OrtException e) {
                Log.e(TAG, "세션 종료 실패", e);
            }
            textToSpeech = null;
        }
        initialized = false;
    }
}
