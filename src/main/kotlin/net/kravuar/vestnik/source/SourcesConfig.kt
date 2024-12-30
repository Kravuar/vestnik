package net.kravuar.vestnik.source

import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
internal class SourcesConfig {
    @Bean
    fun sourcesFacade(
        sourcesRepository: SourcesRepository,
    ): SourcesFacade = SimpleSourcesFacade(sourcesRepository)

    @Bean
    @Primary
    fun notifyingRSSFacade(
        eventPublisher: ApplicationEventPublisher,
        sourcesFacade: SourcesFacade
    ): NotifyingSourcesFacade = NotifyingSourcesFacade(eventPublisher, sourcesFacade)
}