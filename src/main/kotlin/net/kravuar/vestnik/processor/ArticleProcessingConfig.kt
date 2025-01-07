package net.kravuar.vestnik.processor

import jakarta.persistence.EntityManager
import net.kravuar.vestnik.processor.ai.AIArticleProcessingNodesFacade
import net.kravuar.vestnik.processor.ai.AIArticleProcessingNodesRepository
import net.kravuar.vestnik.processor.ai.AIProcessingArticlesFacade
import net.kravuar.vestnik.processor.ai.SimpleAIArticleProcessingNodesFacade
import net.kravuar.vestnik.scrapping.Scrapper
import net.kravuar.vestnik.source.SourcesFacade
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@Import
internal class ArticleProcessingConfig {
    @Bean
    fun aiArticleProcessingFacade(
        sourcesFacade: SourcesFacade,
        entityManager: EntityManager,
        aiArticleProcessingNodesRepository: AIArticleProcessingNodesRepository
    ): AIArticleProcessingNodesFacade = SimpleAIArticleProcessingNodesFacade(
        entityManager,
        aiArticleProcessingNodesRepository
    )

    @Bean
    fun articleProcessor(
        chatModel: ChatModel,
        aiArticleProcessingNodesFacade: AIArticleProcessingNodesFacade,
        processedArticleRepository: ProcessedArticleRepository,
        scrapper: Scrapper
    ): ProcessedArticlesFacade = AIProcessingArticlesFacade(
        chatModel,
        aiArticleProcessingNodesFacade,
        processedArticleRepository,
        scrapper
    )
}