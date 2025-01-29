package net.kravuar.vestnik.channels

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
internal interface ChannelRepository : JpaRepository<Channel, Long> {
    @Query(
        """
        SELECT c
        FROM Channel c
        WHERE c.deleted = false
    """
    )
    fun findChannels(pageable: Pageable): Page<Channel>

    @Query(
        """
        SELECT c
        FROM Channel c
    """
    )
    fun findAllChannels(pageable: Pageable): Page<Channel>

    fun findByName(name: String): Optional<Channel>

    @Modifying
    @Query("UPDATE Channel c SET c.deleted = true WHERE c.name = :name")
    fun deleteByName(name: String): Int
}