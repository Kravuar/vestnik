package net.kravuar.vestnik.processor

import jakarta.persistence.EntityManager
import net.kravuar.vestnik.source.SourcesFacade
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import
internal class ArticleProcessingConfig {
    @Bean
    fun aiArticleProcessingNodesFacade(
        sourcesFacade: SourcesFacade,
        entityManager: EntityManager,
        aiArticleProcessingNodesRepository: AIArticleProcessingNodesRepository
    ) = AIArticleProcessingNodesFacade(
        sourcesFacade,
        entityManager,
        aiArticleProcessingNodesRepository
    )

    @Bean
    fun articleProcessor(
        chatModel: ChatModel,
        aiArticleProcessingNodesFacade: AIArticleProcessingNodesFacade
    ): ArticleProcessor = AIArticleProcessor(
        chatModel,
        aiArticleProcessingNodesFacade,
    )
}