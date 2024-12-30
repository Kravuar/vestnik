package net.kravuar.vestnik.source

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToMany
import net.kravuar.vestnik.destination.Destination
import java.time.Duration

@Entity
class Source(
    @Column(nullable = false, unique = true)
    var name: String,
    @Column(nullable = false)
    var url: String,
    @Column(nullable = false)
    var scheduleDelay: Duration,
    @Column(nullable = false)
    var contentXPath: String,
    @ManyToMany
    var destinations: Set<Destination> = emptySet(),
    @Column
    var suspended: Boolean? = null,
    @Column
    var thumbnailXPath: String? = null,
    @Column
    var activeMode: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null
)