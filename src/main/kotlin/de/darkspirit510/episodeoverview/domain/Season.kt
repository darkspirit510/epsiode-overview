package de.darkspirit510.episodeoverview.domain

data class Season(
    val number: Int,
    val episodes: Map<Int, Episode> = emptyMap()
)
