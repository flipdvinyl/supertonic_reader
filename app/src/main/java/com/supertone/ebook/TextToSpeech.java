package com.supertone.ebook;

import android.util.Log;

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
 * Text-to-Speech inference class
 */
class TextToSpeech {
    private static final String TAG = "TextToSpeech";
    private SupertonicHelper.Config config;
    private UnicodeProcessor textProcessor;
    private OrtSession dpSession;
    private OrtSession textEncSession;
    private OrtSession vectorEstSession;
    private OrtSession vocoderSession;
    public int sampleRate;
    private int baseChunkSize;
    private int chunkCompress;
    private int ldim;
    
    public TextToSpeech(SupertonicHelper.Config config, UnicodeProcessor textProcessor,
                       OrtSession dpSession, OrtSession textEncSession,
                       OrtSession vectorEstSession, OrtSession vocoderSession) {
        this.config = config;
        this.textProcessor = textProcessor;
        this.dpSession = dpSession;
        this.textEncSession = textEncSession;
        this.vectorEstSession = vectorEstSession;
        this.vocoderSession = vocoderSession;
        this.sampleRate = config.ae.sampleRate;
        this.baseChunkSize = config.ae.baseChunkSize;
        this.chunkCompress = config.ttl.chunkCompressFactor;
        this.ldim = config.ttl.latentDim;
    }
    
    public SupertonicHelper.TTSResult call(List<String> textList, SupertonicHelper.Style style, int totalStep, OrtEnvironment env) 
            throws OrtException {
        int bsz = textList.size();
        
        // Process text
        UnicodeProcessor.TextProcessResult textResult = textProcessor.call(textList);
        long[][] textIds = textResult.textIds;
        float[][][] textMask = textResult.textMask;
        
        // Create tensors
        OnnxTensor textIdsTensor = SupertonicHelper.createLongTensor(textIds, env);
        OnnxTensor textMaskTensor = SupertonicHelper.createFloatTensor(textMask, env);
        
        // Predict duration
        Map<String, OnnxTensor> dpInputs = new HashMap<>();
        dpInputs.put("text_ids", textIdsTensor);
        dpInputs.put("style_dp", style.dpTensor);
        dpInputs.put("text_mask", textMaskTensor);
        
        OrtSession.Result dpResult = dpSession.run(dpInputs);
        Object dpValue = dpResult.get(0).getValue();
        float[] duration;
        if (dpValue instanceof float[][]) {
            duration = ((float[][]) dpValue)[0];
        } else {
            duration = (float[]) dpValue;
        }
        
        // Encode text
        Map<String, OnnxTensor> textEncInputs = new HashMap<>();
        textEncInputs.put("text_ids", textIdsTensor);
        textEncInputs.put("style_ttl", style.ttlTensor);
        textEncInputs.put("text_mask", textMaskTensor);
        
        OrtSession.Result textEncResult = textEncSession.run(textEncInputs);
        OnnxTensor textEmbTensor = (OnnxTensor) textEncResult.get(0);
        
        // Sample noisy latent
        NoisyLatentResult noisyLatentResult = sampleNoisyLatent(duration);
        float[][][] xt = noisyLatentResult.noisyLatent;
        float[][][] latentMask = noisyLatentResult.latentMask;
        
        // Prepare constant tensors
        float[] totalStepArray = new float[bsz];
        Arrays.fill(totalStepArray, (float) totalStep);
        OnnxTensor totalStepTensor = OnnxTensor.createTensor(env, totalStepArray);
        
        // Denoising loop
        for (int step = 0; step < totalStep; step++) {
            float[] currentStepArray = new float[bsz];
            Arrays.fill(currentStepArray, (float) step);
            OnnxTensor currentStepTensor = OnnxTensor.createTensor(env, currentStepArray);
            OnnxTensor noisyLatentTensor = SupertonicHelper.createFloatTensor(xt, env);
            OnnxTensor latentMaskTensor = SupertonicHelper.createFloatTensor(latentMask, env);
            OnnxTensor textMaskTensor2 = SupertonicHelper.createFloatTensor(textMask, env);
            
            Map<String, OnnxTensor> vectorEstInputs = new HashMap<>();
            vectorEstInputs.put("noisy_latent", noisyLatentTensor);
            vectorEstInputs.put("text_emb", textEmbTensor);
            vectorEstInputs.put("style_ttl", style.ttlTensor);
            vectorEstInputs.put("latent_mask", latentMaskTensor);
            vectorEstInputs.put("text_mask", textMaskTensor2);
            vectorEstInputs.put("current_step", currentStepTensor);
            vectorEstInputs.put("total_step", totalStepTensor);
            
            OrtSession.Result vectorEstResult = vectorEstSession.run(vectorEstInputs);
            float[][][] denoised = (float[][][]) vectorEstResult.get(0).getValue();
            
            // Update latent
            xt = denoised;
            
            // Clean up
            currentStepTensor.close();
            noisyLatentTensor.close();
            latentMaskTensor.close();
            textMaskTensor2.close();
            vectorEstResult.close();
        }
        
        // Generate waveform
        OnnxTensor finalLatentTensor = SupertonicHelper.createFloatTensor(xt, env);
        Map<String, OnnxTensor> vocoderInputs = new HashMap<>();
        vocoderInputs.put("latent", finalLatentTensor);
        
        OrtSession.Result vocoderResult = vocoderSession.run(vocoderInputs);
        float[][] wavBatch = (float[][]) vocoderResult.get(0).getValue();
        float[] wav = wavBatch[0];
        
        // Clean up
        textIdsTensor.close();
        textMaskTensor.close();
        dpResult.close();
        textEncResult.close();
        totalStepTensor.close();
        finalLatentTensor.close();
        vocoderResult.close();
        
        return new SupertonicHelper.TTSResult(wav, duration);
    }
    
