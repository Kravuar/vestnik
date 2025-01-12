package net.kravuar.vestnik.source

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
internal interface SourcesRepository : JpaRepository<Source, Long> {
    fun findAllByDeletedIsFalse(): List<Source>
    fun findAllByDeletedIsFalse(pageable: Pageable): Page<Source>

    fun findByNameAndDeletedIsFalse(sourceName: String): Optional<Source>

    @Modifying
    @Query("UPDATE Source s SET s.deleted = true WHERE s.name = :name")
    fun deleteByName(name: String): Int
}