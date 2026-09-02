package de.darkspirit510.episodeoverview.presentation

import de.darkspirit510.episodeoverview.application.GenerateTableUseCase
import de.darkspirit510.episodeoverview.domain.EpisodeStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class HtmlGeneratorTest {

    @Test
    fun `series header should render normally`() {

        val generator = HtmlGenerator()
        val tableRows = listOf(
            GenerateTableUseCase.SeriesHeaderRow("TestSeries")
        )

        val html = generator.generateHtml(tableRows)

        assertTrue(html.contains("TestSeries"))
    }

    @Test
    fun `season with all matched episodes should be hidden`() {

        val generator = HtmlGenerator()
        val tableRows = listOf(
            GenerateTableUseCase.SeriesHeaderRow("TestSeries"),
            GenerateTableUseCase.SeriesTableRow("TestSeries", 2, listOf(), 0)
        )

        val html = generator.generateHtml(tableRows)

        assertTrue(html.contains("TestSeries"))
        assertTrue(html.contains("S02"))
        assertFalse(html.contains("S01"))
    }

    @Test
    fun `dashes should be removed from tooltip names`() {
        val generator = HtmlGenerator()
        val tableRows = listOf(
            GenerateTableUseCase.SeriesHeaderRow("TestSeries"),
            GenerateTableUseCase.SeriesTableRow(
                "TestSeries",
                1,
                listOf(
                    GenerateTableUseCase.EpisodeTableCell(
                        1,
                        EpisodeStatus.NAME_MISMATCH,
                         "S01E01 - Dummy Episode.mp4",
                         "Dummy Episode"
                    )
                ),
                1
            )
        )

        val html = generator.generateHtml(tableRows)

        assertTrue(html.contains("F: Dummy Episode"))
        assertTrue(html.contains("T: Dummy Episode"))
        assertFalse(html.contains("Old-Style Episode"))
        assertFalse(html.contains("Old–Style Episode"))
    }
}