package net.kravuar.vestnik.destination

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
internal interface DestinationRepository: JpaRepository<Channel, String> {
    fun findByName(name: String): Channel
}