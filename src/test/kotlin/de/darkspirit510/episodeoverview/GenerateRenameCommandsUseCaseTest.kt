package de.darkspirit510.episodeoverview

import de.darkspirit510.episodeoverview.application.GenerateRenameCommandsUseCase
import de.darkspirit510.episodeoverview.domain.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GenerateRenameCommandsUseCaseTest {

    @Test
    fun `test generate mv commands for mismatched episodes`() {
        val cacheManager = createTestCacheManager(
            scannedEpisodes = mapOf(
                "Temp/Test Series" to listOf(
                    ScannedEpisode(
                        seasonNumber = 1,
                        episodeNumber = 1,
                        fileName = "S01E01 - Unbenannt.mkv",
                        episodeName = null
                    ),
                    ScannedEpisode(
                        seasonNumber = 1,
                        episodeNumber = 2,
                        fileName = "S01E02 - Unbenannt.mkv",
                        episodeName = null
                    ),
                    ScannedEpisode(1, 3, listOf(3), "S01E03 - Correct Name.mkv", "Correct Name")
                )
            ),
            tmdbSeries = mapOf(
                123 to TmdbSeries(
                    id = 123,
                    name = "Test Series",
                    seasons = listOf(
                        TmdbSeason(
                            id = 1,
                            seasonNumber = 1,
                            episodes = listOf(
                                TmdbEpisode(
                                    id = 1,
                                    name = "Pilot",
                                    episodeNumber = 1,
                                    seasonNumber = 1
                                ),
                                TmdbEpisode(
                                    id = 2,
                                    name = "Second Episode",
                                    episodeNumber = 2,
                                    seasonNumber = 1
                                ),
                                TmdbEpisode(
                                    id = 3,
                                    name = "Correct Name",
                                    episodeNumber = 3,
                                    seasonNumber = 1
                                )
                            )
                        )
                    )
                )
            )
        )

        val useCase = GenerateRenameCommandsUseCase(cacheManager)
        val result = useCase.generateRenameCommands("Test Series", 1)
        
        assertEquals(2, result.commands.size)
        assertEquals("S01E01 - Unbenannt.mkv", result.commands[0].oldFileName)
        assertEquals("S01E01 - Pilot.mkv", result.commands[0].newFileName)
        assertEquals("S01E02 - Unbenannt.mkv", result.commands[1].oldFileName)
        assertEquals("S01E02 - Second Episode.mkv", result.commands[1].newFileName)
    }

    @Test
    fun `test formatAsShellCommands generates proper shell commands`() {
        val cacheManager = createTestCacheManager(
            scannedEpisodes = mapOf(
                "Temp/Test Series" to listOf(
                    ScannedEpisode(
                        seasonNumber = 1,
                        episodeNumber = 1,
                        fileName = "S01E01 - Unbenannt.mkv",
                        episodeName = null
                    )
                )
            ),
            tmdbSeries = mapOf(
                123 to TmdbSeries(
                    id = 123,
                    name = "Test Series",
                    seasons = listOf(
                        TmdbSeason(
                            id = 1,
                            seasonNumber = 1,
                            episodes = listOf(
                                TmdbEpisode(
                                    id = 1,
                                    name = "Pilot",
                                    episodeNumber = 1,
                                    seasonNumber = 1
                                )
                            )
                        )
                    )
                )
            )
        )

        val useCase = GenerateRenameCommandsUseCase(cacheManager)
        val result = useCase.generateRenameCommands("Test Series", 1)
        val shellCommands = useCase.formatAsShellCommands(result)
        
        val expectedCommand = "mv 'S01E01 - Unbenannt.mkv' 'S01E01 - Pilot.mkv'"
        assertEquals(expectedCommand, shellCommands.trim())
    }

    @Test
    fun `test formatAsShellCommands handles filenames with special characters`() {
        val cacheManager = createTestCacheManager(
            scannedEpisodes = mapOf(
                "Temp/Test Series" to listOf(
                    ScannedEpisode(
                        seasonNumber = 1,
                        episodeNumber = 1,
                        fileName = "S01E01 - Unbenannt.mkv",
                        episodeName = null
                    )
                )
            ),
            tmdbSeries = mapOf(
                123 to TmdbSeries(
                    id = 123,
                    name = "Test Series",
                    seasons = listOf(
                        TmdbSeason(
                            id = 1,
                            seasonNumber = 1,
                            episodes = listOf(
                                TmdbEpisode(
                                    id = 1,
                                    name = "Pilot Episode",
                                    episodeNumber = 1,
                                    seasonNumber = 1
                                )
                            )
                        )
                    )
                )
            )
        )

        val useCase = GenerateRenameCommandsUseCase(cacheManager)
        val result = useCase.generateRenameCommands("Test Series", 1)
        val shellCommands = useCase.formatAsShellCommands(result)
        
        // Test that filenames are properly quoted
        assertTrue(shellCommands.contains("'"))
        assertTrue(shellCommands.contains("S01E01 - Unbenannt.mkv"))
        assertTrue(shellCommands.contains("S01E01 - Pilot Episode.mkv"))
    }

    @Test
    fun `test no commands generated for matched episodes`() {
        val cacheManager = createTestCacheManager(
            scannedEpisodes = mapOf(
                "Temp/Test Series" to listOf(
                    ScannedEpisode(
                        seasonNumber = 1,
                        episodeNumber = 1,
                        fileName = "S01E01 - Pilot.mkv",
                        episodeName = "Pilot"
                    )
                )
            ),
            tmdbSeries = mapOf(
                123 to TmdbSeries(
                    id = 123,
                    name = "Test Series",
                    seasons = listOf(
                        TmdbSeason(
                            id = 1,
                            seasonNumber = 1,
                            episodes = listOf(
                                TmdbEpisode(
                                    id = 1,
                                    name = "Pilot",
                                    episodeNumber = 1,
                                    seasonNumber = 1
                                )
                            )
                        )
                    )
                )
            )
        )

        val useCase = GenerateRenameCommandsUseCase(cacheManager)
        val result = useCase.generateRenameCommands("Test Series", 1)
        
        assertEquals(0, result.commands.size)
    }

    @Test
    fun `test commands generated for filesystem only episodes`() {
        val cacheManager = createTestCacheManager(
            scannedEpisodes = mapOf(
                "Temp/Test Series" to listOf(
                    ScannedEpisode(
                        seasonNumber = 1,
                        episodeNumber = 1,
                        fileName = "S01E01 - Extra Episode.mkv",
                        episodeName = null
                    )
                )
            ),
            tmdbSeries = mapOf(
                123 to TmdbSeries(
                    id = 123,
                    name = "Test Series",
                    seasons = listOf(
                        TmdbSeason(
                            id = 1,
                            seasonNumber = 1,
                            episodes = listOf(
                                TmdbEpisode(
                                    id = 1,
                                    name = "Pilot",
                                    episodeNumber = 1,
                                    seasonNumber = 1
                                )
                            )
                        )
                    )
                )
            )
        )

        val useCase = GenerateRenameCommandsUseCase(cacheManager)
        val result = useCase.generateRenameCommands("Test Series", 1)
        
        assertEquals(1, result.commands.size)
        assertEquals("S01E01 - Extra Episode.mkv", result.commands[0].oldFileName)
        assertEquals("S01E01 - Pilot.mkv", result.commands[0].newFileName)
    }

    @Test
    fun `test multiple seasons handled correctly`() {
        val cacheManager = createTestCacheManager(
            scannedEpisodes = mapOf(
                "Temp/Test Series" to listOf(
                    ScannedEpisode(
                        seasonNumber = 1,
                        episodeNumber = 1,
                        fileName = "S01E01 - Unbenannt.mkv",
                        episodeName = null
                    ),
                    ScannedEpisode(
                        seasonNumber = 2,
                        episodeNumber = 1,
                        fileName = "S02E01 - Unbenannt.mkv",
                        episodeName = null
                    )
                )
            ),
            tmdbSeries = mapOf(
                123 to TmdbSeries(
                    id = 123,
                    name = "Test Series",
                    seasons = listOf(
                        TmdbSeason(
                            id = 1,
                            seasonNumber = 1,
                            episodes = listOf(
                                TmdbEpisode(
                                    id = 1,
                                    name = "Pilot",
                                    episodeNumber = 1,
                                    seasonNumber = 1
                                )
                            )
                        ),
                        TmdbSeason(
                            id = 2,
                            seasonNumber = 2,
                            episodes = listOf(
                                TmdbEpisode(
                                    id = 2,
                                    name = "Season 2 Opener",
                                    episodeNumber = 1,
                                    seasonNumber = 2
                                )
                            )
                        )
                    )
                )
            )
        )

        val useCase = GenerateRenameCommandsUseCase(cacheManager)
        
        val resultSeason1 = useCase.generateRenameCommands("Test Series", 1)
        assertEquals(1, resultSeason1.commands.size)
        assertEquals("S01E01 - Unbenannt.mkv", resultSeason1.commands[0].oldFileName)
        assertEquals("S01E01 - Pilot.mkv", resultSeason1.commands[0].newFileName)
        
        val resultSeason2 = useCase.generateRenameCommands("Test Series", 2)
        assertEquals(1, resultSeason2.commands.size)
        assertEquals("S02E01 - Unbenannt.mkv", resultSeason2.commands[0].oldFileName)
        assertEquals("S02E01 - Season 2 Opener.mkv", resultSeason2.commands[0].newFileName)
    }

    @Test
    fun `test different file extensions handled correctly`() {
        val cacheManager = createTestCacheManager(
            scannedEpisodes = mapOf(
                "Temp/Test Series" to listOf(
                    ScannedEpisode(
                        seasonNumber = 1,
                        episodeNumber = 1,
                        fileName = "S01E01 - Unbenannt.mp4",
                        episodeName = null
                    ),
                    ScannedEpisode(
                        seasonNumber = 1,
                        episodeNumber = 2,
                        fileName = "S01E02 - Unbenannt.avi",
                        episodeName = null
                    ),
                    ScannedEpisode(
                        seasonNumber = 1,
                        episodeNumber = 3,
                        fileName = "S01E03 - Unbenannt.mkv",
                        episodeName = null
                    )
                )
            ),
            tmdbSeries = mapOf(
                123 to TmdbSeries(
                    id = 123,
                    name = "Test Series",
                    seasons = listOf(
                        TmdbSeason(
                            id = 1,
                            seasonNumber = 1,
                            episodes = listOf(
                                TmdbEpisode(
                                    id = 1,
                                    name = "Pilot",
                                    episodeNumber = 1,
                                    seasonNumber = 1
                                ),
                                TmdbEpisode(
                                    id = 2,
                                    name = "Episode 2",
                                    episodeNumber = 2,
                                    seasonNumber = 1
                                ),
                                TmdbEpisode(
                                    id = 3,
                                    name = "Episode 3",
                                    episodeNumber = 3,
                                    seasonNumber = 1
                                )
                            )
                        )
                    )
                )
            )
        )

        val useCase = GenerateRenameCommandsUseCase(cacheManager)
        val result = useCase.generateRenameCommands("Test Series", 1)
        
        assertEquals(3, result.commands.size)
        assertEquals("S01E01 - Pilot.mp4", result.commands[0].newFileName)
        assertEquals("S01E02 - Episode 2.avi", result.commands[1].newFileName)
        assertEquals("S01E03 - Episode 3.mkv", result.commands[2].newFileName)
    }
}
