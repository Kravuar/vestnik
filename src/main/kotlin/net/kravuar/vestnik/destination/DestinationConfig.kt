package net.kravuar.vestnik.destination

import net.kravuar.vestnik.source.SourcesFacade
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class DestinationConfig {
    @Bean
    fun destinationFacade(
        destinationRepository: DestinationRepository,
        sourcesFacade: SourcesFacade,
    ) = SimpleChannelsFacade(
        destinationRepository,
        sourcesFacade
    )
}