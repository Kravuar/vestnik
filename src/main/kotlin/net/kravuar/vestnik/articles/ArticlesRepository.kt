package net.kravuar.vestnik.articles

import jakarta.transaction.Transactional
import net.kravuar.vestnik.source.Source
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
@Transactional
internal interface ArticlesRepository: JpaRepository<Article, Long> {
    fun existsBySourceGuid(guid: String): Boolean
    fun findTopBySourceOrderByCreatedAtDesc(source: Source): Optional<Article>
}