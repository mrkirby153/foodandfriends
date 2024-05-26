package com.mrkirby153.foodandfriends.service

import com.mrkirby153.foodandfriends.entity.Person
import com.mrkirby153.foodandfriends.entity.PersonRepository
import net.dv8tion.jda.api.entities.User
import org.springframework.stereotype.Service

interface PersonService {

    fun setEmail(user: User, email: String)

    fun getEmail(user: User): String?
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

}