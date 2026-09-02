package com.example.onlinemusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.saveable.rememberSaveable as composeRememberSaveable

/** Compatibility overload for the existing Online screen's saved Boolean state. */
@Composable
fun rememberSaveable(initializer: () -> MutableState<Boolean>): MutableState<Boolean> =
    composeRememberSaveable { initializer() }
