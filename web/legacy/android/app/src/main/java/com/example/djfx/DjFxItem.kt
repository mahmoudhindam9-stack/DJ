package com.example.djfx

data class DjFxItem(
    val id: String,
    val name: String,
    val category: String,
    val source: String,
    val license: String,
    val sourceUrl: String,
    val localUri: String? = null,
    val isFavorite: Boolean = false
)
