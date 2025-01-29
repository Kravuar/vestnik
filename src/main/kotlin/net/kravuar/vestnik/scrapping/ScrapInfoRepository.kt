package net.kravuar.vestnik.scrapping

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

internal interface ScrapInfoRepository: JpaRepository<ScrapInfo, Long> {
    @Query("""
        SELECT si
        FROM ScrapInfo si
        WHERE :url LIKE si.urlPattern
    """)
    fun findFirstByPattern(url: String): Optional<ScrapInfo>
}