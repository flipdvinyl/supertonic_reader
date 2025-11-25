# Supertonic 모델 파일 설정 가이드

## 모델 파일 구조

Supertonic TTS를 사용하려면 다음 파일들이 필요합니다:

### 1. ONNX 모델 파일들
`app/src/main/assets/onnx/` 폴더에 다음 파일들을 배치하세요:

- `duration_predictor.onnx`
- `text_encoder.onnx`
- `vector_estimator.onnx`
- `vocoder.onnx`
- `tts.json` (설정 파일)
- `unicode_indexer.json`

### 2. 목소리 스타일 파일들
`app/src/main/assets/voice_styles/` 폴더에 다음 파일들을 배치하세요:

- `M1.json` (남성 목소리 1)
- `F1.json` (여성 목소리 1)
- `M2.json` (남성 목소리 2)
- `F2.json` (여성 목소리 2)

또는 원하는 목소리 스타일 파일들

## 모델 파일 다운로드

Supertonic 모델 파일은 다음에서 다운로드할 수 있습니다:

1. **Hugging Face**: https://huggingface.co/spaces/Supertone/supertonic
2. **GitHub Releases**: https://github.com/supertone-inc/supertonic/releases

## 파일 배치 방법

### 방법 1: 수동 배치

1. 모델 파일들을 다운로드
2. 다음 구조로 배치:

```
app/src/main/assets/
├── onnx/
│   ├── duration_predictor.onnx
│   ├── text_encoder.onnx
│   ├── vector_estimator.onnx
│   ├── vocoder.onnx
│   ├── tts.json
│   └── unicode_indexer.json
└── voice_styles/
    ├── M1.json
    ├── F1.json
    ├── M2.json
    └── F2.json
```

### 방법 2: 스크립트 사용

모델 파일이 있는 디렉토리에서:

```bash
# ONNX 모델 파일 복사
cp -r /path/to/supertonic/models/onnx app/src/main/assets/

# 목소리 스타일 파일 복사
cp -r /path/to/supertonic/models/voice_styles app/src/main/assets/
```

## APK 설치 시 자동 복사

앱이 실행되면 `SupertonicTTS.initialize()` 메서드가 자동으로:
1. Assets 폴더에서 모델 파일들을 확인
2. 내부 저장소(`/data/data/com.supertone.ebook/files/`)로 복사
3. 복사된 파일들을 사용하여 TTS 엔진 초기화

따라서 모델 파일을 `assets` 폴더에 배치하면 APK 설치 시 자동으로 포함됩니다.

## 주의사항

- 모델 파일들이 크므로 APK 크기가 커질 수 있습니다
- 첫 실행 시 파일 복사에 시간이 걸릴 수 있습니다
- 내부 저장소 공간이 충분한지 확인하세요

## 확인 방법

앱 실행 후 로그에서 다음 메시지를 확인하세요:

```
SupertonicHelper: Copying model files from assets...
SupertonicHelper: File copied successfully: onnx/duration_predictor.onnx
...
SupertonicTTS: Supertonic TTS 초기화 완료 (CPU 모드)
```

## 문제 해결

### "모델 파일을 찾을 수 없습니다"

1. `app/src/main/assets/onnx/` 폴더에 모든 ONNX 파일이 있는지 확인
2. 파일명이 정확한지 확인
3. 빌드 후 APK에 포함되었는지 확인:
   ```bash
   unzip -l app/build/outputs/apk/debug/app-debug.apk | grep onnx
   ```

### "초기화 실패"

1. 로그를 확인하여 어떤 파일이 누락되었는지 확인
2. 모델 파일이 손상되지 않았는지 확인
3. 내부 저장소 공간 확인


