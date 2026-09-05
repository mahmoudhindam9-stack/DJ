# Factory DJ FX sources

The factory catalog uses real audio files from public repositories that identify the source material as CC0/public-domain.

- `Boochi44/free-drum-samples`: CC0 1.0; real WAV one-shots for Drums/Electronic.
  https://github.com/Boochi44/free-drum-samples
- `code4fukui/sound-cc0`: CC0 1.0; real WAV sound effects.
  https://github.com/code4fukui/sound-cc0
- `manuel-palacio/brickstorm`: project provenance identifies the shipped SFX as CC0 Kenney-derived assets.
  https://github.com/manuel-palacio/brickstorm
- `euuuuuuan/voidclad-public`: project provenance identifies the selected Kenney SFX as CC0 and redistributable.
  https://github.com/euuuuuuan/voidclad-public

The application does not synthesize these factory sounds. `DjFxAudioEngine` downloads the real source file when first triggered and persists the downloaded file inside the app's private `djfx_factory_cache` directory for repeat/offline playback after the first successful download.
