package com.supertone.ebook;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Model Download Activity - Download and install Supertonic TTS models
 */
public class ModelDownloadActivity extends AppCompatActivity {
    private static final String TAG = "ModelDownloadActivity";
    
    private TextView statusText;
    private TextView wifiStatusText;
    private Button installButton;
    private Button retryButton;
    private ProgressBar progressBar;
    
    // Hugging Face model files (using raw content URLs)
    // Note: Hugging Face uses Git LFS, so we need to use the resolve/main endpoint
    private static final String HUGGINGFACE_REPO = "Supertone/supertonic";
    private static final String BASE_URL = "https://huggingface.co/" + HUGGINGFACE_REPO + "/resolve/main/";
    
    private static final String[] REQUIRED_FILES = {
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
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_download);
        
        initializeViews();
        checkWifiStatus();
        setupButtons();
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.statusText);
        wifiStatusText = findViewById(R.id.wifiStatusText);
        installButton = findViewById(R.id.installButton);
        retryButton = findViewById(R.id.retryButton);
        progressBar = findViewById(R.id.progressBar);
        
        retryButton.setVisibility(View.GONE);
    }
    
    private void setupButtons() {
        installButton.setOnClickListener(v -> {
            if (isWifiConnected()) {
                startDownload();
            } else {
                Toast.makeText(this, "Please enable Wi-Fi to download models", Toast.LENGTH_LONG).show();
            }
        });
        
        retryButton.setOnClickListener(v -> {
            checkWifiStatus();
            retryButton.setVisibility(View.GONE);
            installButton.setVisibility(View.VISIBLE);
        });
    }
    
    private void checkWifiStatus() {
        if (isWifiConnected()) {
            wifiStatusText.setText("Wi-Fi: Connected");
            wifiStatusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            installButton.setEnabled(true);
        } else {
            wifiStatusText.setText("Wi-Fi: Not Connected - Please enable Wi-Fi");
            wifiStatusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            installButton.setEnabled(false);
        }
    }
    
    private boolean isWifiConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                // Check if it's WiFi
                return networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
            }
        }
        return false;
    }
    
    private void startDownload() {
        installButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        updateStatus("Starting download...");
        
        new Thread(() -> {
            try {
                int totalFiles = REQUIRED_FILES.length;
                int downloaded = 0;
                
                for (String filePath : REQUIRED_FILES) {
                    runOnUiThread(() -> {
                        updateStatus("Downloading: " + filePath);
                    });
                    
                    if (downloadFile(filePath)) {
                        downloaded++;
                        int progress = (downloaded * 100) / totalFiles;
                        runOnUiThread(() -> {
                            progressBar.setProgress(progress);
                        });
                    } else {
                        runOnUiThread(() -> {
                            updateStatus("Failed to download: " + filePath);
                            installButton.setEnabled(true);
                            retryButton.setVisibility(View.VISIBLE);
                            progressBar.setVisibility(View.GONE);
                        });
                        return;
                    }
                }
                
                // All files downloaded, copy to assets location
                runOnUiThread(() -> {
                    updateStatus("Installing models...");
                });
                
                if (installModels()) {
                    runOnUiThread(() -> {
                        updateStatus("Installation complete!");
                        progressBar.setProgress(100);
                        Toast.makeText(this, "Models installed successfully!", Toast.LENGTH_LONG).show();
                        
                        // Mark as installed
                        SplashActivity.markModelsInstalled(this);
                        
                        // Show success and proceed to MainActivity
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            startActivity(new Intent(this, MainActivity.class));
                            finish();
                        }, 2000);
                    });
                } else {
                    runOnUiThread(() -> {
                        updateStatus("Installation failed");
                        installButton.setEnabled(true);
                        retryButton.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.GONE);
                    });
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Download error", e);
                runOnUiThread(() -> {
                    updateStatus("Error: " + e.getMessage());
                    installButton.setEnabled(true);
                    retryButton.setVisibility(View.VISIBLE);
                    progressBar.setVisibility(View.GONE);
                });
            }
        }).start();
    }
    
    private boolean downloadFile(String filePath) {
        try {
            String url = BASE_URL + filePath;
            Log.d(TAG, "Downloading: " + url);
            
            URL downloadUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error: " + responseCode);
                return false;
            }
            
            // Create directory structure
            File cacheDir = getCacheDir();
            File targetFile = new File(cacheDir, filePath);
            targetFile.getParentFile().mkdirs();
            
            // Download file
            InputStream input = connection.getInputStream();
            FileOutputStream output = new FileOutputStream(targetFile);
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            
            output.close();
            input.close();
            connection.disconnect();
            
            Log.d(TAG, "Downloaded: " + filePath);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Download failed: " + filePath, e);
            return false;
        }
    }
    
    private boolean installModels() {
        try {
            File cacheDir = getCacheDir();
            File filesDir = getFilesDir();
            
            // Create directory structure in files directory (same as assets structure)
            new File(filesDir, "onnx").mkdirs();
            new File(filesDir, "voice_styles").mkdirs();
            
            // Copy files from cache to files directory
            for (String filePath : REQUIRED_FILES) {
                File sourceFile = new File(cacheDir, filePath);
                File destFile = new File(filesDir, filePath);
                
                if (!sourceFile.exists()) {
                    Log.e(TAG, "Source file not found: " + sourceFile.getAbsolutePath());
                    return false;
                }
                
                destFile.getParentFile().mkdirs();
                
                // Copy file
                InputStream in = new java.io.FileInputStream(sourceFile);
                FileOutputStream out = new FileOutputStream(destFile);
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                
                out.close();
                in.close();
                
                Log.d(TAG, "Installed: " + filePath + " to " + destFile.getAbsolutePath());
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Installation error", e);
            return false;
        }
    }
    
    private void updateStatus(String status) {
        statusText.setText("Status: " + status);
    }
}

