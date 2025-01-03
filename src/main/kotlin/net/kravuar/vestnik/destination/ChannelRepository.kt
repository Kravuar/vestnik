package net.kravuar.vestnik.destination

import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
internal interface ChannelRepository: JpaRepository<Channel, String> {
    fun findByName(name: String, sort: Sort = Sort.by(Sort.Direction.ASC, "deleted")): Channel
    @Query("UPDATE Channel c SET c.deleted = true WHERE c.name = :name")
    fun deleteByName(name: String): Channel
}