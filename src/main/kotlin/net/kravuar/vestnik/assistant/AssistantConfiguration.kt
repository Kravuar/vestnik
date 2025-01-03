package net.kravuar.vestnik.assistant

import net.kravuar.vestnik.articles.ArticlesFacade
import net.kravuar.vestnik.destination.ChannelsFacade
import net.kravuar.vestnik.post.PostsFacade
import net.kravuar.vestnik.processor.AIArticleProcessingFacade
import net.kravuar.vestnik.source.SourcesFacade
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class AssistantConfiguration {

    @Bean
    fun assistantFacade(
        @Value("\${telegram.bot.name}") name: String,
        @Value("\${telegram.bot.token}") token: String,
        @Value("\${telegram.admin.channel}") adminChannel: Long,
        @Value("\${admins}") admins: Set<Long>,
        @Value("\${owner}") owner: Long,
        sourcesFacade: SourcesFacade,
        channelsFacade: ChannelsFacade,
        articlesFacade: ArticlesFacade,
        postsFacade: PostsFacade,
        aiArticleProcessingFacade: AIArticleProcessingFacade
    ): AssistantFacade = VestnikTelegramAssistantFacade(
        name,
        token,
        adminChannel,
        admins,
        owner,
        sourcesFacade,
        channelsFacade,
        articlesFacade,
        postsFacade,
        aiArticleProcessingFacade
    )
}