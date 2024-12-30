package net.kravuar.vestnik.source

import com.apptasticsoftware.rssreader.Item
import com.apptasticsoftware.rssreader.RssReader
import com.apptasticsoftware.rssreader.util.ItemComparator
import jakarta.transaction.Transactional
import java.time.Duration
import java.time.ZonedDateTime

internal class SimpleSourcesFacade(
    private val sourcesRepository: SourcesRepository
): SourcesFacade {
    private val rssReader = RssReader()

    override fun fetchLatestNews(sourceName: String, delta: Duration): List<Item> {
        with(getSource(sourceName)) {
            if (suspended == true) {
                return emptyList()
            }
            return rssReader
                .read(this.url)
                .sorted(ItemComparator.oldestPublishedItemFirst())
                .filter { it.pubDateZonedDateTime.orElseThrow() < ZonedDateTime.now() - delta}
                .toList()
        }
    }

    override fun getSources(): List<Source> {
        return sourcesRepository.findAll()
    }

    override fun getSource(sourceName: String): Source {
        return sourcesRepository.findByName(sourceName)
    }

    @Transactional
    override fun addSource(source: SourcesFacade.SourceInput): Source {
        return sourcesRepository.save(inputToSource(source))
    }

    @Transactional
    override fun addSources(sources: List<SourcesFacade.SourceInput>): List<Source> {
        return sourcesRepository.saveAll(sources.map { inputToSource(it) })
    }

    @Transactional
    override fun updateSource(sourceName: String, input: SourcesFacade.SourceInput): Source {
        return sourcesRepository.findByName(sourceName).apply {
            input.url.ifPresent{ this.url = it }
            input.name.ifPresent{ this.name = it }
            input.scheduleDelay.ifPresent{ this.scheduleDelay = it }
            input.contentXPath.ifPresent{ this.contentXPath = it }
            input.thumbnailXPath.ifPresent{ this.thumbnailXPath = it }
            input.activeMode.ifPresent{ this.activeMode = it }
            input.suspended.ifPresent{ this.suspended = it }
        }
    }

    @Transactional
    override fun deleteSource(sourceName: String): Source {
        return sourcesRepository.deleteByName(sourceName)
    }

    @Transactional
    override fun deleteSources(sourceNames: List<String>): List<Source> {
        return sourceNames.map { deleteSource(it) }
    }

    companion object {
        fun inputToSource(sourceInput: SourcesFacade.SourceInput): Source {
            return Source(
                sourceInput.name.orElse(null),
                sourceInput.url.orElse(null),
                sourceInput.scheduleDelay.orElse(null),
                sourceInput.contentXPath.orElse(null),
            ).apply {
                sourceInput.thumbnailXPath.ifPresent{ this.thumbnailXPath = it }
                sourceInput.activeMode.ifPresent{ this.activeMode = it }
                sourceInput.suspended.ifPresent{ this.suspended = it }
            }
        }
    }
}