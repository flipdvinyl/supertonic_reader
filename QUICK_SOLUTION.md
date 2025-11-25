# 빠른 해결 방법

## 문제
Java 25는 Gradle과 호환되지 않습니다. Java 17이 필요합니다.

## 해결 방법

### 1단계: Java 17 설치

터미널에서 실행 (비밀번호 입력 필요):
```bash
brew install --cask temurin@17
```

### 2단계: Java 17 사용

현재 세션에서만:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

영구적으로 적용하려면 `~/.zshrc`에 추가:
```bash
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
source ~/.zshrc
```

### 3단계: 빌드

```bash
cd /Users/d/android_ebook
./gradlew --stop  # 기존 daemon 중지
./gradlew assembleDebug
```

## 확인

Java 버전 확인:
```bash
java -version
```

Java 17이 표시되어야 합니다:
```
openjdk version "17.x.x" ...
```

## 대안: Java 21 사용

Java 21도 사용 가능합니다:
```bash
brew install --cask temurin@21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```
