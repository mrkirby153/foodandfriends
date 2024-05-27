package com.mrkirby153.foodandfriends.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import me.mrkirby153.kcutils.ulid.generateUlid
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "person")
class Person(
    var email: String? = null,
    @Column(name = "discord_id")
    var discordUserId: Long = 0
) {
    @Id
    val id: String = generateUlid()
}

interface PersonRepository : JpaRepository<Person, String> {

    fun getByDiscordUserId(discordUserId: Long): Person?
    fun getByEmail(email: String): Person?
}