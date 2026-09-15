package de.darkspirit510.episodeoverview.infrastructure

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.darkspirit510.episodeoverview.application.GenerateTableUseCase
import java.io.File

class TableCache(
    private val cacheBaseDir: String = "cache"
) : TableCacheInterface {
    
    private val mapper = jacksonObjectMapper().apply {
        enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
    }
    
    private val cacheFile: File by lazy {
        val customCacheDir = System.getProperty("episodeoverview.cache.dir")
        val baseDir = customCacheDir ?: cacheBaseDir
        File(baseDir, "table_data.json")
    }
    
    @Volatile
    private var cachedTable: List<GenerateTableUseCase.TableRow>? = null
    
    override fun save(tableRows: List<GenerateTableUseCase.TableRow>) {
        synchronized(this) {
            try {
                mapper.writeValue(cacheFile, tableRows)
                cachedTable = tableRows
            } catch (e: Exception) {
                throw TableCacheException.serializationError(cacheFile.absolutePath, e.message ?: "Unknown error")
            }
        }
    }
    
    override fun load(): List<GenerateTableUseCase.TableRow>? {
        // First try memory cache
        cachedTable?.let { return it }
        
        // Then try filesystem cache
        return synchronized(this) {
            // Double-check memory cache after acquiring lock
            cachedTable?.let { return it }
            
            if (!cacheFile.exists()) {
                return@synchronized null
            }
            
            try {
                val tableRows: List<GenerateTableUseCase.TableRow> = mapper.readValue(
                    cacheFile,
                    object : TypeReference<List<GenerateTableUseCase.TableRow>>() {}
                )
                cachedTable = tableRows
                return tableRows
            } catch (e: Exception) {
                throw TableCacheException.corruptedCacheFile(cacheFile.absolutePath)
            }
        }
    }
    
    fun clear() {
        synchronized(this) {
            cachedTable = null
            cacheFile.delete()
        }
    }
}