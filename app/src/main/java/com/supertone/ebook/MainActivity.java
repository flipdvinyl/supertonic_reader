
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
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
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
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.method.LinkMovementMethod;
import android.text.TextPaint;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private TextView textDisplay;
    private TextView pageNumberDisplay;
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
    private TextView speedSelector; // Speed 선택 TextView
    private String selectedSpeed = "1x"; // 기본 속도 1x
    
    /**
     * 속도 문자열을 speechLength로 변환
     * 발화길이 1 = 1x, 발화길이 2 = 0.5x, 발화길이 0.5 = 2x
     */
    private double speedToSpeechLength(String speed) {
        // "2x", "1.5x", "1.2x", "1x", "0.8x" -> speechLength
        if (speed.equals("2x")) return 0.5;
        if (speed.equals("1.5x")) return 1.0 / 1.5;
        if (speed.equals("1.2x")) return 1.0 / 1.2;
        if (speed.equals("1x")) return 1.0;
        if (speed.equals("0.8x")) return 1.0 / 0.8;
        return 1.0; // 기본값
    }
    private Button generateButton;
    private android.widget.ImageButton playButton;
    private Button copyErrorButton;
    private TextView statusText;
    private TextView metricsText;
    private TextView mediaMetricsText; // 미디어 플레이어 섹션의 CPS/RTF 표시
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
    private List<String> allChunks = new ArrayList<>(); // 전체 텍스트의 모든 청크 (절대 번호 기준)
    private int firstChunkAbsoluteIndex = 0; // 현재 생성 중인 첫 번째 청크의 절대 인덱스
    private int currentPlayingChunkAbsoluteIndex = -1; // 현재 재생 중인 청크의 절대 인덱스
    private double currentChunkPlaybackProgress = 0.0; // 현재 청크의 재생 진행률 (0.0 ~ 1.0)
    private boolean isGenerating = false;
    private boolean isPaused = false;
    private java.util.concurrent.Future<?> currentGenerationTask = null;
    private java.util.concurrent.Future<?> backgroundGenerationTask = null; // 백그라운드 생성 작업
    private Queue<Integer> backgroundGenerationQueue = new LinkedBlockingQueue<>(); // 백그라운드 생성 큐
    private long generationStartTime = 0;
    private int totalTextLength = 0;
    private double totalAudioDuration = 0; // 생성된 모든 청크의 총 재생 시간
    private List<Double> chunkProcessingTimes = new ArrayList<>(); // 각 청크의 생성 시간
    private List<Double> chunkCPSList = new ArrayList<>(); // 각 청크의 CPS (레거시, 평균 계산용)
    private List<Double> chunkRTFList = new ArrayList<>(); // 각 청크의 RTF (레거시, 평균 계산용)
    private Map<Integer, Double> chunkProcessingTimeMap = new HashMap<>(); // 청크 절대 인덱스 -> Processing time 매핑
    private Map<Integer, Double> chunkCPSMap = new HashMap<>(); // 청크 절대 인덱스 -> CPS 매핑
    private Map<Integer, Double> chunkRTFMap = new HashMap<>(); // 청크 절대 인덱스 -> RTF 매핑
    private Map<Integer, ChunkPageInfo> chunkPageInfoCache = new HashMap<>(); // 청크 절대 인덱스 -> ChunkPageInfo 캐시
    private double totalChunkProcessingTime = 0; // 모든 청크 생성 시간의 총합
    
    // 오디오 캐시 관리
    private boolean keepAudioCache = true; // true: 앱 실행 시 캐시 유지, false: 캐시 삭제
    private boolean autoCleanAudioCache = true; // true: 캐시 자동 제거 활성화, false: 비활성화
    private int autoCleanAudioCacheLimit = 30; // 캐시 자동 제거 갯수 (기본 30개)
    
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
    
    // 스크롤/클릭 구분을 위한 변수
    private float touchDownX = 0;
    private float touchDownY = 0;
    private long touchDownTime = 0;
    private static final float MAX_CLICK_DISTANCE = 10.0f; // 픽셀 단위
    private static final long MAX_CLICK_DURATION = 200; // 밀리초 단위
    
    // 스와이프 제스처 감지
    private GestureDetector gestureDetector;
    
    // Voice mapping: Female #1, Female #2, Male #1, Male #2
    private static final String SAMPLE_TEXT_FILE = "sample_text.txt";
    
    // 페이지 분할 기준: 한 페이지에 최대 표시할 줄 수 (동적으로 계산됨)
    private int maxLinesPerPage = 14; // 기본값, setupPages()에서 계산하여 설정

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 상태바 숨기기
        hideStatusBar();
        
        // ActionBar 숨기기 (헤더 영역 안보이게 처리)
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        setContentView(R.layout.activity_main);
        
        // WindowInsets 처리: OS 네비게이션 바 영역을 고려한 레이아웃 설정
        View rootLayout = findViewById(R.id.rootLayout);
        if (rootLayout == null) {
            // 루트 레이아웃이 없으면 content view 사용
            rootLayout = findViewById(android.R.id.content);
        }
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
                int systemBarsType = WindowInsetsCompat.Type.systemBars();
                int bottomInset = windowInsets.getInsets(systemBarsType).bottom;
                
                // 하단 시스템 바 영역만큼 padding 추가
                if (bottomInset > 0) {
                    v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), 
                        v.getPaddingRight(), bottomInset);
                }
                
                return windowInsets;
            });
        }
        
        initializeViews();
        setupVoiceButtons();
        setupSpeedSelector();
        setupButtons();
        initializeTTS();
        
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        progressHandler = new Handler(Looper.getMainLooper());
        
        // 스와이프 제스처 감지 초기화
        gestureDetector = new GestureDetector(this, new SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) {
                    return false;
                }
                
                float deltaX = e2.getX() - e1.getX();
                float deltaY = e2.getY() - e1.getY();
                
                // 수평 스와이프인지 확인 (수직 이동보다 수평 이동이 더 큰 경우)
                if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 100) {
                    // 좌->우 스와이프 (이전 페이지)
                    if (deltaX > 0) {
                        if (currentPageIndex > 0) {
                            currentPageIndex--;
                            displayCurrentPage();
                            updatePageIndicator();
                            return true;
                        }
                    }
                    // 우->좌 스와이프 (다음 페이지)
                    else {
                        if (currentPageIndex < pages.size() - 1) {
                            currentPageIndex++;
                            displayCurrentPage();
                            updatePageIndicator();
                            return true;
                        }
                    }
                }
                return false;
            }
        });
        
        // 오디오 캐시 삭제 (keepAudioCache가 false일 때)
        if (!keepAudioCache) {
            clearAllAudioCache();
        }
        
        // 샘플 텍스트 프리셋 설정
        loadSampleText();
        
        // 진행 바 업데이트 시작
        startProgressUpdate();
    }
    
    /**
     * 상태바 숨기기
     */
    private void hideStatusBar() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // Android 11 (API 30) 이상
                getWindow().setDecorFitsSystemWindows(false);
                android.view.WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(android.view.WindowInsets.Type.statusBars());
                    controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                // Android 10 이하
                View decorView = getWindow().getDecorView();
                int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
                decorView.setSystemUiVisibility(uiOptions);
            }
        } catch (Exception e) {
            // 에러 발생 시 로그만 남기고 계속 진행
            android.util.Log.e("MainActivity", "Error hiding status bar: " + e.getMessage());
        }
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // 포커스를 받을 때마다 상태바 숨기기 (다른 앱에서 돌아올 때 등)
            hideStatusBar();
        }
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
        pageNumberDisplay = findViewById(R.id.pageNumberDisplay);
        // 페이지 번호 아래로 3px 이동
        if (pageNumberDisplay != null) {
            pageNumberDisplay.setTranslationY(8 * getResources().getDisplayMetrics().density);
        }
        textDisplayContainer = findViewById(R.id.textDisplayContainer);
        pageIndicatorFrame = findViewById(R.id.pageIndicatorFrame);
        pageIndicatorHandle = findViewById(R.id.pageIndicatorHandle);
        pageIndicatorHandleBox = findViewById(R.id.pageIndicatorHandleBox);
        voiceButton1 = findViewById(R.id.voiceButton1);
        voiceButton2 = findViewById(R.id.voiceButton2);
        voiceButton3 = findViewById(R.id.voiceButton3);
        voiceButton4 = findViewById(R.id.voiceButton4);
        voiceSelector = findViewById(R.id.voiceSelector);
        speedSelector = findViewById(R.id.speedSelector);
        generateButton = findViewById(R.id.generateButton);
        playButton = findViewById(R.id.playButton);
        copyErrorButton = findViewById(R.id.copyErrorButton);
        // 로그 섹션 제거됨
        statusText = null;
        metricsText = null;
        mediaMetricsText = findViewById(R.id.mediaMetricsText);
        timeDisplay = findViewById(R.id.timeDisplay);
        // 재생시간 표시 숨김
        if (timeDisplay != null) {
            timeDisplay.setVisibility(View.GONE);
        }
        progressBar = findViewById(R.id.progressBar);
        
        // TextView 스크롤 완전히 비활성화
        textDisplay.setFocusable(false);
        textDisplay.setFocusableInTouchMode(false);
        textDisplay.setClickable(true);
        textDisplay.setLongClickable(false);
        
        // 클릭 시 녹색 배경 피드백 비활성화
        textDisplay.setHighlightColor(0x00000000); // 투명색
        
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
        
        // 커스텀 MovementMethod 설정 (한 번만 설정)
        textDisplay.setMovementMethod(new LinkMovementMethod() {
            @Override
            public boolean canSelectArbitrarily() {
                return false;
            }
            
            @Override
            public boolean onTouchEvent(android.widget.TextView widget, android.text.Spannable buffer, android.view.MotionEvent event) {
                // 스크롤을 완전히 차단하고 클릭만 허용
                // MOVE 이벤트는 무시하여 스크롤 방지
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    return false;
                }
                return super.onTouchEvent(widget, buffer, event);
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
            
            // PopupMenu가 표시된 후 배경색 설정 (#aaa)
            mainHandler.post(() -> {
                try {
                    java.lang.reflect.Field mPopupField = popupMenu.getClass().getDeclaredField("mPopup");
                    mPopupField.setAccessible(true);
                    Object mPopup = mPopupField.get(popupMenu);
                    
                    // ListView 가져오기
                    java.lang.reflect.Method getListViewMethod = mPopup.getClass().getDeclaredMethod("getListView");
                    getListViewMethod.setAccessible(true);
                    android.widget.ListView listView = (android.widget.ListView) getListViewMethod.invoke(mPopup);
                    
                    if (listView != null) {
                        // 배경색 #999 설정
                        listView.setBackgroundColor(0xFF999999);
                        
                        // 보더 1px #999 추가
                        android.graphics.drawable.GradientDrawable borderDrawable = new android.graphics.drawable.GradientDrawable();
                        borderDrawable.setColor(0xFF999999);
                        borderDrawable.setStroke(1, 0xFF999999); // 1px #999 보더
                        listView.setBackground(borderDrawable);
                    }
                } catch (Exception e) {
                    // 리플렉션 실패 시 무시 (배경색 설정 실패해도 기능은 정상 작동)
                    android.util.Log.d("MainActivity", "Failed to set popup menu background: " + e.getMessage());
                }
            });
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
    
    private void setupSpeedSelector() {
        // Set default selection (1x)
        updateSpeedSelector();
        
        // Speed 선택 TextView 클릭 시 팝업 메뉴 표시
        speedSelector.setOnClickListener(v -> {
            android.widget.PopupMenu popupMenu = new android.widget.PopupMenu(this, speedSelector);
            popupMenu.getMenu().add(0, 0, 0, "2x");
            popupMenu.getMenu().add(0, 1, 0, "1.5x");
            popupMenu.getMenu().add(0, 2, 0, "1.2x");
            popupMenu.getMenu().add(0, 3, 0, "1x");
            popupMenu.getMenu().add(0, 4, 0, "0.8x");
            
            popupMenu.setOnMenuItemClickListener(item -> {
                String previousSpeed = selectedSpeed;
                switch (item.getItemId()) {
                    case 0:
                        selectedSpeed = "2x";
                        break;
                    case 1:
                        selectedSpeed = "1.5x";
                        break;
                    case 2:
                        selectedSpeed = "1.2x";
                        break;
                    case 3:
                        selectedSpeed = "1x";
                        break;
                    case 4:
                        selectedSpeed = "0.8x";
                        break;
                }
                updateSpeedSelector();
                popupMenu.dismiss();
                
                // Speed 변경 시 현재 읽던 문장부터 새롭게 청크 생성 및 재생
                if (!previousSpeed.equals(selectedSpeed)) {
                    handleSpeedChange();
                }
                
                return true;
            });
            
            popupMenu.show();
            
            // PopupMenu가 표시된 후 배경색 설정 (#aaa)
            mainHandler.post(() -> {
                try {
                    java.lang.reflect.Field mPopupField = popupMenu.getClass().getDeclaredField("mPopup");
                    mPopupField.setAccessible(true);
                    Object mPopup = mPopupField.get(popupMenu);
                    
                    // ListView 가져오기
                    java.lang.reflect.Method getListViewMethod = mPopup.getClass().getDeclaredMethod("getListView");
                    getListViewMethod.setAccessible(true);
                    android.widget.ListView listView = (android.widget.ListView) getListViewMethod.invoke(mPopup);
                    
                    if (listView != null) {
                        // 배경색 #999 설정
                        listView.setBackgroundColor(0xFF999999);
                        
                        // 보더 1px #999 추가
                        android.graphics.drawable.GradientDrawable borderDrawable = new android.graphics.drawable.GradientDrawable();
                        borderDrawable.setColor(0xFF999999);
                        borderDrawable.setStroke(1, 0xFF999999); // 1px #999 보더
                        listView.setBackground(borderDrawable);
                    }
                } catch (Exception e) {
                    // 리플렉션 실패 시 무시 (배경색 설정 실패해도 기능은 정상 작동)
                    android.util.Log.d("MainActivity", "Failed to set popup menu background: " + e.getMessage());
                }
            });
        });
    }
    
    private void updateSpeedSelector() {
        if (speedSelector != null) {
            // "Speed\n속도값" 형식으로 표시 (두 줄)
            String fullText = "Speed\n" + selectedSpeed;
            SpannableString spannable = new SpannableString(fullText);
            
            // "Speed" 부분을 #999 색상으로 설정
            spannable.setSpan(new android.text.style.ForegroundColorSpan(0xFF999999), 
                0, "Speed".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            // 속도값에 물결무늬 언더라인 추가
            int speedValueStart = "Speed\n".length();
            int speedValueEnd = spannable.length();
            spannable.setSpan(new WavyUnderlineSpan(), speedValueStart, speedValueEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            speedSelector.setText(spannable);
        }
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
            // "Voice\n보이스이름" 형식으로 표시 (두 줄)
            String fullText = "Voice\n" + selectedVoiceName;
            SpannableString spannable = new SpannableString(fullText);
            
            // "Voice" 부분을 #999 색상으로 설정
            spannable.setSpan(new android.text.style.ForegroundColorSpan(0xFF999999), 
                0, "Voice".length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            // 보이스 이름에 물결무늬 언더라인 추가
            int voiceNameStart = "Voice\n".length();
            int voiceNameEnd = spannable.length();
            spannable.setSpan(new WavyUnderlineSpan(), voiceNameStart, voiceNameEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
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
        generateButton.setOnClickListener(v -> {
            // 1번 센텐스부터 재생
            if (!sentences.isEmpty()) {
                generateAudio("0", null, null);
            }
        });
        playButton.setOnClickListener(v -> {
            // 청크가 없으면 1번 센텐스부터 재생
            if (audioChunks.isEmpty()) {
                if (!sentences.isEmpty()) {
                    generateAudio("0", null, null);
                }
                return;
            }
            
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                pauseAudio();
                playButton.setImageResource(R.drawable.ic_play);
            } else {
                // 재생 시작 전에 현재 재생 중인 세그먼트가 있는 페이지로 이동
                navigateToCurrentPlayingChunkPage();
                
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
        currentPlayingChunkAbsoluteIndex = -1;
        currentChunkPlaybackProgress = 0.0;
        
        isGenerating = false;
        currentGenerationTask = null;
        
        // 하이라이트 제거
        displayCurrentPage();
        
        updateStatus("Previous generation cancelled");
    }
    
    /**
     * Cancel current generation process only (keep audio files)
     * 목소리 변경 시 사용 - 기존 오디오 파일은 유지하고 생성 작업만 취소
     */
    private void cancelGenerationOnly() {
        if (currentGenerationTask != null && !currentGenerationTask.isDone()) {
            currentGenerationTask.cancel(true);
            android.util.Log.d("MainActivity", "Cancelling current generation (keeping audio files)");
        }
        
        isGenerating = false;
        currentGenerationTask = null;
    }
    
    /**
     * 백그라운드 생성 작업 취소
     */
    private void cancelBackgroundGeneration() {
        if (backgroundGenerationTask != null && !backgroundGenerationTask.isDone()) {
            backgroundGenerationTask.cancel(true);
            android.util.Log.d("MainActivity", "Cancelling background generation");
        }
        backgroundGenerationQueue.clear();
        backgroundGenerationTask = null;
    }
    
    /**
     * Clear all audio chunk files (목소리별 청크는 유지하므로 파일 삭제하지 않음)
     */
    private void clearAudioChunks() {
        // 목소리별 이미 생성된 청크 오디오는 지울 필요 없음
        // 파일 삭제 로직 제거
    }
    
    /**
     * 오래된 오디오 캐시 파일 자동 정리
     * 캐시 파일이 autoCleanAudioCacheLimit 개수를 넘으면 오래된 파일부터 삭제
     */
    private void cleanOldAudioCache() {
        try {
            File cacheDir = getCacheDir();
            if (cacheDir == null || !cacheDir.exists()) {
                return;
            }
            
            File[] files = cacheDir.listFiles();
            if (files == null) {
                return;
            }
            
            // tts_chunk_*.wav 파일만 필터링
            List<File> audioCacheFiles = new ArrayList<>();
            for (File file : files) {
                if (file.isFile() && file.getName().startsWith("tts_chunk_") && file.getName().endsWith(".wav")) {
                    audioCacheFiles.add(file);
                }
            }
            
            // 파일 개수가 제한을 넘으면 오래된 파일부터 삭제
            if (audioCacheFiles.size() > autoCleanAudioCacheLimit) {
                // 파일 수정 시간 기준으로 정렬 (오래된 파일이 앞에 오도록)
                audioCacheFiles.sort((f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified()));
                
                // 오래된 파일부터 삭제 (제한 개수만큼 남기고 나머지 삭제)
                int filesToDelete = audioCacheFiles.size() - autoCleanAudioCacheLimit;
                int deletedCount = 0;
                for (int i = 0; i < filesToDelete; i++) {
                    if (audioCacheFiles.get(i).delete()) {
                        deletedCount++;
                    }
                }
                android.util.Log.d("MainActivity", "Auto-cleaned " + deletedCount + " old audio cache files (limit: " + autoCleanAudioCacheLimit + ")");
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error cleaning old audio cache: " + e.getMessage());
        }
    }
    
    /**
     * 모든 오디오 캐시 파일 삭제 (tts_chunk_*.wav)
     */
    private void clearAllAudioCache() {
        try {
            File cacheDir = getCacheDir();
            if (cacheDir != null && cacheDir.exists()) {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    int deletedCount = 0;
                    for (File file : files) {
                        if (file.isFile() && file.getName().startsWith("tts_chunk_") && file.getName().endsWith(".wav")) {
                            if (file.delete()) {
                                deletedCount++;
                            }
                        }
                    }
                    android.util.Log.d("MainActivity", "Cleared " + deletedCount + " audio cache files");
                }
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error clearing audio cache: " + e.getMessage());
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
     * 세그먼트 재생 시점에 CPS/RTF 표시
     * @param absoluteChunkIndex 청크의 절대 인덱스
     */
    private void showChunkMetrics(int absoluteChunkIndex) {
        Double cps = chunkCPSMap.get(absoluteChunkIndex);
        Double rtf = chunkRTFMap.get(absoluteChunkIndex);
        
        if (cps == null || rtf == null) {
            // 해당 청크의 메트릭이 없으면 표시하지 않음
            return;
        }
        
        String cpsValue = String.format("%.0f", cps);
        String rtfValue = String.format("%.3fx", rtf);
        String logText = String.format("CPS %s\nRTF %s", cpsValue, rtfValue);
        
        // 미디어 플레이어 섹션에 표시
        if (mediaMetricsText != null && mainHandler != null) {
            // 기존 예약된 숨기기 작업 제거
            mainHandler.removeCallbacksAndMessages(null);
            
            // SpannableString으로 레이블은 회색, 값은 검정으로 설정
            SpannableString spannable = new SpannableString(logText);
            
            // "CPS" 레이블을 #999 색상으로 설정
            int cpsLabelStart = 0;
            int cpsLabelEnd = "CPS".length();
            spannable.setSpan(new android.text.style.ForegroundColorSpan(0xFF999999), 
                cpsLabelStart, cpsLabelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            // CPS값은 검정색 (기본 색상이므로 별도 설정 불필요)
            // 하지만 명시적으로 검정색으로 설정
            int cpsValueStart = "CPS ".length();
            int cpsValueEnd = cpsValueStart + cpsValue.length();
            spannable.setSpan(new android.text.style.ForegroundColorSpan(0xFF000000), 
                cpsValueStart, cpsValueEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            // "RTF" 레이블을 #999 색상으로 설정
            int rtfLabelStart = logText.indexOf("RTF");
            int rtfLabelEnd = rtfLabelStart + "RTF".length();
            spannable.setSpan(new android.text.style.ForegroundColorSpan(0xFF999999), 
                rtfLabelStart, rtfLabelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            // RTF값은 검정색으로 설정
            int rtfValueStart = rtfLabelEnd + 1; // "RTF " 다음
            int rtfValueEnd = rtfValueStart + rtfValue.length();
            spannable.setSpan(new android.text.style.ForegroundColorSpan(0xFF000000), 
                rtfValueStart, rtfValueEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            
            // 텍스트 표시
            mediaMetricsText.setText(spannable);
            
            // 1초 후 텍스트 숨기기
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mediaMetricsText != null) {
                        mediaMetricsText.setText("");
                    }
                }
            }, 1500); // 1.5초 후 실행
        }
    }
    
    /**
     * 세그먼트 생성 시 Processing time, CPS, RTF 저장
     * @param absoluteChunkIndex 청크의 절대 인덱스
     * @param chunkText 청크 텍스트
     * @param processingTime 생성 시간 (초)
     * @param audioDuration 오디오 재생 시간 (초)
     */
    private void saveChunkMetrics(int absoluteChunkIndex, String chunkText, double processingTime, double audioDuration) {
        if (chunkText == null || chunkText.isEmpty() || processingTime <= 0) {
            return;
        }
        
        // Processing time 저장
        chunkProcessingTimeMap.put(absoluteChunkIndex, processingTime);
        
        // CPS 계산: 문자 수 / 처리 시간
        double cps = chunkText.length() / processingTime;
        
        // RTF 계산: 처리 시간 / 오디오 재생 시간
        double rtf = audioDuration > 0 ? processingTime / audioDuration : 0;
        
        // Map에 저장
        chunkCPSMap.put(absoluteChunkIndex, cps);
        chunkRTFMap.put(absoluteChunkIndex, rtf);
        
        // 레거시 리스트에도 추가 (필요한 경우)
        chunkProcessingTimes.add(processingTime);
        chunkCPSList.add(cps);
        chunkRTFList.add(rtf);
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
        mapChunksToSentencesWithAbsoluteIndex(chunks, startSentenceIndex, 0);
    }
    
    /**
     * 청크를 문장에 매핑 (절대 청크 번호 사용)
     * @param chunks 청크 리스트
     * @param startSentenceIndex 시작 문장 인덱스
     * @param startChunkAbsoluteIndex 시작 청크의 절대 인덱스
     */
    private void mapChunksToSentencesWithAbsoluteIndex(List<String> chunks, int startSentenceIndex, int startChunkAbsoluteIndex) {
        // sentenceToChunkMap이 충분히 크지 않으면 확장
        while (sentenceToChunkMap.size() < sentences.size()) {
            sentenceToChunkMap.add(-1);
        }
        
        // fullText에서 각 문장의 위치를 미리 계산
        int fullTextPos = 0;
        for (int i = 0; i < startSentenceIndex && i < sentences.size(); i++) {
            String prevSentence = sentences.get(i);
            int pos = fullText.indexOf(prevSentence, fullTextPos);
            if (pos >= 0) {
                fullTextPos = pos + prevSentence.length();
            }
        }
        
        // 각 청크를 순서대로 처리
        int sentenceIndex = startSentenceIndex;
        int chunkStartInFullText = fullTextPos;
        for (int relativeChunkIndex = 0; relativeChunkIndex < chunks.size() && sentenceIndex < sentences.size(); relativeChunkIndex++) {
            String chunk = chunks.get(relativeChunkIndex).trim();
            
            // fullText에서 이 청크의 위치 찾기
            int chunkPos = fullText.indexOf(chunk, chunkStartInFullText);
            if (chunkPos < 0) {
                // 정확히 일치하지 않으면 부분 매칭 시도
                String chunkPart = chunk.length() > 50 ? chunk.substring(0, 50) : chunk;
                chunkPos = fullText.indexOf(chunkPart, chunkStartInFullText);
            }
            if (chunkPos < 0) {
                // 청크를 찾을 수 없으면 다음 청크로
                chunkStartInFullText += chunk.length();
                continue;
            }
            
            int chunkEndInFullText = chunkPos + chunk.length();
            
            // 절대 청크 번호 계산
            int absoluteChunkIndex = startChunkAbsoluteIndex + relativeChunkIndex;
            
            // 이 청크에 포함된 연속된 문장들을 찾아 매핑
            while (sentenceIndex < sentences.size()) {
                String sentence = sentences.get(sentenceIndex).trim();
                
                // fullText에서 이 문장의 위치 찾기 (chunkStartInFullText 이후부터)
                int sentencePos = fullText.indexOf(sentence, chunkStartInFullText);
                if (sentencePos >= 0 && sentencePos < chunkEndInFullText) {
                    // 이 문장이 이 청크 내에 있으면 매핑
                    sentenceToChunkMap.set(sentenceIndex, absoluteChunkIndex);
                    sentenceIndex++;
                    chunkStartInFullText = sentencePos + sentence.length();
                } else {
                    // 이 청크에 더 이상 문장이 없으면 다음 청크로
                    chunkStartInFullText = chunkEndInFullText;
                    break;
                }
            }
        }
    }
    
    /**
     * 청크를 파일로 저장 (보이스별로 구분)
     */
    private File saveChunk(byte[] audioData, int index, String voice, String speed) throws IOException {
        // 보이스별, 속도별 파일명: tts_chunk_{voice}_{speed}_{index}.wav
        String speedStr = speed.replace(".", "p").replace("x", "");
        File chunkFile = new File(getCacheDir(), "tts_chunk_" + voice + "_" + speedStr + "_" + index + ".wav");
        FileOutputStream fos = new FileOutputStream(chunkFile);
        fos.write(audioData);
        fos.close();
        
        // keepAudioCache가 true이고 autoCleanAudioCache가 true일 때만 오래된 캐시 파일 정리
        // keepAudioCache가 false면 앱 실행 시 캐시를 삭제하므로 자동 제거 로직 불필요
        if (keepAudioCache && autoCleanAudioCache) {
            cleanOldAudioCache();
        }
        
        return chunkFile;
    }
    
    /**
     * 보이스별 청크 파일 찾기
     */
    private File findChunkFile(String voice, String speed, int absoluteChunkIndex) {
        // 보이스별, 속도별 파일명: tts_chunk_{voice}_{speed}_{index}.wav
        String speedStr = speed.replace(".", "p").replace("x", "");
        File chunkFile = new File(getCacheDir(), "tts_chunk_" + voice + "_" + speedStr + "_" + absoluteChunkIndex + ".wav");
        if (chunkFile.exists()) {
            return chunkFile;
        }
        return null;
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
            currentPlayingChunkAbsoluteIndex = firstChunkAbsoluteIndex;
            currentChunkPlaybackProgress = 0.0;
            
            // 오디오 길이 계산 및 메트릭스 업데이트
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
                
            // CPS/RTF 저장 또는 업데이트
            if (chunkText != null) {
                if (processingTime > 0) {
                    // 새로 생성한 경우: CPS/RTF 모두 계산하여 저장
                    saveChunkMetrics(firstChunkAbsoluteIndex, chunkText, processingTime, firstChunkAudioDuration);
                }
                // 기존 파일인 경우: Map에 이미 저장된 값이 있으면 사용 (초기화하지 않았으므로 유지됨)
            }
            
            // 재생 시작 시점에 해당 청크의 CPS/RTF 표시
            showChunkMetrics(firstChunkAbsoluteIndex);
            
            mediaPlayer.setOnCompletionListener(mp -> {
                if (!isPaused && currentChunkIndex < audioChunks.size() - 1) {
                    playNextChunk();
                } else if (currentChunkIndex >= audioChunks.size() - 1) {
                    // 현재 문장의 모든 청크 재생 완료
                    // 다음 문장 찾기
                    int currentAbsoluteChunkIndex = firstChunkAbsoluteIndex + currentChunkIndex;
                    int currentSentenceIndex = -1;
                    
                    // 현재 청크에 해당하는 문장 찾기
                    for (int i = 0; i < sentenceToChunkMap.size(); i++) {
                        if (sentenceToChunkMap.get(i) == currentAbsoluteChunkIndex) {
                            currentSentenceIndex = i;
                            break;
                        }
                    }
                    
                    // 문장을 찾지 못하면 청크 텍스트로 문장 찾기
                    if (currentSentenceIndex < 0 && currentAbsoluteChunkIndex >= 0 && currentAbsoluteChunkIndex < allChunks.size()) {
                        String currentChunkText = allChunks.get(currentAbsoluteChunkIndex);
                        for (int i = 0; i < sentences.size(); i++) {
                            if (sentences.get(i).contains(currentChunkText) || currentChunkText.contains(sentences.get(i))) {
                                currentSentenceIndex = i;
                                break;
                            }
                        }
                    }
                    
                    // 다음 문장이 있으면 자동으로 생성 및 재생
                    if (currentSentenceIndex >= 0 && currentSentenceIndex < sentences.size() - 1) {
                        int nextSentenceIndex = currentSentenceIndex + 1;
                        android.util.Log.d("MainActivity", "Current sentence " + currentSentenceIndex + " completed, generating next sentence " + nextSentenceIndex);
                        generateAudio(String.valueOf(nextSentenceIndex), null, null);
                    } else {
                        // 모든 문장 재생 완료
                    updateStatus("Playback complete");
                    playButton.setEnabled(true);
                    playButton.setImageResource(R.drawable.ic_play);
                    currentChunkIndex = 0;
                    currentPlayingChunkAbsoluteIndex = -1;
                    currentChunkPlaybackProgress = 0.0;
                    isPaused = false;
                    // 하이라이트 제거
                    displayCurrentPage();
                    }
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
            
            // 청크 하이라이트 및 페이지 업데이트
            updateChunkHighlight();
            checkAndUpdatePageForChunk();
            
            // n페이지의 첫 번째 세그먼트 재생 시 자동 페이지 전환
            checkAndAutoNavigateToNextChunkPage(firstChunkAbsoluteIndex);
            
            // 재생 시점에 백그라운드 생성 시작 (n+1, n+2, n+3)
            int absoluteChunkIndex = firstChunkAbsoluteIndex + currentChunkIndex;
            startBackgroundGeneration(absoluteChunkIndex, selectedVoice, selectedSpeed);
            
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
            
            // 절대 청크 인덱스 업데이트
            currentPlayingChunkAbsoluteIndex = firstChunkAbsoluteIndex + currentChunkIndex;
            currentChunkPlaybackProgress = 0.0;
            
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(nextChunkFile.getAbsolutePath());
            mediaPlayer.prepare();
            
            mediaPlayer.setOnCompletionListener(mp -> {
                if (!isPaused && currentChunkIndex < audioChunks.size() - 1) {
                    playNextChunk();
                } else if (currentChunkIndex >= audioChunks.size() - 1) {
                    // 현재 문장의 모든 청크 재생 완료
                    // 다음 문장 찾기
                    int currentAbsoluteChunkIndex = firstChunkAbsoluteIndex + currentChunkIndex;
                    int currentSentenceIndex = -1;
                    
                    // 현재 청크에 해당하는 문장 찾기
                    for (int i = 0; i < sentenceToChunkMap.size(); i++) {
                        if (sentenceToChunkMap.get(i) == currentAbsoluteChunkIndex) {
                            currentSentenceIndex = i;
                            break;
                        }
                    }
                    
                    // 문장을 찾지 못하면 청크 텍스트로 문장 찾기
                    if (currentSentenceIndex < 0 && currentAbsoluteChunkIndex >= 0 && currentAbsoluteChunkIndex < allChunks.size()) {
                        String currentChunkText = allChunks.get(currentAbsoluteChunkIndex);
                        for (int i = 0; i < sentences.size(); i++) {
                            if (sentences.get(i).contains(currentChunkText) || currentChunkText.contains(sentences.get(i))) {
                                currentSentenceIndex = i;
                                break;
                            }
                        }
                    }
                    
                    // 다음 문장이 있으면 자동으로 생성 및 재생
                    if (currentSentenceIndex >= 0 && currentSentenceIndex < sentences.size() - 1) {
                        int nextSentenceIndex = currentSentenceIndex + 1;
                        android.util.Log.d("MainActivity", "Current sentence " + currentSentenceIndex + " completed, generating next sentence " + nextSentenceIndex);
                        generateAudio(String.valueOf(nextSentenceIndex), null, null);
                    } else {
                        // 모든 문장 재생 완료
                    updateStatus("Playback complete");
                    playButton.setEnabled(true);
                    playButton.setImageResource(R.drawable.ic_play);
                    currentChunkIndex = 0;
                    currentPlayingChunkAbsoluteIndex = -1;
                    currentChunkPlaybackProgress = 0.0;
                    isPaused = false;
                    // 하이라이트 제거
                    displayCurrentPage();
                    }
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
            int absoluteChunkIndex = firstChunkAbsoluteIndex + currentChunkIndex;
            updateStatus("Playing segment " + (absoluteChunkIndex + 1));
            
            // 재생 시작 시점에 해당 청크의 CPS/RTF 표시
            showChunkMetrics(absoluteChunkIndex);
            
            // 청크 하이라이트 및 페이지 업데이트
            updateChunkHighlight();
            checkAndUpdatePageForChunk();
            
            // n페이지의 첫 번째 세그먼트 재생 시 자동 페이지 전환
            checkAndAutoNavigateToNextChunkPage(absoluteChunkIndex);
            
            // n+1 재생 시점에 n+3을 백그라운드 생성에 추가
            addNextToBackgroundQueue(absoluteChunkIndex, selectedVoice, selectedSpeed);
            
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
                        int duration = mediaPlayer.getDuration();
                        
                        // 현재 청크의 재생 진행률 계산
                        if (duration > 0) {
                            currentChunkPlaybackProgress = (double) current / duration;
                        }
                        
                        // 현재 재생 중인 청크의 페이지 전환 확인
                        checkAndUpdatePageForChunk();
                        
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
                        
                        // 재생시간 표시 숨김
                        // if (timeDisplay != null) {
                        //     timeDisplay.setText(formatTime(currentChunkPosition) + " / " + formatTime(totalDuration));
                        // }
                    } catch (Exception e) {
                        // 무시
                    }
                }
                // 재생시간 표시 숨김
                // else if (timeDisplay != null) {
                //     // MediaPlayer가 없으면 00:00 / 전체 시간 표시
                //     int totalDuration = (int)(totalAudioDuration * 1000);
                //     timeDisplay.setText("00:00 / " + formatTime(totalDuration));
                // }
                
                progressHandler.postDelayed(this, 100); // 100ms마다 업데이트
            }
        });
    }
    
    /**
     * 현재 재생 중인 청크에 대해 페이지 전환 확인 및 처리
     */
    private void checkAndUpdatePageForChunk() {
        if (currentPlayingChunkAbsoluteIndex < 0 || pages.isEmpty()) {
            return;
        }
        
        // 현재 청크 텍스트 찾기
        String currentChunkText = null;
        if (currentChunkIndex >= 0 && currentChunkIndex < chunkTexts.size()) {
            currentChunkText = chunkTexts.get(currentChunkIndex);
        } else if (currentPlayingChunkAbsoluteIndex >= 0 && currentPlayingChunkAbsoluteIndex < allChunks.size()) {
            currentChunkText = allChunks.get(currentPlayingChunkAbsoluteIndex);
        }
        
        if (currentChunkText == null || currentChunkText.isEmpty()) {
            return;
        }
        
        // 청크가 속한 페이지 정보 찾기
        ChunkPageInfo chunkInfo = findChunkPages(currentChunkText, currentPlayingChunkAbsoluteIndex);
        
        if (chunkInfo.startPage < 0) {
            return;
        }
        
        // 여러 페이지에 걸쳐있는 경우만 자동 페이지 전환 처리
        // 단, 현재 페이지가 시작 페이지나 끝 페이지일 때만 작동
        if (chunkInfo.spansMultiplePages()) {
            // 현재 페이지가 시작 페이지나 끝 페이지 중 하나인지 확인
            boolean isOnChunkPage = (currentPageIndex == chunkInfo.startPage || currentPageIndex == chunkInfo.endPage);
            
            if (isOnChunkPage) {
                // 현재 페이지가 시작 페이지이고, 분할 비율만큼 재생되었으면 다음 페이지로
                if (currentPageIndex == chunkInfo.startPage && 
                    currentChunkPlaybackProgress >= chunkInfo.splitRatio) {
                    // 다음 페이지로 이동
                    if (chunkInfo.endPage > currentPageIndex && chunkInfo.endPage < pages.size()) {
                        currentPageIndex = chunkInfo.endPage;
                        displayCurrentPage();
                        updatePageIndicator();
                    }
                }
            }
        }
        // 한 페이지에만 있는 경우는 강제 페이지 이동하지 않음 (제거됨)
    }
    
    /**
     * 현재 재생 중인 세그먼트가 있는 페이지로 이동
     */
    private void navigateToCurrentPlayingChunkPage() {
        if (currentPlayingChunkAbsoluteIndex < 0 || pages.isEmpty() || allChunks.isEmpty()) {
            return;
        }
        
        // 현재 재생 중인 청크 텍스트 찾기
        String currentChunkText = null;
        if (currentChunkIndex >= 0 && currentChunkIndex < chunkTexts.size()) {
            currentChunkText = chunkTexts.get(currentChunkIndex);
        } else if (currentPlayingChunkAbsoluteIndex >= 0 && currentPlayingChunkAbsoluteIndex < allChunks.size()) {
            currentChunkText = allChunks.get(currentPlayingChunkAbsoluteIndex);
        }
        
        if (currentChunkText == null || currentChunkText.isEmpty()) {
            return;
        }
        
        // 청크가 속한 페이지 정보 찾기
        ChunkPageInfo chunkInfo = findChunkPages(currentChunkText, currentPlayingChunkAbsoluteIndex);
        
        if (chunkInfo.startPage < 0) {
            return;
        }
        
        // 현재 페이지가 청크가 있는 페이지가 아니면 시작 페이지로 이동
        if (currentPageIndex != chunkInfo.startPage && currentPageIndex != chunkInfo.endPage) {
            currentPageIndex = chunkInfo.startPage;
            displayCurrentPage();
            updatePageIndicator();
        }
    }
    
    /**
     * n페이지의 첫 번째 세그먼트 재생 시 자동 페이지 전환
     * n페이지의 첫 번째 세그먼트 재생할 때, 지금 n-1페이지를 보고 있다면 강제로 n페이지로 이동
     * 읽기 시작할 때 한 번만 적용됨
     * 
     * @param currentChunkAbsoluteIndex 현재 재생 중인 청크의 절대 인덱스
     */
    private void checkAndAutoNavigateToNextChunkPage(int currentChunkAbsoluteIndex) {
        if (currentChunkAbsoluteIndex < 0 || pages.isEmpty() || allChunks.isEmpty()) {
            return;
        }
        
        // 현재 재생 중인 청크의 페이지 정보 찾기
        String currentChunkText = null;
        if (currentChunkIndex >= 0 && currentChunkIndex < chunkTexts.size()) {
            currentChunkText = chunkTexts.get(currentChunkIndex);
        } else if (currentChunkAbsoluteIndex >= 0 && currentChunkAbsoluteIndex < allChunks.size()) {
            currentChunkText = allChunks.get(currentChunkAbsoluteIndex);
        }
        
        if (currentChunkText == null || currentChunkText.isEmpty()) {
            return;
        }
        
        ChunkPageInfo currentChunkInfo = findChunkPages(currentChunkText, currentChunkAbsoluteIndex);
        if (currentChunkInfo.startPage < 0) {
            return;
        }
        
        int chunkPage = currentChunkInfo.startPage;
        
        // 현재 청크가 n페이지의 첫 번째 세그먼트인지 확인
        // n페이지에 있는 다른 청크들 중에서 현재 청크보다 이전 청크가 없는지 확인
        boolean isFirstChunkOnPage = true;
        for (int i = 0; i < currentChunkAbsoluteIndex; i++) {
            if (i < allChunks.size()) {
                String otherChunkText = allChunks.get(i);
                if (otherChunkText != null && !otherChunkText.isEmpty()) {
                    ChunkPageInfo otherChunkInfo = findChunkPages(otherChunkText, i);
                    if (otherChunkInfo.startPage == chunkPage) {
                        // n페이지에 더 이전 청크가 있음
                        isFirstChunkOnPage = false;
                        break;
                    }
                }
            }
        }
        
        // 현재 보고 있는 페이지가 n-1인지 확인
        boolean isViewingPreviousPage = (currentPageIndex == chunkPage - 1);
        
        android.util.Log.d("MainActivity", "checkAndAutoNavigateToNextChunkPage: " +
            "chunk=" + currentChunkAbsoluteIndex + ", " +
            "chunkPage=" + chunkPage + ", " +
            "currentViewingPage=" + currentPageIndex + ", " +
            "isFirstChunkOnPage=" + isFirstChunkOnPage + ", " +
            "isViewingPreviousPage=" + isViewingPreviousPage);
        
        // 조건이 맞으면 n페이지로 자동 전환
        // 재생 시작 시점에만 호출되므로 중복 체크 불필요
        if (isFirstChunkOnPage && isViewingPreviousPage && chunkPage >= 0 && chunkPage < pages.size()) {
            currentPageIndex = chunkPage;
            displayCurrentPage();
            updatePageIndicator();
            android.util.Log.d("MainActivity", "Auto-navigated from page " + (chunkPage - 1) + 
                " to page " + chunkPage + " for first chunk " + currentChunkAbsoluteIndex + " on page " + chunkPage);
        }
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
            currentPlayingChunkAbsoluteIndex = -1;
            currentChunkPlaybackProgress = 0.0;
            isPaused = false;
            updateStatus("Stopped");
            playButton.setEnabled(true);
            playButton.setImageResource(R.drawable.ic_play);
            // 하이라이트 제거
            displayCurrentPage();
        }
    }
    
    private void updateStatus(String status) {
        updateStatus(status, false);
    }
    
    private void updateStatus(String status, boolean isError) {
        // '...' 제거
        String cleanedStatus = status.replace("...", "");
        if (statusText != null) {
        statusText.setText(cleanedStatus);
        }
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
            
            // 쌍따옴표로 둘러싸인 부분을 먼저 보호
            java.util.Map<String, String> quoteMap = new java.util.HashMap<>();
            String quoteProtectedLine = trimmedLine;
            int quoteMarkerIndex = 0;
            
            // 유니코드 쌍따옴표 패턴: 열림 " (U+201C), 닫힘 " (U+201D)
            Pattern quotePattern = Pattern.compile("\u201C[^\u201D]*\u201D");
            java.util.regex.Matcher quoteMatcher = quotePattern.matcher(trimmedLine);
            StringBuffer quoteSb = new StringBuffer();
            
            while (quoteMatcher.find()) {
                String quotedText = quoteMatcher.group();
                String marker = "QUOTE_MARKER_" + quoteMarkerIndex;
                quoteMap.put(marker, quotedText);
                quoteMatcher.appendReplacement(quoteSb, marker);
                quoteMarkerIndex++;
            }
            quoteMatcher.appendTail(quoteSb);
            quoteProtectedLine = quoteSb.toString();
            
            // 약어 뒤의 마침표를 임시로 치환하여 보호
            String protectedLine = quoteProtectedLine;
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
            abbrevMatcher = Pattern.compile(abbrevRegex).matcher(quoteProtectedLine);
            markerIndex = 0;
            while (abbrevMatcher.find()) {
                String abbrev = abbrevMatcher.group();
                markerMap.put("ABBREV_MARKER_" + markerIndex, abbrev);
                markerIndex++;
            }
            
            // 쌍따옴표 마커 위치 찾기
            Pattern quoteMarkerPattern = Pattern.compile("QUOTE_MARKER_\\d+");
            java.util.regex.Matcher quoteMarkerMatcher = quoteMarkerPattern.matcher(protectedLine);
            java.util.List<int[]> quoteMarkerRanges = new ArrayList<>();
            while (quoteMarkerMatcher.find()) {
                quoteMarkerRanges.add(new int[]{quoteMarkerMatcher.start(), quoteMarkerMatcher.end()});
            }
            
            // 문장 구분자로 분할하되, 쌍따옴표 마커 내부는 분할하지 않음
            List<String> tempSentencesList = new ArrayList<>();
            int lastSplitPos = 0;
            
            // 문장 구분자 패턴
            Pattern sentenceEndPattern = Pattern.compile("(?<=\\.{2,10})\\s+|(?<=[.!?])(?!\\.)\\s+");
            java.util.regex.Matcher sentenceEndMatcher = sentenceEndPattern.matcher(protectedLine);
            
            while (sentenceEndMatcher.find()) {
                int matchStart = sentenceEndMatcher.start();
                int matchEnd = sentenceEndMatcher.end();
                
                // 이 위치가 쌍따옴표 마커 내부에 있는지 확인
                boolean insideQuoteMarker = false;
                for (int[] range : quoteMarkerRanges) {
                    if (matchStart >= range[0] && matchStart < range[1]) {
                        insideQuoteMarker = true;
                        break;
                    }
                }
                
                if (!insideQuoteMarker) {
                    // 쌍따옴표 마커 외부에서만 분할
                    String sentence = protectedLine.substring(lastSplitPos, matchStart).trim();
                    if (!sentence.isEmpty()) {
                        tempSentencesList.add(sentence);
                    }
                    lastSplitPos = matchEnd;
                }
            }
            
            // 마지막 부분 추가
            String lastSentence = protectedLine.substring(lastSplitPos).trim();
            if (!lastSentence.isEmpty()) {
                tempSentencesList.add(lastSentence);
            }
            
            // 분할된 문장이 없으면 전체를 하나의 문장으로 처리
            if (tempSentencesList.isEmpty()) {
                tempSentencesList.add(protectedLine.trim());
            }
            
            for (String sentence : tempSentencesList) {
                // 임시 마커를 원래 약어로 복원
                sentence = sentence.trim();
                for (java.util.Map.Entry<String, String> entry : markerMap.entrySet()) {
                    sentence = sentence.replace(entry.getKey(), entry.getValue());
                }
                // 쌍따옴표 마커를 원래 쌍따옴표로 복원
                for (java.util.Map.Entry<String, String> entry : quoteMap.entrySet()) {
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
        
        // 전체 텍스트를 청크로 나누어 절대 청크 번호 결정
        allChunks = chunkText(fullText);
        
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
            
            // 최대 줄 수 계산 (앱 실행 시 한 번만 계산)
            if (pageHeight > 0 && textDisplay != null) {
                calculateMaxLinesPerPage();
            }
            
            if (pageWidth > 0 && pageHeight > 0) {
                calculatePages();
                displayCurrentPage();
                updatePageIndicator();
            }
        });
    }
    
    /**
     * 최대 줄 수 계산 - 텍스트뷰 높이와 폰트/줄간격 정보를 기반으로 계산
     * (최대 줄 수 - 9)을 페이지당 줄수로 사용
     */
    private void calculateMaxLinesPerPage() {
        if (textDisplay == null || pageHeight <= 0) {
            maxLinesPerPage = 14; // 기본값
            return;
        }
        
        // TextView의 Paint 설정 가져오기
        TextPaint textPaint = new TextPaint(textDisplay.getPaint());
        float textSize = textPaint.getTextSize();
        
        // TextView의 줄간격 정보 가져오기
        float lineSpacingMultiplier = textDisplay.getLineSpacingMultiplier();
        float lineSpacingExtra = textDisplay.getLineSpacingExtra();
        
        // 한 줄의 높이 계산
        // lineSpacingMultiplier는 기본 텍스트 높이에 곱해지는 배수
        // lineSpacingExtra는 추가로 더해지는 여백
        float lineHeight = textSize * lineSpacingMultiplier + lineSpacingExtra;
        
        // 최대 줄 수 계산 (소수점 버림)
        int maxLines = (int) (pageHeight / lineHeight);
        
        // (최대 줄 수 - 9)을 페이지당 줄수로 사용
        maxLinesPerPage = Math.max(1, maxLines - 9); // 최소 1줄 보장
        
        android.util.Log.d("MainActivity", "Calculated maxLinesPerPage: " + maxLinesPerPage + 
            " (maxLines: " + maxLines + ", pageHeight: " + pageHeight + 
            ", lineHeight: " + lineHeight + ")");
    }
    
    /**
     * 페이지 계산 - StaticLayout을 사용하여 계산된 줄 수 기준으로 페이지 분리
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
        
        // 계산된 줄 수씩 페이지 분할
        while (currentLine < totalLines) {
            // 현재 페이지의 마지막 줄 (maxLinesPerPage줄째, 인덱스는 maxLinesPerPage - 1)
            int pageEndLine = Math.min(currentLine + maxLinesPerPage - 1, totalLines - 1);
            
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
            
            // 정확히 maxLinesPerPage줄이 되도록 조정
            int pageLineCount = pageLayout.getLineCount();
            if (pageLineCount != maxLinesPerPage && pageEndLine < totalLines - 1) {
                // maxLinesPerPage줄이 아니면 조정
                if (pageLineCount > maxLinesPerPage) {
                    // maxLinesPerPage줄을 넘으면 이전 줄의 끝으로 조정
                    if (pageEndLine > currentLine) {
                        pageEndLine = pageEndLine - 1;
                        textEnd = fullLayout.getLineEnd(pageEndLine);
                        pageText = fullText.substring(textStart, textEnd);
                    }
                } else if (pageLineCount < maxLinesPerPage && pageEndLine < totalLines - 1) {
                    // maxLinesPerPage줄보다 적으면 다음 줄까지 포함 시도
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
                    if (nextPageLayout.getLineCount() <= maxLinesPerPage) {
                        pageEndLine = nextLineEnd;
                        textEnd = nextTextEnd;
                        pageText = nextPageText;
                    }
                }
            }
            
            // 최소 17줄 보장: 페이지가 17줄보다 작게 잘리면 다음 페이지 내용을 가져와서 17줄까지 채우기
            pageLayout = new StaticLayout(
                pageText,
                textPaintForLayout,
                (int) pageWidth,
                Layout.Alignment.ALIGN_NORMAL,
                lineSpacingMultiplier,
                lineSpacingExtra,
                false
            );
            pageLineCount = pageLayout.getLineCount();
            int minLinesPerPage = 17; // 최소 17줄 보장
            
            if (pageLineCount < minLinesPerPage && pageEndLine < totalLines - 1) {
                // 17줄보다 적으면 다음 줄들을 추가하여 최소 17줄까지 채우기
                int targetLineEnd = Math.min(currentLine + minLinesPerPage - 1, totalLines - 1);
                int targetTextEnd = fullLayout.getLineEnd(targetLineEnd);
                String targetPageText = fullText.substring(textStart, targetTextEnd);
                
                StaticLayout targetPageLayout = new StaticLayout(
                    targetPageText,
                    textPaintForLayout,
                    (int) pageWidth,
                    Layout.Alignment.ALIGN_NORMAL,
                    lineSpacingMultiplier,
                    lineSpacingExtra,
                    false
                );
                
                int targetLineCount = targetPageLayout.getLineCount();
                if (targetLineCount >= minLinesPerPage || targetLineEnd >= totalLines - 1) {
                    // 17줄 이상이 되거나 마지막 페이지인 경우
                    pageEndLine = targetLineEnd;
                    textEnd = targetTextEnd;
                    pageText = targetPageText;
                } else {
                    // 17줄을 채우기 위해 더 많은 줄 추가 시도
                    while (targetLineEnd < totalLines - 1 && targetLineCount < minLinesPerPage) {
                        targetLineEnd = Math.min(targetLineEnd + 1, totalLines - 1);
                        targetTextEnd = fullLayout.getLineEnd(targetLineEnd);
                        targetPageText = fullText.substring(textStart, targetTextEnd);
                        targetPageLayout = new StaticLayout(
                            targetPageText,
                            textPaintForLayout,
                            (int) pageWidth,
                            Layout.Alignment.ALIGN_NORMAL,
                            lineSpacingMultiplier,
                            lineSpacingExtra,
                            false
                        );
                        targetLineCount = targetPageLayout.getLineCount();
                    }
                    pageEndLine = targetLineEnd;
                    textEnd = targetTextEnd;
                    pageText = targetPageText;
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
        if (pages.isEmpty() || currentPageIndex < 0 || currentPageIndex >= pages.size()) {
            if (textDisplay != null) {
            textDisplay.setText("");
            }
            return;
        }
        
        String pageText = pages.get(currentPageIndex);
        
        // 페이지 첫 줄의 앞쪽 공백 제거
        if (pageText != null && !pageText.isEmpty()) {
            int firstNewlineIndex = pageText.indexOf('\n');
            if (firstNewlineIndex >= 0) {
                // 첫 줄이 있는 경우
                String firstLine = pageText.substring(0, firstNewlineIndex);
                String restOfText = pageText.substring(firstNewlineIndex);
                // 첫 줄의 앞쪽 공백 제거
                String trimmedFirstLine = firstLine.replaceAll("^\\s+", "");
                pageText = trimmedFirstLine + restOfText;
            } else {
                // 첫 줄만 있는 경우
                pageText = pageText.replaceAll("^\\s+", "");
            }
        }
        
        // StaticLayout으로 계산한 텍스트를 그대로 사용 (줄바꿈 제거하지 않음)
        // 클릭 가능한 텍스트 생성 (스타일 없는 ClickableSpan)
        SpannableString spannable = new SpannableString(pageText);
        
        // 모든 문장을 확인하여 페이지 텍스트에 나타나는 부분을 클릭 가능하게 만들기
        // 페이지에 걸쳐있는 문장도 클릭 가능하도록 처리
        for (String sentence : sentences) {
            final String sentenceText = sentence.trim();
            if (sentenceText.isEmpty()) {
                continue;
            }
            
            // 페이지 텍스트에서 문장의 일부라도 나타나는지 확인
            // 페이지에 걸쳐있는 경우에도 클릭 가능하도록 부분 매칭 허용
            int sentenceStartInPage = pageText.indexOf(sentenceText);
            
            // 전체 문장이 페이지에 없으면 간단한 부분 매칭만 시도 (성능 최적화)
            if (sentenceStartInPage < 0) {
                // 문장의 앞부분만 확인 (최대 50글자, 성능 최적화를 위해 루프 제거)
                if (sentenceText.length() >= 10) {
                    String sentencePart = sentenceText.substring(0, Math.min(50, sentenceText.length()));
                    sentenceStartInPage = pageText.indexOf(sentencePart);
                    if (sentenceStartInPage >= 0) {
                        // 부분 매칭을 찾았지만, 전체 문장을 클릭 가능하게 만들기 위해
                        // 페이지에 나타나는 부분만 클릭 가능하게 설정
                        int sentenceEndInPage = sentenceStartInPage + sentencePart.length();
                        if (sentenceEndInPage > spannable.length()) {
                            sentenceEndInPage = spannable.length();
                        }
                        
                        if (sentenceStartInPage < sentenceEndInPage && sentenceStartInPage >= 0) {
                            // 전체 문장을 저장하여 클릭 시 전체 문장 재생
                            final String fullSentence = sentenceText;
                            spannable.setSpan(new ClickableSpan() {
                                @Override
                                public void onClick(View widget) {
                                    // 스크롤이 아닌 실제 클릭인지 확인
                                    if (isActualClick()) {
                                    handleSentenceClick(fullSentence);
                                    }
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
            } else {
                // 전체 문장이 페이지에 나타나는 경우
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
        
        // 현재 재생 중인 청크 하이라이트 적용
        if (currentPlayingChunkAbsoluteIndex >= 0) {
            String currentChunkText = null;
            if (currentChunkIndex >= 0 && currentChunkIndex < chunkTexts.size()) {
                currentChunkText = chunkTexts.get(currentChunkIndex);
            } else if (currentPlayingChunkAbsoluteIndex >= 0 && currentPlayingChunkAbsoluteIndex < allChunks.size()) {
                currentChunkText = allChunks.get(currentPlayingChunkAbsoluteIndex);
            }
            
            if (currentChunkText != null && !currentChunkText.isEmpty()) {
                ChunkPageInfo chunkInfo = findChunkPages(currentChunkText, currentPlayingChunkAbsoluteIndex);
                
                // 현재 페이지에 청크가 나타나는지 확인
                if (currentPageIndex == chunkInfo.startPage || currentPageIndex == chunkInfo.endPage) {
                    int highlightStart = -1;
                    int highlightEnd = -1;
                    
                    if (currentPageIndex == chunkInfo.startPage) {
                        // 시작 페이지인 경우
                        highlightStart = chunkInfo.chunkStartInStartPage;
                        if (chunkInfo.spansMultiplePages()) {
                            // 여러 페이지에 걸쳐있으면 현재 페이지의 끝까지
                            highlightEnd = pageText.length();
                        } else {
                            // 한 페이지에만 있으면 청크의 끝까지
                            highlightEnd = Math.min(chunkInfo.chunkEndInEndPage, pageText.length());
                        }
                    } else if (currentPageIndex == chunkInfo.endPage && chunkInfo.spansMultiplePages()) {
                        // 끝 페이지인 경우 (여러 페이지에 걸쳐있는 경우)
                        highlightStart = 0;
                        highlightEnd = Math.min(chunkInfo.chunkEndInEndPage, pageText.length());
                    }
                    
                    if (highlightStart >= 0 && highlightEnd > highlightStart && highlightEnd <= spannable.length()) {
                        // #eee 배경색 적용
                        spannable.setSpan(new BackgroundColorSpan(0xFFEEEEEE), 
                            highlightStart, highlightEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
        }
        
        textDisplay.setText(spannable);
        
        // 페이지 번호 업데이트
        updatePageNumber();
    }
    
    /**
     * 페이지 번호 업데이트
     */
    private void updatePageNumber() {
        if (pageNumberDisplay == null || pages.isEmpty()) {
            if (pageNumberDisplay != null) {
                pageNumberDisplay.setText("");
            }
            return;
        }
        
        int currentPageNumber = currentPageIndex + 1;
        int totalPages = pages.size();
        String pageNumberText = "—  " + currentPageNumber + "  —";
        
        // 본문 폰트 크기의 70%로 설정
        float baseTextSize = textDisplay.getTextSize();
        float pageNumberTextSize = baseTextSize * 0.7f;
        pageNumberDisplay.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, pageNumberTextSize);
        
        pageNumberDisplay.setText(pageNumberText);
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
        int targetAbsoluteChunkIndex = -1; // 절대 청크 번호
        int matchedSentenceIndex = -1;
        
        // 정확히 일치하는 문장 찾기
        for (int i = 0; i < sentences.size(); i++) {
            String s = sentences.get(i).trim();
            if (s.equals(clickedSentence)) {
                matchedSentenceIndex = i;
                // 해당 문장의 절대 청크 번호 찾기
                if (i < sentenceToChunkMap.size()) {
                    targetAbsoluteChunkIndex = sentenceToChunkMap.get(i);
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
                        targetAbsoluteChunkIndex = sentenceToChunkMap.get(i);
                    }
                    break;
                }
            }
        }
        
        // 절대 청크 번호로 파일 찾기 (현재 선택된 보이스)
        if (targetAbsoluteChunkIndex >= 0) {
            File chunkFile = findChunkFile(selectedVoice, selectedSpeed, targetAbsoluteChunkIndex);
            
            if (chunkFile != null && chunkFile.exists()) {
                // 청크가 여러 페이지에 걸쳐있는지 확인하여 페이지 이동 처리
                String chunkText = null;
                if (targetAbsoluteChunkIndex < allChunks.size()) {
                    chunkText = allChunks.get(targetAbsoluteChunkIndex);
                }
                
                if (chunkText != null && !chunkText.isEmpty()) {
                    ChunkPageInfo chunkInfo = findChunkPages(chunkText, targetAbsoluteChunkIndex);
                    
                    // 여러 페이지에 걸쳐있고, 현재 페이지가 끝 페이지인 경우
                    if (chunkInfo.spansMultiplePages() && currentPageIndex == chunkInfo.endPage) {
                        // 문장이 시작 페이지에 있는지 확인
                        int sentenceStartInFullText = fullText.indexOf(clickedSentence);
                        if (sentenceStartInFullText >= 0) {
                            // 문장이 시작 페이지에 있으면 시작 페이지로 이동
                            int currentPos = 0;
                            for (int pageIdx = 0; pageIdx < pages.size(); pageIdx++) {
                                String pageText = pages.get(pageIdx);
                                int pageStart = currentPos;
                                int pageEnd = currentPos + pageText.length();
                                
                                if (sentenceStartInFullText >= pageStart && sentenceStartInFullText < pageEnd) {
                                    if (pageIdx != currentPageIndex) {
                                        currentPageIndex = pageIdx;
                                        displayCurrentPage();
                                        updatePageIndicator();
                                    }
                                    break;
                                }
                                
                                currentPos = pageEnd;
                            }
                        }
                    }
                }
                
                // 기존 오디오 파일이 있으면 바로 재생
                // 기존 생성 작업만 취소 (오디오 파일은 유지)
                cancelGenerationOnly();
                // 선택한 문장부터 생성 및 재생
                generateAudio(clickedSentence, null, null);
            } else {
                // 해당 문장부터 생성 및 재생
                // 기존 생성 작업 즉시 취소
                cancelGenerationOnly();
                // 선택한 문장부터 생성 및 재생
                generateAudio(clickedSentence, null, null);
            }
        } else {
            // 해당 문장부터 생성 및 재생
            // 기존 생성 작업 즉시 취소
            cancelGenerationOnly();
            // 선택한 문장을 최우선 순위로 생성
            generateAudio(clickedSentence, null, null);
        }
        
        // 클릭한 세그먼트에 #ccc 배경색 적용 (0.5초) - generateAudio 호출 후에 약간의 지연을 두고 적용
        // Handler를 사용하여 UI 업데이트 후 하이라이트 적용 (50ms 지연)
        mainHandler.postDelayed(() -> {
            highlightClickedSegment(clickedSentence);
        }, 50);
        
        // 기존 하이라이트 제거 Runnable 취소
        if (highlightRemoveRunnable != null) {
            mainHandler.removeCallbacks(highlightRemoveRunnable);
        }
        
        // 0.5초 후 하이라이트 제거
        highlightRemoveRunnable = () -> {
            if (textDisplay != null) {
                // 텍스트를 다시 설정하여 하이라이트 제거
                displayCurrentPage();
            }
            highlightRemoveRunnable = null;
        };
        mainHandler.postDelayed(highlightRemoveRunnable, 500);
    }
    
    /**
     * 클릭한 세그먼트에 #ccc 배경색 적용 (재생 중인 세그먼트 배경 로직 재사용)
     */
    private void highlightClickedSegment(String clickedSentence) {
        if (textDisplay == null || pages.isEmpty() || clickedSentence == null || clickedSentence.trim().isEmpty()) {
            return;
        }
        
        // 현재 페이지 텍스트 가져오기
        String pageText = pages.get(currentPageIndex);
        if (pageText == null || pageText.isEmpty()) {
                    return;
                }
                
        // 클릭한 문장을 현재 페이지에서 찾기
        String clickedSentenceTrimmed = clickedSentence.trim();
        int sentenceStartInPage = pageText.indexOf(clickedSentenceTrimmed);
        
        // 정확히 일치하지 않으면 부분 매칭 시도
        if (sentenceStartInPage < 0 && clickedSentenceTrimmed.length() >= 10) {
            String sentencePart = clickedSentenceTrimmed.substring(0, Math.min(50, clickedSentenceTrimmed.length()));
            sentenceStartInPage = pageText.indexOf(sentencePart);
        }
        
        if (sentenceStartInPage < 0) {
                        return;
                    }
                    
        // 현재 표시된 텍스트 가져오기
        CharSequence currentText = textDisplay.getText();
        if (!(currentText instanceof SpannableString)) {
                        return;
                    }
                    
        SpannableString spannable = (SpannableString) currentText;
        
        // 하이라이트 범위 계산
        int highlightStart = sentenceStartInPage;
        int highlightEnd = sentenceStartInPage + clickedSentenceTrimmed.length();
        
        // 범위가 spannable 길이를 초과하지 않도록 조정
        if (highlightEnd > spannable.length()) {
            highlightEnd = spannable.length();
        }
        
        if (highlightStart >= 0 && highlightEnd > highlightStart && highlightEnd <= spannable.length()) {
            // #ccc 배경색 적용 (0xFFCCCCCC)
            spannable.setSpan(new BackgroundColorSpan(0xFFCCCCCC), 
                highlightStart, highlightEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textDisplay.setText(spannable);
        }
    }
    
    /**
     * 특정 문장부터 생성 및 재생
     * 어떤 문장을, 어떤 목소리로, 어떤 속도로 생성 및 재생
     * 만약 이미 생성된 파일이 있다면 새로 생성하지 않고, 기존 파일 재생
     * 멈추었을 때 생성된 파일들 유지
     * 
     * @param targetSentence 재생할 문장 (문자열 또는 문장 인덱스)
     * @param voice 목소리 (null이면 selectedVoice 사용)
     * @param speed 속도 (null이면 selectedSpeed 사용)
     */
    private void generateAudio(String targetSentence, String voice, String speed) {
        // 목소리 설정 (파라미터가 없으면 selectedVoice 사용)
        final String targetVoice = (voice != null) ? voice : selectedVoice;
        // 속도 설정 (파라미터가 없으면 selectedSpeed 사용)
        final String targetSpeed = (speed != null) ? speed : selectedSpeed;
        final double speechLength = speedToSpeechLength(targetSpeed);
        
        // 문장 위치 찾기
        int sentenceIndex = -1;
        
        // targetSentence가 숫자 문자열인지 확인 (문장 인덱스로 사용)
        try {
            int index = Integer.parseInt(targetSentence.trim());
            if (index >= 0 && index < sentences.size()) {
                sentenceIndex = index;
            }
        } catch (NumberFormatException e) {
            // 숫자가 아니면 문장 텍스트로 검색
        for (int i = 0; i < sentences.size(); i++) {
            if (sentences.get(i).contains(targetSentence) || targetSentence.contains(sentences.get(i))) {
                sentenceIndex = i;
                break;
                }
            }
        }
        
        if (sentenceIndex < 0) {
            // 문장을 찾을 수 없으면 0번 문장 사용
            sentenceIndex = 0;
        }
        
        // 해당 문장부터의 텍스트 생성
        StringBuilder textFromSentence = new StringBuilder();
        for (int i = sentenceIndex; i < sentences.size(); i++) {
            if (textFromSentence.length() > 0) {
                textFromSentence.append(" ");
            }
            textFromSentence.append(sentences.get(i));
        }
        
        // 기존 생성 작업만 취소 (오디오 파일은 유지)
        cancelGenerationOnly();
        
        // 백그라운드 생성 작업 중단
        cancelBackgroundGeneration();
        
        // 재생 중지
        stopAudio();
        
        // 재생리스트만 초기화 (오디오 파일은 유지)
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
        // Map은 초기화하지 않음 (기존 파일 재생 시 저장된 CPS/RTF 사용)
        // chunkCPSMap.clear();
        // chunkRTFMap.clear();
        chunkPageInfoCache.clear(); // 청크 페이지 정보 캐시 초기화
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
        
        // 전체 텍스트의 청크 리스트 사용 (절대 번호 기준)
        // allChunks는 setupPages()에서 이미 생성되어 있음
        if (allChunks.isEmpty()) {
            allChunks = chunkText(fullText);
        }
        
        // 현재 문장이 속한 절대 청크 번호 찾기
        int targetAbsoluteChunkIndex = -1;
        if (sentenceIndex < sentenceToChunkMap.size() && sentenceToChunkMap.get(sentenceIndex) >= 0) {
            targetAbsoluteChunkIndex = sentenceToChunkMap.get(sentenceIndex);
        } else {
            // 매핑이 없으면 전체 청크 리스트에서 찾기
            String targetSentenceText = sentences.get(sentenceIndex);
            for (int i = 0; i < allChunks.size(); i++) {
                if (allChunks.get(i).contains(targetSentenceText)) {
                    targetAbsoluteChunkIndex = i;
                    break;
                }
            }
        }
        
        // 절대 청크 번호를 찾지 못하면 처음부터 생성
        if (targetAbsoluteChunkIndex < 0) {
            targetAbsoluteChunkIndex = 0;
        }
        
        // 현재 생성할 청크의 절대 시작 인덱스
        firstChunkAbsoluteIndex = targetAbsoluteChunkIndex;
        
        // 해당 절대 청크 번호부터의 청크 리스트 생성
        List<String> chunksFromIndex = new ArrayList<>();
        if (targetAbsoluteChunkIndex < allChunks.size()) {
            for (int i = targetAbsoluteChunkIndex; i < allChunks.size(); i++) {
                chunksFromIndex.add(allChunks.get(i));
            }
        }
        
        // 첫 번째 청크 파일이 이미 있는지 확인
        File firstChunkFile = findChunkFile(targetVoice, targetSpeed, targetAbsoluteChunkIndex);
        
        // 첫 번째 청크 파일이 있으면 바로 재생
        if (firstChunkFile != null && firstChunkFile.exists()) {
            // 기존 오디오 파일이 있으면 바로 재생
            android.util.Log.d("MainActivity", "Existing audio file found for chunk " + (targetAbsoluteChunkIndex + 1) + ", playing immediately");
            
            // 재생 중지
            stopAudio();
            
            // 재생리스트 초기화
            audioChunks.clear();
            chunkTexts.clear();
            currentChunkIndex = 0;
            
            // 첫 번째 청크 추가
            audioChunks.add(firstChunkFile);
            if (targetAbsoluteChunkIndex < allChunks.size()) {
                chunkTexts.add(allChunks.get(targetAbsoluteChunkIndex));
            }
            
            // 첫 번째 청크의 절대 인덱스 설정
            firstChunkAbsoluteIndex = targetAbsoluteChunkIndex;
            
            // 문장-청크 매핑 업데이트
            if (startSentenceIndex < sentenceToChunkMap.size()) {
                sentenceToChunkMap.set(startSentenceIndex, targetAbsoluteChunkIndex);
            }
            
            // 첫 번째 청크 즉시 재생
            playFirstChunk(firstChunkFile, allChunks.get(targetAbsoluteChunkIndex), 0.0);
            updateStatus("Playing segment " + (targetAbsoluteChunkIndex + 1) + "...");
            
            // 재생 시점에 백그라운드 생성 시작 (n+1, n+2, n+3)
            startBackgroundGeneration(targetAbsoluteChunkIndex, targetVoice, targetSpeed);
            
            return; // 첫 번째 청크가 있으면 여기서 종료
        }
        
        // 첫 번째 청크 파일이 없으면 생성 시작
        // 즉시 상태 업데이트 (백그라운드 스레드 시작 전)
        updateStatus("Generating segment " + (targetAbsoluteChunkIndex + 1) + "...");
        // 첫 번째 청크 생성 중일 때 gen 아이콘 표시
        playButton.setImageResource(R.drawable.ic_gen);
        
        final List<String> finalChunks = chunksFromIndex;
        final int finalStartAbsoluteIndex = targetAbsoluteChunkIndex;
        // 생성 시작 시점의 목소리 저장 (목소리 변경 감지용)
        final String generationVoice = targetVoice;
        
        currentGenerationTask = executorService.submit(() -> {
            try {
                List<String> chunks = finalChunks;
                mainHandler.post(() -> updateStatus("Generating " + chunks.size() + " segments..."));
                
                if (chunks.isEmpty()) {
                    throw new Exception("Chunk generation failed");
                }
                
                // 첫 번째 청크 생성 및 즉시 재생 (이미 생성된 파일이 있으면 재사용)
                String firstChunk = chunks.get(0);
                // 상태는 이미 업데이트되었으므로 여기서는 Play 버튼만 업데이트
                mainHandler.post(() -> {
                    // 첫 번째 청크 생성 중일 때 gen 아이콘 표시 (이미 설정되었지만 확실히 하기 위해)
                    playButton.setImageResource(R.drawable.ic_gen);
                });
                
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                
                // 절대 청크 번호 사용
                int absoluteChunkIndex = finalStartAbsoluteIndex;
                File firstChunkFile2 = findChunkFile(generationVoice, targetSpeed, absoluteChunkIndex);
                double firstChunkProcessingTime2 = 0.0;
                
                // 파일이 없으면 생성
                if (firstChunkFile2 == null || !firstChunkFile2.exists()) {
                    long firstChunkStartTime2 = System.currentTimeMillis();
                    byte[] firstAudioData = ttsEngine.generate(firstChunk, generationVoice, speechLength);
                    
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    
                    if (firstAudioData == null || firstAudioData.length == 0) {
                        throw new Exception("First chunk generation failed");
                    }
                    
                    long firstChunkEndTime2 = System.currentTimeMillis();
                    firstChunkProcessingTime2 = (firstChunkEndTime2 - firstChunkStartTime2) / 1000.0;
                    chunkProcessingTimes.add(firstChunkProcessingTime2);
                    totalChunkProcessingTime += firstChunkProcessingTime2;
                    
                    firstChunkFile2 = saveChunk(firstAudioData, absoluteChunkIndex, generationVoice, targetSpeed);
                    
                    // CPS/RTF 저장 (임시값, playFirstChunk에서 정확한 오디오 길이로 재계산됨)
                    long fileSize = firstChunkFile2.length();
                    double estimatedDuration = (fileSize > 44) ? (fileSize - 44) / (24000.0 * 2.0) : 0;
                    // Map에 저장 (playFirstChunk에서 정확한 값으로 업데이트됨)
                    saveChunkMetrics(absoluteChunkIndex, firstChunk, firstChunkProcessingTime2, estimatedDuration);
                }
                
                audioChunks.add(firstChunkFile2);
                chunkTexts.add(firstChunk); // 청크 텍스트 저장
                
                // 문장-청크 매핑 업데이트 (절대 번호 사용)
                mapChunksToSentencesWithAbsoluteIndex(chunks, startSentenceIndex, finalStartAbsoluteIndex);
                
                // 람다 표현식에서 사용하기 위해 final 변수로 복사
                final File finalFirstChunkFile = firstChunkFile2;
                final double finalFirstChunkProcessingTime2 = firstChunkProcessingTime2;
                
                // 첫 번째 청크 즉시 재생 (오디오 길이 계산은 playFirstChunk 내부에서 수행)
                // 선택한 문장의 청크 생성 완료 후 즉시 재생 시작
                mainHandler.post(() -> {
                    if (!Thread.currentThread().isInterrupted()) {
                        playFirstChunk(finalFirstChunkFile, firstChunk, finalFirstChunkProcessingTime2);
                        updateStatus("Playing segment " + (finalStartAbsoluteIndex + 1) + "...");
                        
                        // 재생 시점에 백그라운드 생성 시작 (n+1, n+2, n+3)
                        startBackgroundGeneration(finalStartAbsoluteIndex, generationVoice, targetSpeed);
                            }
                        });
                
                mainHandler.post(() -> {
                    updateStatus("Segment generated and playing");
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
     * 백그라운드에서 다음 세그먼트들을 생성
     * @param currentAbsoluteChunkIndex 현재 재생 중인 청크의 절대 인덱스
     * @param voice 생성에 사용할 목소리
     */
    private void startBackgroundGeneration(int currentAbsoluteChunkIndex, String voice, String speed) {
        // 기존 백그라운드 생성 작업 취소
        cancelBackgroundGeneration();
        
        // 현재 청크에 해당하는 문장 인덱스 찾기
        int currentSentenceIndex = -1;
        for (int i = 0; i < sentenceToChunkMap.size(); i++) {
            if (sentenceToChunkMap.get(i) == currentAbsoluteChunkIndex) {
                currentSentenceIndex = i;
                break;
            }
        }
        
        // 문장을 찾지 못하면 청크 텍스트로 문장 찾기
        if (currentSentenceIndex < 0 && currentAbsoluteChunkIndex >= 0 && currentAbsoluteChunkIndex < allChunks.size()) {
            String currentChunkText = allChunks.get(currentAbsoluteChunkIndex);
            for (int i = 0; i < sentences.size(); i++) {
                if (sentences.get(i).contains(currentChunkText) || currentChunkText.contains(sentences.get(i))) {
                    currentSentenceIndex = i;
                    break;
                }
            }
        }
        
        if (currentSentenceIndex < 0 || currentSentenceIndex >= sentences.size() - 1) {
            // 다음 문장이 없으면 백그라운드 생성 불필요
            return;
        }
                    
        // n+1, n+2, n+3 문장의 청크 인덱스를 큐에 추가
        int nextSentenceIndex1 = currentSentenceIndex + 1;
        int nextSentenceIndex2 = currentSentenceIndex + 2;
        int nextSentenceIndex3 = currentSentenceIndex + 3;
        
        // n+1 문장의 청크 인덱스 찾기
        int nextChunkIndex1 = -1;
        if (nextSentenceIndex1 < sentenceToChunkMap.size() && sentenceToChunkMap.get(nextSentenceIndex1) >= 0) {
            nextChunkIndex1 = sentenceToChunkMap.get(nextSentenceIndex1);
        } else {
            // 매핑이 없으면 전체 청크 리스트에서 찾기
            String nextSentenceText1 = sentences.get(nextSentenceIndex1);
            for (int i = 0; i < allChunks.size(); i++) {
                if (allChunks.get(i).contains(nextSentenceText1)) {
                    nextChunkIndex1 = i;
                    break;
                }
            }
        }
        
        // n+2 문장의 청크 인덱스 찾기
        int nextChunkIndex2 = -1;
        if (nextSentenceIndex2 < sentences.size()) {
            if (nextSentenceIndex2 < sentenceToChunkMap.size() && sentenceToChunkMap.get(nextSentenceIndex2) >= 0) {
                nextChunkIndex2 = sentenceToChunkMap.get(nextSentenceIndex2);
        } else {
            // 매핑이 없으면 전체 청크 리스트에서 찾기
                String nextSentenceText2 = sentences.get(nextSentenceIndex2);
            for (int i = 0; i < allChunks.size(); i++) {
                    if (allChunks.get(i).contains(nextSentenceText2)) {
                        nextChunkIndex2 = i;
                    break;
                }
            }
        }
        }
        
        // n+3 문장의 청크 인덱스 찾기
        int nextChunkIndex3 = -1;
        if (nextSentenceIndex3 < sentences.size()) {
            if (nextSentenceIndex3 < sentenceToChunkMap.size() && sentenceToChunkMap.get(nextSentenceIndex3) >= 0) {
                nextChunkIndex3 = sentenceToChunkMap.get(nextSentenceIndex3);
            } else {
                // 매핑이 없으면 전체 청크 리스트에서 찾기
                String nextSentenceText3 = sentences.get(nextSentenceIndex3);
                for (int i = 0; i < allChunks.size(); i++) {
                    if (allChunks.get(i).contains(nextSentenceText3)) {
                        nextChunkIndex3 = i;
                        break;
                    }
                }
            }
        }
        
        // 큐에 추가
        if (nextChunkIndex1 >= 0) {
            backgroundGenerationQueue.offer(nextChunkIndex1);
        }
        if (nextChunkIndex2 >= 0) {
            backgroundGenerationQueue.offer(nextChunkIndex2);
        }
        if (nextChunkIndex3 >= 0) {
            backgroundGenerationQueue.offer(nextChunkIndex3);
        }
        
        // 백그라운드 생성 작업 시작
        if (!backgroundGenerationQueue.isEmpty()) {
            backgroundGenerationTask = executorService.submit(() -> {
                processBackgroundGenerationQueue(voice, speed);
            });
        }
    }
    
    /**
     * 백그라운드 생성 큐 처리
     * @param voice 생성에 사용할 목소리
     * @param speed 생성에 사용할 속도
     */
    private void processBackgroundGenerationQueue(String voice, String speed) {
        double speechLength = speedToSpeechLength(speed);
        while (!backgroundGenerationQueue.isEmpty() && !Thread.currentThread().isInterrupted()) {
            Integer chunkIndex = backgroundGenerationQueue.poll();
            if (chunkIndex == null) {
                break;
            }
            
            try {
                // 이미 파일이 있으면 스킵
                File existingFile = findChunkFile(voice, speed, chunkIndex);
                if (existingFile != null && existingFile.exists()) {
                    android.util.Log.d("MainActivity", "Background: Chunk " + (chunkIndex + 1) + " already exists, skipping");
                    continue;
                }
                
                // 청크 텍스트 가져오기
                if (chunkIndex >= allChunks.size()) {
                    continue;
                }
                
                String chunkText = allChunks.get(chunkIndex);
                
                // 청크 생성
                android.util.Log.d("MainActivity", "Background: Generating chunk " + (chunkIndex + 1));
                long generationStartTime = System.currentTimeMillis();
                byte[] audioData = ttsEngine.generate(chunkText, voice, speechLength);
                long generationEndTime = System.currentTimeMillis();
                double processingTime = (generationEndTime - generationStartTime) / 1000.0;
                        
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        
                if (audioData != null && audioData.length > 0) {
                    File savedFile = saveChunk(audioData, chunkIndex, voice, speed);
                    
                    // 오디오 길이 계산
                    double audioDuration = 0;
                    if (savedFile != null && savedFile.exists()) {
                        long fileSize = savedFile.length();
                        audioDuration = (fileSize > 44) ? (fileSize - 44) / (24000.0 * 2.0) : 0;
                    }
                    
                    // CPS/RTF 저장
                    saveChunkMetrics(chunkIndex, chunkText, processingTime, audioDuration);
                    
                    android.util.Log.d("MainActivity", "Background: Chunk " + (chunkIndex + 1) + " generated successfully");
                }
                } catch (Exception e) {
                android.util.Log.e("MainActivity", "Background generation error for chunk " + (chunkIndex + 1) + ": " + e.getMessage());
            }
        }
        
        backgroundGenerationTask = null;
    }
    
    /**
     * 다음 청크 재생 시점에 n+3을 백그라운드 생성에 추가
     * @param currentAbsoluteChunkIndex 현재 재생 중인 청크의 절대 인덱스
     * @param voice 생성에 사용할 목소리
     * @param speed 생성에 사용할 속도
     */
    private void addNextToBackgroundQueue(int currentAbsoluteChunkIndex, String voice, String speed) {
        // 현재 청크에 해당하는 문장 인덱스 찾기
        int currentSentenceIndex = -1;
        for (int i = 0; i < sentenceToChunkMap.size(); i++) {
            if (sentenceToChunkMap.get(i) == currentAbsoluteChunkIndex) {
                currentSentenceIndex = i;
                break;
            }
        }
        
        // 문장을 찾지 못하면 청크 텍스트로 문장 찾기
        if (currentSentenceIndex < 0 && currentAbsoluteChunkIndex >= 0 && currentAbsoluteChunkIndex < allChunks.size()) {
            String currentChunkText = allChunks.get(currentAbsoluteChunkIndex);
            for (int i = 0; i < sentences.size(); i++) {
                if (sentences.get(i).contains(currentChunkText) || currentChunkText.contains(sentences.get(i))) {
                    currentSentenceIndex = i;
                    break;
                }
            }
        }
        
        if (currentSentenceIndex < 0 || currentSentenceIndex >= sentences.size() - 2) {
            // n+3 문장이 없으면 추가 불필요
                        return;
                    }
                    
        // n+3 문장의 청크 인덱스 찾기
        int nextSentenceIndex3 = currentSentenceIndex + 3;
        int nextChunkIndex3 = -1;
        
        if (nextSentenceIndex3 < sentenceToChunkMap.size() && sentenceToChunkMap.get(nextSentenceIndex3) >= 0) {
            nextChunkIndex3 = sentenceToChunkMap.get(nextSentenceIndex3);
                        } else {
            // 매핑이 없으면 전체 청크 리스트에서 찾기
            if (nextSentenceIndex3 < sentences.size()) {
                String nextSentenceText3 = sentences.get(nextSentenceIndex3);
                for (int i = 0; i < allChunks.size(); i++) {
                    if (allChunks.get(i).contains(nextSentenceText3)) {
                        nextChunkIndex3 = i;
                        break;
                    }
                }
            }
        }
        
        // 큐에 추가
        if (nextChunkIndex3 >= 0) {
            // 이미 큐에 있으면 추가하지 않음
            if (!backgroundGenerationQueue.contains(nextChunkIndex3)) {
                backgroundGenerationQueue.offer(nextChunkIndex3);
                android.util.Log.d("MainActivity", "Background: Added chunk " + (nextChunkIndex3 + 1) + " to queue");
                
                // 백그라운드 생성 작업이 없으면 시작
                if (backgroundGenerationTask == null || backgroundGenerationTask.isDone()) {
                    backgroundGenerationTask = executorService.submit(() -> {
                        processBackgroundGenerationQueue(voice, speed);
                    });
                }
            }
        }
    }
    
    /**
     * Voice 변경 처리 (현재 재생 중인 청크부터 시작)
     */
    private void handleVoiceChange() {
        // 현재 재생 중인 청크의 절대 번호 찾기
        // currentChunkIndex는 audioChunks 리스트 내의 상대 인덱스이므로,
        // 절대 청크 번호를 찾기 위해 파일명에서 추출하거나 firstChunkAbsoluteIndex를 사용
        int currentAbsoluteChunkIndex = -1;
        String currentChunkText = null;
        int targetSentenceIndex = -1;
        
        android.util.Log.d("MainActivity", "Voice change: currentChunkIndex: " + currentChunkIndex + 
            ", audioChunks.size(): " + audioChunks.size() + 
            ", chunkTexts.size(): " + chunkTexts.size() + 
            ", sentenceToChunkMap.size(): " + sentenceToChunkMap.size() +
            ", firstChunkAbsoluteIndex: " + firstChunkAbsoluteIndex);
        
        // audioChunks가 비어있지 않고, currentChunkIndex가 유효한 범위 내에 있는지 확인
        if (audioChunks.isEmpty()) {
            android.util.Log.d("MainActivity", "Voice change: audioChunks is empty, generating from beginning");
            if (!sentences.isEmpty()) {
                cancelGenerationOnly();
                generateAudio("0", selectedVoice, null);
            }
            return;
        }
        
        // currentChunkIndex가 audioChunks 범위 내에 있는지 확인
        if (currentChunkIndex < 0 || currentChunkIndex >= audioChunks.size()) {
            android.util.Log.w("MainActivity", "Voice change: currentChunkIndex out of range: " + currentChunkIndex + 
                " (audioChunks.size(): " + audioChunks.size() + "), generating from beginning");
            if (!sentences.isEmpty()) {
                cancelGenerationOnly();
                generateAudio("0", selectedVoice, null);
            }
            return;
        }
        
        // 절대 청크 번호 계산: firstChunkAbsoluteIndex + currentChunkIndex
        currentAbsoluteChunkIndex = firstChunkAbsoluteIndex + currentChunkIndex;
        
        // chunkTexts가 비어있지 않고, currentChunkIndex가 범위 내에 있는지 확인
        if (chunkTexts.isEmpty() || currentChunkIndex >= chunkTexts.size()) {
            android.util.Log.w("MainActivity", "Voice change: chunkTexts is empty or currentChunkIndex out of range: " + 
                currentChunkIndex + " (chunkTexts.size(): " + chunkTexts.size() + "), generating from beginning");
            if (!sentences.isEmpty()) {
                cancelGenerationOnly();
                generateAudio("0", selectedVoice, null);
            }
            return;
        }
        
        // 현재 청크의 텍스트 가져오기 (cancelGenerationOnly 호출 전에!)
        currentChunkText = chunkTexts.get(currentChunkIndex);
        if (currentChunkText == null || currentChunkText.trim().isEmpty()) {
            android.util.Log.w("MainActivity", "Voice change: currentChunkText is null or empty, generating from beginning");
            if (!sentences.isEmpty()) {
                cancelGenerationOnly();
                generateAudio("0", selectedVoice, null);
            }
            return;
        }
        
        // 현재 절대 청크 번호에 해당하는 문장 찾기 - cancelGenerationOnly 호출 전에!
        for (int i = 0; i < sentenceToChunkMap.size(); i++) {
            if (sentenceToChunkMap.get(i) == currentAbsoluteChunkIndex) {
                targetSentenceIndex = i;
                break;
            }
        }
        
        // 매핑을 찾을 수 없으면, 청크 텍스트를 직접 사용하여 문장 찾기 (fallback) - cancelGenerationOnly 호출 전에!
        if (targetSentenceIndex < 0) {
            android.util.Log.w("MainActivity", "Voice change: sentence mapping not found for absolute chunk " + currentAbsoluteChunkIndex + 
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
        
        // 목소리 변경 시: 기존 생성 작업만 취소 (오디오 파일은 유지)
        cancelGenerationOnly();
        
        // 찾은 문장이 있으면 해당 문장부터 생성
        if (targetSentenceIndex >= 0 && targetSentenceIndex < sentences.size()) {
            String targetSentence = String.valueOf(targetSentenceIndex);
            android.util.Log.d("MainActivity", "Voice change: found target sentence at index " + targetSentenceIndex + 
                " for absolute chunk " + currentAbsoluteChunkIndex + ", generating from this sentence");
            generateAudio(targetSentence, selectedVoice, null);
            return;
        }
        
        // 모든 방법으로 찾을 수 없으면 처음부터 생성
        android.util.Log.w("MainActivity", "Voice change: could not find target sentence, generating from beginning");
        if (!sentences.isEmpty()) {
            generateAudio("0", selectedVoice, null);
        }
    }
    
    /**
     * 재생 중일 때 Voice 변경 처리 (deprecated - handleVoiceChange 사용)
     */
    private void handleVoiceChangeDuringPlayback() {
        handleVoiceChange();
    }
    
    /**
     * Speed 변경 시 현재 읽던 문장부터 새롭게 청크 생성 및 재생
     */
    private void handleSpeedChange() {
        // 현재 재생 중인 청크의 절대 번호 찾기
        int currentAbsoluteChunkIndex = -1;
        int targetSentenceIndex = -1;
        
        // audioChunks가 비어있지 않고, currentChunkIndex가 유효한 범위 내에 있는지 확인
        if (audioChunks.isEmpty()) {
            if (!sentences.isEmpty()) {
                cancelGenerationOnly();
                generateAudio("0", null, null);
            }
            return;
        }
        
        // currentChunkIndex가 audioChunks 범위 내에 있는지 확인
        if (currentChunkIndex < 0 || currentChunkIndex >= audioChunks.size()) {
            if (!sentences.isEmpty()) {
                cancelGenerationOnly();
                generateAudio("0", null, null);
            }
            return;
        }
        
        // 절대 청크 번호 계산: firstChunkAbsoluteIndex + currentChunkIndex
        currentAbsoluteChunkIndex = firstChunkAbsoluteIndex + currentChunkIndex;
        
        // 현재 청크에 해당하는 문장 찾기
        for (int i = 0; i < sentenceToChunkMap.size(); i++) {
            if (sentenceToChunkMap.get(i) == currentAbsoluteChunkIndex) {
                targetSentenceIndex = i;
                break;
            }
        }
        
        // 문장을 찾지 못하면 청크 텍스트로 문장 찾기
        if (targetSentenceIndex < 0 && currentAbsoluteChunkIndex >= 0 && currentAbsoluteChunkIndex < allChunks.size()) {
            String currentChunkText = allChunks.get(currentAbsoluteChunkIndex);
            for (int i = 0; i < sentences.size(); i++) {
                if (sentences.get(i).contains(currentChunkText) || currentChunkText.contains(sentences.get(i))) {
                    targetSentenceIndex = i;
                    break;
                }
            }
        }
        
        // 문장을 찾았으면 해당 문장부터 생성 및 재생
        if (targetSentenceIndex >= 0 && targetSentenceIndex < sentences.size()) {
            cancelGenerationOnly();
            generateAudio(String.valueOf(targetSentenceIndex), null, null);
        } else if (!sentences.isEmpty()) {
            // 문장을 찾지 못하면 첫 번째 문장부터 생성
            cancelGenerationOnly();
            generateAudio("0", null, null);
        }
    }
    
    /**
     * 절대 청크 번호로 재생
     */
    private void playChunkByAbsoluteIndex(int absoluteChunkIndex) {
        // 현재 선택된 보이스로 파일 찾기
        File chunkFile = findChunkFile(selectedVoice, selectedSpeed, absoluteChunkIndex);
        
        if (chunkFile == null || !chunkFile.exists()) {
            // 파일이 없으면 해당 청크를 생성해야 함
            android.util.Log.d("MainActivity", "Chunk file not found for voice " + selectedVoice + ", chunk " + absoluteChunkIndex);
            // 해당 청크 텍스트 찾기
            if (absoluteChunkIndex >= 0 && absoluteChunkIndex < allChunks.size()) {
                // 해당 청크가 속한 문장 찾기
                int targetSentenceIndex = -1;
                for (int i = 0; i < sentenceToChunkMap.size(); i++) {
                    if (sentenceToChunkMap.get(i) == absoluteChunkIndex) {
                        targetSentenceIndex = i;
                        break;
                    }
                }
                if (targetSentenceIndex < 0) {
                    targetSentenceIndex = 0; // 찾을 수 없으면 0번 문장 사용
                }
                // 해당 문장부터 생성 및 재생
                cancelGenerationOnly();
                generateAudio(String.valueOf(targetSentenceIndex), selectedVoice, null);
            } else {
                updateStatus("Chunk file not found: " + (absoluteChunkIndex + 1));
            }
            return;
        }
        
        // audioChunks에서 해당 파일 찾기
        int relativeIndex = -1;
        for (int i = 0; i < audioChunks.size(); i++) {
            if (audioChunks.get(i).equals(chunkFile)) {
                relativeIndex = i;
                break;
            }
        }
        
        // audioChunks에 없으면 추가
        if (relativeIndex < 0) {
            audioChunks.add(chunkFile);
            relativeIndex = audioChunks.size() - 1;
            // chunkTexts에도 추가
            if (absoluteChunkIndex < allChunks.size()) {
                chunkTexts.add(allChunks.get(absoluteChunkIndex));
            }
        }
        
        // firstChunkAbsoluteIndex 업데이트 (필요시)
        if (audioChunks.size() == 1 || firstChunkAbsoluteIndex > absoluteChunkIndex) {
            firstChunkAbsoluteIndex = absoluteChunkIndex;
        }
        
        playChunkAtIndex(relativeIndex);
    }
    
    /**
     * 특정 청크 인덱스부터 재생 (상대 인덱스)
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
            // 절대 청크 인덱스 업데이트
            currentPlayingChunkAbsoluteIndex = firstChunkAbsoluteIndex + index;
            currentChunkPlaybackProgress = 0.0;
            
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
            int absoluteChunkIndex = firstChunkAbsoluteIndex + index;
            updateStatus("Playing segment " + (absoluteChunkIndex + 1));
            
            // 청크 하이라이트 및 페이지 업데이트
            updateChunkHighlight();
            checkAndUpdatePageForChunk();
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Error playing chunk at index", e);
            updateStatus("Playback error: " + e.getMessage());
        }
    }
    
    /**
     * 실제 클릭인지 스크롤인지 확인
     */
    private boolean isActualClick() {
        // touchDownTime이 0이면 스크롤로 판단됨
        if (touchDownTime == 0) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis();
        long duration = currentTime - touchDownTime;
        
        // 시간 체크
        if (duration > MAX_CLICK_DURATION) {
            return false; // 너무 오래 눌렀음 (스크롤 가능성)
        }
        
        // 실제 클릭으로 판단
        return true;
    }
    
    /**
     * 텍스트 클릭 핸들러 설정
     */
    private void setupTextClickHandler() {
        // TextView에 직접 터치 리스너 설정 (z-index 최상위)
        textDisplay.setOnTouchListener((v, event) -> {
            // 스와이프 제스처 먼저 처리
            if (gestureDetector.onTouchEvent(event)) {
                return true;
            }
            
            // 터치 다운 시 위치와 시간 저장
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchDownX = event.getX();
                touchDownY = event.getY();
                touchDownTime = System.currentTimeMillis();
            }
            
            // 터치 업 시 거리 체크
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float deltaX = Math.abs(event.getX() - touchDownX);
                float deltaY = Math.abs(event.getY() - touchDownY);
                float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                
                // 스크롤로 판단되는 경우 (거리가 너무 멀거나 수평 이동이 큰 경우)
                if (distance > MAX_CLICK_DISTANCE || deltaX > MAX_CLICK_DISTANCE) {
                    touchDownTime = 0; // 클릭이 아님을 표시
                    return false; // 스크롤로 처리
                }
            }
            
            // MOVE 이벤트는 스크롤로 간주
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float deltaX = Math.abs(event.getX() - touchDownX);
                float deltaY = Math.abs(event.getY() - touchDownY);
                if (deltaX > MAX_CLICK_DISTANCE || deltaY > MAX_CLICK_DISTANCE) {
                    touchDownTime = 0; // 클릭이 아님을 표시
                }
            }
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
            // 스와이프 제스처 먼저 처리
            if (gestureDetector.onTouchEvent(event)) {
                return true;
            }
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
     * 청크가 속한 페이지와 분할 정보 찾기
     * @param chunkText 청크 텍스트
     * @param absoluteChunkIndex 절대 청크 인덱스
     * @return ChunkPageInfo 객체 (시작 페이지, 끝 페이지, 분할 비율 등)
     */
    private ChunkPageInfo findChunkPages(String chunkText, int absoluteChunkIndex) {
        if (chunkText == null || chunkText.isEmpty() || pages.isEmpty()) {
            return new ChunkPageInfo(-1, -1, 0.0, 0, 0);
        }
        
        // 캐시 확인
        if (chunkPageInfoCache.containsKey(absoluteChunkIndex)) {
            return chunkPageInfoCache.get(absoluteChunkIndex);
        }
        
        // fullText에서 청크의 위치 찾기
        int chunkStartInFullText = fullText.indexOf(chunkText);
        if (chunkStartInFullText < 0) {
            // 정확히 일치하지 않으면 간단한 부분 매칭만 시도 (성능 최적화)
            String chunkTrimmed = chunkText.trim();
            if (chunkTrimmed.length() > 0) {
                // 앞부분만 확인 (성능 최적화를 위해 루프 제거)
                int maxCheckLength = Math.min(100, chunkTrimmed.length());
                String chunkPart = chunkTrimmed.substring(0, maxCheckLength);
                chunkStartInFullText = fullText.indexOf(chunkPart);
            }
        }
        
        if (chunkStartInFullText < 0) {
            return new ChunkPageInfo(-1, -1, 0.0, 0, 0);
        }
        
        int chunkEndInFullText = chunkStartInFullText + chunkText.length();
        
        // 각 페이지에서 청크가 나타나는 위치 찾기
        int startPage = -1;
        int endPage = -1;
        int chunkStartInStartPage = -1;
        int chunkEndInEndPage = -1;
        
        int currentPos = 0;
        for (int pageIdx = 0; pageIdx < pages.size(); pageIdx++) {
            String pageText = pages.get(pageIdx);
            int pageStart = currentPos;
            int pageEnd = currentPos + pageText.length();
            
            // 청크가 이 페이지와 겹치는지 확인
            if (chunkStartInFullText < pageEnd && chunkEndInFullText > pageStart) {
                if (startPage < 0) {
                    startPage = pageIdx;
                    chunkStartInStartPage = chunkStartInFullText - pageStart;
                }
                endPage = pageIdx;
                chunkEndInEndPage = chunkEndInFullText - pageStart;
            }
            
            currentPos = pageEnd;
        }
        
        // 분할 비율 계산 (문자 수 기준)
        double splitRatio = 0.0;
        if (startPage >= 0 && endPage >= 0 && startPage != endPage) {
            // 여러 페이지에 걸쳐있는 경우
            String startPageText = pages.get(startPage);
            int chunkInStartPageLength = startPageText.length() - chunkStartInStartPage;
            splitRatio = (double) chunkInStartPageLength / chunkText.length();
        }
        
        ChunkPageInfo result = new ChunkPageInfo(startPage, endPage, splitRatio, chunkStartInStartPage, chunkEndInEndPage);
        
        // 캐시에 저장
        chunkPageInfoCache.put(absoluteChunkIndex, result);
        
        return result;
    }
    
    /**
     * 청크 페이지 정보를 담는 클래스
     */
    private static class ChunkPageInfo {
        int startPage;
        int endPage;
        double splitRatio; // 첫 페이지에 있는 비율 (0.0 ~ 1.0)
        int chunkStartInStartPage; // 시작 페이지에서 청크의 시작 위치
        int chunkEndInEndPage; // 끝 페이지에서 청크의 끝 위치
        
        ChunkPageInfo(int startPage, int endPage, double splitRatio, int chunkStartInStartPage, int chunkEndInEndPage) {
            this.startPage = startPage;
            this.endPage = endPage;
            this.splitRatio = splitRatio;
            this.chunkStartInStartPage = chunkStartInStartPage;
            this.chunkEndInEndPage = chunkEndInEndPage;
        }
        
        boolean spansMultiplePages() {
            return startPage >= 0 && endPage >= 0 && startPage != endPage;
        }
    }
    
    /**
     * 현재 재생 중인 청크 하이라이트 업데이트
     */
    private void updateChunkHighlight() {
        if (currentPlayingChunkAbsoluteIndex < 0 || pages.isEmpty()) {
            return;
        }
        
        // 현재 청크 텍스트 찾기
        String currentChunkText = null;
        if (currentChunkIndex >= 0 && currentChunkIndex < chunkTexts.size()) {
            currentChunkText = chunkTexts.get(currentChunkIndex);
        } else if (currentPlayingChunkAbsoluteIndex >= 0 && currentPlayingChunkAbsoluteIndex < allChunks.size()) {
            currentChunkText = allChunks.get(currentPlayingChunkAbsoluteIndex);
        }
        
        if (currentChunkText == null || currentChunkText.isEmpty()) {
            return;
        }
        
        // 청크가 속한 페이지 정보 찾기
        ChunkPageInfo chunkInfo = findChunkPages(currentChunkText, currentPlayingChunkAbsoluteIndex);
        
        // 현재 페이지가 청크가 속한 페이지 중 하나인지 확인
        if (chunkInfo.startPage < 0) {
            return;
        }
        
        // 현재 페이지가 청크의 시작 페이지인지 확인
        if (currentPageIndex == chunkInfo.startPage || currentPageIndex == chunkInfo.endPage) {
            displayCurrentPage(); // 페이지를 다시 표시하여 하이라이트 적용
        }
    }
    
    /**
     * 페이지 인디케이터 업데이트
     */
    private void updatePageIndicator() {
        // 페이지 번호도 함께 업데이트
        updatePageNumber();
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

