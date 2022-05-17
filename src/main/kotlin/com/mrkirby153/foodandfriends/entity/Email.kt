package com.mrkirby153.foodandfriends.entity

import net.dv8tion.jda.api.entities.User
import org.springframework.data.jpa.repository.JpaRepository
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "emails")
class Email(
    @Id
    @GeneratedValue
    var id: Long = 0,
    @Column(name = "user_id")
    var userId: String? = null,

    @Column(name = "email")
    var email: String? = null
) {
    constructor(user: User, email: String) : this(userId = user.id, email = email)
}

interface EmailRepo : JpaRepository<Email, Long> {
    fun findByUserId(userId: String?): Email?

    fun deleteByUserId(userId: String?)
}