package net.kravuar.vestnik.source

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
internal interface SourcesRepository: JpaRepository<Source, Int> {
    fun findByName(name: String): Source
    fun deleteByName(name: String): Source
}