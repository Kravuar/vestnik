package net.kravuar.vestnik.assistant

import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.bot.settings.limiters.CommonLimiter
import dev.inmo.tgbotapi.utils.TelegramAPIUrlsKeeper
import korlibs.time.millisecondsLong
import net.kravuar.vestnik.articles.ArticlesFacade
import net.kravuar.vestnik.channels.ChannelsFacade
import net.kravuar.vestnik.processor.ProcessedArticlesFacade
import net.kravuar.vestnik.processor.nodes.AIArticleProcessingNodesFacade
import net.kravuar.vestnik.scrapping.ScrappingFacade
import net.kravuar.vestnik.source.SourcesFacade
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.Duration.Companion.seconds

@Configuration
internal class AssistantConfiguration {

    @Bean
    fun telegramApiBot(
        @Value("\${telegram.bot.token}") token: String,
    ): TelegramBot = telegramBot(TelegramAPIUrlsKeeper(token)) {
        requestsLimiter = CommonLimiter(regenTime = 20.seconds.millisecondsLong)
    }

    @Bean
    fun assistantFacade(
        @Value("\${telegram.admin.channel}") adminChannel: Long,
        @Value("\${admins}") admins: Set<Long>,
        @Value("\${owner}") owner: Long,
        telegramBot: TelegramBot,
        config: AssistantProperties,
        sourcesFacade: SourcesFacade,
        channelsFacade: ChannelsFacade,
        articlesFacade: ArticlesFacade,
        scrappingFacade: ScrappingFacade,
        processedArticlesFacade: ProcessedArticlesFacade,
        aiArticleProcessingNodesFacade: AIArticleProcessingNodesFacade
    ): TelegramAssistantFacade = TelegramAssistantFacade(
        adminChannel,
        admins,
        owner,
        telegramBot,
        config,
        sourcesFacade,
        channelsFacade,
        articlesFacade,
        scrappingFacade,
        processedArticlesFacade,
        aiArticleProcessingNodesFacade
    )
}