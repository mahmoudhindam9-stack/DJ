# Factory DJ FX Sources

The factory sound banks use real audio files from public CC0 sources. The app does **not** synthesize replacement tones for these factory pads.

## Sources

### Boochi44/free-drum-samples
CC0 1.0. Real WAV one-shot drum kits used by the Drums and Electronic banks.
https://github.com/Boochi44/free-drum-samples

### code4fukui/sound-cc0
CC0 1.0 public-domain sound effects used by the DJ FX and Party banks.
https://github.com/code4fukui/sound-cc0

### Kenney Sci-Fi Sounds via danvanderboom/Aetherium
The repository includes a committed Kenney Sci-Fi Sounds license file and documents the included Kenney sound effects as CC0 1.0. Selected real OGG files are used by the DJ FX, Electronic and Party banks.
https://github.com/danvanderboom/Aetherium/tree/main/samples/unity/Aphelion/Assets/ThirdParty/Kenney/SciFiSounds

### Kenney SFX subset via euuuuuan/voidclad-public
The project's provenance documents the selected Kenney Impact/Sci-Fi files as CC0-1.0 and redistributable. Selected real OGG files are used by the Party bank.
https://github.com/euuuuuuan/voidclad-public/tree/main/assets/sfx/kenney

## Runtime behavior

The Gradle task `prepareFactoryFxAssets` downloads the real source files at **build time** into generated Android assets. They are therefore packaged into the APK/AAB and are available offline at runtime.

The app does not contact these sources when a factory pad is pressed. `DjFxAudioEngine` plays `asset:///factory_fx/...` directly through the existing Media3/ExoPlayer audio path.

The task fails the build if any factory asset cannot be downloaded or is unexpectedly small, rather than silently shipping placeholder/generated audio.
