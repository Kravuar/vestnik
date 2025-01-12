package net.kravuar.vestnik.processor

import net.kravuar.vestnik.processor.nodes.AIArticleProcessingNodesFacade
import net.kravuar.vestnik.processor.nodes.AIArticleProcessingNodesRepository
import net.kravuar.vestnik.processor.nodes.SimpleAIArticleProcessingNodesFacade
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
        aiArticleProcessingNodesRepository: AIArticleProcessingNodesRepository
    ): AIArticleProcessingNodesFacade = SimpleAIArticleProcessingNodesFacade(
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