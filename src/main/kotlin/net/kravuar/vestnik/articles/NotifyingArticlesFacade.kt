package net.kravuar.vestnik.articles

import net.kravuar.vestnik.events.EntityEvent
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration

internal class NotifyingArticlesFacade(
    private val eventPublisher: ApplicationEventPublisher,
    private val articlesFacade: ArticlesFacade,
) : ArticlesFacade {
    override fun fetchAndStoreLatestNews(delta: Duration): List<Article> {
        return articlesFacade.fetchAndStoreLatestNews(delta).onEach {
            eventPublisher.publishEvent(EntityEvent.created(this, it))
        }
    }

    override fun fetchAndStoreLatestNews(sourceName: String, delta: Duration): List<Article> {
        return articlesFacade.fetchAndStoreLatestNews(sourceName, delta).onEach {
            eventPublisher.publishEvent(EntityEvent.created(this, it))
        }
    }

    override fun getArticles(status: Article.Status?): List<Article> {
        return articlesFacade.getArticles(status)
    }

    override fun getArticles(status: Article.Status?, page: Int): Pair<Long, List<Article>> {
        return articlesFacade.getArticles(status, page)
    }

    override fun getArticle(id: Long): Article {
        return articlesFacade.getArticle(id)
    }

    override fun updateArticle(id: Long, input: ArticlesFacade.ArticleInput): Article {
        return articlesFacade.updateArticle(id, input).also {
            eventPublisher.publishEvent(EntityEvent.updated(this, it))
        }
    }
}