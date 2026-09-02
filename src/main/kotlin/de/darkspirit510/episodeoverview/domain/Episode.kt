package de.darkspirit510.episodeoverview.domain

data class Episode(
    val number: Int,
    val name: String? = null,
    val fileName: String? = null,
    val status: EpisodeStatus = EpisodeStatus.FILESYSTEM_ONLY
)
