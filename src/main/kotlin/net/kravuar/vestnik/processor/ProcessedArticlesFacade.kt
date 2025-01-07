package net.kravuar.vestnik.processor

import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.commons.Constants.Companion.DEFAULT_MODE
import java.util.Optional

interface ProcessedArticlesFacade {
    fun getModes(article: Article): List<String>
    fun processArticle(article: Article, mode: String = DEFAULT_MODE): ProcessedArticle
    fun reprocessArticle(processedArticleId: Long, remarks: String): ProcessedArticle
    fun getProcessedArticle(id: Long): ProcessedArticle
    fun getProcessedArticleOptional(id: Long): Optional<ProcessedArticle>
}