package net.kravuar.vestnik.source

import com.apptasticsoftware.rssreader.Item
import net.kravuar.vestnik.commons.EntityEvent
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration

internal class NotifyingSourcesFacade(
    private val eventPublisher: ApplicationEventPublisher,
    private val sourcesFacade: SourcesFacade
): SourcesFacade {

    override fun fetchLatestNews(sourceName: String, delta: Duration): List<Item> {
        return sourcesFacade.fetchLatestNews(sourceName, delta)
    }

    override fun getSources(): List<Source> {
        return sourcesFacade.getSources()
    }

    override fun getSource(sourceName: String): Source {
        return sourcesFacade.getSource(sourceName)
    }

    override fun addSource(source: SourcesFacade.SourceInput): Source {
        return sourcesFacade.addSource(source)
            .also { eventPublisher.publishEvent(EntityEvent.created(this, it)) }
    }

    override fun addSources(sources: List<SourcesFacade.SourceInput>): List<Source> {
        return sourcesFacade.addSources(sources)
            .onEach { eventPublisher.publishEvent(EntityEvent.created(this, it)) }
    }

    override fun updateSource(sourceName: String, input: SourcesFacade.SourceInput): Source {
        return sourcesFacade.updateSource(sourceName, input)
            .also { eventPublisher.publishEvent(EntityEvent.updated(this, it)) }
    }

    override fun deleteSource(sourceName: String): Source {
        return sourcesFacade.deleteSource(sourceName)
            .also { eventPublisher.publishEvent(EntityEvent.deleted(this, it)) }
    }

    override fun deleteSources(sourceNames: List<String>): List<Source> {
        return sourcesFacade.deleteSources(sourceNames)
            .onEach { eventPublisher.publishEvent(EntityEvent.deleted(this, it)) }
    }
}