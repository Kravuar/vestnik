package net.kravuar.vestnik.articles

import net.kravuar.vestnik.source.SourcesFacade
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.TaskScheduler

@Configuration
internal class ArticlesConfig {
    @Bean
    fun articleScheduler(
        scheduler: TaskScheduler,
        articleFacade: ArticleFacade,
    ): ArticleScheduler = ArticleScheduler(
        scheduler,
        articleFacade,
    )

    @Bean
    fun articleFacade(
        articlesRepository: ArticlesRepository,
        sourcesFacade: SourcesFacade,
    ): ArticleFacade = SimpleArticleFacade(
        articlesRepository,
        sourcesFacade
    )

    @Bean
    @Primary
    fun notifyingArticleFacade(
        eventPublisher: ApplicationEventPublisher,
        articleFacade: ArticleFacade,
    ): ArticleFacade = NotifyingArticleFacade(
        eventPublisher,
        articleFacade
    )
}