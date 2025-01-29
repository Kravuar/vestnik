package net.kravuar.vestnik.processor

import jakarta.transaction.Transactional
import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.commons.Page
import net.kravuar.vestnik.processor.nodes.AIArticleProcessingNodesFacade
import net.kravuar.vestnik.scrapping.Scrapper
import org.apache.logging.log4j.LogManager
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.ChatOptionsBuilder
import org.springframework.ai.chat.prompt.Prompt
import java.util.Optional

internal open class AIProcessingArticlesFacade(
    private val chatModel: ChatModel,
    private val aiArticleProcessingNodesFacade: AIArticleProcessingNodesFacade,
    private val processedArticleRepository: ProcessedArticleRepository,
    private val scrapper: Scrapper,
) : ProcessedArticlesFacade {
    @Transactional
    override fun processArticle(article: Article, mode: String): ProcessedArticle {
        return aiArticleProcessingNodesFacade.getChain(mode).let { chain ->
            LOG.info("Обработка режимом $mode статьи $article")
            val content = article.source.contentXPath?.let {
                scrapper.scrap(article.url, it)
            } ?: article.description ?: throw IllegalStateException("Не задан XPath для сбора контента, а также отсутствует контент в самой новости")
            val processedContent = chain
                .fold(content) { currentContent, node ->
                    chatModel.call(
                        Prompt(
                            listOf<Message>(
                                SystemMessage(node.prompt),
                                UserMessage(currentContent)
                            ),
                            ChatOptionsBuilder.builder()
                                .withModel(node.model)
                                .withTemperature(node.temperature)
                                .build()
                        )
                    ).result.output.content
                }
            processedArticleRepository.save(ProcessedArticle(article, processedContent, mode)).also {
                LOG.info("Завершена обработка режимом $mode статьи $article")
            }
        }
    }

    override fun getModes(page: Int): Page<String> {
        return aiArticleProcessingNodesFacade.getModes(page)
    }

    @Transactional
    override fun reprocessArticle(processedArticleId: Long, remarks: String): ProcessedArticle {
        return getProcessedArticle(processedArticleId)
            .run {
                LOG.info("Повторная обработка статьи $this, исходная статья $article")
                val reprocessNode = aiArticleProcessingNodesFacade.getReprocessNode()
                val reprocessedContent = chatModel.call(
                    Prompt(
                        listOf<Message>(
                            SystemMessage(reprocessNode.prompt),
                            UserMessage(content),
                            UserMessage(remarks)
                        ),
                        ChatOptionsBuilder.builder()
                            .withModel(reprocessNode.model)
                            .withTemperature(reprocessNode.temperature)
                            .build()
                    )
                ).result.output.content
                this.content = reprocessedContent
                processedArticleRepository.save(this).also {
                    LOG.info("Повторная обработка статьи $this завершена")
                }
            }
    }

    override fun getProcessedArticle(id: Long): ProcessedArticle {
        return processedArticleRepository.findById(id)
            .orElseThrow { throw RuntimeException("Обработанная статья с id=$id не найдена") }
    }

    override fun getProcessedArticleOptional(id: Long): Optional<ProcessedArticle> {
        return processedArticleRepository.findById(id)
    }

    companion object {
        private val LOG = LogManager.getLogger(AIProcessingArticlesFacade::class.java)
    }
}


