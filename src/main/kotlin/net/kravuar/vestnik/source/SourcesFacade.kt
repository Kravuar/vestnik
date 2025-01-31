package net.kravuar.vestnik.source

import net.kravuar.vestnik.channels.Channel
import net.kravuar.vestnik.commons.Page
import java.time.Duration
import java.util.Optional

interface SourcesFacade {
    data class SourceInput(
        var name: Optional<String> = Optional.empty(),
        var url: Optional<String> = Optional.empty(),
        var scheduleDelay: Optional<Duration> = Optional.empty(),
        var channels: Optional<MutableSet<Channel>> = Optional.empty(),
        var suspended: Optional<Boolean> = Optional.empty(),
    )

    /**
     * Find all non deleted
     */
    fun getSources(): List<Source>
    /**
     * Find page of non deleted
     */
    fun getSources(page: Int): Page<Source>

    /**
     * Find all including deleted
     */
    fun getAllSources(): List<Source>
    /**
     * Find page including deleted
     */
    fun getAllSources(page: Int): Page<Source>

    /**
     * Find specific non deleted
     */
    fun getSourceByName(sourceName: String): Source

    fun addSource(source: SourceInput): Source
    fun updateSource(sourceName: String, input: SourceInput): Source
    fun deleteSource(sourceName: String): Boolean
}