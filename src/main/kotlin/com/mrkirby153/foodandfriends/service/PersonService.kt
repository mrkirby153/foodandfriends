package com.mrkirby153.foodandfriends.service

import com.mrkirby153.foodandfriends.entity.Person
import com.mrkirby153.foodandfriends.entity.PersonRepository
import net.dv8tion.jda.api.entities.User
import org.springframework.stereotype.Service

interface PersonService {

    fun setEmail(user: User, email: String)

    fun getEmail(user: User): String?

    fun getByUser(user: User) = getByUser(user.idLong)

    fun getByUser(userId: Long): Person?

    fun getOrCreate(user: User) = getOrCreate(user.idLong)

    fun getOrCreate(userId: Long): Person
}


@Service
class PersonManager(
    private val personRepository: PersonRepository
) : PersonService {

    override fun setEmail(user: User, email: String) {
        val person = personRepository.getByDiscordUserId(user.idLong)
        if (person == null) {
            personRepository.save(Person(email, user.idLong))
            return
        }
        person.email = email
        personRepository.save(person)
    }

    override fun getEmail(user: User): String? {
        return personRepository.getByDiscordUserId(user.idLong)?.email
    }

    override fun getByUser(userId: Long): Person? {
        return personRepository.getByDiscordUserId(userId)
    }

    override fun getOrCreate(userId: Long): Person {
        return personRepository.getByDiscordUserId(userId) ?: personRepository.save(
            Person(
                discordUserId = userId
            )
        )
    }

}