import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

fun test(exoPlayer: ExoPlayer, items: List<MediaItem>, currentIndex: Int) {
    val currentExoIndex = exoPlayer.currentMediaItemIndex
    // ...
}
