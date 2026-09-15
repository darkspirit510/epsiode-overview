package de.darkspirit510.episodeoverview.domain

data class ScannedEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeNumbers: List<Int> = listOf(),
    val fileName: String,
    val episodeName: String? = null
)
