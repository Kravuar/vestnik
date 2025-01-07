package net.kravuar.vestnik.assistant

import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.commons.EntityEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

@Configuration
internal class EventConfig(
    private val assistantFacade: TelegramAssistantFacade
) {

    @EventListener(condition = "event.state == 'CREATED'")
    fun notifyAboutNewArticle(event: EntityEvent<Article>) {
        assistantFacade.notifyNewArticle(event.entity)
    }
}