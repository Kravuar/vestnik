package net.kravuar.vestnik.scrapping

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class ScrapperConfig {

    @Bean
    fun scrapper(
        @Value("\${bright.data.token}") token: String,
        @Value("\${bright.data.zone}") zone: String,
    ): Scrapper = BrightDataScrapper(
        token,
        zone,
    )

    @Bean
    fun scrappingFacade(
        scrapper: Scrapper,
        scrapInfoRepository: ScrapInfoRepository
    ): ScrappingFacade = SimpleScrappingFacade(
        scrapper,
        scrapInfoRepository
    )
}