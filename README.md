# Kid Day Board

Android task board for children. Parents can add tasks for each day of the week in settings, while children see a simple daily board.

## Features

- Child-facing board that shows only today's tasks, progress, and voice buttons.
- Soft illustrated card UI with a white wave panel and gradient task strips.
- Circular center voice control inspired by modern charity/task-card mobile layouts.
- Settings button for parent week setup.
- Child name in settings, used in the UI and spoken coach prompts.
- Parent task entry for any weekday from settings.
- Completion badge on finished tasks.
- Celebration confetti when all of today's tasks are complete.
- `Hello` button that speaks a child-friendly prompt, then swaps to `Speak`.
- `Speak` button records audio inside the app rather than launching the Google speech UI.
- Bundled whisper.cpp Android runtime and `ggml-tiny.en.bin` model for offline transcription.
- Idle reset returns the voice control to `Hello`.
- Gemma 4 E2B-ready coach layer through LiteRT-LM.
- TTS abstraction with Android TTS enabled today and automatic best installed English voice selection.

## Gemma 4 E2B model

The APK does not bundle the Gemma model because the Android LiteRT-LM model is multi-GB. The app looks for:

```text
Android/data/com.fahimc.kiddayboard/files/models/gemma-4-E2B-it.litertlm
```

Use the LiteRT Community Gemma 4 E2B model:

```text
litert-community/gemma-4-E2B-it-litert-lm
```

When that file exists on-device, `LiteRtGemmaCoach` initializes LiteRT-LM on a background dispatcher and streams responses through `sendMessageAsync`. Without the file, the board stays fully usable through the local deterministic coach.

## TTS direction

The shipped APK uses Android `TextToSpeech` so it works immediately. It now selects the highest quality installed English voice it can find, preferring enhanced voices when available, and the settings panel includes a voice test.

For a less robotic neural voice, Kokoro is still the intended upgrade. Sherpa-ONNX also has Android/Kotlin support for Kokoro, Piper, and VITS engines, which makes it a better native Android route than hand-rolling ONNX Runtime integration. A production Kokoro implementation should add:

- Sherpa-ONNX Android runtime or ONNX Runtime Mobile.
- A quantized Kokoro ONNX model and voice file.
- A streaming PCM playback queue.
- A fallback to Android TTS when model load fails.

## Speech-to-text

The app no longer uses Android `RecognizerIntent`, so the child stays inside the app when pressing `Speak`. The APK records a five-second 16 kHz mono PCM clip using `AudioRecord`, writes it as a temporary WAV file, and transcribes it locally with whisper.cpp.

Bundled runtime/model:

- `dev.ffmpegkit-maintained:whisper-android:1.0.0`
- `app/src/main/assets/models/ggml-tiny.en.bin`
- `arm64-v8a` APK target

The first transcription copies the model from assets into app cache because whisper.cpp needs a filesystem path. All speech-to-text stays on device.

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleRelease
```

The release build is debug-key signed for easy side-loading during testing. Replace the signing config before distributing through Play Store.
