package net.kravuar.vestnik.scrapping

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
internal class ScrapperConfig {
    @Bean
    fun scrapper(
        @Value("\${bright.data.token}") token: String,
        @Value("\${bright.data.zone}") zone: String,
        webClient: WebClient,
    ): Scrapper = BrightDataScrapper(
        token,
        zone,
        webClient
    )
}