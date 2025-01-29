package net.kravuar.vestnik.articles

import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
@Transactional
internal interface ArticlesRepository: JpaRepository<Article, Long> {
    fun existsByUrl(url: String): Boolean
}