package de.darkspirit510.episodeoverview.presentation

import de.darkspirit510.episodeoverview.application.GenerateRenameCommandsUseCase
import de.darkspirit510.episodeoverview.application.GenerateTableUseCase
import de.darkspirit510.episodeoverview.application.RefreshUseCase
import de.darkspirit510.episodeoverview.infrastructure.CacheManager
import de.darkspirit510.episodeoverview.infrastructure.FileSystemScanner
import de.darkspirit510.episodeoverview.infrastructure.TmdbClient
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.module() {
    // Load configuration - prioritize environment variables, then config file
    // Try to load from classpath first (for IntelliJ), then fall back to environment.config
    val classpathProps = mutableMapOf<String, String>()
    try {
        javaClass.getResourceAsStream("/application.conf")?.use { inputStream ->
            inputStream.bufferedReader().forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                    val (key, value) = trimmed.split("=", limit = 2)
                    classpathProps[key.trim()] = value.trim().removeSurrounding("\"", "\"")
                }
            }
        }
    } catch (e: Exception) {
        // Ignore
    }
    
    val config = environment.config
    
    val baseDir = System.getenv("BASE_DIR") 
        ?: classpathProps["baseDir"]
        ?: config.propertyOrNull("baseDir")?.getString()
        ?: throw IllegalStateException("Base directory is required. Set BASE_DIR environment variable or baseDir in application.conf")
    
    val subDirsRaw = System.getenv("SUB_DIRS") 
        ?: classpathProps["subDirs"]
        ?: config.propertyOrNull("subDirs")?.getString()
        ?: "[\"Temp\"]"
    
    val subDirs = subDirsRaw
        .removeSurrounding("[", "]")
        .split(",")
        .map { dir -> dir.trim().removeSurrounding("\"", "\"") }
    
    val tmdbApiToken = System.getenv("TMDB_API_TOKEN") 
        ?: classpathProps["tmdbApiToken"]
        ?: config.propertyOrNull("tmdbApiToken")?.getString()
        ?: throw IllegalStateException("TMDB API token is required. Set TMDB_API_TOKEN environment variable or tmdbApiToken in application.conf. Note: Must be the 'API Key (v3 auth)' from https://www.themoviedb.org/settings/api")
    
    val tmdbBaseUrl = System.getenv("TMDB_BASE_URL") 
        ?: classpathProps["tmdbBaseUrl"]
        ?: config.propertyOrNull("tmdbBaseUrl")?.getString() 
        ?: "https://api.themoviedb.org/3"
    
    val maxEpisodesPerSeason = System.getenv("MAX_EPISODES_PER_SEASON")
        ?.toIntOrNull()
        ?: classpathProps["maxEpisodesPerSeason"]?.toIntOrNull()
        ?: config.propertyOrNull("maxEpisodesPerSeason")?.getString()?.toIntOrNull()
        ?: 75

    val fileSystemScanner = FileSystemScanner()
    val tmdbClient = TmdbClient(tmdbApiToken, tmdbBaseUrl)
    val cacheManager = CacheManager()

    val refreshUseCase = RefreshUseCase(fileSystemScanner, tmdbClient, cacheManager, baseDir, subDirs)
    val generateTableUseCase = GenerateTableUseCase(cacheManager)
    val generateRenameCommandsUseCase = GenerateRenameCommandsUseCase(cacheManager)
    val htmlGenerator = HtmlGenerator(maxEpisodesPerSeason)

    routing {
        get("/refresh") {
            try {
                val summary = refreshUseCase.refresh()
                val summaryText = summary.entries.joinToString("<br>") { "${it.key}: ${it.value}" }
                call.respondText(
                    "Refresh complete!<br><br>$summaryText<br><br><a href='/table'>View table</a>",
                    contentType = ContentType.Text.Html
                )
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Refresh failed: ${e.message}")
            }
        }

        get("/") {
            try {
                val tableRows = generateTableUseCase.generateTable()
                val html = htmlGenerator.generateHtml(tableRows)
                call.respondText(html, contentType = ContentType.Text.Html)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Table generation failed: ${e.message}")
            }
        }

        get("/table") {
            try {
                val tableRows = generateTableUseCase.generateTable()
                val html = htmlGenerator.generateHtml(tableRows)
                call.respondText(html, contentType = ContentType.Text.Html)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Table generation failed: ${e.message}")
            }
        }

        get("/rename-commands") {
            try {
                val series = call.request.queryParameters["series"]
                val season = call.request.queryParameters["season"]?.toIntOrNull()
                
                if (series == null || season == null) {
                    call.respond(HttpStatusCode.BadRequest, "Missing series or season parameter")
                    return@get
                }
                
                val result = generateRenameCommandsUseCase.generateRenameCommands(series, season)
                val commands = generateRenameCommandsUseCase.formatAsShellCommands(result)
                
                call.respondText(commands, contentType = ContentType.Text.Plain)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to generate rename commands: ${e.message}")
            }
        }

        get("/styles.css") {
            val cssContent = javaClass.getResourceAsStream("/styles.css")?.bufferedReader()?.readText()
                ?: Styles.CSS // Fallback to embedded CSS if external file not found
            call.respondText(cssContent, contentType = ContentType.Text.CSS)
        }
    }
}
