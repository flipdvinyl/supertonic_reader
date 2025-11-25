package com.supertone.ebook;

import android.app.Application;
import android.graphics.Typeface;

public class EbookApplication extends Application {
    private static Typeface defaultTypeface;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Literata 폰트 로드
        try {
            defaultTypeface = Typeface.createFromAsset(getAssets(), "fonts/literata.ttf");
            // 전역 폰트 설정을 위한 Reflection 사용
            // 하지만 Android에서는 styles.xml을 통한 설정이 더 권장됨
            android.util.Log.d("EbookApplication", "Literata font loaded successfully");
        } catch (Exception e) {
            android.util.Log.e("EbookApplication", "Failed to load Literata font", e);
            // 폰트 로드 실패 시 기본 폰트 사용
            defaultTypeface = Typeface.DEFAULT;
        }
    }
    
    public static Typeface getDefaultTypeface() {
        return defaultTypeface != null ? defaultTypeface : Typeface.DEFAULT;
    }
}

