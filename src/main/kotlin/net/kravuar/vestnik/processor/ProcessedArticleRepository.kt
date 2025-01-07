package net.kravuar.vestnik.processor

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
internal interface ProcessedArticleRepository: JpaRepository<ProcessedArticle, Long>