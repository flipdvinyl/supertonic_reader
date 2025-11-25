# Android Ebook Reader with Supertonic TTS

ONYX go7 전자책 리더를 위한 최소한의 TTS 데모 앱입니다.

## 특징

- CPU 전용 실행 (GPU 불필요)
- 최소한의 UI (코드 최소화, 성능 최대화)
- Supertonic TTS 엔진 통합
- 4가지 목소리 선택 가능
- 최대 1000자 텍스트 입력

## 요구사항

- Android Studio
- Android SDK 24 이상
- ONNX Runtime Android 라이브러리

## 설정 방법

1. **Supertonic 모델 파일 준비**
   - Supertonic 저장소에서 모델 파일을 다운로드
   - `app/src/main/assets/` 폴더에 `supertonic_model.onnx` 파일 배치
   - 실제 모델 파일명과 경로는 Supertonic Java 구현에 맞게 수정 필요

2. **Supertonic Java 구현 통합**
   - `https://github.com/supertone-inc/supertonic/tree/main/java` 의 실제 구현을 확인
   - `SupertonicTTS.java` 파일을 실제 구현에 맞게 수정
   - 특히 다음 부분 수정 필요:
     - 모델 입력/출력 형식
     - 텍스트 전처리 로직
     - 오디오 후처리 로직

3. **빌드 및 실행**
   ```bash
   ./gradlew assembleDebug
   ```

## 프로젝트 구조

```
android_ebook/
├── app/
│   ├── src/main/
│   │   ├── java/com/supertone/ebook/
│   │   │   ├── MainActivity.java      # 메인 액티비티
│   │   │   └── SupertonicTTS.java     # TTS 엔진 래퍼
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml  # UI 레이아웃
│   │   │   └── values/
│   │   │       └── strings.xml        # 문자열 리소스
│   │   └── assets/
│   │       └── supertonic_model.onnx  # TTS 모델 파일 (추가 필요)
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## 주요 기능

### UI 구성
- **텍스트 입력창**: 최대 1000자 입력 가능, 샘플 텍스트 프리셋 포함
- **목소리 선택**: 4가지 목소리 중 선택 (voice1, voice2, voice3, voice4)
- **생성 버튼**: 텍스트를 오디오로 변환
- **플레이어 컨트롤**: 재생, 일시정지, 정지 버튼
- **상태 표시**: 현재 작업 상태 표시

### 성능 최적화
- CPU 전용 실행 (GPU 불필요)
- 백그라운드 스레드에서 오디오 생성
- 최소한의 UI 요소로 렌더링 부하 최소화
- ONNX Runtime 최적화 옵션 사용

## 수정 필요 사항

`SupertonicTTS.java` 파일의 다음 메서드들을 실제 Supertonic 구현에 맞게 수정해야 합니다:

1. **`preprocessText()`**: 텍스트 정규화 및 토크나이징
2. **`generate()`**: 모델 입력/출력 형식
3. **모델 파일 경로**: 실제 모델 파일명과 위치

Supertonic 저장소의 Java 구현을 참고하세요:
https://github.com/supertone-inc/supertonic/tree/main/java

## 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.
Supertonic 모델은 OpenRAIL-M 라이선스를 따릅니다.


