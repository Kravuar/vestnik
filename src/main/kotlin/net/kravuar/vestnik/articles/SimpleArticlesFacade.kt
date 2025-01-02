package net.kravuar.vestnik.articles

import com.apptasticsoftware.rssreader.Item
import jakarta.transaction.Transactional
import net.kravuar.vestnik.source.SourcesFacade
import net.kravuar.vestnik.source.Source
import java.time.Duration

internal class SimpleArticlesFacade(
    private val articlesRepository: ArticlesRepository,
    private val sourcesFacade: SourcesFacade,
) : ArticlesFacade {

    @Transactional
    override fun fetchAndStoreLatestNews(delta: Duration): List<Article> {
        return sourcesFacade.getSources().flatMap {
            fetchAndStoreLatestNews(it.name, delta)
        }
    }

    @Transactional
    override fun fetchAndStoreLatestNews(sourceName: String, delta: Duration): List<Article> {
        return sourcesFacade.fetchLatestNews(sourceName, delta).map {
            itemToArticle(sourcesFacade.getSource(sourceName), it)
        }.also { articlesRepository.saveAll(it) }
    }

    override fun getArticle(id: Long): Article {
        return articlesRepository.findById(id).orElseThrow { IllegalArgumentException("Новость с id=$id не найдена") }
    }

    @Transactional
    override fun updateArticle(id: Long, input: ArticlesFacade.ArticleInput): Article {
        return getArticle(id).apply {
            input.content.ifPresent { content = it }
            input.status.ifPresent { status = it }
        }
    }

    companion object {
        fun itemToArticle(source: Source, item: Item): Article {
            return Article(
                source,
                item.title.orElseThrow(),
                item.content.orElseThrow(),
                item.link.orElseThrow(),
                Status.NEW
            )
        }
    }
}