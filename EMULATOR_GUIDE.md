# 에뮬레이터 실행 가이드

## 빠른 시작

### 방법 1: 자동 스크립트 사용

```bash
cd /Users/d/android_ebook
./start_emulator.sh
```

에뮬레이터가 부팅될 때까지 기다린 후:

```bash
./test_emulator.sh
```

### 방법 2: 수동으로 시작

```bash
# 환경 변수 설정
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools

# 사용 가능한 에뮬레이터 확인
emulator -list-avds

# 에뮬레이터 시작 (백그라운드)
emulator -avd Medium_Phone_API_36.1 &

# 에뮬레이터가 부팅될 때까지 대기 (약 30초~1분)
# 다른 터미널에서 확인:
adb devices

# 부팅 완료 후 (device가 표시되면):
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.supertone.ebook/.MainActivity
```

### 방법 3: Android Studio에서 시작

1. Android Studio 실행
2. Tools > Device Manager
3. 원하는 에뮬레이터 옆의 ▶️ 버튼 클릭
4. 부팅 완료 후 `./test_emulator.sh` 실행

## 에뮬레이터 상태 확인

```bash
adb devices
```

출력 예시:
```
List of devices attached
emulator-5554   device    # 부팅 완료
```

`device`가 표시되면 준비 완료입니다.

## 문제 해결

### "no devices/emulators found"

에뮬레이터가 실행되지 않았습니다:
1. `emulator -list-avds`로 사용 가능한 AVD 확인
2. `emulator -avd <이름>`으로 시작
3. 부팅 완료까지 대기 (1-2분)

### 에뮬레이터가 없음

Android Studio에서 생성:
1. Tools > Device Manager
2. Create Device
3. Phone > Pixel 5 선택
4. System Image: API 34 (Android 14) 선택
5. Finish

### ADB 연결 문제

```bash
adb kill-server
adb start-server
adb devices
```

## 빠른 테스트

에뮬레이터가 이미 실행 중이면:

```bash
cd /Users/d/android_ebook
./test_emulator.sh
```

이 스크립트는 자동으로:
1. APK 빌드 (이미 빌드되어 있으면 스킵)
2. 에뮬레이터에 설치
3. 앱 실행


