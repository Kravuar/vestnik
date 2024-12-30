package net.kravuar.vestnik.processor

import net.kravuar.vestnik.articles.Article
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.ChatOptionsBuilder
import org.springframework.ai.chat.prompt.Prompt

internal class AIArticleProcessor(
    private val chatModel: ChatModel,
    private val aiArticleProcessingNodesFacade: AIArticleProcessingNodesFacade,
): ArticleProcessor {
    override fun processArticle(article: Article, mode: String): String {
        return aiArticleProcessingNodesFacade.findRoot(article.source.name, mode).let { rootNode ->
            generateSequence(rootNode) { it.child }
                .fold(article.content) { currentContent, node ->
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
        }
    }
}


