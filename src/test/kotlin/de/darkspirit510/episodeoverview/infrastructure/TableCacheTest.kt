package de.darkspirit510.episodeoverview.infrastructure

import de.darkspirit510.episodeoverview.application.GenerateTableUseCase
import de.darkspirit510.episodeoverview.domain.EpisodeStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File

class TableCacheTest {

    private lateinit var tempCacheDir: File
    private lateinit var tableCache: TableCache

    @BeforeEach
    fun setUp() {
        // Create a temporary directory for testing
        tempCacheDir = File(System.getProperty("java.io.tmpdir"), "episodeoverview-tablecache-test-${System.currentTimeMillis()}")
        tempCacheDir.mkdirs()
        
        // Set system property to use temp directory
        System.setProperty("episodeoverview.cache.dir", tempCacheDir.absolutePath)
        
        tableCache = TableCache(tempCacheDir.absolutePath)
    }

    @AfterEach
    fun tearDown() {
        // Clean up temporary directory
        tempCacheDir.deleteRecursively()
        // Clear system property
        System.clearProperty("episodeoverview.cache.dir")
    }

    @Test
    fun `save and load table rows`() {
        val tableRows = listOf(
            GenerateTableUseCase.SubDirHeaderRow("TestSubDir"),
            GenerateTableUseCase.SeriesHeaderRow("Series1"),
            GenerateTableUseCase.SeriesTableRow(
                seriesName = "Series1",
                seasonNumber = 1,
                episodes = listOf(
                    GenerateTableUseCase.EpisodeTableCell(1, EpisodeStatus.MATCHED, "file1.mp4", "Episode 1"),
                    GenerateTableUseCase.EpisodeTableCell(2, EpisodeStatus.NAME_MISMATCH, "file2.mp4", "Episode 2")
                ),
                totalEpisodeCount = 2
            )
        )

        // Save the table rows
        tableCache.save(tableRows)

        // Load the table rows
        val loadedRows = tableCache.load()

        assertNotNull(loadedRows)
        val nonNullLoadedRows = loadedRows!!
        assertEquals(tableRows.size, nonNullLoadedRows.size)
        
        // Check that the loaded rows have the same content
        assertEquals("TestSubDir", (nonNullLoadedRows[0] as GenerateTableUseCase.SubDirHeaderRow).subDir)
        assertEquals("Series1", (nonNullLoadedRows[1] as GenerateTableUseCase.SeriesHeaderRow).seriesName)
        
        val seriesTableRow = nonNullLoadedRows[2] as GenerateTableUseCase.SeriesTableRow
        assertEquals("Series1", seriesTableRow.seriesName)
        assertEquals(1, seriesTableRow.seasonNumber)
        assertEquals(2, seriesTableRow.episodes.size)
    }

    @Test
    fun `load from empty cache returns null`() {
        val loadedRows = tableCache.load()
        assertNull(loadedRows)
    }

    @Test
    fun `memory cache works after save`() {
        val tableRows = listOf(
            GenerateTableUseCase.SubDirHeaderRow("MemoryTest")
        )

        tableCache.save(tableRows)
        
        // Clear the filesystem cache file to test memory cache
        val cacheFile = File(tempCacheDir, "table_data.json")
        cacheFile.delete()
        
        // Should still return from memory cache
        val loadedRows = tableCache.load()
        assertNotNull(loadedRows)
        val nonNullLoadedRows = loadedRows!!
        assertEquals(1, nonNullLoadedRows.size)
    }

    @Test
    fun `clear cache removes both memory and filesystem cache`() {
        val tableRows = listOf(
            GenerateTableUseCase.SubDirHeaderRow("ClearTest")
        )

        tableCache.save(tableRows)
        
        // Verify cache exists
        assertNotNull(tableCache.load())
        
        // Clear the cache
        tableCache.clear()
        
        // Verify cache is cleared
        assertNull(tableCache.load())
        
        // Verify file is deleted
        val cacheFile = File(tempCacheDir, "table_data.json")
        assertEquals(false, cacheFile.exists())
    }

    @Test
    fun `corrupted cache file throws TableCacheException`() {
        // Create a corrupted cache file
        val cacheFile = File(tempCacheDir, "table_data.json")
        cacheFile.writeText("corrupted data {{{")

        assertThrows(TableCacheException::class.java) {
            tableCache.load()
        }
    }

    @Test
    fun `cache uses custom directory when system property is set`() {
        val customDir = File(tempCacheDir, "custom")
        customDir.mkdirs()
        
        System.setProperty("episodeoverview.cache.dir", customDir.absolutePath)
        
        val customTableCache = TableCache()
        val tableRows = listOf(
            GenerateTableUseCase.SubDirHeaderRow("CustomDirTest")
        )
        
        customTableCache.save(tableRows)
        
        // Verify file was created in custom directory
        val cacheFile = File(customDir, "table_data.json")
        assertEquals(true, cacheFile.exists())
        
        // Clean up
        customDir.deleteRecursively()
    }

    @Test
    fun `concurrent access is thread safe`() {
        val tableRows = listOf(
            GenerateTableUseCase.SubDirHeaderRow("ConcurrentTest")
        )

        // Test concurrent saves and loads
        val threads = mutableListOf<Thread>()
        
        // Create threads that save and load
        repeat(10) { _ ->
            threads.add(Thread {
                repeat(5) {
                    tableCache.save(tableRows)
                    tableCache.load()
                }
            })
        }
        
        // Start all threads
        threads.forEach { it.start() }
        
        // Wait for all threads to complete
        threads.forEach { it.join() }
        
        // Verify cache is still valid
        val loadedRows = tableCache.load()
        assertNotNull(loadedRows)
        val nonNullLoadedRows = loadedRows!!
        assertEquals(1, nonNullLoadedRows.size)
    }
}