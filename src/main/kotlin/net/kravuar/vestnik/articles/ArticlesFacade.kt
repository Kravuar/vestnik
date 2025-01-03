package net.kravuar.vestnik.articles

import net.kravuar.vestnik.source.Source
import java.time.Duration
import java.util.Optional

interface ArticlesFacade {
    data class ArticleInput(
        var source: Optional<Source> = Optional.empty(),
        val title: Optional<String> = Optional.empty(),
        val content: Optional<String> = Optional.empty(),
        var status: Optional<Article.Status> = Optional.empty(),
    )

    fun fetchAndStoreLatestNews(delta: Duration): List<Article>
    fun fetchAndStoreLatestNews(sourceName: String, delta: Duration): List<Article>
    fun getArticles(status: Article.Status? = null): List<Article>
    fun getArticles(status: Article.Status? = null, page: Int = 1): Pair<Long, List<Article>>
    fun getArticle(id: Long): Article
    fun updateArticle(id: Long, input: ArticleInput): Article
}