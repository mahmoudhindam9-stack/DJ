package com.example.player

import com.example.model.AudioItem
import java.security.SecureRandom

/**
 * Controller and state machine for shuffle playback.
 * Enforces:
 * - Every Shuffle cycle has a NEW random order.
 * - No repetition within a cycle (all songs play once).
 * - Persisted cycle state across app restarts.
 * - Independent fresh randomization for new cycles.
 * - Prevents first song of new cycle matching the final song of previous cycle (when size > 1).
 * - Library additions insert into unplayed pool; deletions removed cleanly.
 * - Previous returns to actual playback history without reshuffling.
 */
object ShuffleManager {
    private val random = SecureRandom()

    /**
     * Creates a new random order for the given song pool.
     * Guarantees:
     * 1. No repetition of songs within the cycle.
     * 2. If pool size > 1, the new order will not match previousOrder.
     * 3. If lastCompletedSongId is given and pool size > 1, the first song of the new cycle
     *    will NOT be the same as the final song of the previous cycle.
     */
    fun generateCycleOrder(
        pool: List<AudioItem>,
        lastCompletedSongId: String? = null,
        previousOrder: List<AudioItem>? = null
    ): List<AudioItem> {
        val distinctPool = pool.distinctBy { it.id.ifEmpty { it.uri.toString() } }
        if (distinctPool.isEmpty()) return emptyList()
        if (distinctPool.size == 1) return distinctPool

        var order = distinctPool.shuffled(random)

        // Ensure fresh randomization that doesn't duplicate previous cycle order if pool has >= 2 songs
        var attempts = 0
        while (attempts < 10 && previousOrder != null && previousOrder.size == order.size &&
            order.map { it.id.ifEmpty { it.uri.toString() } } == previousOrder.map { it.id.ifEmpty { it.uri.toString() } }
        ) {
            order = distinctPool.shuffled(random)
            attempts++
        }

        // Prevent first song of new cycle from matching the final song of previous cycle
        val firstId = order.first().id.ifEmpty { order.first().uri.toString() }
        if (lastCompletedSongId != null && firstId == lastCompletedSongId && order.size > 1) {
            val swapIndex = 1 + random.nextInt(order.size - 1)
            val mutable = order.toMutableList()
            val temp = mutable[0]
            mutable[0] = mutable[swapIndex]
            mutable[swapIndex] = temp
            order = mutable
        }

        return order
    }

    /**
     * Starts a new cycle. If [preferredFirstSong] is specified (e.g. user selected a specific track),
     * that track is placed first, and the remaining pool is shuffled for the rest of the cycle.
     */
    fun startNewCycle(
        pool: List<AudioItem>,
        preferredFirstSong: AudioItem? = null,
        cycleNumber: Int = 1,
        lastCompletedSongId: String? = null
    ): ShuffleState {
        val distinctPool = pool.distinctBy { it.id.ifEmpty { it.uri.toString() } }
        if (distinctPool.isEmpty()) {
            return ShuffleState(emptyList(), -1, cycleNumber, lastCompletedSongId)
        }

        val order = if (preferredFirstSong != null) {
            val targetKey = preferredFirstSong.id.ifEmpty { preferredFirstSong.uri.toString() }
            val actualFirst = distinctPool.firstOrNull { (it.id.ifEmpty { it.uri.toString() }) == targetKey } ?: preferredFirstSong
            val others = distinctPool.filter { (it.id.ifEmpty { it.uri.toString() }) != targetKey }
            listOf(actualFirst) + others.shuffled(random)
        } else {
            generateCycleOrder(distinctPool, lastCompletedSongId)
        }

        return ShuffleState(
            currentOrder = order,
            currentIndex = 0,
            cycleNumber = cycleNumber,
            lastCompletedSongId = lastCompletedSongId
        )
    }

