package net.kravuar.vestnik.channels

import dev.inmo.tgbotapi.bot.TelegramBot
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class ChannelsConfig {
    @Bean
    fun channelsFacade(
        channelRepository: ChannelRepository,
        publishers: List<ChannelPublisher>
    ) = SimpleChannelsFacade(
        channelRepository,
        publishers.associateBy { it.platform() }
    )

    @Bean
    fun telegramPublisher(
        telegramBot: TelegramBot
    ) = TelegramPublisher(telegramBot)

    @Bean
    fun vkPublisher() = VKPublisher()
}