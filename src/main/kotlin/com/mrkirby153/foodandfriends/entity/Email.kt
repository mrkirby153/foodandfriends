package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.dv8tion.jda.api.entities.User
import org.springframework.data.jpa.repository.JpaRepository

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