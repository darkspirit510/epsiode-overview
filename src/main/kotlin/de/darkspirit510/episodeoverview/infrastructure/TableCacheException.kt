package de.darkspirit510.episodeoverview.infrastructure

class TableCacheException(message: String) : Exception(message) {
    companion object {
        fun corruptedCacheFile(cacheFilePath: String): TableCacheException {
            return TableCacheException(
                "Cache file is corrupted: $cacheFilePath. " +
                "Please <a href='/refresh'>refresh the data</a> to regenerate the cache."
            )
        }

        fun cacheFileNotFound(cacheFilePath: String): TableCacheException {
            return TableCacheException(
                "Cache file not found: $cacheFilePath. " +
                "Please <a href='/refresh'>refresh the data</a> to generate the cache."
            )
        }

        fun serializationError(cacheFilePath: String, error: String): TableCacheException {
            return TableCacheException(
                "Failed to serialize/deserialize cache file $cacheFilePath: $error. " +
                "Please <a href='/refresh'>refresh the data</a> to regenerate the cache."
            )
        }
    }
}