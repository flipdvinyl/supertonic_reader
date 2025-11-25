package com.supertone.ebook;

import android.content.Context;
import android.util.Log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * Supertonic Helper 클래스 (Android 버전)
 * 원본: https://github.com/supertone-inc/supertonic/tree/main/java
 */
public class SupertonicHelper {
    private static final String TAG = "SupertonicHelper";
    
    /**
     * Assets에서 파일을 내부 저장소로 복사
     */
    public static void copyAssetToFilesDir(Context context, String assetPath, String destFileName) throws IOException {
        File destFile = new File(context.getFilesDir(), destFileName);
        if (destFile.exists()) {
            Log.d(TAG, "File already exists: " + destFileName);
            return;
        }
        
        Log.d(TAG, "Copying asset: " + assetPath + " to " + destFile.getAbsolutePath());
        InputStream is = context.getAssets().open(assetPath);
        FileOutputStream os = new FileOutputStream(destFile);
        
        byte[] buffer = new byte[8192];
        int length;
        while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
        }
        
        os.flush();
        os.close();
        is.close();
        
        Log.d(TAG, "File copied successfully: " + destFileName);
    }
    
    /**
     * Assets에서 디렉토리 전체를 복사
     */
    public static void copyAssetsDir(Context context, String assetDir, String destDirName) throws IOException {
        File destDir = new File(context.getFilesDir(), destDirName);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        
        String[] files = context.getAssets().list(assetDir);
        if (files != null) {
            for (String file : files) {
                String assetPath = assetDir + "/" + file;
                String destPath = destDirName + "/" + file;
                
                // 디렉토리인지 확인
                String[] subFiles = context.getAssets().list(assetPath);
                if (subFiles != null && subFiles.length > 0) {
                    // 디렉토리
                    copyAssetsDir(context, assetPath, destPath);
                } else {
                    // 파일
                    copyAssetToFilesDir(context, assetPath, destPath);
                }
            }
        }
    }
    
    /**
     * JSON 파일에서 long 배열 로드
     */
    public static long[] loadJsonLongArray(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File(filePath));
        List<Long> list = new ArrayList<>();
        for (JsonNode node : root) {
            list.add(node.asLong());
        }
        long[] array = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }
    
    /**
     * Long 텐서 생성
     */
    public static OnnxTensor createLongTensor(long[][] data, OrtEnvironment env) throws OrtException {
        int rows = data.length;
        int cols = data[0].length;
        long[] flat = new long[rows * cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(data[i], 0, flat, i * cols, cols);
        }
        long[] shape = {rows, cols};
        return OnnxTensor.createTensor(env, LongBuffer.wrap(flat), shape);
    }
    
    /**
     * Float 텐서 생성 (3D)
     */
    public static OnnxTensor createFloatTensor(float[][][] data, OrtEnvironment env) throws OrtException {
        int dim0 = data.length;
        int dim1 = data[0].length;
        int dim2 = data[0][0].length;
        float[] flat = new float[dim0 * dim1 * dim2];
        int idx = 0;
        for (float[][] d0 : data) {
            for (float[] d1 : d0) {
                System.arraycopy(d1, 0, flat, idx, dim2);
                idx += dim2;
            }
        }
        long[] shape = {dim0, dim1, dim2};
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(flat), shape);
    }
    
    /**
     * Float 텐서 생성 (2D)
     */
    public static OnnxTensor createFloatTensor(float[][] data, OrtEnvironment env) throws OrtException {
        int rows = data.length;
        int cols = data[0].length;
        float[] flat = new float[rows * cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(data[i], 0, flat, i * cols, cols);
        }
        long[] shape = {rows, cols};
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(flat), shape);
    }
    
    /**
     * Float 배열을 WAV 파일로 저장
     */
    public static void writeWavFile(String filePath, float[] wavData, int sampleRate) throws IOException {
        int numSamples = wavData.length;
        int numChannels = 1;
        int bitsPerSample = 16;
        int byteRate = sampleRate * numChannels * bitsPerSample / 8;
        int blockAlign = numChannels * bitsPerSample / 8;
        int dataSize = numSamples * numChannels * bitsPerSample / 8;
        int fileSize = 36 + dataSize;
        
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // WAV 헤더
        buffer.put("RIFF".getBytes());
        buffer.putInt(fileSize);
        buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) numChannels);
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) blockAlign);
        buffer.putShort((short) bitsPerSample);
        buffer.put("data".getBytes());
        buffer.putInt(dataSize);
        
        // 오디오 데이터
        for (float sample : wavData) {
            short intSample = (short) Math.max(Short.MIN_VALUE,
                Math.min(Short.MAX_VALUE, (int) (sample * Short.MAX_VALUE)));
            buffer.putShort(intSample);
        }
        
        FileOutputStream fos = new FileOutputStream(filePath);
        fos.write(buffer.array());
        fos.close();
    }
    
    /**
     * 파일명 정리
     */
    public static String sanitizeFilename(String text, int maxLen) {
        String sanitized = text.replaceAll("[^a-zA-Z0-9]", "_");
        if (sanitized.length() > maxLen) {
            sanitized = sanitized.substring(0, maxLen);
        }
        return sanitized;
    }
    
    /**
     * Config 클래스
     */
    public static class Config {
        public static class AEConfig {
            public int sampleRate;
            public int baseChunkSize;
        }
        
        public static class TTLConfig {
            public int chunkCompressFactor;
            public int latentDim;
        }
        
        public AEConfig ae;
        public TTLConfig ttl;
    }
    
    /**
     * Style 클래스
     */
    public static class Style {
        public OnnxTensor ttlTensor;
        public OnnxTensor dpTensor;
        
        public Style(OnnxTensor ttlTensor, OnnxTensor dpTensor) {
            this.ttlTensor = ttlTensor;
            this.dpTensor = dpTensor;
        }
        
        public void close() throws OrtException {
            if (ttlTensor != null) ttlTensor.close();
            if (dpTensor != null) dpTensor.close();
        }
    }
    
    /**
     * TTSResult 클래스
     */
    public static class TTSResult {
        public float[] wav;
        public float[] duration;
        
        public TTSResult(float[] wav, float[] duration) {
            this.wav = wav;
            this.duration = duration;
        }
    }
    
    /**
     * 목소리 스타일 로드
     */
    public static Style loadVoiceStyle(List<String> voiceStylePaths, boolean verbose, OrtEnvironment env) 
            throws IOException, OrtException {
        int bsz = voiceStylePaths.size();
        
        // 첫 번째 파일에서 차원 읽기
        ObjectMapper mapper = new ObjectMapper();
        JsonNode firstRoot = mapper.readTree(new File(voiceStylePaths.get(0)));
        
        long[] ttlDims = new long[3];
        for (int i = 0; i < 3; i++) {
            ttlDims[i] = firstRoot.get("style_ttl").get("dims").get(i).asLong();
        }
        long[] dpDims = new long[3];
        for (int i = 0; i < 3; i++) {
            dpDims[i] = firstRoot.get("style_dp").get("dims").get(i).asLong();
        }
        
        long ttlDim1 = ttlDims[1];
        long ttlDim2 = ttlDims[2];
        long dpDim1 = dpDims[1];
        long dpDim2 = dpDims[2];
        
        // 배열 할당
        int ttlSize = (int) (bsz * ttlDim1 * ttlDim2);
        int dpSize = (int) (bsz * dpDim1 * dpDim2);
        float[] ttlFlat = new float[ttlSize];
        float[] dpFlat = new float[dpSize];
        
        // 데이터 채우기
        for (int i = 0; i < bsz; i++) {
            JsonNode root = mapper.readTree(new File(voiceStylePaths.get(i)));
            
            // TTL 데이터 플래튼화
            int ttlOffset = (int) (i * ttlDim1 * ttlDim2);
            int idx = 0;
            JsonNode ttlData = root.get("style_ttl").get("data");
            for (JsonNode batch : ttlData) {
                for (JsonNode row : batch) {
                    for (JsonNode val : row) {
                        ttlFlat[ttlOffset + idx++] = (float) val.asDouble();
                    }
                }
            }
            
            // DP 데이터 플래튼화
            int dpOffset = (int) (i * dpDim1 * dpDim2);
            idx = 0;
            JsonNode dpData = root.get("style_dp").get("data");
            for (JsonNode batch : dpData) {
                for (JsonNode row : batch) {
                    for (JsonNode val : row) {
                        dpFlat[dpOffset + idx++] = (float) val.asDouble();
                    }
                }
            }
        }
        
        long[] ttlShape = {bsz, ttlDim1, ttlDim2};
        long[] dpShape = {bsz, dpDim1, dpDim2};
        
        OnnxTensor ttlTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(ttlFlat), ttlShape);
        OnnxTensor dpTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(dpFlat), dpShape);
        
        if (verbose) {
            Log.d(TAG, "Loaded " + bsz + " voice styles");
        }
        
        return new Style(ttlTensor, dpTensor);
    }
    
    /**
     * TTS 컴포넌트 로드
     */
    public static TextToSpeech loadTextToSpeech(String onnxDir, boolean useGpu, OrtEnvironment env) 
            throws IOException, OrtException {
        if (useGpu) {
            throw new RuntimeException("GPU mode is not supported yet");
        }
        Log.d(TAG, "Using CPU for inference");
        
        // Config 로드
        Config config = loadCfgs(onnxDir);
        
        // Session 옵션 생성
        OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
        
        // 모델 로드
        OrtSession dpSession = env.createSession(onnxDir + "/duration_predictor.onnx", opts);
        OrtSession textEncSession = env.createSession(onnxDir + "/text_encoder.onnx", opts);
        OrtSession vectorEstSession = env.createSession(onnxDir + "/vector_estimator.onnx", opts);
        OrtSession vocoderSession = env.createSession(onnxDir + "/vocoder.onnx", opts);
        
        // 텍스트 프로세서 로드
        UnicodeProcessor textProcessor = new UnicodeProcessor(onnxDir + "/unicode_indexer.json");
        
        return new TextToSpeech(config, textProcessor, dpSession, textEncSession, vectorEstSession, vocoderSession);
    }
    
    /**
     * Config 로드
     */
    public static Config loadCfgs(String onnxDir) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File(onnxDir + "/tts.json"));
        
        Config config = new Config();
        config.ae = new Config.AEConfig();
        config.ae.sampleRate = root.get("ae").get("sample_rate").asInt();
        config.ae.baseChunkSize = root.get("ae").get("base_chunk_size").asInt();
        
        config.ttl = new Config.TTLConfig();
        config.ttl.chunkCompressFactor = root.get("ttl").get("chunk_compress_factor").asInt();
        config.ttl.latentDim = root.get("ttl").get("latent_dim").asInt();
        
        return config;
    }
    
    /**
     * Latent mask 생성
     */
    public static float[][][] getLatentMask(long[] wavLengths, Config config) {
        long baseChunkSize = config.ae.baseChunkSize;
        long chunkCompressFactor = config.ttl.chunkCompressFactor;
        long latentSize = baseChunkSize * chunkCompressFactor;
        
        long[] latentLengths = new long[wavLengths.length];
        long maxLen = 0;
        for (int i = 0; i < wavLengths.length; i++) {
            latentLengths[i] = (wavLengths[i] + latentSize - 1) / latentSize;
            maxLen = Math.max(maxLen, latentLengths[i]);
        }
        
        float[][][] mask = new float[wavLengths.length][1][(int) maxLen];
        for (int i = 0; i < wavLengths.length; i++) {
            for (int j = 0; j < maxLen; j++) {
                mask[i][0][j] = j < latentLengths[i] ? 1.0f : 0.0f;
            }
        }
        return mask;
    }
}
