package net.kravuar.vestnik.articles

import net.kravuar.vestnik.commons.Page
import net.kravuar.vestnik.source.Source
import java.time.Duration
import java.util.Optional

interface ArticlesFacade {
    data class ArticleInput(
        var source: Optional<Source> = Optional.empty(),
        val title: Optional<String> = Optional.empty(),
        val description: Optional<String> = Optional.empty(),
        val url: Optional<String> = Optional.empty(),
    )

    fun getArticles(): List<Article>
    fun getArticles(page: Int): Page<Article>
    fun getLatestArticle(source: Source): Optional<Article>

    fun getArticle(id: Long): Article

    fun fetchAndStoreLatestNews(delta: Duration): List<Article>
    fun fetchAndStoreLatestNews(sourceName: String, delta: Duration): List<Article>
}