    /**
     * Advances to the next track.
     * If current cycle is exhausted (at the last track), generates a completely NEW cycle
     * with fresh randomization, cycleNumber + 1, and prevents first song = last song of previous cycle.
     */
    fun next(state: ShuffleState, pool: List<AudioItem>): ShuffleState {
        if (state.currentOrder.isEmpty()) {
            return startNewCycle(pool, cycleNumber = 1)
        }

        if (state.currentIndex < state.currentOrder.lastIndex) {
            return state.copy(currentIndex = state.currentIndex + 1)
        }

        // Cycle is exhausted!
        val lastSongId = state.currentOrder.lastOrNull()?.let { it.id.ifEmpty { it.uri.toString() } }
        val effectivePool = if (pool.isNotEmpty()) pool else state.currentOrder
        val newCycleOrder = generateCycleOrder(
            pool = effectivePool,
            lastCompletedSongId = lastSongId,
            previousOrder = state.currentOrder
        )

        return ShuffleState(
            currentOrder = newCycleOrder,
            currentIndex = 0,
            cycleNumber = state.cycleNumber + 1,
            lastCompletedSongId = lastSongId
        )
    }

    /**
     * Goes back to actual playback history without regenerating or reshuffling.
     */
    fun previous(state: ShuffleState): ShuffleState {
        if (state.currentIndex > 0) {
            return state.copy(currentIndex = state.currentIndex - 1)
        }
        return state.copy(currentIndex = 0.coerceAtMost(state.currentOrder.lastIndex))
    }

    /**
     * Jump to a specific index in the current cycle.
     */
    fun jumpToIndex(state: ShuffleState, index: Int): ShuffleState {
        if (state.currentOrder.isEmpty()) return state
        val safeIndex = index.coerceIn(0, state.currentOrder.lastIndex)
        return state.copy(currentIndex = safeIndex)
    }

    /**
     * Handles library modifications (songs added or removed):
     * - Removes deleted songs from cycle.
     * - Adds new songs to the remaining / unplayed pool.
     * - Preserves played history and current song position.
     * - When cycle finishes, next cycle will use full updated library.
     */
    fun onLibraryChanged(state: ShuffleState, newLibrary: List<AudioItem>): ShuffleState {
        val distinctLibrary = newLibrary.distinctBy { it.id.ifEmpty { it.uri.toString() } }
        val validKeys = distinctLibrary.map { it.id.ifEmpty { it.uri.toString() } }.toSet()

        if (state.currentOrder.isEmpty()) {
            return if (distinctLibrary.isNotEmpty()) {
                startNewCycle(distinctLibrary, cycleNumber = state.cycleNumber)
            } else {
                state.copy(currentOrder = emptyList(), currentIndex = -1)
            }
        }

        val currentSongKey = state.currentSong?.let { it.id.ifEmpty { it.uri.toString() } }
        val playedKeys = state.playedSongs.map { it.id.ifEmpty { it.uri.toString() } }
        val unplayedKeys = state.unplayedSongs.map { it.id.ifEmpty { it.uri.toString() } }

        // Filter deleted songs
        val remainingPlayed = state.playedSongs.filter { (it.id.ifEmpty { it.uri.toString() }) in validKeys }
        val currentStillValid = currentSongKey != null && currentSongKey in validKeys
        val remainingUnplayed = state.unplayedSongs.filter { (it.id.ifEmpty { it.uri.toString() }) in validKeys }

        // Find newly added songs not yet in this cycle
        val cycleKeys = (playedKeys + listOfNotNull(currentSongKey) + unplayedKeys).toSet()
        val addedSongs = distinctLibrary.filter { (it.id.ifEmpty { it.uri.toString() }) !in cycleKeys }

        // Add new songs to the unplayed pool, randomly shuffled
        val newUnplayedPool = (remainingUnplayed + addedSongs).shuffled(random)

        val newOrder = mutableListOf<AudioItem>()
        newOrder.addAll(remainingPlayed)

        val curSong = state.currentSong
        var newIndex: Int
        if (currentStillValid && curSong != null) {
            newOrder.add(curSong)
            newIndex = newOrder.lastIndex
            newOrder.addAll(newUnplayedPool)
        } else if (newUnplayedPool.isNotEmpty()) {
            newIndex = newOrder.size
            newOrder.addAll(newUnplayedPool)
        } else if (remainingPlayed.isNotEmpty()) {
            newIndex = remainingPlayed.lastIndex
        } else {
            newIndex = -1
        }

        return state.copy(
            currentOrder = newOrder,
            currentIndex = newIndex.coerceIn(-1, (newOrder.size - 1).coerceAtLeast(-1))
        )
    }
}
