package de.darkspirit510.episodeoverview.presentation

import de.darkspirit510.episodeoverview.application.GenerateTableUseCase
import de.darkspirit510.episodeoverview.domain.EpisodeStatus

class HtmlGenerator(private val maxEpisodesPerSeason: Int = 75) {

       fun generateHtml(tableRows: List<GenerateTableUseCase.TableRow>): String {
           // First, find the maximum number of episodes across all series
           val seriesRows = tableRows.filterIsInstance<GenerateTableUseCase.SeriesTableRow>()
           val maxEpisodes = seriesRows.maxOfOrNull { it.episodes.size } ?: 0
           
           // Check if any season has overflow (more episodes than maxEpisodesPerSeason)
           val hasOverflow = seriesRows.any { it.totalEpisodeCount > maxEpisodesPerSeason }
           
           // Calculate the number of columns: season column + maxEpisodesPerSeason columns + overflow column if needed
           val episodeColumns = maxEpisodes.coerceAtMost(maxEpisodesPerSeason)
           
            val headerRow = buildString {
                append("<tr><th class=\"header-cell\">Season</th>")
                repeat(episodeColumns) { episodeIndex ->
                    append("<th class=\"header-cell\">${episodeIndex + 1}</th>")
                }
                if (hasOverflow) {
                    append("<th class=\"header-cell\">...</th>")
                }
                append("</tr>")
            }
           
            // Calculate total columns for colspan: season column + episode columns + overflow column if needed
            val totalDisplayColumns = 1 + maxEpisodesPerSeason + (if (hasOverflow) 1 else 0)
            
            val bodyRows = buildString {
                tableRows.forEach { row ->
                    when (row) {
                        is GenerateTableUseCase.SubDirHeaderRow -> {
                            append("<tr><td colspan=\"$totalDisplayColumns\" class=\"subdir-header\">${escapeHtml(row.subDir)}</td></tr>")
                        }
                         is GenerateTableUseCase.SeriesHeaderRow -> {
                             append("<tr><td colspan=\"$totalDisplayColumns\" class=\"series-header\">${escapeHtml(row.seriesName)}</td></tr>")
                         }
                        is GenerateTableUseCase.SeriesTableRow -> {
                            val seriesName = escapeHtml(row.seriesName)
                            val seasonNumber = row.seasonNumber
                            val seasonCell = "<td class=\"season-cell\" onclick=\"showRenameDialog('$seriesName', $seasonNumber)\">S${row.seasonNumber.toString().padStart(2, '0')}</td>"

                            // Limit episodes to maxEpisodesPerSeason
                            val limitedEpisodes = row.episodes.take(maxEpisodesPerSeason)
                             val episodeCells = limitedEpisodes.joinToString("") { cell ->
                                 val displayText = cell.episodeNumber.toString()
                                 val statusClass = when (cell.status) {
                                     EpisodeStatus.MATCHED -> "matched"
                                     EpisodeStatus.NAME_MISMATCH -> "name-mismatch"
                                     EpisodeStatus.FILESYSTEM_ONLY -> "filesystem-only"
                                     EpisodeStatus.TMDB_ONLY -> "tmdb-only"
                                 }
                                 
                                 // Add tooltip for different statuses
                                 when (cell.status) {
                                     EpisodeStatus.NAME_MISMATCH -> {
                                          if (cell.filesystemName != null && cell.tmdbName != null) {
                                               // Trim filesystem prefix (SXXEYY, space, dash, space) from filename
                                                val trimmedFsName = cell.filesystemName
                                                    .replace(Regex("^S\\d+E\\d+[\\s-]*"), "")
                                                    .replace(Regex("\\.[^.]*$"), "")
                                               val escapedFsName = escapeHtml(trimmedFsName)
                                            val trimmedTmdbName = cell.tmdbName
                                            val escapedTmdbName = escapeHtml(trimmedTmdbName)
                                          """<td class="episode-cell $statusClass">
                                                 <div class="tooltip">$displayText
                                                     <div class="tooltiptext">F: $escapedFsName<br>T: $escapedTmdbName</div>
                                                 </div>
                                             </td>"""
                                         } else {
                                             "<td class=\"episode-cell $statusClass\">$displayText</td>"
                                         }
                                     }
                                     EpisodeStatus.TMDB_ONLY -> {
                                         if (cell.tmdbName != null) {
                                          val escapedTmdbName = escapeHtml(cell.tmdbName)
                                          """<td class="episode-cell $statusClass">
                                                 <div class="tooltip">$displayText
                                                     <div class="tooltiptext">T: $escapedTmdbName</div>
                                                 </div>
                                             </td>"""
                                         } else {
                                             "<td class=\"episode-cell $statusClass\">$displayText</td>"
                                         }
                                     }
                                     EpisodeStatus.FILESYSTEM_ONLY -> {
                                         if (cell.filesystemName != null) {
                                          val escapedFsName = escapeHtml(cell.filesystemName)
                                          """<td class="episode-cell $statusClass">
                                                 <div class="tooltip">$displayText
                                                     <div class="tooltiptext">F: $escapedFsName</div>
                                                 </div>
                                             </td>"""
                                         } else {
                                             "<td class=\"episode-cell $statusClass\">$displayText</td>"
                                         }
                                     }
                                     EpisodeStatus.MATCHED -> {
                                         "<td class=\"episode-cell $statusClass\">$displayText</td>"
                                     }
                                 }
                             }

                            // Add overflow indicator if this season has more episodes than the limit
                            val overflowCell = if (row.totalEpisodeCount > maxEpisodesPerSeason) {
                                "<td class=\"overflow-cell\">...${row.totalEpisodeCount}</td>"
                            } else {
                                ""
                            }

                            // Pad with empty cells if this row has fewer episodes than the max display limit
                            val paddingCells = List(maxEpisodesPerSeason - limitedEpisodes.size) {
                                "<td class=\"empty-cell\"> </td>"
                            }.joinToString("")

                            append("<tr>$seasonCell$episodeCells$paddingCells$overflowCell</tr>")
                        }
                   }
               }
           }

         return """
         <!DOCTYPE html>
         <html lang="en">
         <head>
             <meta charset="UTF-8">
             <meta name="viewport" content="width=device-width, initial-scale=1.0">
             <title>Episode Overview</title>
             <link rel="stylesheet" href="/styles.css">
             <style>
                 #renameDialog {
                     display: none;
                     position: fixed;
                     top: 0;
                     left: 0;
                     width: 100%;
                     height: 100%;
                     background-color: rgba(0, 0, 0, 0.7);
                     z-index: 1000;
                     overflow: auto;
                 }
                 #renameDialogContent {
                     background-color: #fefefe;
                     margin: 10% auto;
                     padding: 20px;
                     border: 1px solid #888;
                     width: 80%;
                     max-width: 800px;
                     box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
                     position: relative;
                 }
                 .close {
                     color: #aaa;
                     position: absolute;
                     top: 10px;
                     right: 20px;
                     font-size: 28px;
                     font-weight: bold;
                     cursor: pointer;
                 }
                 .close:hover {
                     color: black;
                 }
                 #renameCommands {
                     white-space: pre-wrap;
                     font-family: monospace;
                     font-size: 14px;
                     line-height: 1.5;
                     background-color: #f5f5f5;
                     padding: 15px;
                     border-radius: 4px;
                     margin-top: 15px;
                     overflow-x: auto;
                 }
                 .season-cell {
                     cursor: pointer;
                     text-decoration: underline;
                     color: #0066cc;
                 }
                 .season-cell:hover {
                     color: #004499;
                     background-color: #f0f0f0;
                 }
             </style>
         </head>
         <body>
             <h1>Episode Overview</h1>
             <p><a href="/refresh">Refresh Data</a></p>
              <div class="table-container">
                  <table>
                      $headerRow
                      $bodyRows
                  </table>
              </div>
              
              <!-- Rename Dialog -->
              <div id="renameDialog" onclick="closeRenameDialog(event)">
                  <div id="renameDialogContent" onclick="event.stopPropagation()">
                      <span class="close" onclick="closeRenameDialog()">&times;</span>
                      <h2 id="renameDialogTitle">Rename Commands</h2>
                      <div id="renameCommands"></div>
                  </div>
              </div>
              
              <script>
                  function showRenameDialog(seriesName, seasonNumber) {
                      const dialog = document.getElementById('renameDialog');
                      const commandsDiv = document.getElementById('renameCommands');
                      const title = document.getElementById('renameDialogTitle');
                      
                      title.textContent = 'Rename Commands for ' + seriesName + ' S' + String(seasonNumber).padStart(2, '0');
                      commandsDiv.textContent = 'Loading...';
                      dialog.style.display = 'block';
                      
                      // Fetch rename commands via AJAX
                      const xhr = new XMLHttpRequest();
                      xhr.open('GET', '/rename-commands?series=' + encodeURIComponent(seriesName) + '&season=' + seasonNumber, true);
                      xhr.onreadystatechange = function() {
                          if (xhr.readyState === 4) {
                              if (xhr.status === 200) {
                                  commandsDiv.textContent = xhr.responseText;
                              } else {
                                  commandsDiv.textContent = 'Error: ' + xhr.statusText;
                              }
                          }
                      };
                      xhr.send();
                  }
                  
                  function closeRenameDialog(event) {
                      const dialog = document.getElementById('renameDialog');
                      if (event && event.target === dialog) {
                          dialog.style.display = 'none';
                      } else {
                          dialog.style.display = 'none';
                      }
                  }
                  
                  // Close dialog when clicking the X button
                  document.querySelector('.close').onclick = function() {
                      document.getElementById('renameDialog').style.display = 'none';
                  };
              </script>
         </body>
         </html>
         """.trimIndent()
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