    private NoisyLatentResult sampleNoisyLatent(float[] duration) {
        int bsz = duration.length;
        float maxDur = 0;
        for (float d : duration) {
            maxDur = Math.max(maxDur, d);
        }
        
        long wavLenMax = (long) (maxDur * sampleRate);
        long[] wavLengths = new long[bsz];
        for (int i = 0; i < bsz; i++) {
            wavLengths[i] = (long) (duration[i] * sampleRate);
        }
        
        int chunkSize = baseChunkSize * chunkCompress;
        int latentLen = (int) ((wavLenMax + chunkSize - 1) / chunkSize);
        int latentDim = ldim * chunkCompress;
        
        Random rng = new Random();
        float[][][] noisyLatent = new float[bsz][latentDim][latentLen];
        for (int b = 0; b < bsz; b++) {
            for (int d = 0; d < latentDim; d++) {
                for (int t = 0; t < latentLen; t++) {
                    // Box-Muller transform
                    double u1 = Math.max(1e-10, rng.nextDouble());
                    double u2 = rng.nextDouble();
                    noisyLatent[b][d][t] = (float) (Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2));
                }
            }
        }
        
        float[][][] latentMask = SupertonicHelper.getLatentMask(wavLengths, config);
        
        // Apply mask
        for (int b = 0; b < bsz; b++) {
            for (int d = 0; d < latentDim; d++) {
                for (int t = 0; t < latentLen; t++) {
                    noisyLatent[b][d][t] *= latentMask[b][0][t];
                }
            }
        }
        
        return new NoisyLatentResult(noisyLatent, latentMask);
    }
    
    public void close() throws OrtException {
        if (dpSession != null) dpSession.close();
        if (textEncSession != null) textEncSession.close();
        if (vectorEstSession != null) vectorEstSession.close();
        if (vocoderSession != null) vocoderSession.close();
    }
    
    /**
     * Noisy latent result holder
     */
    private static class NoisyLatentResult {
        float[][][] noisyLatent;
        float[][][] latentMask;
        
        NoisyLatentResult(float[][][] noisyLatent, float[][][] latentMask) {
            this.noisyLatent = noisyLatent;
            this.latentMask = latentMask;
        }
    }
}


