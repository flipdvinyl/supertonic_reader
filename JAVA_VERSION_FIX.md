# Java 버전 문제 해결 가이드

## 문제
Java 25가 설치되어 있지만, Gradle 8.4는 Java 21까지만 지원합니다.

## 해결 방법

### 방법 1: Java 17 설치 및 사용 (권장)

터미널에서 다음 명령어 실행:
```bash
brew install --cask temurin@17
```

설치 후 Java 17을 사용하도록 설정:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

영구적으로 적용하려면 `~/.zshrc`에 추가:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

### 방법 2: Gradle 버전 업그레이드 (이미 적용됨)

Gradle을 8.10.2로 업그레이드했습니다. 이 버전은 Java 25를 지원합니다.

다시 빌드 시도:
```bash
cd /Users/d/android_ebook
./gradlew clean
./gradlew assembleDebug
```

### 방법 3: 현재 Java 버전 확인

설치된 Java 버전 확인:
```bash
/usr/libexec/java_home -V
```

특정 버전 사용:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)  # Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 21)  # Java 21
export JAVA_HOME=$(/usr/libexec/java_home -v 25)  # Java 25
```

## 확인

Java 버전 확인:
```bash
java -version
```

Gradle이 올바른 Java를 사용하는지 확인:
```bash
./gradlew --version
```


