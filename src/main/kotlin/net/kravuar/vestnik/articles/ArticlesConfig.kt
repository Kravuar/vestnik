package net.kravuar.vestnik.articles

import net.kravuar.vestnik.source.SourcesFacade
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.TaskScheduler


@Configuration
internal class ArticlesConfig {

    @Bean(destroyMethod = "stopPollingAll", initMethod = "startPollingAll")
    fun articleScheduler(
        scheduler: TaskScheduler,
        articlesFacade: ArticlesFacade,
        sourcesFacade: SourcesFacade,
    ): ArticleScheduler = ArticleScheduler(
        scheduler,
        articlesFacade,
        sourcesFacade
    )

    @Bean
    fun articleFacade(
        articlesRepository: ArticlesRepository,
        sourcesFacade: SourcesFacade,
    ): ArticlesFacade = SimpleArticlesFacade(
        articlesRepository,
        sourcesFacade
    )

    @Bean
    @Primary
    fun notifyingArticleFacade(
        eventPublisher: ApplicationEventPublisher,
        articlesFacade: ArticlesFacade,
    ): ArticlesFacade = NotifyingArticlesFacade(
        eventPublisher,
        articlesFacade
    )
}