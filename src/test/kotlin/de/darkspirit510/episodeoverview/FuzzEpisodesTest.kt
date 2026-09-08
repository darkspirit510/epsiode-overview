package de.darkspirit510.episodeoverview

import de.darkspirit510.episodeoverview.application.GenerateTableUseCase
import de.darkspirit510.episodeoverview.application.GenerateTableUseCase.SeriesTableRow
import de.darkspirit510.episodeoverview.domain.*
import de.darkspirit510.episodeoverview.infrastructure.FuzzEpisodesConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class FuzzEpisodesTest {

    private var backupConfigFile: File? = null
    private var backupResourcesConfigFile: File? = null

    @BeforeEach
    fun setUp() {
        // Reset the cached config before each test
        FuzzEpisodesConfig.resetCache()
        
        // Backup any existing config files that we might overwrite
        val configFile = File("fuzz_episodes.conf")
        if (configFile.exists()) {
            backupConfigFile = File("fuzz_episodes.conf.backup")
            configFile.copyTo(backupConfigFile!!, overwrite = true)
            configFile.delete()
        }
        
        val resourcesConfigFile = File("src/main/resources/fuzz_episodes.conf")
        if (resourcesConfigFile.exists()) {
            backupResourcesConfigFile = File("src/main/resources/fuzz_episodes.conf.backup")
            resourcesConfigFile.copyTo(backupResourcesConfigFile!!, overwrite = true)
            resourcesConfigFile.delete()
        }
    }

    @AfterEach
    fun tearDown() {
        // Restore original config files if they existed BEFORE cleaning up
        backupConfigFile?.let { backup ->
            if (backup.exists()) {
                backup.copyTo(File("fuzz_episodes.conf"), overwrite = true)
                backup.delete()
            }
        }
        
        backupResourcesConfigFile?.let { backup ->
            if (backup.exists()) {
                backup.copyTo(File("src/main/resources/fuzz_episodes.conf"), overwrite = true)
                backup.delete()
            }
        }
        
        // Clean up any test files we created (only if no backup existed)
        if (backupConfigFile == null) {
            File("fuzz_episodes.conf").delete()
        }
        if (backupResourcesConfigFile == null) {
            File("src/main/resources/fuzz_episodes.conf").delete()
        }
        
        // Clean up backup files
        File("fuzz_episodes.conf.backup").delete()
        File("src/main/resources/fuzz_episodes.conf.backup").delete()
        
        FuzzEpisodesConfig.resetCache()
    }

    // ==================== FuzzEpisodesConfig Tests ====================

    @Test
    fun `test config file loading from filesystem`() {
        // Create a temporary config file
        val configFile = File("fuzz_episodes.conf")
        configFile.writeText("Test Series 1\nTest Series 2\n# Comment\n")

        val series = FuzzEpisodesConfig.getFuzzSeries()
        assertTrue(series.contains("Test Series 1"))
        assertTrue(series.contains("Test Series 2"))
        assertFalse(series.contains("# Comment"))
        assertFalse(series.contains(""))
    }

    @Test
    fun `test shouldFuzzSeries returns true for configured series`() {
        val configFile = File("fuzz_episodes.conf")
        configFile.writeText("Test Series\n")

        assertTrue(FuzzEpisodesConfig.shouldFuzzSeries("Test Series"))
        assertTrue(FuzzEpisodesConfig.shouldFuzzSeries("test series")) // case insensitive
    }

    @Test
    fun `test shouldFuzzSeries returns false for non-configured series`() {
        val configFile = File("fuzz_episodes.conf")
        configFile.writeText("Test Series\n")

        assertFalse(FuzzEpisodesConfig.shouldFuzzSeries("Other Series"))
    }

    @Test
    fun `test empty config when no files found`() {
        // Ensure no config file exists
        File("fuzz_episodes.conf").delete()
        File("src/main/resources/fuzz_episodes.conf").delete()
        
        FuzzEpisodesConfig.resetCache()
        
        val series = FuzzEpisodesConfig.getFuzzSeries()
        assertTrue(series.isEmpty(), "Should have no series when no config files exist")
    }

    // ==================== SeriesCutPoints Domain Tests ====================

    @Test
    fun `test SeriesCutPoints getCutPoint returns correct cut point`() {
        val cutPoints = SeriesCutPoints("Test Series", listOf(
            SeasonCutPoint(1, 28),
            SeasonCutPoint(2, 50)
        ))

        assertEquals(28, cutPoints.getCutPoint(1))
        assertEquals(50, cutPoints.getCutPoint(2))
        assertNull(cutPoints.getCutPoint(3))
    }

    @Test
    fun `test SeriesCutPoints getCutPoint returns null for non-existent season`() {
        val cutPoints = SeriesCutPoints("Test Series", emptyList())
        assertNull(cutPoints.getCutPoint(1))
    }

    @Test
    fun `test SeriesCutPoints creation and properties`() {
        val cutPoints = SeriesCutPoints("Test Series", listOf(
            SeasonCutPoint(1, 28)
        ))

        assertEquals("Test Series", cutPoints.seriesName)
        assertEquals(1, cutPoints.cutPoints.size)
    }

    // ==================== SeasonCutPoint Domain Tests ====================

    @Test
    fun `test SeasonCutPoint creation and properties`() {
        val cutPoint = SeasonCutPoint(1, 28)
        assertEquals(1, cutPoint.tmdbSeason)
        assertEquals(28, cutPoint.cutAtEpisode)
    }

    // ==================== SeriesName Normalization Tests ====================

    @Test
    fun `test series name normalization for matching`() {
        // Test the normalization logic used in FuzzEpisodesConfig
        val config = FuzzEpisodesConfig
        
        // Access the private normalizeSeriesName function via reflection
        val normalizeMethod = config.javaClass.getDeclaredMethod("normalizeSeriesName", String::class.java)
        normalizeMethod.isAccessible = true
        
        val normalize = { name: String -> normalizeMethod.invoke(config, name) as String }
        
        // Test various normalizations
        assertEquals("test-series", normalize("Test Series"))
        assertEquals("test-series", normalize("test series"))
        assertEquals("test-series", normalize("Test-Series"))
        assertEquals("another-series", normalize("Another Series"))
        assertEquals("another-series", normalize("another series"))
    }

    @Test
    fun `test fuzzing matches normalized series names`() {
        val configFile = File("fuzz_episodes.conf")
        configFile.writeText("Test Series\n")

        // All these variations should match the config entry
        assertTrue(FuzzEpisodesConfig.shouldFuzzSeries("Test Series"))
        assertTrue(FuzzEpisodesConfig.shouldFuzzSeries("test series"))
        assertTrue(FuzzEpisodesConfig.shouldFuzzSeries("Test-Series"))
        assertTrue(FuzzEpisodesConfig.shouldFuzzSeries("test-series"))
    }

    // ==================== Episode Status Tests ====================

    @Test
    fun `test EpisodeStatus values`() {
        // Verify all expected status values exist
        assertEquals("MATCHED", EpisodeStatus.MATCHED.toString())
        assertEquals("NAME_MISMATCH", EpisodeStatus.NAME_MISMATCH.toString())
        assertEquals("FILESYSTEM_ONLY", EpisodeStatus.FILESYSTEM_ONLY.toString())
        assertEquals("TMDB_ONLY", EpisodeStatus.TMDB_ONLY.toString())
    }

    // ==================== Cut Point Detection Tests ====================

    @Test
    fun `test SeriesCutPoints with multiple cut points`() {
        val cutPoints = SeriesCutPoints("Test Series", listOf(
            SeasonCutPoint(1, 28),
            SeasonCutPoint(2, 50),
            SeasonCutPoint(3, 100)
        ))

        assertEquals(28, cutPoints.getCutPoint(1))
        assertEquals(50, cutPoints.getCutPoint(2))
        assertEquals(100, cutPoints.getCutPoint(3))
        assertNull(cutPoints.getCutPoint(4))
    }

    @Test
    fun `test SeriesCutPoints hasCutPoints`() {
        val cutPointsWithData = SeriesCutPoints("Test Series", listOf(SeasonCutPoint(1, 28)))
        val cutPointsEmpty = SeriesCutPoints("Test Series", emptyList())
        
        assertTrue(cutPointsWithData.hasCutPoints())
        assertFalse(cutPointsEmpty.hasCutPoints())
    }

    // ==================== Config File Loading Tests ====================

    @Test
    fun `test config loading from src main resources`() {
        // Create config in src/main/resources
        val configFile = File("src/main/resources/fuzz_episodes.conf")
        configFile.writeText("Test Series 1\nTest Series 2\n")

        val series = FuzzEpisodesConfig.getFuzzSeries()
        assertTrue(series.contains("Test Series 1"))
        assertTrue(series.contains("Test Series 2"))
    }

    @Test
    fun `test config loading from current directory`() {
        // Create config in current directory
        val configFile = File("fuzz_episodes.conf")
        configFile.writeText("Test Series 1\nTest Series 2\n")

        val series = FuzzEpisodesConfig.getFuzzSeries()
        assertTrue(series.contains("Test Series 1"))
        assertTrue(series.contains("Test Series 2"))
    }
}
