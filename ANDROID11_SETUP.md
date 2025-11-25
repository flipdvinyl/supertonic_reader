# Android 11 (API 30) 타깃 설정

## 현재 설정

- **minSdk**: 24 (Android 7.0)
- **targetSdk**: 30 (Android 11)
- **compileSdk**: 34 (최신 SDK로 컴파일, 호환성 유지)

## Android 11 주요 고려사항

### 1. 저장소 권한
Android 11부터 Scoped Storage가 기본이지만, targetSdk 30으로 설정했으므로:
- Android 10 (API 29) 이하: READ/WRITE_EXTERNAL_STORAGE 권한 필요
- Android 11 (API 30) 이상: Scoped Storage 사용 (권한 불필요)

현재 설정:
- `maxSdkVersion="29"`로 Android 10 이하에서만 저장소 권한 요청

### 2. 패키지 가시성
Android 11부터 패키지 가시성 제한이 있지만, targetSdk 30이므로 영향 없음

### 3. 백그라운드 위치 접근
현재 앱에서는 사용하지 않음

## 빌드 및 테스트

```bash
cd /Users/d/android_ebook
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew assembleDebug
```

## ONYX go7 호환성

- Android 11 기반
- CPU 전용 실행 (GPU 없음)
- targetSdk 30으로 설정하여 최적 호환성

## 다음 단계

1. Supertonic 모델 파일 배치
   - `app/src/main/assets/supertonic_model.onnx`

2. Supertonic Java 구현 추가
   - https://github.com/supertone-inc/supertonic/tree/main/java 참고

3. 테스트
   - ONYX go7 또는 Android 11 에뮬레이터에서 테스트


