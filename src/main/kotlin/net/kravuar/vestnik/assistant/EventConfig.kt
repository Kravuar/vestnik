package net.kravuar.vestnik.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.commons.EntityEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener

@Configuration
internal class EventConfig(
    private val assistantFacade: TelegramAssistantFacade
) {

    @EventListener(condition = "event.state == 'CREATED'")
    fun notifyAboutNewArticle(event: EntityEvent<Article>) {
        assistantFacade.notifyNewArticle(event.entity)
    }

    @EventListener(ContextRefreshedEvent::class)
    fun startAssistant(event: ContextRefreshedEvent) {
        CoroutineScope(Dispatchers.Default + SupervisorJob())
            .launch { assistantFacade.start() }
    }
}