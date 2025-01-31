package net.kravuar.vestnik.articles

import net.kravuar.vestnik.commons.EntityEvent
import net.kravuar.vestnik.commons.Page
import net.kravuar.vestnik.source.Source
import org.springframework.context.ApplicationEventPublisher
import java.time.Duration
import java.util.Optional

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

    override fun getArticles(): List<Article> {
        return articlesFacade.getArticles()
    }

    override fun getArticles(page: Int): Page<Article> {
        return articlesFacade.getArticles(page)
    }

    override fun getLatestArticle(source: Source): Optional<Article> {
        return articlesFacade.getLatestArticle(source)
    }

    override fun getArticle(id: Long): Article {
        return articlesFacade.getArticle(id)
    }
}