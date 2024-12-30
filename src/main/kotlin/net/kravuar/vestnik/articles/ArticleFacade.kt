package net.kravuar.vestnik.articles

import net.kravuar.vestnik.source.Source
import java.time.Duration
import java.util.Optional

interface ArticleFacade {
    data class ArticleInput(
        var source: Optional<Source>,
        val title: Optional<String>,
        val content: Optional<String>,
        var status: Optional<Status>,
    )

    fun fetchAndStoreLatestNews(delta: Duration): List<Article>
    fun fetchAndStoreLatestNews(sourceName: String, delta: Duration): List<Article>
    fun getArticle(id: Long): Article
    fun updateArticle(id: Long, input: ArticleInput): Article
}