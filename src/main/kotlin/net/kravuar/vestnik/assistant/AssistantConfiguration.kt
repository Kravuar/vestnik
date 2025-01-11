package net.kravuar.vestnik.assistant

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.telegramBot
import net.kravuar.vestnik.articles.ArticlesFacade
import net.kravuar.vestnik.channels.ChannelsFacade
import net.kravuar.vestnik.processor.ProcessedArticlesFacade
import net.kravuar.vestnik.processor.nodes.AIArticleProcessingNodesFacade
import net.kravuar.vestnik.source.SourcesFacade
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class AssistantConfiguration {

    @Bean
    fun telegramApiBot(
        @Value("\${telegram.bot.token}") token: String,
    ): TelegramBot = telegramBot(token)

    @Bean
    fun assistantFacade(
        @Value("\${telegram.admin.channel}") adminChannel: Long,
        @Value("\${admins}") admins: Set<Long>,
        @Value("\${owner}") owner: Long,
        telegramBot: TelegramBot,
        sourcesFacade: SourcesFacade,
        channelsFacade: ChannelsFacade,
        articlesFacade: ArticlesFacade,
        processedArticlesFacade: ProcessedArticlesFacade,
        aiArticleProcessingNodesFacade: AIArticleProcessingNodesFacade
    ): TelegramAssistantFacade = TelegramAssistantFacade(
        adminChannel,
        admins,
        owner,
        telegramBot,
        sourcesFacade,
        channelsFacade,
        articlesFacade,
        processedArticlesFacade,
        aiArticleProcessingNodesFacade
    )
}