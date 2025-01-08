package net.kravuar.vestnik.processor.nodes

import net.kravuar.vestnik.commons.Constants.Companion.DEFAULT_MODE
import net.kravuar.vestnik.commons.EntityEvent
import net.kravuar.vestnik.source.Source
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

@Configuration
internal class EventConfig(
    private val aiArticleProcessingNodesFacade: AIArticleProcessingNodesFacade,
) {

    @EventListener(condition = "#event.state == 'CREATED'")
    fun createInitialSequence(event: EntityEvent<Source>) {
        aiArticleProcessingNodesFacade.createSequence(
            event.entity,
            DEFAULT_MODE,
        )
    }
}