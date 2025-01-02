package net.kravuar.vestnik.assistant

import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.destination.ChannelPlatform
import net.kravuar.vestnik.events.EntityEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

@Configuration
internal class EventConfig(
    private val assistantFacade: AssistantFacade
) {
    @EventListener(condition = "event.state == 'CREATED'")
    fun notifyAboutNewArticle(event: EntityEvent<Article>) {
        assistantFacade.sendMessageToAdmins(
            """Получена новая новость:
            ${ADMIN_CHANNEL_PLATFORM.l1()}
            ${ADMIN_CHANNEL_PLATFORM.l2()}=${event.entity.url}
            ${ADMIN_CHANNEL_PLATFORM.l3()}
            ${ADMIN_CHANNEL_PLATFORM.b1()}
            ${event.entity.title}
            ${ADMIN_CHANNEL_PLATFORM.b2()}
            ${ADMIN_CHANNEL_PLATFORM.l4()}
            """.trimIndent()
        )
    }

    @EventListener(condition = "event.state == 'UPDATED' && event.entity.status == 'PROCESSED'")
    fun notifyAboutArticleProcessed(event: EntityEvent<Article>) {
        assistantFacade.sendMessageToAdmins(
            """Новость готова для публикации:
            ${ADMIN_CHANNEL_PLATFORM.l1()}
            ${ADMIN_CHANNEL_PLATFORM.l2()}=${event.entity.url}
            ${ADMIN_CHANNEL_PLATFORM.l3()}
            ${ADMIN_CHANNEL_PLATFORM.b1()}
            ${event.entity.title}
            ${ADMIN_CHANNEL_PLATFORM.b2()}
            ${ADMIN_CHANNEL_PLATFORM.l4()}
            
            ${ADMIN_CHANNEL_PLATFORM.i1()}
            Содержание:
            ${ADMIN_CHANNEL_PLATFORM.i2()}
            ${event.entity.content}
            """.trimIndent()
        )
    }

    companion object {
        private val ADMIN_CHANNEL_PLATFORM = ChannelPlatform.TELEGRAM
    }
}