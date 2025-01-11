package net.kravuar.vestnik.articles

import com.apptasticsoftware.rssreader.Item
import jakarta.transaction.Transactional
import net.kravuar.vestnik.commons.Page
import net.kravuar.vestnik.source.Source
import net.kravuar.vestnik.source.SourcesFacade
import org.apache.logging.log4j.LogManager
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.Duration

internal open class SimpleArticlesFacade(
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
            itemToArticle(sourcesFacade.getSourceByName(sourceName), it)
        }.also {
            articlesRepository.saveAll(it).also { savedArticles ->
                if (savedArticles.isNotEmpty()) {
                    LOG.info("Сохранены новые статьи: $savedArticles")
                }
            }
        }
    }

    override fun getArticles(): List<Article> {
        return articlesRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
    }

    override fun getArticles(page: Int): Page<Article> {
        return articlesRepository.findAll(
            PageRequest.of(
                page - 1,
                Page.DEFAULT_PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "createdAt")
            )
        ).let {
            Page(
                it.totalPages,
                it.content
            )
        }
    }

    override fun getArticle(id: Long): Article {
        return articlesRepository.findById(id).orElseThrow { IllegalArgumentException("Новость с id=$id не найдена") }
    }

    @Transactional
    override fun updateArticle(id: Long, input: ArticlesFacade.ArticleInput): Article {
        return getArticle(id).apply {
            input.title.ifPresent { title = it }
            input.description.ifPresent { description = it }
            input.url.ifPresent { url = it }
        }.also {
            LOG.info("Обновлена статья: $it")
        }
    }

    companion object {
        private val LOG = LogManager.getLogger(SimpleArticlesFacade::class.java)

        fun itemToArticle(source: Source, item: Item): Article {
            return Article(
                source,
                item.title.orElseThrow(),
                item.content.orElseThrow(),
                item.link.orElseThrow(),
            )
        }
    }
}