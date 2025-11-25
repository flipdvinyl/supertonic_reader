# Java 설치 가이드

## 방법 1: Homebrew로 설치 (권장)

터미널에서 다음 명령어 실행:

```bash
brew install --cask temurin
```

설치 중 관리자 비밀번호를 입력하라는 메시지가 나올 수 있습니다.

## 방법 2: 수동 다운로드

1. https://adoptium.net/temurin/releases/ 방문
2. macOS용 JDK 다운로드 (Java 11 이상 권장)
3. .pkg 파일 실행하여 설치

## 설치 확인

설치 후 다음 명령어로 확인:

```bash
java -version
```

다음과 같은 출력이 나오면 성공:
```
openjdk version "XX.X.X" ...
```

## 다음 단계

Java 설치 후:

```bash
cd /Users/d/android_ebook
./test_emulator.sh
```


