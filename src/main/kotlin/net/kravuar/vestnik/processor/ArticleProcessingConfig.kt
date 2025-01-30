package net.kravuar.vestnik.processor

import net.kravuar.vestnik.processor.nodes.AIArticleProcessingNodesFacade
import net.kravuar.vestnik.processor.nodes.ChainedAiArticleProcessingNodesRepository
import net.kravuar.vestnik.processor.nodes.SimpleAIArticleProcessingNodesFacade
import net.kravuar.vestnik.scrapping.ScrappingFacade
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
        chainedAiArticleProcessingNodesRepository: ChainedAiArticleProcessingNodesRepository
    ): AIArticleProcessingNodesFacade = SimpleAIArticleProcessingNodesFacade(
        chainedAiArticleProcessingNodesRepository
    )

    @Bean
    fun articleProcessor(
        chatModel: ChatModel,
        aiArticleProcessingNodesFacade: AIArticleProcessingNodesFacade,
        processedArticleRepository: ProcessedArticleRepository,
        scrappingFacade: ScrappingFacade
    ): ProcessedArticlesFacade = AIProcessingArticlesFacade(
        chatModel,
        aiArticleProcessingNodesFacade,
        processedArticleRepository,
        scrappingFacade
    )
}