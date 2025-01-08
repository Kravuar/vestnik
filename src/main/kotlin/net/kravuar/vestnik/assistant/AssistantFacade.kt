package net.kravuar.vestnik.assistant

import net.kravuar.vestnik.articles.Article

interface AssistantFacade {
    fun notifyNewArticle(article: Article)
}