package net.kravuar.vestnik.articles

import org.springframework.context.annotation.Configuration

@Configuration
internal class FunctionsConfig(
    private val articlesFacade: ArticlesFacade
) {
}