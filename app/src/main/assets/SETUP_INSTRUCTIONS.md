# 모델 파일 설정 안내

## 자동 다운로드 (권장)

터미널에서 다음 명령어 실행:

```bash
cd /Users/d/android_ebook
./download_models.sh
```

## 수동 다운로드

### 방법 1: Hugging Face에서 다운로드

1. https://huggingface.co/spaces/Supertone/supertonic 방문
2. "Files and versions" 탭 클릭
3. 필요한 파일들 다운로드:
   - `onnx/duration_predictor.onnx`
   - `onnx/text_encoder.onnx`
   - `onnx/vector_estimator.onnx`
   - `onnx/vocoder.onnx`
   - `onnx/tts.json`
   - `onnx/unicode_indexer.json`
   - `voice_styles/M1.json`
   - `voice_styles/F1.json`
   - `voice_styles/M2.json`
   - `voice_styles/F2.json`

4. 다운로드한 파일들을 다음 위치에 배치:
   ```
   app/src/main/assets/onnx/
   app/src/main/assets/voice_styles/
   ```

### 방법 2: Git LFS로 클론

```bash
# Git LFS 설치 필요
git lfs install
git clone https://huggingface.co/spaces/Supertone/supertonic
cp -r supertonic/onnx app/src/main/assets/
cp -r supertonic/voice_styles app/src/main/assets/
```

### 방법 3: Python 스크립트 사용

```python
from huggingface_hub import hf_hub_download
import os

repo_id = "Supertone/supertonic"
files = [
    "onnx/duration_predictor.onnx",
    "onnx/text_encoder.onnx",
    "onnx/vector_estimator.onnx",
    "onnx/vocoder.onnx",
    "onnx/tts.json",
    "onnx/unicode_indexer.json",
    "voice_styles/M1.json",
    "voice_styles/F1.json",
]

for file in files:
    local_path = f"app/src/main/assets/{file}"
    os.makedirs(os.path.dirname(local_path), exist_ok=True)
    hf_hub_download(repo_id=repo_id, filename=file, local_dir="app/src/main/assets")
```

## 파일 구조 확인

다운로드 후 다음 명령어로 확인:

```bash
ls -lh app/src/main/assets/onnx/
ls -lh app/src/main/assets/voice_styles/
```

## 주의사항

- 모델 파일들이 크므로 다운로드에 시간이 걸릴 수 있습니다
- ONNX 파일들은 보통 수십 MB ~ 수백 MB 크기입니다
- 모든 파일이 다운로드되었는지 확인하세요


