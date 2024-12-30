package net.kravuar.vestnik.processor

import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.commons.Constants.Companion.DEFAULT_MODE

interface ArticleProcessor {
    fun processArticle(article: Article, mode: String = DEFAULT_MODE): String
}