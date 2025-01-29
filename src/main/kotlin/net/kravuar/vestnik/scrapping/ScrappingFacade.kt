package net.kravuar.vestnik.scrapping

import net.kravuar.vestnik.commons.Page
import java.util.Optional

interface ScrappingFacade {
    data class ScrapInfoInput(
        var urlPattern: Optional<String> = Optional.empty(),
        var contentXPath: Optional<String> = Optional.empty(),
    )

    fun scrap(url: String): String
    fun getScrapInfos(page: Int): Page<ScrapInfo>
    fun addScrapInfo(input: ScrapInfoInput): ScrapInfo
    fun updateScrapInfo(id: Long, input: ScrapInfoInput): ScrapInfo
    fun deleteScrapInfo(id: Long): Boolean
}