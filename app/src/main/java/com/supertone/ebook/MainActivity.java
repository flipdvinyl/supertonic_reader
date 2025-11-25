package com.supertone.ebook;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.text.method.LinkMovementMethod;
import android.text.TextPaint;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private TextView textDisplay;
    private FrameLayout textDisplayContainer;
    private FrameLayout pageIndicatorFrame;
    private View pageIndicatorHandle;
    private View pageIndicatorHandleBox;
    private Button voiceButton1; // Female #1 (voice2)
    private Button voiceButton2; // Female #2 (voice4)
    private Button voiceButton3; // Male #1 (voice1)
    private Button voiceButton4; // Male #2 (voice3)
    private String selectedVoice = "voice2"; // Default: Female #1
    private String selectedVoiceName = "Female #1"; // 현재 선택된 보이스 이름
    private TextView voiceSelector; // Voice 선택 TextView
    private Button generateButton;
    private android.widget.ImageButton playButton;
    private Button copyErrorButton;
    private TextView statusText;
    private TextView metricsText;
    private TextView timeDisplay; // 재생 시간 표시 (현재/전체)
    private android.widget.SeekBar progressBar;
    private MediaPlayer mediaPlayer;
    private SupertonicTTS ttsEngine;
    private ExecutorService executorService;
    private Handler mainHandler;
    private Handler progressHandler;
    private String lastErrorMessage = "";
    private List<File> audioChunks = new ArrayList<>();
    private List<String> chunkTexts = new ArrayList<>(); // 청크 텍스트 저장
    private int currentChunkIndex = 0;
    private boolean isGenerating = false;
    private boolean isPaused = false;
    private java.util.concurrent.Future<?> currentGenerationTask = null;
    private long generationStartTime = 0;
    private int totalTextLength = 0;
    private double totalAudioDuration = 0; // 생성된 모든 청크의 총 재생 시간
    private List<Double> chunkProcessingTimes = new ArrayList<>(); // 각 청크의 생성 시간
    private List<Double> chunkCPSList = new ArrayList<>(); // 각 청크의 CPS
    private List<Double> chunkRTFList = new ArrayList<>(); // 각 청크의 RTF
    private double totalChunkProcessingTime = 0; // 모든 청크 생성 시간의 총합
    
    // Page-based display variables
    private String fullText = "";
    private List<String> pages = new ArrayList<>();
    private int currentPageIndex = 0;
    private List<String> sentences = new ArrayList<>(); // 문장 리스트
    private List<Integer> sentenceToChunkMap = new ArrayList<>(); // 문장 -> 청크 인덱스 매핑
    private List<List<Integer>> pageToSentencesMap = new ArrayList<>(); // 페이지 -> 문장 인덱스 리스트 매핑
    private float pageWidth = 0;
    private float pageHeight = 0;
    private boolean isDraggingIndicator = false;
    private float indicatorStartX = 0;
    private Runnable highlightRemoveRunnable = null; // 하이라이트 제거를 위한 Runnable 추적
    
    // Voice mapping: Female #1, Female #2, Male #1, Male #2
    private static final String SAMPLE_TEXT_FILE = "sample_text.txt";
    
    // 페이지 분할 기준: 한 페이지에 최대 표시할 줄 수 (16줄부터 다음 페이지로 넘어감)
    private static final int MAX_LINES_PER_PAGE = 15;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ActionBar 숨기기 (헤더 영역 안보이게 처리)
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupVoiceButtons();
        setupButtons();
        initializeTTS();
        
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        progressHandler = new Handler(Looper.getMainLooper());
        
        // 샘플 텍스트 프리셋 설정
        loadSampleText();
        
        // 진행 바 업데이트 시작
        startProgressUpdate();
    }
    
    /**
     * Load sample text from assets file
     */
    private void loadSampleText() {
        try {
            java.io.InputStream is = getAssets().open(SAMPLE_TEXT_FILE);
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(is, "UTF-8"));
            StringBuilder text = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
            reader.close();
            is.close();
            fullText = text.toString().trim();
            setupPages();
        } catch (Exception e) {
            // If file not found, use empty string
            android.util.Log.w("MainActivity", "Sample text file not found: " + SAMPLE_TEXT_FILE);
            fullText = "";
            setupPages();
        }
    }
    
    private void initializeViews() {
        textDisplay = findViewById(R.id.textDisplay);
        textDisplayContainer = findViewById(R.id.textDisplayContainer);
        pageIndicatorFrame = findViewById(R.id.pageIndicatorFrame);
        pageIndicatorHandle = findViewById(R.id.pageIndicatorHandle);
        pageIndicatorHandleBox = findViewById(R.id.pageIndicatorHandleBox);
        voiceButton1 = findViewById(R.id.voiceButton1);
        voiceButton2 = findViewById(R.id.voiceButton2);
        voiceButton3 = findViewById(R.id.voiceButton3);
        voiceButton4 = findViewById(R.id.voiceButton4);
        voiceSelector = findViewById(R.id.voiceSelector);
        generateButton = findViewById(R.id.generateButton);
        playButton = findViewById(R.id.playButton);
        copyErrorButton = findViewById(R.id.copyErrorButton);
        statusText = findViewById(R.id.statusText);
        metricsText = findViewById(R.id.metricsText);
        timeDisplay = findViewById(R.id.timeDisplay);
        progressBar = findViewById(R.id.progressBar);
        
        // TextView 스크롤 완전히 비활성화
        textDisplay.setFocusable(false);
        textDisplay.setFocusableInTouchMode(false);
        textDisplay.setClickable(true);
        textDisplay.setLongClickable(false);
        
        // 화살표 키 이벤트 무시 (문장 선택 방지)
        textDisplay.setOnKeyListener((v, keyCode, event) -> {
            // 위/아래/좌/우 화살표 키 무시
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                return true; // 이벤트 소비하여 처리하지 않음
            }
            return false;
        });
        
        // 텍스트 클릭 이벤트 설정
        setupTextClickHandler();
        
        // 페이지 인디케이터 설정
        setupPageIndicator();
        
        // Play 버튼 초기 활성화 (청크가 없어도 Generate 기능 사용 가능)
        playButton.setEnabled(true);
        
        // Play 버튼 초기 아이콘 설정
        playButton.setImageResource(R.drawable.ic_play);
    }
    
    private void setupVoiceButtons() {
        // Set default selection (Female #1)
        updateVoiceButtonSelection(voiceButton1);
        updateVoiceSelector();
        
        // Voice 선택 TextView 클릭 시 팝업 메뉴 표시
        voiceSelector.setOnClickListener(v -> {
            android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(this, voiceSelector);
            popupMenu.getMenu().add(0, 0, 0, "Female #1");
            popupMenu.getMenu().add(0, 1, 0, "Female #2");
            popupMenu.getMenu().add(0, 2, 0, "Male #1");
            popupMenu.getMenu().add(0, 3, 0, "Male #2");
            
            popupMenu.setOnMenuItemClickListener(item -> {
                String previousVoice = selectedVoice;
                switch (item.getItemId()) {
                    case 0:
                        selectedVoice = "voice2";
                        selectedVoiceName = "Female #1";
                        updateVoiceButtonSelection(voiceButton1);
                        break;
                    case 1:
                        selectedVoice = "voice4";
                        selectedVoiceName = "Female #2";
                        updateVoiceButtonSelection(voiceButton2);
                        break;
                    case 2:
                        selectedVoice = "voice1";
                        selectedVoiceName = "Male #1";
                        updateVoiceButtonSelection(voiceButton3);
                        break;
                    case 3:
                        selectedVoice = "voice3";
                        selectedVoiceName = "Male #2";
                        updateVoiceButtonSelection(voiceButton4);
                        break;
                }
                updateVoiceSelector();
                popupMenu.dismiss();
                
                // Voice 변경 시 현재 읽던 문장부터 새롭게 청크 생성 및 재생
                if (!previousVoice.equals(selectedVoice)) {
                    handleVoiceChange();
                }
                
                return true;
            });
            
            popupMenu.show();
        });
        
        voiceButton1.setOnClickListener(v -> {
            String previousVoice = selectedVoice;
            selectedVoice = "voice2"; // Female #1
            selectedVoiceName = "Female #1";
            updateVoiceButtonSelection(voiceButton1);
            updateVoiceSelector();
            
            // Voice 변경 시 현재 읽던 문장부터 새롭게 청크 생성 및 재생
            if (!previousVoice.equals(selectedVoice)) {
                handleVoiceChange();
            }
        });
        
        voiceButton2.setOnClickListener(v -> {
            String previousVoice = selectedVoice;
            selectedVoice = "voice4"; // Female #2
            selectedVoiceName = "Female #2";
            updateVoiceButtonSelection(voiceButton2);
            updateVoiceSelector();
            
            // Voice 변경 시 현재 읽던 문장부터 새롭게 청크 생성 및 재생
            if (!previousVoice.equals(selectedVoice)) {
                handleVoiceChange();
            }
        });
        
        voiceButton3.setOnClickListener(v -> {
            String previousVoice = selectedVoice;
            selectedVoice = "voice1"; // Male #1
            selectedVoiceName = "Male #1";
            updateVoiceButtonSelection(voiceButton3);
            updateVoiceSelector();
            
            // Voice 변경 시 현재 읽던 문장부터 새롭게 청크 생성 및 재생
            if (!previousVoice.equals(selectedVoice)) {
                handleVoiceChange();
            }
        });
        
        voiceButton4.setOnClickListener(v -> {
            String previousVoice = selectedVoice;
            selectedVoice = "voice3"; // Male #2
            selectedVoiceName = "Male #2";
            updateVoiceButtonSelection(voiceButton4);
            updateVoiceSelector();
            
            // Voice 변경 시 현재 읽던 문장부터 새롭게 청크 생성 및 재생
            if (!previousVoice.equals(selectedVoice)) {
                handleVoiceChange();
            }
        });
    }
    
    private void updateVoiceButtonSelection(Button selectedButton) {
        // Reset all buttons
        voiceButton1.setAlpha(0.5f);
        voiceButton2.setAlpha(0.5f);
        voiceButton3.setAlpha(0.5f);
        voiceButton4.setAlpha(0.5f);
        
        // Highlight selected button
        selectedButton.setAlpha(1.0f);
    }
    
    private void updateVoiceSelector() {
        if (voiceSelector != null) {
            SpannableString spannable = new SpannableString("Voice: " + selectedVoiceName);
            // 보이스 이름에 물결무늬 언더라인 추가
            int start = "Voice: ".length();
            int end = spannable.length();
            spannable.setSpan(new WavyUnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            voiceSelector.setText(spannable);
        }
    }
    
    /**
     * 일반 직선 언더라인을 그리는 커스텀 Span
     */
    private static class WavyUnderlineSpan extends android.text.style.ReplacementSpan {
        private static final float UNDERLINE_THICKNESS = 2f;
        private static final float UNDERLINE_OFFSET = 4f; // baseline에서 아래로 4px
        
        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            return (int) paint.measureText(text, start, end);
        }
        
        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            // 텍스트 그리기
            canvas.drawText(text, start, end, x, y, paint);
            
            // 직선 언더라인 그리기
            float textWidth = paint.measureText(text, start, end);
            float baseline = y;
            // 언더라인을 아래로 1px 더 띄우기 (기존 로직 유지)
            float underlineY = baseline + UNDERLINE_OFFSET + 1f;
            
            // 원래 Paint 상태 저장
            Paint.Style originalStyle = paint.getStyle();
            int originalColor = paint.getColor();
            float originalStrokeWidth = paint.getStrokeWidth();
            
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(UNDERLINE_THICKNESS);
            paint.setAntiAlias(true);
            // 회색으로 설정 (#888888)
            paint.setColor(0xFF888888);
            
            // 직선 언더라인 그리기
            canvas.drawLine(x, underlineY, x + textWidth, underlineY, paint);
            
            // Paint 상태 복원
            paint.setStyle(originalStyle);
            paint.setColor(originalColor);
            paint.setStrokeWidth(originalStrokeWidth);
        }
    }
    
    private void setupButtons() {
        generateButton.setOnClickListener(v -> generateAudio());
        playButton.setOnClickListener(v -> {
            // 청크가 없으면 Generate 기능 실행
            if (audioChunks.isEmpty()) {
                generateAudio();
                return;
            }
            
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                pauseAudio();
                playButton.setImageResource(R.drawable.ic_play);
            } else {
                playAudio();
                playButton.setImageResource(R.drawable.ic_pause);
            }
        });
        copyErrorButton.setOnClickListener(v -> copyErrorMessageToClipboard());
    }
    
    private void initializeTTS() {
        try {
            ttsEngine = new SupertonicTTS(getApplicationContext());
            updateStatus("Supertonic TTS engine ready");
        } catch (Exception e) {
            updateStatus("TTS engine initialization failed: " + e.getMessage());
            // UI 테스트를 위해 버튼은 활성화 유지
        }
    }
    
    private void generateAudio() {
        if (fullText.isEmpty()) {
            Toast.makeText(this, "Please enter text", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String text = fullText;
        
        // If already generating, cancel the current process and start fresh
        if (isGenerating) {
            cancelCurrentGeneration();
        }
        
        // selectedVoice is already set by button click
        isGenerating = true;
        
        // 기존 재생 중지 및 데이터 초기화
        stopAudio();
        clearAudioChunks();
        audioChunks.clear();
        chunkTexts.clear();
        currentChunkIndex = 0;
        
        // Reset generation metrics
        generationStartTime = System.currentTimeMillis();
        totalTextLength = text.length();
        totalAudioDuration = 0;
        chunkProcessingTimes.clear();
        chunkCPSList.clear();
        chunkRTFList.clear();
        totalChunkProcessingTime = 0;
        updateLogConsole("", true); // Clear console
        
        // 문장-청크 매핑 초기화
        sentenceToChunkMap.clear();
        for (int i = 0; i < sentences.size(); i++) {
            sentenceToChunkMap.add(-1); // 초기값: 매핑 없음
        }
        
        currentGenerationTask = executorService.submit(() -> {
            try {
                // 텍스트를 청크로 분할
                List<String> chunks = chunkText(text);
                mainHandler.post(() -> updateStatus("Generating " + chunks.size() + " segments..."));
                
                // 청크를 문장에 매핑
                mapChunksToSentences(chunks);
                
                if (chunks.isEmpty()) {
                    throw new Exception("Chunk generation failed");
                }
                
                // 첫 번째 청크 생성 및 즉시 재생
                String firstChunk = chunks.get(0);
                mainHandler.post(() -> {
                    updateStatus("Generating first segment...");
                    // 첫 번째 청크 생성 중일 때 gen 아이콘 표시
                    playButton.setImageResource(R.drawable.ic_gen);
                });
                
                // Check if cancelled before generating first chunk
                if (Thread.currentThread().isInterrupted()) {
                    android.util.Log.d("MainActivity", "Generation interrupted before first chunk");
                    return;
                }
                
                long firstChunkStartTime = System.currentTimeMillis();
                byte[] firstAudioData = null;
                try {
                    firstAudioData = ttsEngine.generate(firstChunk, selectedVoice);
                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    throw e;
                }
                
                // Check again if cancelled after first chunk generation
                if (Thread.currentThread().isInterrupted()) {
                    android.util.Log.d("MainActivity", "Generation interrupted after first chunk generation");
                    return;
                }
                
                if (firstAudioData == null || firstAudioData.length == 0) {
                    throw new Exception("First chunk generation failed");
                }
                
                long firstChunkEndTime = System.currentTimeMillis();
                double firstChunkProcessingTime = (firstChunkEndTime - firstChunkStartTime) / 1000.0;
                chunkProcessingTimes.add(firstChunkProcessingTime);
                totalChunkProcessingTime += firstChunkProcessingTime;
                
                File firstChunkFile = saveChunk(firstAudioData, 0);
                audioChunks.add(firstChunkFile);
                chunkTexts.add(firstChunk); // 청크 텍스트 저장
                
                // 첫 번째 청크 즉시 재생 시작 (오디오 길이 계산은 playFirstChunk 내부에서 수행)
                mainHandler.post(() -> {
                    if (!Thread.currentThread().isInterrupted()) {
                        playFirstChunk(firstChunkFile, firstChunk, firstChunkProcessingTime);
                        updateStatus("Playing first segment... (generating rest)");
                    }
                });
                
                // 나머지 청크들을 백그라운드에서 생성
                for (int i = 1; i < chunks.size(); i++) {
                    // Check if cancelled
                    if (Thread.currentThread().isInterrupted()) {
                        android.util.Log.d("MainActivity", "Generation interrupted at chunk " + i);
                        return;
                    }
                    
                    String chunk = chunks.get(i);
                    final int chunkIndex = i;
                    final int totalChunks = chunks.size();
                    mainHandler.post(() -> updateStatus("Generating segment " + (chunkIndex + 1) + "/" + totalChunks + "..."));
                    
                    long chunkStartTime = System.currentTimeMillis();
                    byte[] chunkAudioData = null;
                    try {
                        chunkAudioData = ttsEngine.generate(chunk, selectedVoice);
                    } catch (Exception e) {
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        throw e;
                    }
                    
                    // Check again if cancelled after generation
                    if (Thread.currentThread().isInterrupted()) {
                        android.util.Log.d("MainActivity", "Generation interrupted after chunk " + i + " generation");
                        return;
                    }
                    
                    if (chunkAudioData != null && chunkAudioData.length > 0) {
                        long chunkEndTime = System.currentTimeMillis();
                        double chunkProcessingTime = (chunkEndTime - chunkStartTime) / 1000.0;
                        chunkProcessingTimes.add(chunkProcessingTime);
                        totalChunkProcessingTime += chunkProcessingTime;
                        
                        File chunkFile = saveChunk(chunkAudioData, i);
                        audioChunks.add(chunkFile);
                        chunkTexts.add(chunk); // 청크 텍스트 저장
                        
                        // Estimate audio duration from chunk size (rough estimate)
                        // WAV file: 44 bytes header + audio data
                        // For 16-bit mono at 24kHz: duration = (dataSize - 44) / (24000 * 2)
                        long chunkSize = chunkFile.length();
                        double estimatedDuration = (chunkSize > 44) ? (chunkSize - 44) / (24000.0 * 2.0) : 0;
                        totalAudioDuration += estimatedDuration;
                        
                        // Calculate metrics for this chunk
                        double chunkCPS = chunk.length() > 0 && chunkProcessingTime > 0 ? chunk.length() / chunkProcessingTime : 0;
                        double chunkRTF = estimatedDuration > 0 ? chunkProcessingTime / estimatedDuration : 0;
                        chunkCPSList.add(chunkCPS);
                        chunkRTFList.add(chunkRTF);
                        
                        // Update log console
                        updateLogConsoleMetrics();
                        
                        // 현재 재생 중인 청크가 마지막이면 다음 청크 재생
                        mainHandler.post(() -> {
                            if (mediaPlayer != null && !mediaPlayer.isPlaying() && 
                                !isPaused && currentChunkIndex < audioChunks.size() - 1) {
                                playNextChunk();
                            }
                        });
                    }
                }
                
                mainHandler.post(() -> {
                    updateStatus("All segments generated");
                    isGenerating = false;
                    currentGenerationTask = null;
                });
            } catch (java.util.concurrent.CancellationException e) {
                // Generation was cancelled, ignore
                android.util.Log.d("MainActivity", "Generation cancelled");
            } catch (Exception e) {
                // Only show error if not cancelled
                if (!Thread.currentThread().isInterrupted()) {
                    mainHandler.post(() -> {
                        String errorMsg = e.getMessage();
                        if (errorMsg == null) {
                            errorMsg = e.getClass().getSimpleName();
                        }
                        String fullErrorMsg = "Error: " + errorMsg;
                        lastErrorMessage = fullErrorMsg + "\n\nFull error:\n" + getStackTrace(e);
                        updateStatus(fullErrorMsg, true);
                        Toast.makeText(MainActivity.this, 
                            "An error occurred. Click copy button to see full details.", 
                            Toast.LENGTH_LONG).show();
                        isGenerating = false;
                        currentGenerationTask = null;
                    });
                }
            }
        });
    }
    
    /**
     * Cancel current generation process and clean up
     */
    private void cancelCurrentGeneration() {
        if (currentGenerationTask != null && !currentGenerationTask.isDone()) {
            currentGenerationTask.cancel(true);
            android.util.Log.d("MainActivity", "Cancelling current generation");
        }
        
        // Stop audio playback
        stopAudio();
        
        // Clear audio chunks
        clearAudioChunks();
        audioChunks.clear();
        chunkTexts.clear();
        currentChunkIndex = 0;
        
        isGenerating = false;
        currentGenerationTask = null;
        
        updateStatus("Previous generation cancelled");
    }
    
    /**
     * Clear all audio chunk files
     */
    private void clearAudioChunks() {
        for (File chunkFile : audioChunks) {
            try {
                if (chunkFile != null && chunkFile.exists()) {
                    chunkFile.delete();
                }
            } catch (Exception e) {
                android.util.Log.w("MainActivity", "Failed to delete chunk file: " + chunkFile, e);
            }
        }
    }
    
    /**
     * Update metrics display
     */
    private void updateLogConsole(String text, boolean clear) {
        if (metricsText == null) return;
        
        if (clear) {
            metricsText.setText("");
        } else if (text != null && !text.isEmpty()) {
            metricsText.setText(text);
        }
    }
    
    /**
     * Update metrics display (3-column format)
     */
    private void updateLogConsoleMetrics() {
        if (metricsText == null || chunkProcessingTimes.isEmpty()) return;
        
        // 1단: Processing Time
        double currentChunkTime = chunkProcessingTimes.get(chunkProcessingTimes.size() - 1);
        String totalTimeStr = formatTimeLong(totalChunkProcessingTime);
        String processingTimeStr = String.format("Proc. time: %.2fs / %s", currentChunkTime, totalTimeStr);
        
        // 2단: Characters per sec. (평균값)
        double avgCPS = 0;
        if (!chunkCPSList.isEmpty()) {
            double sumCPS = 0;
            for (Double cps : chunkCPSList) {
                sumCPS += cps;
            }
            avgCPS = sumCPS / chunkCPSList.size();
        }
        String cpsStr = String.format("chars. per sec.: %.0f", avgCPS);
        
        // 3단: RTF (평균값)
        double avgRTF = 0;
        if (!chunkRTFList.isEmpty()) {
            double sumRTF = 0;
            for (Double rtf : chunkRTFList) {
                sumRTF += rtf;
            }
            avgRTF = sumRTF / chunkRTFList.size();
        }
        String rtfStr = String.format("RTF: %.3fx", avgRTF);
        
        // 3단 구조로 표시 (각 1/3씩)
        String logText = String.format("%s | %s | %s", processingTimeStr, cpsStr, rtfStr);
        metricsText.setText(logText);
    }
    
    /**
     * 시간 포맷팅 (초 -> MM:SS.SS)
     */
    private String formatTimeLong(double seconds) {
        int totalSeconds = (int) seconds;
        int minutes = totalSeconds / 60;
        int secs = totalSeconds % 60;
        // 소수점 두 자리 (밀리초)
        int centiseconds = (int) ((seconds - totalSeconds) * 100);
        return String.format("%02d:%02d.%02d", minutes, secs, centiseconds);
    }
    
    /**
     * 텍스트를 청크로 분할
     * splitIntoSentences()를 사용하여 문장을 분리하고, 10글자 이하는 앞 문장에 합침
     * 약어를 위한 마침표는 청크 분리에서 제외 (splitIntoSentences에서 처리)
     */
    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }
        
        // splitIntoSentences()를 사용하여 문장 분리 (동일한 로직 사용)
        List<String> sentences = splitIntoSentences(text);
        
        // 문장들을 청크로 합치기 (10글자 이하는 합침)
        StringBuilder currentChunk = new StringBuilder();
        
        for (String sentence : sentences) {
            int sentenceLength = sentence.trim().length();
            
            // 10글자 이하인 경우 현재 청크에 합침
            if (sentenceLength <= 10) {
                if (currentChunk.length() > 0) {
                    currentChunk.append(" ");
                }
                currentChunk.append(sentence);
            } else {
                // 10글자 초과인 경우
                // 현재 청크가 있으면 먼저 저장
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
                // 새 문장을 개별 청크로 추가
                chunks.add(sentence);
            }
        }
        
        // 마지막 청크 추가
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        // 청크가 없으면 전체 텍스트를 하나의 청크로
        if (chunks.isEmpty()) {
            chunks.add(text.trim());
        }
        
        return chunks;
    }
    
    /**
     * 청크를 문장에 매핑 (정확한 순서 기반 매핑)
     * chunkText()가 10글자 이하 문장을 합치므로, 하나의 청크에 여러 문장이 포함될 수 있음
     * 각 청크에 포함된 연속된 문장들을 순서대로 찾아 매핑
     */
    private void mapChunksToSentences(List<String> chunks) {
        mapChunksToSentencesFromIndex(chunks, 0);
    }
    
    /**
     * 청크를 문장에 매핑 (특정 문장 인덱스부터 시작)
     */
    private void mapChunksToSentencesFromIndex(List<String> chunks, int startSentenceIndex) {
        // sentenceToChunkMap이 충분히 크지 않으면 확장
        while (sentenceToChunkMap.size() < sentences.size()) {
            sentenceToChunkMap.add(-1);
        }
        
        // 각 청크를 순서대로 처리
        int sentenceIndex = startSentenceIndex;
        for (int chunkIndex = 0; chunkIndex < chunks.size() && sentenceIndex < sentences.size(); chunkIndex++) {
            String chunk = chunks.get(chunkIndex).trim();
            String remainingChunk = chunk;
            
            // 이 청크에 포함된 연속된 문장들을 찾아 매핑
            while (sentenceIndex < sentences.size() && remainingChunk.length() > 0) {
                String sentence = sentences.get(sentenceIndex).trim();
                
                // 청크의 현재 위치에서 이 문장을 찾기
                int pos = remainingChunk.indexOf(sentence);
                if (pos >= 0) {
                    // 이 문장을 이 청크에 매핑
                    sentenceToChunkMap.set(sentenceIndex, chunkIndex);
                    sentenceIndex++;
                    
                    // 청크의 나머지 부분 업데이트 (이 문장 이후 부분)
                    remainingChunk = remainingChunk.substring(pos + sentence.length()).trim();
                } else {
                    // 이 청크에 더 이상 문장이 없으면 다음 청크로
                    break;
                }
            }
        }
    }
    
    /**
     * 청크를 파일로 저장
     */
    private File saveChunk(byte[] audioData, int index) throws IOException {
        File chunkFile = new File(getCacheDir(), "tts_chunk_" + index + ".wav");
        FileOutputStream fos = new FileOutputStream(chunkFile);
        fos.write(audioData);
        fos.close();
        return chunkFile;
    }
    
    /**
     * 첫 번째 청크 재생
     */
    private void playFirstChunk(File firstChunkFile) {
        playFirstChunk(firstChunkFile, null, 0);
    }
    
    /**
     * 첫 번째 청크 재생 (메트릭스 계산 포함)
     */
    private void playFirstChunk(File firstChunkFile, String chunkText, double processingTime) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            
            // 파일 존재 확인
            if (!firstChunkFile.exists()) {
                android.util.Log.e("MainActivity", "Audio file does not exist: " + firstChunkFile.getAbsolutePath());
                updateStatus("Audio file not found");
                return;
            }
            
            // 파일 크기 확인
            long fileSize = firstChunkFile.length();
            if (fileSize == 0) {
                android.util.Log.e("MainActivity", "Audio file is empty: " + firstChunkFile.getAbsolutePath());
                updateStatus("Audio file is empty");
                return;
            }
            
            android.util.Log.d("MainActivity", "Playing first chunk: " + firstChunkFile.getAbsolutePath() + ", size: " + fileSize);
            
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(firstChunkFile.getAbsolutePath());
            mediaPlayer.prepare();
            currentChunkIndex = 0;
            
            // 오디오 길이 계산 및 메트릭스 업데이트
            if (chunkText != null && processingTime > 0) {
                double firstChunkAudioDuration = 0;
                int duration = mediaPlayer.getDuration();
                if (duration > 0) {
                    firstChunkAudioDuration = duration / 1000.0;
                    totalAudioDuration += firstChunkAudioDuration;
                } else {
                    // Estimate from file size
                    firstChunkAudioDuration = (fileSize > 44) ? (fileSize - 44) / (24000.0 * 2.0) : 0;
                    totalAudioDuration += firstChunkAudioDuration;
                }
                
                double firstChunkCPS = chunkText.length() > 0 && processingTime > 0 ? chunkText.length() / processingTime : 0;
                double firstChunkRTF = firstChunkAudioDuration > 0 ? processingTime / firstChunkAudioDuration : 0;
                chunkCPSList.add(firstChunkCPS);
                chunkRTFList.add(firstChunkRTF);
                
                // Update log console
                updateLogConsoleMetrics();
            }
            
            mediaPlayer.setOnCompletionListener(mp -> {
                if (!isPaused && currentChunkIndex < audioChunks.size() - 1) {
                    playNextChunk();
                } else if (currentChunkIndex >= audioChunks.size() - 1) {
                    // 모든 청크 재생 완료
                    updateStatus("Playback complete");
                    playButton.setEnabled(true);
                    playButton.setImageResource(R.drawable.ic_play);
                    currentChunkIndex = 0;
                    isPaused = false;
                }
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                android.util.Log.e("MainActivity", "MediaPlayer error: what=" + what + ", extra=" + extra);
                updateStatus("Playback error: " + what);
                return true;
            });
            
            mediaPlayer.start();
            startProgressUpdate();
            playButton.setEnabled(true);
            playButton.setImageResource(R.drawable.ic_pause);
            isPaused = false;
            updateStatus("Playing");
            
            android.util.Log.d("MainActivity", "First chunk playback started");
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error playing first chunk", e);
            updateStatus("Playback error: " + e.getMessage());
            playButton.setEnabled(true);
        }
    }
    
    /**
     * 다음 청크 재생
     */
    private void playNextChunk() {
        if (currentChunkIndex >= audioChunks.size() - 1) {
            return;
        }
        
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            
            currentChunkIndex++;
            File nextChunkFile = audioChunks.get(currentChunkIndex);
            
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(nextChunkFile.getAbsolutePath());
            mediaPlayer.prepare();
            
            mediaPlayer.setOnCompletionListener(mp -> {
                if (!isPaused && currentChunkIndex < audioChunks.size() - 1) {
                    playNextChunk();
                } else if (currentChunkIndex >= audioChunks.size() - 1) {
                    // 모든 청크 재생 완료
                    updateStatus("Playback complete");
                    playButton.setEnabled(true);
                    playButton.setImageResource(R.drawable.ic_play);
                    currentChunkIndex = 0;
                    isPaused = false;
                }
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                android.util.Log.e("MainActivity", "MediaPlayer error: what=" + what + ", extra=" + extra);
                updateStatus("Playback error: " + what);
                return true;
            });
            
            mediaPlayer.start();
            startProgressUpdate();
            playButton.setImageResource(R.drawable.ic_pause);
            isPaused = false;
            updateStatus("Playing segment " + (currentChunkIndex + 1) + "/" + audioChunks.size());
            
            // Update total audio duration
            if (mediaPlayer.getDuration() > 0) {
                totalAudioDuration += mediaPlayer.getDuration() / 1000.0;
            }
            
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error playing next chunk", e);
            updateStatus("Next segment playback error: " + e.getMessage());
        }
    }
    
    /**
     * 재생 시간 업데이트 시작
     */
    private void startProgressUpdate() {
        progressHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null) {
                    try {
                        int current = mediaPlayer.getCurrentPosition();
                        // 현재 재생 중인 청크의 현재 위치 + 이전 청크들의 총 시간
                        int currentChunkPosition = current;
                        for (int i = 0; i < currentChunkIndex; i++) {
                            if (i < audioChunks.size()) {
                                // 이전 청크들의 재생 시간 합산 (추정)
                                File chunkFile = audioChunks.get(i);
                                if (chunkFile != null && chunkFile.exists()) {
                                    long chunkSize = chunkFile.length();
                                    if (chunkSize > 44) {
                                        double chunkDuration = (chunkSize - 44) / (24000.0 * 2.0);
                                        currentChunkPosition += (int)(chunkDuration * 1000);
                                    }
                                }
                            }
                        }
                        
                        // 전체 재생 시간: 모든 청크의 총합
                        int totalDuration = (int)(totalAudioDuration * 1000);
                        
                        if (timeDisplay != null) {
                            timeDisplay.setText(formatTime(currentChunkPosition) + " / " + formatTime(totalDuration));
                        }
                    } catch (Exception e) {
                        // 무시
                    }
                } else if (timeDisplay != null) {
                    // MediaPlayer가 없으면 00:00 / 전체 시간 표시
                    int totalDuration = (int)(totalAudioDuration * 1000);
                    timeDisplay.setText("00:00 / " + formatTime(totalDuration));
                }
                
                progressHandler.postDelayed(this, 100); // 100ms마다 업데이트
            }
        });
    }
    
    /**
     * 시간 포맷팅 (밀리초 -> MM:SS)
     */
    private String formatTime(int milliseconds) {
        int seconds = milliseconds / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    /**
     * 진행 바 클릭 처리
     */
    private void seekTo(int progress) {
        if (mediaPlayer != null && mediaPlayer.getDuration() > 0) {
            mediaPlayer.seekTo(progress);
        }
    }
    
    private void generateAudioOld() {
        String text = fullText;
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter text", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // selectedVoice is already set by button click
        generateButton.setEnabled(false);
        updateStatus("Generating audio...");
        
        executorService.execute(() -> {
            try {
                byte[] audioData = ttsEngine.generate(text, selectedVoice);
                
                mainHandler.post(() -> {
                    if (audioData != null && audioData.length > 0) {
                        saveAndPlayAudio(audioData);
                        updateStatus("Audio generation complete");
                    } else {
                        updateStatus("Audio generation failed");
                        generateButton.setEnabled(true);
                    }
                });
            } catch (IllegalStateException e) {
                // 모델 파일 없음 - 사용자 친화적 메시지
                mainHandler.post(() -> {
                    String errorMsg = e.getMessage();
                    if (errorMsg == null) {
                        errorMsg = "TTS model files required";
                    }
                    lastErrorMessage = "Error: " + errorMsg + "\n\nFull error:\n" + getStackTrace(e);
                    updateStatus(errorMsg, true);
                    Toast.makeText(MainActivity.this, 
                        "Please download Supertonic model files.\n" +
                        "Use the model download feature in the app.", 
                        Toast.LENGTH_LONG).show();
                    generateButton.setEnabled(true);
                });
            } catch (UnsupportedOperationException e) {
                // 더미 구현 - 사용자 친화적 메시지
                mainHandler.post(() -> {
                    String errorMsg = e.getMessage();
                    if (errorMsg == null) {
                        errorMsg = "TTS feature implementation required";
                    }
                    lastErrorMessage = "Error: " + errorMsg + "\n\nFull error:\n" + getStackTrace(e);
                    updateStatus(errorMsg, true);
                    Toast.makeText(MainActivity.this, 
                        "Supertonic Java implementation required.\n" +
                        "See: https://github.com/supertone-inc/supertonic/tree/main/java", 
                        Toast.LENGTH_LONG).show();
                    generateButton.setEnabled(true);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    String errorMsg = e.getMessage();
                    if (errorMsg == null) {
                        errorMsg = e.getClass().getSimpleName();
                    }
                    String fullErrorMsg = "Error: " + errorMsg;
                    lastErrorMessage = fullErrorMsg + "\n\nFull error:\n" + getStackTrace(e);
                    updateStatus(fullErrorMsg, true);
                    Toast.makeText(MainActivity.this, 
                        "An error occurred. Click copy button to see full details.", 
                        Toast.LENGTH_LONG).show();
                    generateButton.setEnabled(true);
                });
            }
        });
    }
    
    private void saveAndPlayAudio(byte[] audioData) {
        try {
            File audioFile = new File(getCacheDir(), "tts_output.wav");
            FileOutputStream fos = new FileOutputStream(audioFile);
            fos.write(audioData);
            fos.close();
            
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mediaPlayer.prepare();
            
            playButton.setEnabled(true);
            playButton.setImageResource(R.drawable.ic_play);
            generateButton.setEnabled(true);
            
            mediaPlayer.setOnCompletionListener(mp -> {
                updateStatus("Playback complete");
                playButton.setEnabled(true);
                playButton.setImageResource(R.drawable.ic_play);
            });
            
        } catch (IOException e) {
            updateStatus("Audio file save failed: " + e.getMessage());
            generateButton.setEnabled(true);
        }
    }
    
    private void playAudio() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            try {
                // 현재 청크가 끝났다면 다음 청크로
                if (currentChunkIndex < audioChunks.size() - 1 && 
                    mediaPlayer.getCurrentPosition() >= mediaPlayer.getDuration() - 100) {
                    playNextChunk();
                } else {
                    mediaPlayer.start();
                    isPaused = false;
                    updateStatus("Playing");
                    playButton.setEnabled(true);
                    playButton.setImageResource(R.drawable.ic_pause);
                }
            } catch (Exception e) {
                updateStatus("Playback error: " + e.getMessage());
            }
        } else if (audioChunks.size() > 0 && currentChunkIndex < audioChunks.size()) {
            // 처음 재생 시작
            isPaused = false;
            playFirstChunk(audioChunks.get(0));
        }
    }
    
    private void pauseAudio() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            // 현재 재생 중인 청크 번호 저장
            // currentChunkIndex는 이미 현재 재생 중인 청크를 나타내므로 명시적으로 보존
            // (pause 시점에 currentChunkIndex가 올바르게 유지되도록 보장)
            if (currentChunkIndex >= 0 && currentChunkIndex < audioChunks.size()) {
                // 현재 청크 번호가 유효하면 그대로 유지
                android.util.Log.d("MainActivity", "Pause: saving current chunk index: " + currentChunkIndex);
            }
            
            mediaPlayer.pause();
            isPaused = true;
            updateStatus("Paused");
            playButton.setEnabled(true);
            playButton.setImageResource(R.drawable.ic_play);
        }
    }
    
    private void stopAudio() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.prepare();
            } catch (Exception e) {
                // 무시
            }
            currentChunkIndex = 0;
            isPaused = false;
            updateStatus("Stopped");
            playButton.setEnabled(true);
            playButton.setImageResource(R.drawable.ic_play);
        }
    }
    
    private void updateStatus(String status) {
        updateStatus(status, false);
    }
    
    private void updateStatus(String status, boolean isError) {
        // '...' 제거
        String cleanedStatus = status.replace("...", "");
        statusText.setText(cleanedStatus);
        if (isError) {
            copyErrorButton.setVisibility(View.VISIBLE);
        } else {
            copyErrorButton.setVisibility(View.GONE);
            lastErrorMessage = "";
        }
    }
    
    private void copyErrorMessageToClipboard() {
        if (lastErrorMessage.isEmpty()) {
            Toast.makeText(this, "No error message to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Error Message", lastErrorMessage);
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(this, "Error message copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * 약어를 고려하여 텍스트를 문장으로 분할 (chunkText와 동일한 로직)
     */
    private List<String> splitIntoSentences(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return result;
        }
        
        // 약어 리스트 (chunkText와 동일)
        String[] abbreviations = {
            "Mr.", "Mrs.", "Ms.", "Dr.", "Prof.", "Sr.", "Jr.", "Rev.", "Hon.", "Fr.", "St.", 
            "Gov.", "Pres.", "Mgr.", "Supt.", "Rep.", "Sen.", "Dept.", "Assoc.", "Univ.", 
            "Co.", "Corp.", "Inc.", "Ltd.", "Ave.", "Blvd.", "Rd.", "Ln.", 
            "Ct.", "Hwy.", "Mt.", "Ft.", "B.A.", "B.S.", "M.A.", "M.S.", "Ph.D.", "Ed.D.", 
            "D.D.S.", "M.D.", "LL.D.", "D.Phil.", "in.", "ft.", "gal.", "qt.", "pt.", 
            "lb.", "oz.", "yd.", "sec.", "min.", "hr.", "a.m.", "p.m.", "U.S.", "U.K.", 
            "U.A.E.", "E.U.", "B.C.", "A.D.", "etc.", "e.g.", "i.e.", "vs.", "approx.", 
            "misc.", "fig.", "vol.", "no.", "al.", "yr.", "mo.", "ed.", "trans.", "cf.", 
            "chap.", "pp."
        };
        
        // 약어 패턴 생성
        StringBuilder abbrevPattern = new StringBuilder();
        for (int i = 0; i < abbreviations.length; i++) {
            if (i > 0) abbrevPattern.append("|");
            abbrevPattern.append("(?i)\\b").append(Pattern.quote(abbreviations[i]));
        }
        if (abbrevPattern.length() > 0) {
            abbrevPattern.append("|");
        }
        abbrevPattern.append("\\b[A-Z][A-Z]?\\.");
        String abbrevRegex = abbrevPattern.toString();
        
        // 줄바꿈으로 먼저 분할
        String[] lines = text.split("\\n", -1);
        
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }
            
            // 약어 뒤의 마침표를 임시로 치환하여 보호
            String protectedLine = trimmedLine;
            int markerIndex = 0;
            java.util.regex.Matcher abbrevMatcher = Pattern.compile(abbrevRegex).matcher(protectedLine);
            StringBuffer sb = new StringBuffer();
            
            while (abbrevMatcher.find()) {
                abbrevMatcher.appendReplacement(sb, "ABBREV_MARKER_" + markerIndex);
                markerIndex++;
            }
            abbrevMatcher.appendTail(sb);
            protectedLine = sb.toString();
            
            // 약어 마커와 원래 약어 매핑 저장
            java.util.Map<String, String> markerMap = new java.util.HashMap<>();
            abbrevMatcher = Pattern.compile(abbrevRegex).matcher(trimmedLine);
            markerIndex = 0;
            while (abbrevMatcher.find()) {
                String abbrev = abbrevMatcher.group();
                markerMap.put("ABBREV_MARKER_" + markerIndex, abbrev);
                markerIndex++;
            }
            
            // 문장 끝 구분자로 분할
            String[] tempSentences = protectedLine.split("(?<=\\.{2,10})\\s+|(?<=[.!?])(?!\\.)\\s+");
            
            for (String sentence : tempSentences) {
                // 임시 마커를 원래 약어로 복원
                sentence = sentence.trim();
                for (java.util.Map.Entry<String, String> entry : markerMap.entrySet()) {
                    sentence = sentence.replace(entry.getKey(), entry.getValue());
                }
                
                if (!sentence.isEmpty()) {
                    result.add(sentence);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 텍스트를 페이지로 분할
     */
    private void setupPages() {
        pages.clear();
        sentences.clear();
        sentenceToChunkMap.clear();
        pageToSentencesMap.clear();
        
        if (fullText.isEmpty()) {
            textDisplay.setText("");
            updatePageIndicator();
            return;
        }
        
        // 약어를 고려하여 텍스트를 문장으로 분할
        sentences = splitIntoSentences(fullText);
        
        // 페이지 크기 계산 - 현재 렌더링 창 크기를 기반으로 높이 고정
        textDisplayContainer.post(() -> {
            if (textDisplay == null) return;
            
            // TextView의 실제 padding 가져오기
            int textViewPaddingLeft = textDisplay.getPaddingLeft();
            int textViewPaddingRight = textDisplay.getPaddingRight();
            int textViewPaddingTop = textDisplay.getPaddingTop();
            int textViewPaddingBottom = textDisplay.getPaddingBottom();
            
            // TextView의 실제 너비와 높이 가져오기
            int textViewWidth = textDisplay.getWidth();
            int textViewHeight = textDisplay.getHeight();
            
            // StaticLayout에 사용할 실제 사용 가능한 너비 (TextView 너비 - 좌우 padding)
            pageWidth = textViewWidth - textViewPaddingLeft - textViewPaddingRight;
            pageHeight = textViewHeight - textViewPaddingTop - textViewPaddingBottom;
            
            // 텍스트뷰 높이를 현재 컨테이너 높이로 고정
            if (pageHeight > 0) {
                android.view.ViewGroup.LayoutParams params = textDisplay.getLayoutParams();
                if (params != null) {
                    params.height = textViewHeight;
                    textDisplay.setLayoutParams(params);
                }
            }
            
            if (pageWidth > 0 && pageHeight > 0) {
                calculatePages();
                displayCurrentPage();
                updatePageIndicator();
            }
        });
    }
    
    /**
     * 페이지 계산 - StaticLayout을 사용하여 정확히 15줄 기준으로 페이지 분리
     * TextView의 실제 설정을 사용하여 StaticLayout 생성
     */
    private void calculatePages() {
        pages.clear();
        pageToSentencesMap.clear();
        
        if (fullText.isEmpty() || pageWidth <= 0 || textDisplay == null) {
            if (!fullText.isEmpty()) {
                pages.add(fullText);
                pageToSentencesMap.add(new ArrayList<>());
            }
            return;
        }
        
        // TextView의 실제 Paint 설정 사용 (textSize, typeface 등이 정확히 일치)
        TextPaint textPaintForLayout = new TextPaint(textDisplay.getPaint());
        
        // TextView의 lineSpacingMultiplier 가져오기
        float lineSpacingMultiplier = textDisplay.getLineSpacingMultiplier();
        float lineSpacingExtra = textDisplay.getLineSpacingExtra();
        
        // 전체 텍스트를 StaticLayout으로 렌더링 (TextView와 동일한 설정)
        // includePadding은 false로 설정 (StaticLayout 자체 padding 없음)
        StaticLayout fullLayout = new StaticLayout(
            fullText,
            textPaintForLayout,
            (int) pageWidth,
            Layout.Alignment.ALIGN_NORMAL,
            lineSpacingMultiplier,
            lineSpacingExtra,
            false // includePadding
        );
        
        int totalLines = fullLayout.getLineCount();
        int currentLine = 0;
        
        // 15줄씩 페이지 분할
        while (currentLine < totalLines) {
            // 현재 페이지의 마지막 줄 (15줄째, 인덱스는 14)
            int pageEndLine = Math.min(currentLine + MAX_LINES_PER_PAGE - 1, totalLines - 1);
            
            // 해당 줄의 시작과 끝 위치 찾기
            int textStart = fullLayout.getLineStart(currentLine);
            int textEnd = fullLayout.getLineEnd(pageEndLine);
            
            // 페이지 텍스트 추출
            String pageText = fullText.substring(textStart, textEnd);
            
            // 추출한 페이지 텍스트를 다시 StaticLayout으로 렌더링하여 정확히 15줄인지 확인
            StaticLayout pageLayout = new StaticLayout(
                pageText,
                textPaintForLayout,
                (int) pageWidth,
                Layout.Alignment.ALIGN_NORMAL,
                lineSpacingMultiplier,
                lineSpacingExtra,
                false
            );
            
            // 정확히 15줄이 되도록 조정
            int pageLineCount = pageLayout.getLineCount();
            if (pageLineCount != MAX_LINES_PER_PAGE && pageEndLine < totalLines - 1) {
                // 15줄이 아니면 조정
                if (pageLineCount > MAX_LINES_PER_PAGE) {
                    // 15줄을 넘으면 이전 줄의 끝으로 조정
                    if (pageEndLine > currentLine) {
                        pageEndLine = pageEndLine - 1;
                        textEnd = fullLayout.getLineEnd(pageEndLine);
                        pageText = fullText.substring(textStart, textEnd);
                    }
                } else if (pageLineCount < MAX_LINES_PER_PAGE && pageEndLine < totalLines - 1) {
                    // 15줄보다 적으면 다음 줄까지 포함 시도
                    int nextLineEnd = Math.min(pageEndLine + 1, totalLines - 1);
                    int nextTextEnd = fullLayout.getLineEnd(nextLineEnd);
                    String nextPageText = fullText.substring(textStart, nextTextEnd);
                    StaticLayout nextPageLayout = new StaticLayout(
                        nextPageText,
                        textPaintForLayout,
                        (int) pageWidth,
                        Layout.Alignment.ALIGN_NORMAL,
                        lineSpacingMultiplier,
                        lineSpacingExtra,
                        false
                    );
                    if (nextPageLayout.getLineCount() <= MAX_LINES_PER_PAGE) {
                        pageEndLine = nextLineEnd;
                        textEnd = nextTextEnd;
                        pageText = nextPageText;
                    }
                }
            }
            
            pages.add(pageText);
            pageToSentencesMap.add(new ArrayList<>());
            
            // 다음 페이지 시작 위치
            currentLine = pageEndLine + 1;
        }
        
        if (pages.isEmpty()) {
            pages.add(fullText);
            pageToSentencesMap.add(new ArrayList<>());
        }
    }
    
    /**
     * 현재 페이지 표시 (원문 sentences 리스트와 동기화)
     * 페이지를 넘나드는 문장도 올바르게 처리
     */
    private void displayCurrentPage() {
        // 페이지 이동 시 기존 하이라이트 제거 Runnable 취소 및 즉시 하이라이트 제거
        if (highlightRemoveRunnable != null) {
            mainHandler.removeCallbacks(highlightRemoveRunnable);
            highlightRemoveRunnable = null;
        }
        // 즉시 하이라이트 제거 (텍스트를 다시 설정)
        if (textDisplay != null && textDisplay.getText() != null) {
            CharSequence currentText = textDisplay.getText();
            textDisplay.setText(currentText);
        }
        
        if (pages.isEmpty() || currentPageIndex < 0 || currentPageIndex >= pages.size()) {
            textDisplay.setText("");
            return;
        }
        
        String pageText = pages.get(currentPageIndex);
        
        // StaticLayout으로 계산한 텍스트를 그대로 사용 (줄바꿈 제거하지 않음)
        // 클릭 가능한 텍스트 생성 (스타일 없는 ClickableSpan)
        SpannableString spannable = new SpannableString(pageText);
        
        // pageToSentencesMap을 사용하여 이 페이지에 나타나는 문장들 찾기
        if (currentPageIndex < pageToSentencesMap.size()) {
            List<Integer> pageSentences = pageToSentencesMap.get(currentPageIndex);
            
            for (Integer sentenceIndex : pageSentences) {
                if (sentenceIndex < 0 || sentenceIndex >= sentences.size()) {
                    continue;
                }
                
                String sentence = sentences.get(sentenceIndex);
                final String sentenceText = sentence.trim();
                if (sentenceText.isEmpty()) {
                    continue;
                }
                
                // 페이지 텍스트에서 문장 위치 찾기
                int sentenceStartInPage = pageText.indexOf(sentenceText);
                if (sentenceStartInPage >= 0) {
                    int sentenceEndInPage = sentenceStartInPage + sentenceText.length();
                    
                    if (sentenceEndInPage > spannable.length()) {
                        sentenceEndInPage = spannable.length();
                    }
                    
                    if (sentenceStartInPage < sentenceEndInPage && sentenceStartInPage >= 0) {
                        // 전체 문장을 저장하여 클릭 시 전체 문장 재생
                        final String fullSentence = sentenceText;
                        spannable.setSpan(new ClickableSpan() {
                            @Override
                            public void onClick(View widget) {
                                handleSentenceClick(fullSentence);
                            }
                            
                            @Override
                            public void updateDrawState(TextPaint ds) {
                                super.updateDrawState(ds);
                                // 하이퍼링크 스타일 제거 (기본 색상, 언더라인 없음)
                                ds.setUnderlineText(false);
                                ds.setColor(textDisplay.getCurrentTextColor());
                            }
                        }, sentenceStartInPage, sentenceEndInPage, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }
        
        // pageToSentencesMap이 없거나 비어있는 경우 기존 방식으로 fallback
        if (currentPageIndex >= pageToSentencesMap.size() || 
            (currentPageIndex < pageToSentencesMap.size() && pageToSentencesMap.get(currentPageIndex).isEmpty())) {
            for (String sentence : sentences) {
                final String sentenceText = sentence.trim();
                if (sentenceText.isEmpty()) {
                    continue;
                }
                
                int sentenceStartInOriginal = pageText.indexOf(sentenceText);
                if (sentenceStartInOriginal >= 0) {
                    int sentenceEnd = sentenceStartInOriginal + sentenceText.length();
                    if (sentenceEnd > spannable.length()) {
                        sentenceEnd = spannable.length();
                    }
                    
                    if (sentenceStartInOriginal < sentenceEnd && sentenceStartInOriginal >= 0) {
                        final String fullSentence = sentenceText;
                        spannable.setSpan(new ClickableSpan() {
                            @Override
                            public void onClick(View widget) {
                                handleSentenceClick(fullSentence);
                            }
                            
                            @Override
                            public void updateDrawState(TextPaint ds) {
                                super.updateDrawState(ds);
                                ds.setUnderlineText(false);
                                ds.setColor(textDisplay.getCurrentTextColor());
                            }
                        }, sentenceStartInOriginal, sentenceEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }
        
        textDisplay.setText(spannable);
        // 커스텀 MovementMethod: 클릭만 허용하고 스크롤/키 이벤트 완전히 차단
        textDisplay.setMovementMethod(new LinkMovementMethod() {
            @Override
            public boolean canSelectArbitrarily() {
                return false;
            }
            
            @Override
            public boolean onTouchEvent(android.widget.TextView widget, android.text.Spannable buffer, android.view.MotionEvent event) {
                // 스크롤을 완전히 차단하고 클릭만 허용
                if (event.getAction() == MotionEvent.ACTION_DOWN || 
                    event.getAction() == MotionEvent.ACTION_UP) {
                    return super.onTouchEvent(widget, buffer, event);
                }
                // MOVE 이벤트는 무시하여 스크롤 방지
                return false;
            }
            
            @Override
            public boolean onKeyDown(android.widget.TextView widget, android.text.Spannable buffer, int keyCode, android.view.KeyEvent event) {
                // 위/아래/좌/우 화살표 키 무시
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return true; // 이벤트 소비하여 처리하지 않음
                }
                return super.onKeyDown(widget, buffer, keyCode, event);
            }
            
            @Override
            public boolean onKeyOther(android.widget.TextView widget, android.text.Spannable buffer, android.view.KeyEvent event) {
                // 위/아래/좌/우 화살표 키 무시
                int keyCode = event.getKeyCode();
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return true; // 이벤트 소비하여 처리하지 않음
                }
                return super.onKeyOther(widget, buffer, event);
            }
        });
    }
    
    /**
     * 문장 클릭 처리 (정확한 문장 매칭)
     */
    private void handleSentenceClick(String sentence) {
        // 현재 재생 중인 청크 번호 저장 (문장 클릭 전에)
        int previousChunkIndex = currentChunkIndex;
        if (previousChunkIndex >= 0 && previousChunkIndex < audioChunks.size()) {
            android.util.Log.d("MainActivity", "Sentence click: previous chunk index: " + previousChunkIndex);
        }
        
        // 정확한 문장 매칭 (trim 후 비교)
        String clickedSentence = sentence.trim();
        int targetChunkIndex = -1;
        int matchedSentenceIndex = -1;
        
        // 정확히 일치하는 문장 찾기
        for (int i = 0; i < sentences.size(); i++) {
            String s = sentences.get(i).trim();
            if (s.equals(clickedSentence)) {
                matchedSentenceIndex = i;
                // 해당 문장의 청크 인덱스 찾기
                if (i < sentenceToChunkMap.size()) {
                    targetChunkIndex = sentenceToChunkMap.get(i);
                }
                break;
            }
        }
        
        // 정확히 일치하지 않으면 포함 관계로 찾기 (fallback)
        if (matchedSentenceIndex < 0) {
            for (int i = 0; i < sentences.size(); i++) {
                String s = sentences.get(i).trim();
                if (s.contains(clickedSentence) || clickedSentence.contains(s)) {
                    matchedSentenceIndex = i;
                    if (i < sentenceToChunkMap.size()) {
                        targetChunkIndex = sentenceToChunkMap.get(i);
                    }
                    break;
                }
            }
        }
        
        if (targetChunkIndex >= 0 && targetChunkIndex < audioChunks.size()) {
            // 해당 청크부터 재생
            currentChunkIndex = targetChunkIndex;
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            playChunkAtIndex(targetChunkIndex);
        } else {
            // 해당 문장부터 생성 및 재생
            cancelCurrentGeneration();
            generateFromSentence(clickedSentence);
        }
        
        // 기존 하이라이트 제거 Runnable 취소
        if (highlightRemoveRunnable != null) {
            mainHandler.removeCallbacks(highlightRemoveRunnable);
        }
        
        // 0.5초 후 하이라이트 제거 (텍스트를 다시 설정하여 하이라이트 제거)
        highlightRemoveRunnable = () -> {
            if (textDisplay != null) {
                // 텍스트를 다시 설정하여 하이라이트 제거
                CharSequence currentText = textDisplay.getText();
                textDisplay.setText(currentText);
            }
            highlightRemoveRunnable = null;
        };
        mainHandler.postDelayed(highlightRemoveRunnable, 500);
    }
    
    /**
     * 특정 문장부터 생성 및 재생
     */
    private void generateFromSentence(String targetSentence) {
        // 문장 위치 찾기
        int sentenceIndex = -1;
        for (int i = 0; i < sentences.size(); i++) {
            if (sentences.get(i).contains(targetSentence) || targetSentence.contains(sentences.get(i))) {
                sentenceIndex = i;
                break;
            }
        }
        
        if (sentenceIndex < 0) {
            return;
        }
        
        // 해당 문장부터의 텍스트 생성
        StringBuilder textFromSentence = new StringBuilder();
        for (int i = sentenceIndex; i < sentences.size(); i++) {
            if (textFromSentence.length() > 0) {
                textFromSentence.append(" ");
            }
            textFromSentence.append(sentences.get(i));
        }
        
        // 기존 재생/생성 중지
        cancelCurrentGeneration();
        stopAudio();
        // 기존 생성한 음원 파일은 유지하고, 재생리스트만 초기화
        audioChunks.clear();
        chunkTexts.clear();
        currentChunkIndex = 0;
        
        // 생성 시작
        isGenerating = true;
        generationStartTime = System.currentTimeMillis();
        totalTextLength = textFromSentence.length();
        totalAudioDuration = 0;
        chunkProcessingTimes.clear();
        chunkCPSList.clear();
        chunkRTFList.clear();
        totalChunkProcessingTime = 0;
        updateLogConsole("", true);
        
        // 문장-청크 매핑 초기화 (해당 문장부터)
        for (int i = sentenceIndex; i < sentences.size(); i++) {
            if (i < sentenceToChunkMap.size()) {
                sentenceToChunkMap.set(i, -1);
            }
        }
        
        // 람다 표현식에서 사용하기 위해 final 변수로 복사
        final int startSentenceIndex = sentenceIndex;
        
        // 즉시 상태 업데이트 (백그라운드 스레드 시작 전)
        updateStatus("Generating first segment...");
        // 첫 번째 청크 생성 중일 때 gen 아이콘 표시
        playButton.setImageResource(R.drawable.ic_gen);
        
        currentGenerationTask = executorService.submit(() -> {
            try {
                List<String> chunks = chunkText(textFromSentence.toString());
                mainHandler.post(() -> updateStatus("Generating " + chunks.size() + " segments..."));
                
                if (chunks.isEmpty()) {
                    throw new Exception("Chunk generation failed");
                }
                
                // 첫 번째 청크 생성 및 즉시 재생
                String firstChunk = chunks.get(0);
                // 상태는 이미 업데이트되었으므로 여기서는 Play 버튼만 업데이트
                mainHandler.post(() -> {
                    // 첫 번째 청크 생성 중일 때 gen 아이콘 표시 (이미 설정되었지만 확실히 하기 위해)
                    playButton.setImageResource(R.drawable.ic_gen);
                });
                
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                
                long firstChunkStartTime2 = System.currentTimeMillis();
                byte[] firstAudioData = ttsEngine.generate(firstChunk, selectedVoice);
                
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                
                if (firstAudioData == null || firstAudioData.length == 0) {
                    throw new Exception("First chunk generation failed");
                }
                
                long firstChunkEndTime2 = System.currentTimeMillis();
                double firstChunkProcessingTime2 = (firstChunkEndTime2 - firstChunkStartTime2) / 1000.0;
                chunkProcessingTimes.add(firstChunkProcessingTime2);
                totalChunkProcessingTime += firstChunkProcessingTime2;
                
                File firstChunkFile = saveChunk(firstAudioData, 0);
                audioChunks.add(firstChunkFile);
                chunkTexts.add(firstChunk); // 청크 텍스트 저장
                
                // 문장-청크 매핑 업데이트 (startSentenceIndex부터 시작하는 문장들만 고려)
                mapChunksToSentencesFromIndex(chunks, startSentenceIndex);
                
                // 첫 번째 청크 즉시 재생 (오디오 길이 계산은 playFirstChunk 내부에서 수행)
                mainHandler.post(() -> {
                    if (!Thread.currentThread().isInterrupted()) {
                        playFirstChunk(firstChunkFile, firstChunk, firstChunkProcessingTime2);
                        updateStatus("Playing first segment... (generating rest)");
                    }
                });
                
                // 나머지 청크 생성 (기존 로직과 동일)
                for (int i = 1; i < chunks.size(); i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    String chunk = chunks.get(i);
                    final int chunkIndex = i;
                    mainHandler.post(() -> updateStatus("Generating segment " + (chunkIndex + 1) + "/" + chunks.size() + "..."));
                    
                    long chunkStartTime = System.currentTimeMillis();
                    byte[] chunkAudioData = ttsEngine.generate(chunk, selectedVoice);
                    
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    if (chunkAudioData != null && chunkAudioData.length > 0) {
                        long chunkEndTime = System.currentTimeMillis();
                        double chunkProcessingTime = (chunkEndTime - chunkStartTime) / 1000.0;
                        chunkProcessingTimes.add(chunkProcessingTime);
                        totalChunkProcessingTime += chunkProcessingTime;
                        
                        File chunkFile = saveChunk(chunkAudioData, i);
                        audioChunks.add(chunkFile);
                        
                        // Estimate audio duration from chunk size
                        long chunkSize = chunkFile.length();
                        double estimatedDuration = (chunkSize > 44) ? (chunkSize - 44) / (24000.0 * 2.0) : 0;
                        totalAudioDuration += estimatedDuration;
                        
                        // Calculate metrics for this chunk
                        double chunkCPS = chunk.length() > 0 && chunkProcessingTime > 0 ? chunk.length() / chunkProcessingTime : 0;
                        double chunkRTF = estimatedDuration > 0 ? chunkProcessingTime / estimatedDuration : 0;
                        chunkCPSList.add(chunkCPS);
                        chunkRTFList.add(chunkRTF);
                        
                        // Update log console
                        updateLogConsoleMetrics();
                        
                        mainHandler.post(() -> {
                            if (mediaPlayer != null && !mediaPlayer.isPlaying() && 
                                !isPaused && currentChunkIndex < audioChunks.size() - 1) {
                                playNextChunk();
                            }
                        });
                    }
                }
                
                mainHandler.post(() -> {
                    updateStatus("All segments generated");
                    isGenerating = false;
                    currentGenerationTask = null;
                });
            } catch (java.util.concurrent.CancellationException e) {
                android.util.Log.d("MainActivity", "Generation cancelled");
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    mainHandler.post(() -> {
                        updateStatus("Error: " + e.getMessage(), true);
                        isGenerating = false;
                        currentGenerationTask = null;
                    });
                }
            }
        });
    }
    
    /**
     * Voice 변경 처리 (현재 재생 중인 청크부터 시작)
     */
    private void handleVoiceChange() {
        // 현재 재생 중인 청크 번호 저장 (목소리 변경 전에)
        // cancelCurrentGeneration() 호출 전에 모든 필요한 정보를 먼저 저장
        int currentChunk = currentChunkIndex;
        String currentChunkText = null;
        int targetSentenceIndex = -1;
        
        android.util.Log.d("MainActivity", "Voice change: saving current chunk index: " + currentChunk + 
            ", audioChunks.size(): " + audioChunks.size() + 
            ", chunkTexts.size(): " + chunkTexts.size() + 
            ", sentenceToChunkMap.size(): " + sentenceToChunkMap.size());
        
        // audioChunks가 비어있지 않고, currentChunkIndex가 유효한 범위 내에 있는지 확인
        if (audioChunks.isEmpty()) {
            android.util.Log.d("MainActivity", "Voice change: audioChunks is empty, generating from beginning");
            if (!fullText.isEmpty()) {
                cancelCurrentGeneration();
                generateAudio();
            }
            return;
        }
        
        // currentChunkIndex가 audioChunks 범위 내에 있는지 확인
        if (currentChunk < 0 || currentChunk >= audioChunks.size()) {
            android.util.Log.w("MainActivity", "Voice change: currentChunkIndex out of range: " + currentChunk + 
                " (audioChunks.size(): " + audioChunks.size() + "), generating from beginning");
            if (!fullText.isEmpty()) {
                cancelCurrentGeneration();
                generateAudio();
            }
            return;
        }
        
        // chunkTexts가 비어있지 않고, currentChunkIndex가 범위 내에 있는지 확인
        if (chunkTexts.isEmpty() || currentChunk >= chunkTexts.size()) {
            android.util.Log.w("MainActivity", "Voice change: chunkTexts is empty or currentChunkIndex out of range: " + 
                currentChunk + " (chunkTexts.size(): " + chunkTexts.size() + "), generating from beginning");
            if (!fullText.isEmpty()) {
                cancelCurrentGeneration();
                generateAudio();
            }
            return;
        }
        
        // 현재 청크의 텍스트 가져오기 (cancelCurrentGeneration 호출 전에!)
        currentChunkText = chunkTexts.get(currentChunk);
        if (currentChunkText == null || currentChunkText.trim().isEmpty()) {
            android.util.Log.w("MainActivity", "Voice change: currentChunkText is null or empty, generating from beginning");
            if (!fullText.isEmpty()) {
                cancelCurrentGeneration();
                generateAudio();
            }
            return;
        }
        
        // 현재 청크에 해당하는 문장 찾기 (첫 번째 매칭) - cancelCurrentGeneration 호출 전에!
        for (int i = 0; i < sentenceToChunkMap.size(); i++) {
            if (sentenceToChunkMap.get(i) == currentChunk) {
                targetSentenceIndex = i;
                break;
            }
        }
        
        // 매핑을 찾을 수 없으면, 청크 텍스트를 직접 사용하여 문장 찾기 (fallback) - cancelCurrentGeneration 호출 전에!
        if (targetSentenceIndex < 0) {
            android.util.Log.w("MainActivity", "Voice change: sentence mapping not found for chunk " + currentChunk + 
                ", trying to find sentence by chunk text");
            for (int i = 0; i < sentences.size(); i++) {
                String sentence = sentences.get(i).trim();
                if (currentChunkText.contains(sentence) || sentence.contains(currentChunkText.trim())) {
                    targetSentenceIndex = i;
                    android.util.Log.d("MainActivity", "Voice change: found sentence by text matching at index " + targetSentenceIndex);
                    break;
                }
            }
        }
        
        // 이제 cancelCurrentGeneration() 호출 (이미 필요한 정보는 모두 저장됨)
        cancelCurrentGeneration();
        
        // 찾은 문장이 있으면 해당 문장부터 생성
        if (targetSentenceIndex >= 0 && targetSentenceIndex < sentences.size()) {
            String targetSentence = sentences.get(targetSentenceIndex);
            android.util.Log.d("MainActivity", "Voice change: found target sentence at index " + targetSentenceIndex + 
                " for chunk " + currentChunk + ", generating from this sentence");
            generateFromSentence(targetSentence);
            return;
        }
        
        // 모든 방법으로 찾을 수 없으면 처음부터 생성
        android.util.Log.w("MainActivity", "Voice change: could not find target sentence, generating from beginning");
        if (!fullText.isEmpty()) {
            generateAudio();
        }
    }
    
    /**
     * 재생 중일 때 Voice 변경 처리 (deprecated - handleVoiceChange 사용)
     */
    private void handleVoiceChangeDuringPlayback() {
        handleVoiceChange();
    }
    
    /**
     * 특정 청크 인덱스부터 재생
     */
    private void playChunkAtIndex(int index) {
        if (index < 0 || index >= audioChunks.size()) {
            return;
        }
        
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            
            currentChunkIndex = index;
            File chunkFile = audioChunks.get(index);
            
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(chunkFile.getAbsolutePath());
            mediaPlayer.prepare();
            
            mediaPlayer.setOnCompletionListener(mp -> {
                if (!isPaused && currentChunkIndex < audioChunks.size() - 1) {
                    playNextChunk();
                }
            });
            
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                android.util.Log.e("MainActivity", "MediaPlayer error: what=" + what + ", extra=" + extra);
                updateStatus("Playback error: " + what);
                return true;
            });
            
            mediaPlayer.start();
            startProgressUpdate();
            isPaused = false;
            playButton.setImageResource(R.drawable.ic_pause);
            updateStatus("Playing from segment " + (index + 1));
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error playing chunk at index", e);
            updateStatus("Playback error: " + e.getMessage());
        }
    }
    
    /**
     * 텍스트 클릭 핸들러 설정
     */
    private void setupTextClickHandler() {
        // TextView에 직접 터치 리스너 설정 (z-index 최상위)
        textDisplay.setOnTouchListener((v, event) -> {
            float x = event.getX();
            float textViewWidth = textDisplay.getWidth();
            
            // 좌측 15% 또는 우측 15% 영역인지 확인
            boolean isPageNavigationArea = (x < textViewWidth * 0.15f) || (x > textViewWidth * 0.85f);
            
            if (isPageNavigationArea) {
                // 페이지 넘김 영역: ACTION_DOWN과 ACTION_UP 모두 처리하여 문장 선택 방지
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    // ACTION_DOWN에서도 이벤트 소비하여 LinkMovementMethod가 처리하지 않도록
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    // 좌측 15% 영역 클릭 시 이전 페이지
                    if (x < textViewWidth * 0.15f) {
                        if (currentPageIndex > 0) {
                            currentPageIndex--;
                            displayCurrentPage();
                            updatePageIndicator();
                        }
                    }
                    // 우측 15% 영역 클릭 시 다음 페이지
                    else if (x > textViewWidth * 0.85f) {
                        if (currentPageIndex < pages.size() - 1) {
                            currentPageIndex++;
                            displayCurrentPage();
                            updatePageIndicator();
                        }
                    }
                    return true; // 이벤트 소비하여 LinkMovementMethod가 처리하지 않도록
                }
                // MOVE 이벤트도 소비
                return true;
            }
            
            // 중앙 영역(15%~85%)은 LinkMovementMethod가 처리하도록 false 반환
            return false;
        });
        
        // Container에도 리스너 설정 (백업, TextView가 이벤트를 처리하지 않을 경우)
        textDisplayContainer.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float x = event.getX();
                float containerWidth = textDisplayContainer.getWidth();
                
                // 좌측 15% 영역 클릭 시 이전 페이지
                if (x < containerWidth * 0.15f) {
                    if (currentPageIndex > 0) {
                        currentPageIndex--;
                        displayCurrentPage();
                        updatePageIndicator();
                    }
                    return true;
                }
                
                // 우측 15% 영역 클릭 시 다음 페이지
                if (x > containerWidth * 0.85f) {
                    if (currentPageIndex < pages.size() - 1) {
                        currentPageIndex++;
                        displayCurrentPage();
                        updatePageIndicator();
                    }
                    return true;
                }
            }
            return false;
        });
    }
    
    /**
     * 페이지 인디케이터 설정
     */
    private void setupPageIndicator() {
        float touchExpansion = 8 * getResources().getDisplayMetrics().density; // 8px 확장
        
        // 핸들 드래그 처리 (검은색 핸들러와 흰색 박스 모두)
        // 클릭 영역을 상하좌우 8px 확장하기 위해 터치 이벤트를 처리
        android.view.View.OnTouchListener handleDragListener = (v, event) -> {
            float x = event.getX();
            float y = event.getY();
            float frameWidth = pageIndicatorFrame.getWidth();
            
            // 핸들러의 실제 위치와 크기
            float handleLeft = pageIndicatorHandle.getLeft();
            float handleRight = pageIndicatorHandle.getRight();
            float handleTop = pageIndicatorHandle.getTop();
            float handleBottom = pageIndicatorHandle.getBottom();
            
            // 확장된 클릭 영역
            float expandedLeft = handleLeft - touchExpansion;
            float expandedRight = handleRight + touchExpansion;
            float expandedTop = handleTop - touchExpansion;
            float expandedBottom = handleBottom + touchExpansion;
            
            // 터치 위치가 확장된 영역 내에 있는지 확인
            boolean isInExpandedArea = (x >= expandedLeft && x <= expandedRight && 
                                        y >= expandedTop && y <= expandedBottom);
            
            // 핸들러나 흰색 박스를 직접 터치한 경우 또는 확장된 영역 내에 있는 경우
            boolean isHandleTouch = (v == pageIndicatorHandle || v == pageIndicatorHandleBox) || isInExpandedArea;
            
            if (!isHandleTouch) {
                return false; // 확장된 영역 밖이면 처리하지 않음
            }
            
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDraggingIndicator = true;
                    // 핸들러의 현재 위치 기준으로 시작 위치 계산
                    if (v == pageIndicatorHandle) {
                        indicatorStartX = x;
                    } else if (v == pageIndicatorHandleBox) {
                        // 흰색 박스를 드래그할 때는 핸들러 위치 기준으로 계산
                        indicatorStartX = x - (pageIndicatorHandle.getLeft() - pageIndicatorHandleBox.getLeft());
                    } else {
                        // 확장된 영역에서 터치한 경우, 핸들러 중심을 기준으로 계산
                        float handleCenterX = handleLeft + (handleRight - handleLeft) / 2;
                        indicatorStartX = x - handleCenterX;
                    }
                    return true;
                    
                case MotionEvent.ACTION_MOVE:
                    if (isDraggingIndicator) {
                        float handleX;
                        if (v == pageIndicatorHandle) {
                            handleX = pageIndicatorHandle.getLeft() + x - indicatorStartX;
                        } else if (v == pageIndicatorHandleBox) {
                            // 흰색 박스를 드래그할 때
                            handleX = pageIndicatorHandleBox.getLeft() + x - indicatorStartX + 3 * getResources().getDisplayMetrics().density;
                        } else {
                            // 확장된 영역에서 드래그할 때, 핸들러 중심을 기준으로 계산
                            float handleCenterX = handleLeft + (handleRight - handleLeft) / 2;
                            handleX = handleCenterX + x - indicatorStartX;
                        }
                        float maxX = frameWidth - pageIndicatorHandle.getWidth();
                        
                        if (handleX < 0) handleX = 0;
                        if (handleX > maxX) handleX = maxX;
                        
                        // 페이지 계산
                        float ratio = handleX / maxX;
                        int targetPage = (int) (ratio * pages.size());
                        if (targetPage < 0) targetPage = 0;
                        if (targetPage >= pages.size()) targetPage = pages.size() - 1;
                        
                        if (targetPage != currentPageIndex) {
                            currentPageIndex = targetPage;
                            displayCurrentPage();
                            updatePageIndicator();
                        }
                    }
                    return true;
                    
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDraggingIndicator = false;
                    return true;
            }
            return false;
        };
        
        pageIndicatorHandle.setOnTouchListener(handleDragListener);
        pageIndicatorHandleBox.setOnTouchListener(handleDragListener);
        
        // 인디케이터 프레임 클릭 처리 (확장된 영역 확인 후 처리)
        pageIndicatorFrame.setOnTouchListener((v, event) -> {
            float x = event.getX();
            float y = event.getY();
            float frameWidth = pageIndicatorFrame.getWidth();
            
            // 핸들러의 실제 위치와 크기
            float handleLeft = pageIndicatorHandle.getLeft();
            float handleRight = pageIndicatorHandle.getRight();
            float handleTop = pageIndicatorHandle.getTop();
            float handleBottom = pageIndicatorHandle.getBottom();
            
            // 확장된 클릭 영역
            float expandedLeft = handleLeft - touchExpansion;
            float expandedRight = handleRight + touchExpansion;
            float expandedTop = handleTop - touchExpansion;
            float expandedBottom = handleBottom + touchExpansion;
            
            // 확장된 영역 내에 있는지 확인
            boolean isInExpandedArea = (x >= expandedLeft && x <= expandedRight && 
                                        y >= expandedTop && y <= expandedBottom);
            
            if (isInExpandedArea) {
                // 확장된 영역에서 터치한 경우 핸들러 드래그 리스너로 위임
                return handleDragListener.onTouch(pageIndicatorFrame, event);
            }
            
            // 확장된 영역 밖이면 페이지 이동 처리
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // 클릭 위치에 따라 페이지 이동
                float ratio = x / frameWidth;
                int targetPage = (int) (ratio * pages.size());
                if (targetPage < 0) targetPage = 0;
                if (targetPage >= pages.size()) targetPage = pages.size() - 1;
                
                currentPageIndex = targetPage;
                displayCurrentPage();
                updatePageIndicator();
                return true;
            }
            return false;
        });
    }
    
    /**
     * 페이지 인디케이터 업데이트
     */
    private void updatePageIndicator() {
        if (pages.isEmpty() || pageIndicatorFrame == null || pageIndicatorHandle == null || pageIndicatorHandleBox == null) {
            return;
        }
        
        float frameWidth = pageIndicatorFrame.getWidth();
        float handleWidth = pageIndicatorHandle.getWidth();
        float maxX = frameWidth - handleWidth;
        
        float ratio = pages.size() > 1 ? (float) currentPageIndex / (pages.size() - 1) : 0;
        float handleX = ratio * maxX;
        
        // 흰색 박스 위치 설정 (핸들러보다 3dp 왼쪽)
        // handleX는 핸들러의 왼쪽 위치이므로, 흰색 박스는 handleX - 3dp 위치에 있어야 함
        float marginDp = 3 * getResources().getDisplayMetrics().density;
        float boxX = handleX - marginDp;
        
        // 최 좌측일 때는 boxX가 0 이상이 되도록 보장 (마진 중복 방지)
        if (boxX < 0) {
            boxX = 0;
            // boxX가 0이면 핸들러는 흰색 박스 내부에서 layout_marginStart="3dp"로 인해
            // 실제로 3dp 위치에 있게 됨. 따라서 handleX를 0으로 설정하면 됨
            handleX = 0;
        }
        
        // 검은색 핸들러 위치 설정
        android.widget.FrameLayout.LayoutParams handleParams = 
            (android.widget.FrameLayout.LayoutParams) pageIndicatorHandle.getLayoutParams();
        handleParams.leftMargin = (int) handleX;
        pageIndicatorHandle.setLayoutParams(handleParams);
        
        // 흰색 박스 위치 설정
        android.widget.FrameLayout.LayoutParams boxParams = 
            (android.widget.FrameLayout.LayoutParams) pageIndicatorHandleBox.getLayoutParams();
        boxParams.leftMargin = (int) boxX;
        pageIndicatorHandleBox.setLayoutParams(boxParams);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cancel any ongoing generation
        if (isGenerating) {
            cancelCurrentGeneration();
        }
        
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        
        // Clear audio chunks
        clearAudioChunks();
        
        if (executorService != null) {
            executorService.shutdown();
        }
        if (ttsEngine != null) {
            ttsEngine.release();
        }
        if (progressHandler != null) {
            progressHandler.removeCallbacksAndMessages(null);
        }
    }
}

