package net.kravuar.vestnik.source

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToMany
import net.kravuar.vestnik.destination.Channel
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
    @ManyToMany(cascade = [
        CascadeType.PERSIST,
        CascadeType.MERGE
    ])
    var channels: MutableSet<Channel> = HashSet(),
    @Column
    var suspended: Boolean? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null
)