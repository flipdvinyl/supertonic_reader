# 빠른 해결 방법

## 현재 상황
- ✅ Android SDK 설치됨
- ✅ Gradle wrapper 생성됨  
- ❌ Java만 설치하면 됩니다!

## Java 설치

터미널에서 실행:
```bash
brew install --cask temurin
```

비밀번호 입력이 필요할 수 있습니다.

## 설치 후 실행

```bash
cd /Users/d/android_ebook
source setup_env.sh  # 환경 변수 설정 (선택사항)
./test_emulator.sh   # 에뮬레이터에서 앱 실행
```

## 에뮬레이터가 없다면

Android Studio를 실행하고:
1. Tools > Device Manager
2. Create Device 클릭
3. 원하는 기기 선택 (예: Pixel 5)
4. System Image 선택 (API 34 권장)
5. Finish

그 다음 다시 `./test_emulator.sh` 실행
