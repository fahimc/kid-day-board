# Kid Day Board

Android task board for children. Parents can add tasks for each day of the week, children see today's board first, and the week view is one tap away.

## Features

- Day-first board with a week switcher.
- Parent task entry for any weekday.
- Completion badge on finished tasks.
- Celebration confetti when all of today's tasks are complete.
- `Hello` button that speaks a child-friendly prompt.
- `Speak` button that uses Android speech recognition and marks matching tasks complete.
- Gemma 4 E2B-ready coach layer through LiteRT-LM.
- TTS abstraction with Android TTS enabled today and a Kokoro ONNX path documented below.

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

The shipped APK uses Android `TextToSpeech` so it works immediately. Kokoro is the intended neural TTS upgrade because the quantized ONNX mobile variants are small enough for on-device use. A production Kokoro implementation should add:

- ONNX Runtime Mobile.
- A quantized Kokoro ONNX model and voice file.
- A streaming PCM playback queue.
- A fallback to Android TTS when model load fails.

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleRelease
```

The release build is debug-key signed for easy side-loading during testing. Replace the signing config before distributing through Play Store.
