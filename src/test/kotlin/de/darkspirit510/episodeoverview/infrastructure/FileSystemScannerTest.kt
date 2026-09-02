package de.darkspirit510.episodeoverview.infrastructure

import de.darkspirit510.episodeoverview.domain.ScannedEpisode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.io.TempDir
import java.io.File

class FileSystemScannerTest {

    @Test
    fun `should skip Bonus and Output directories and log their paths`(@TempDir tempDir: File) {
        val scanner = FileSystemScanner()

        val seriesDir = File(tempDir, "TestSeries").apply { mkdir() }
        val seasonDir = File(seriesDir, "S01").apply { mkdir() }
        File(seasonDir, "S01E01 Episode 1.mp4").createNewFile()

        val bonusDir = File(tempDir, "Bonus").apply { mkdir() }
        val bonusSeasonDir = File(bonusDir, "S01").apply { mkdir() }
        File(bonusSeasonDir, "S01E01 Bonus Episode.mp4").createNewFile()

        val outputDir = File(tempDir, "Output").apply { mkdir() }
        val outputSeasonDir = File(outputDir, "S01").apply { mkdir() }
        File(outputSeasonDir, "S01E01 Output Episode.mp4").createNewFile()

        val result = scanner.scanDirectory(tempDir.absolutePath, listOf("TestSeries", "Bonus", "Output"))

        assertEquals(1, result.size)
        assertTrue(result.containsKey("TestSeries/TestSeries"))
    }

    @Test
    fun `should skip nested Bonus and Output directories`(@TempDir tempDir: File) {
        val scanner = FileSystemScanner()

        val seriesDir = File(tempDir, "TestSeries").apply { mkdir() }
        val nestedDir = File(seriesDir, "ActualSeries").apply { mkdir() }
        val seasonDir = File(nestedDir, "S01").apply { mkdir() }
        File(seasonDir, "S01E01 Episode 1.mp4").createNewFile()

        val nestedBonusDir = File(nestedDir, "Bonus").apply { mkdir() }
        val nestedBonusSeasonDir = File(nestedBonusDir, "S01").apply { mkdir() }
        File(nestedBonusSeasonDir, "S01E01 Bonus Episode.mp4").createNewFile()

        val nestedOutputDir = File(nestedDir, "Output").apply { mkdir() }
        val nestedOutputSeasonDir = File(nestedOutputDir, "S01").apply { mkdir() }
        File(nestedOutputSeasonDir, "S01E01 Output Episode.mp4").createNewFile()

        val result = scanner.scanDirectory(tempDir.absolutePath, listOf("TestSeries"))

         assertEquals(1, result.size)
         assertTrue(result.containsKey("TestSeries/ActualSeries"))
     }

    @Test
    fun `should extract episode name from standard filename`() {
        val scanner = FileSystemScanner()
        val result = scanner.extractEpisodeInfo("S01E01 - Episode Name.mp4")
        
        assertEquals(1, result?.seasonNumber)
        assertEquals(1, result?.episodeNumber)
        assertEquals("S01E01 - Episode Name.mp4", result?.fileName)
        assertEquals("Episode Name.mp4", result?.episodeName)
    }

    @Test
    fun `should handle multi-episode range S01E01-E02 - Dummy Episode`() {
        val scanner = FileSystemScanner()
        val result = scanner.extractEpisodeInfo("S01E01-E02 - Dummy Episode.mp4")
        
        assertEquals(1, result?.seasonNumber)
        assertEquals(1, result?.episodeNumber)
        assertEquals("S01E01-E02 - Dummy Episode.mp4", result?.fileName)
        assertEquals("Dummy Episode.mp4", result?.episodeName)
    }

    @Test
    fun `should handle concatenated episodes S01E01E02 - Dummy Concatenated`() {
        val scanner = FileSystemScanner()
        val result = scanner.extractEpisodeInfo("S01E01E02 - Dummy Concatenated.mp4")
        
        assertEquals(1, result?.seasonNumber)
        assertEquals(1, result?.episodeNumber)
        assertEquals("S01E01E02 - Dummy Concatenated.mp4", result?.fileName)
        assertEquals("Dummy Concatenated.mp4", result?.episodeName)
    }

    @Test
    fun `should handle filename without episode name`() {
        val scanner = FileSystemScanner()
        val result = scanner.extractEpisodeInfo("S01E01.mp4")
        
        assertEquals(1, result?.seasonNumber)
        assertEquals(1, result?.episodeNumber)
        assertEquals("S01E01.mp4", result?.fileName)
        assertNull(result?.episodeName)
    }

    @Test
    fun `should handle filename with spaces and special characters in name`() {
        val scanner = FileSystemScanner()
        val result = scanner.extractEpisodeInfo("S02E05 - The Beginning of the End (Part 1).mkv")
        
        assertEquals(2, result?.seasonNumber)
        assertEquals(5, result?.episodeNumber)
        assertEquals("S02E05 - The Beginning of the End (Part 1).mkv", result?.fileName)
        assertEquals("The Beginning of the End (Part 1).mkv", result?.episodeName)
    }

    @Test
    fun `should return null for invalid filename`() {
        val scanner = FileSystemScanner()
        val result = scanner.extractEpisodeInfo("Invalid Filename.mp4")
        
        assertNull(result)
    }

    @Test
    fun `should skip season 0`() {
        val scanner = FileSystemScanner()
        val result = scanner.extractEpisodeInfo("S00E01 - Special Episode.mp4")
        
        assertNull(result)
    }
}
