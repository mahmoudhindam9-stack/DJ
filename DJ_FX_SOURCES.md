# DJ FX Sources

The DJ FX Library is designed so the app can open trusted sound libraries and import downloaded audio files locally.

## Recommended sources

### Pixabay Sound Effects
- DJ search: https://pixabay.com/sound-effects/search/dj/
- License: Pixabay License.
- Suitable for personal and commercial projects under the site's license; attribution is not required.
- Do not redistribute a downloaded sound as a standalone asset/library.

### Freesound
- DJ search with CC0 filter: https://freesound.org/search/?q=dj&f=license:%22Creative+Commons+0%22
- Only use files explicitly marked CC0 when the goal is unrestricted redistribution/use.
- Freesound's API has separate terms and may require permission for commercial API use, so this app intentionally does not depend on the Freesound API.

## App integration

`DjFxLibrary.kt` adds an in-app DJ FX card to the existing Professional Sampler. The user can:

1. Open Pixabay DJ sounds or Freesound CC0 results.
2. Download sound files using the source site's own download flow.
3. Import multiple local audio files into the app.
4. Play the imported FX directly from the sampler.

Imported FX are stored in the app's private files directory under `dj_fx_library` rather than being redistributed by the app.

## Existing CC0 sampler

The existing `ProfessionalSampler.kt` keeps the repository's CC0 one-shot banks from `Boochi44/free-drum-samples`. That repository states its samples are CC0 1.0 and suitable for commercial use without attribution.
