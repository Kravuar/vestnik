package net.kravuar.vestnik.source

import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
internal interface SourcesRepository: JpaRepository<Source, Long> {
    fun findByName(name: String, sort: Sort = Sort.by(Sort.Direction.ASC, "deleted")): Source
    @Modifying
    @Query("UPDATE Source s SET s.deleted = true WHERE s.name = :name")
    fun deleteByName(name: String): Source
}