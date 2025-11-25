# ✅ 모델 파일 다운로드 완료!

모든 Supertonic 모델 파일이 성공적으로 다운로드되어 `app/src/main/assets/` 폴더에 배치되었습니다.

## 다운로드된 파일

### ONNX 모델 파일들
- ✅ `onnx/duration_predictor.onnx`
- ✅ `onnx/text_encoder.onnx`
- ✅ `onnx/vector_estimator.onnx`
- ✅ `onnx/vocoder.onnx`
- ✅ `onnx/tts.json`
- ✅ `onnx/unicode_indexer.json`

### 목소리 스타일 파일들
- ✅ `voice_styles/M1.json` (남성 목소리 1)
- ✅ `voice_styles/F1.json` (여성 목소리 1)
- ✅ `voice_styles/M2.json` (남성 목소리 2)
- ✅ `voice_styles/F2.json` (여성 목소리 2)

## 다음 단계

### 1. APK 빌드

```bash
cd /Users/d/android_ebook
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew assembleDebug
```

### 2. 에뮬레이터에서 테스트

```bash
./quick_test.sh
```

또는 수동으로:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.supertone.ebook/.MainActivity
```

## 작동 방식

1. **APK 설치 시**: 모델 파일들이 APK에 포함되어 설치됩니다
2. **앱 첫 실행 시**: `SupertonicTTS.initialize()`가 자동으로:
   - Assets 폴더에서 모델 파일들을 확인
   - 내부 저장소(`/data/data/com.supertone.ebook/files/`)로 복사
   - TTS 엔진 초기화
3. **TTS 사용 시**: 복사된 모델 파일들을 사용하여 텍스트를 음성으로 변환

## 파일 크기

모델 파일들의 총 크기는 약 수백 MB입니다. APK 크기가 커질 수 있으니 참고하세요.

## 문제 해결

### "모델 파일을 찾을 수 없습니다" 오류

1. APK에 파일이 포함되었는지 확인:
   ```bash
   unzip -l app/build/outputs/apk/debug/app-debug.apk | grep onnx
   ```

2. 로그 확인:
   ```bash
   adb logcat | grep Supertonic
   ```

3. 내부 저장소 확인:
   ```bash
   adb shell ls -la /data/data/com.supertone.ebook/files/onnx/
   ```

## 참고

- 모델 파일들은 Hugging Face에서 다운로드되었습니다
- 다운로드 스크립트: `./download_from_hf.sh`
- 수동 다운로드 가이드: `app/src/main/assets/SETUP_INSTRUCTIONS.md`


