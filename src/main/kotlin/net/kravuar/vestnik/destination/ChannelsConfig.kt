package net.kravuar.vestnik.destination

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class ChannelsConfig {
    @Bean
    fun channelsFacade(
        channelRepository: ChannelRepository,
    ) = SimpleChannelsFacade(
        channelRepository
    )
}