package net.kravuar.vestnik.articles

import net.kravuar.vestnik.events.EntityEvent
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration

internal class NotifyingArticleFacade(
    private val eventPublisher: ApplicationEventPublisher,
    private val articleFacade: ArticleFacade,
) : ArticleFacade {
    override fun fetchAndStoreLatestNews(delta: Duration): List<Article> {
        return articleFacade.fetchAndStoreLatestNews(delta).onEach {
            eventPublisher.publishEvent(EntityEvent.created(this, it))
        }
    }

    override fun fetchAndStoreLatestNews(sourceName: String, delta: Duration): List<Article> {
        return articleFacade.fetchAndStoreLatestNews(sourceName, delta).onEach {
            eventPublisher.publishEvent(EntityEvent.created(this, it))
        }
    }

    override fun getArticle(id: Long): Article {
        return articleFacade.getArticle(id)
    }

    override fun updateArticle(id: Long, input: ArticleFacade.ArticleInput): Article {
        return articleFacade.updateArticle(id, input).also {
            eventPublisher.publishEvent(EntityEvent.updated(this, it))
        }
    }
}