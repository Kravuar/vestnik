package net.kravuar.vestnik.source

import net.kravuar.vestnik.commons.EntityEvent
import net.kravuar.vestnik.commons.Page
import org.springframework.context.ApplicationEventPublisher

internal class NotifyingSourcesFacade(
    private val eventPublisher: ApplicationEventPublisher,
    private val sourcesFacade: SourcesFacade
) : SourcesFacade {

    override fun getSources(): List<Source> {
        return sourcesFacade.getSources()
    }

    override fun getSources(page: Int): Page<Source> {
        return sourcesFacade.getSources(page)
    }

    override fun getAllSources(): List<Source> {
        return sourcesFacade.getAllSources()
    }

    override fun getAllSources(page: Int): Page<Source> {
        return sourcesFacade.getAllSources(page)
    }

    override fun getSourceByName(sourceName: String): Source {
        return sourcesFacade.getSourceByName(sourceName)
    }

    override fun addSource(source: SourcesFacade.SourceInput): Source {
        return sourcesFacade.addSource(source)
            .also { eventPublisher.publishEvent(EntityEvent.created(this, it)) }
    }

    override fun updateSource(sourceName: String, input: SourcesFacade.SourceInput): Source {
        return sourcesFacade.updateSource(sourceName, input)
            .also { eventPublisher.publishEvent(EntityEvent.updated(this, it)) }
    }

    override fun deleteSource(sourceName: String): Boolean {
        with(getSourceByName(sourceName)) {
            return sourcesFacade.deleteSource(sourceName).also {
                eventPublisher.publishEvent(EntityEvent.deleted(this@NotifyingSourcesFacade, this))
            }
        }
    }
}