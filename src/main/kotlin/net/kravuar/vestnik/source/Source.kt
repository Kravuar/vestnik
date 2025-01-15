package net.kravuar.vestnik.source

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToMany
import jakarta.validation.constraints.NotBlank
import net.kravuar.vestnik.channels.Channel
import java.time.Duration

@Entity
class Source(
    @Column(nullable = false, unique = true)
    @NotBlank
    var name: String,
    @Column(nullable = false)
    @NotBlank
    var url: String,
    @Column(nullable = false)
    var scheduleDelay: Duration,
    @Column(nullable = false)
    @NotBlank
    var contentXPath: String,
    @ManyToMany(fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    var channels: MutableSet<Channel> = HashSet(),
    @Column(nullable = false)
    var deleted: Boolean = false,
    @Column
    var suspended: Boolean? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
) {
    override fun toString(): String {
        return "Source(id=$id, name='$name', scheduleDelay=$scheduleDelay, url='$url', contentXPath='$contentXPath', deleted=$deleted, suspended=$suspended)"
    }
}