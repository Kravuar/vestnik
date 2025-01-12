package net.kravuar.vestnik.processor

import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
@Transactional
internal interface ProcessedArticleRepository: JpaRepository<ProcessedArticle, Long>