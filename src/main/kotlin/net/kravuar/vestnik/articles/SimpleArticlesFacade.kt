package net.kravuar.vestnik.articles

import com.apptasticsoftware.rssreader.Item
import com.apptasticsoftware.rssreader.RssReader
import com.apptasticsoftware.rssreader.util.ItemComparator
import jakarta.transaction.Transactional
import net.kravuar.vestnik.commons.Page
import net.kravuar.vestnik.source.Source
import net.kravuar.vestnik.source.SourcesFacade
import org.apache.logging.log4j.LogManager
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.Duration
import java.util.Optional

internal open class SimpleArticlesFacade(
    private val articlesRepository: ArticlesRepository,
    private val sourcesFacade: SourcesFacade,
) : ArticlesFacade {

    private fun fetchNews(sourceName: String): List<Article> {
        with(sourcesFacade.getSourceByName(sourceName)) {
            if (suspended == true) {
                LOG.info("Источник $sourceName приостановлен, fetch не будет произведён")
                return emptyList()
            }
            return RssReader()
                .read(this.url)
                .sorted(ItemComparator.oldestPublishedItemFirst())
                .map {
                    itemToArticle(this, it)
                }.toList()
        }
    }

    @Transactional
    override fun fetchAndStoreLatestNews(delta: Duration): List<Article> {
        return sourcesFacade.getSources().flatMap {
            fetchAndStoreLatestNews(it.name, delta)
        }
    }

    @Transactional
    override fun fetchAndStoreLatestNews(sourceName: String, delta: Duration): List<Article> {
        return fetchNews(sourceName).mapNotNull { article ->
            if (!articlesRepository.existsBySourceGuid(article.sourceGuid)) {
                articlesRepository.save(article).also {
                    LOG.info("Сохранена новая статья: $article")
                }
            } else {
                LOG.info("Статья уже существовала: $article")
                null
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

    override fun getLatestArticle(source: Source): Optional<Article> {
        return articlesRepository.findTopBySourceOrderByCreatedAtDesc(source)
    }

    override fun getArticle(id: Long): Article {
        return articlesRepository.findById(id).orElseThrow { IllegalArgumentException("Новость с id=$id не найдена") }
    }

    companion object {
        private val LOG = LogManager.getLogger(SimpleArticlesFacade::class.java)

        fun itemToArticle(source: Source, item: Item): Article {
            return Article(
                source = source,
                title = item.title.orElseThrow(),
                sourceGuid = item.guid.orElseThrow(),
                description = item.description.orElse(null),
                url = item.link.orElseThrow(),
                createdAt = item.pubDateZonedDateTime.orElseThrow().toOffsetDateTime()
            )
        }
    }
}