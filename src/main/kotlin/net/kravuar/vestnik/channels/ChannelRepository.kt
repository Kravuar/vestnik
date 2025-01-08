package net.kravuar.vestnik.channels

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
internal interface ChannelRepository: JpaRepository<Channel, Long> {
    fun findAllByDeletedIsFalse(): List<Channel>
    fun findAllByDeletedIsFalse(pageable: Pageable): Page<Channel>

    fun findByName(name: String): Channel

    @Modifying
    @Query("UPDATE Channel c SET c.deleted = true WHERE c.name = :name")
    fun deleteByName(name: String): Channel
}