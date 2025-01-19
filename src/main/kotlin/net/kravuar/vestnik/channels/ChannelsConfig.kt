package net.kravuar.vestnik.channels

import dev.inmo.tgbotapi.bot.TelegramBot
import net.kravuar.vestnik.post.PostsFacade
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class ChannelsConfig {
    @Bean
    fun channelsFacade(
        channelRepository: ChannelRepository,
        postsFacade: PostsFacade,
        publishers: List<PostPublisher>
    ) = SimpleChannelsFacade(
        channelRepository,
        postsFacade,
        publishers.associateBy { it.platform() }
    )

    @Bean
    fun telegramPublisher(
        postsFacade: PostsFacade,
        telegramBot: TelegramBot
    ) = TelegramPublisher(
        postsFacade,
        telegramBot
    )
}