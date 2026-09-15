package de.darkspirit510.episodeoverview.infrastructure

import de.darkspirit510.episodeoverview.application.GenerateTableUseCase

interface TableCacheInterface {
    fun save(tableRows: List<GenerateTableUseCase.TableRow>)
    fun load(): List<GenerateTableUseCase.TableRow>?
}