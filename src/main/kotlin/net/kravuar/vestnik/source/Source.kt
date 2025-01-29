package net.kravuar.vestnik.source

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.validation.constraints.NotBlank
import java.time.Duration

@Entity
class Source(
    @Column(nullable = false, unique = true)
    @get:NotBlank
    var name: String,
    @Column(nullable = false)
    @get:NotBlank
    var url: String,
    @Column(nullable = false)
    var scheduleDelay: Duration,
    @Column
    var contentXPath: String? = null,
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