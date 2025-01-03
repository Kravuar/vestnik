package net.kravuar.vestnik.source

import com.apptasticsoftware.rssreader.Item
import net.kravuar.vestnik.destination.Channel
import java.time.Duration
import java.util.Optional

interface SourcesFacade {
    data class SourceInput(
        var name: Optional<String> = Optional.empty(),
        var url: Optional<String> = Optional.empty(),
        var scheduleDelay: Optional<Duration> = Optional.empty(),
        var contentXPath: Optional<String> = Optional.empty(),
        var channels: Optional<MutableSet<Channel>> = Optional.empty(),
        var suspended: Optional<Boolean> = Optional.empty(),
    )
    fun fetchLatestNews(sourceName: String, delta: Duration): List<Item>
    fun getSources(): List<Source>
    fun getSource(sourceName: String): Source
    fun addSource(source: SourceInput): Source
    fun addSources(sources: List<SourceInput>): List<Source>
    fun updateSource(sourceName: String, input: SourceInput): Source
    fun deleteSource(sourceName: String): Source
    fun deleteSources(sourceNames: List<String>): List<Source>
}