package net.kravuar.vestnik.articles

import net.kravuar.vestnik.commons.EntityEvent
import net.kravuar.vestnik.source.Source
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

@Configuration
internal class ArticlePollerEventConfig(
    private val articleScheduler: ArticleScheduler
) {

    @EventListener(condition = "#event.state == 'CREATED' && #event.entity.suspended == false")
    fun startPolling(event: EntityEvent<Source>) {
        articleScheduler.startPolling(event.entity)
    }

    @EventListener(condition = "#event.state == 'DELETED'")
    fun stopPolling(event: EntityEvent<Source>) {
        articleScheduler.stopPolling(event.entity.name)
    }

    @EventListener(condition = "#event.state == 'UPDATED'")
    fun switchPolling(event: EntityEvent<Source>) {
        if (event.entity.suspended == true) {
            articleScheduler.stopPolling(event.entity.name)
        } else {
            articleScheduler.startPolling(event.entity)
        }
    }
}