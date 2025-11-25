package com.supertone.ebook;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

/**
 * Splash Activity - Check if models are installed
 */
public class SplashActivity extends AppCompatActivity {
    private static final String TAG = "SplashActivity";
    private static final String PREFS_NAME = "ebook_prefs";
    private static final String KEY_MODELS_INSTALLED = "models_installed";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Check if models are installed
        if (areModelsInstalled()) {
            // Models exist, go to MainActivity
            Log.d(TAG, "Models found, proceeding to MainActivity");
            startActivity(new Intent(this, MainActivity.class));
            finish();
        } else {
            // Models not found, go to download activity
            Log.d(TAG, "Models not found, proceeding to ModelDownloadActivity");
            startActivity(new Intent(this, ModelDownloadActivity.class));
            finish();
        }
    }
    
    /**
     * Check if all required model files exist
     */
    private boolean areModelsInstalled() {
        try {
            // Check if models are already verified (cached)
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean cached = prefs.getBoolean(KEY_MODELS_INSTALLED, false);
            if (cached) {
                // Double check by verifying files exist
                if (verifyModelFiles()) {
                    return true;
                } else {
                    // Files missing, clear cache
                    prefs.edit().remove(KEY_MODELS_INSTALLED).apply();
                }
            }
            
            // Verify model files exist
            return verifyModelFiles();
        } catch (Exception e) {
            Log.e(TAG, "Error checking models", e);
            return false;
        }
    }
    
    /**
     * Verify all required model files exist in files directory or assets
     */
    private boolean verifyModelFiles() {
        try {
            String[] requiredFiles = {
                "onnx/duration_predictor.onnx",
                "onnx/text_encoder.onnx",
                "onnx/vector_estimator.onnx",
                "onnx/vocoder.onnx",
                "onnx/tts.json",
                "onnx/unicode_indexer.json",
                "voice_styles/M1.json",
                "voice_styles/F1.json",
                "voice_styles/M2.json",
                "voice_styles/F2.json"
            };
            
            java.io.File filesDir = getFilesDir();
            
            // First check files directory (downloaded models)
            boolean allFilesExist = true;
            for (String filePath : requiredFiles) {
                java.io.File file = new java.io.File(filesDir, filePath);
                if (!file.exists()) {
                    allFilesExist = false;
                    break;
                }
            }
            
            if (allFilesExist) {
                Log.d(TAG, "All model files found in files directory");
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                prefs.edit().putBoolean(KEY_MODELS_INSTALLED, true).apply();
                return true;
            }
            
            // If not in files directory, check assets
            for (String filePath : requiredFiles) {
                try {
                    android.content.res.AssetManager assetManager = getAssets();
                    java.io.InputStream is = assetManager.open(filePath);
                    if (is == null) {
                        Log.d(TAG, "Missing file: " + filePath);
                        return false;
                    }
                    is.close();
                } catch (Exception e) {
                    Log.d(TAG, "File not found in assets: " + filePath);
                    return false;
                }
            }
            
            // All files exist in assets, cache the result
            Log.d(TAG, "All model files found in assets");
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_MODELS_INSTALLED, true).apply();
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error verifying model files", e);
            return false;
        }
    }
    
    /**
     * Mark models as installed (called from ModelDownloadActivity)
     */
    public static void markModelsInstalled(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_MODELS_INSTALLED, true).apply();
    }
}

