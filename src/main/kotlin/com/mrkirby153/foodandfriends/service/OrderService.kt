package com.mrkirby153.foodandfriends.service

import com.mrkirby153.foodandfriends.entity.Order
import com.mrkirby153.foodandfriends.entity.OrderRepository
import com.mrkirby153.foodandfriends.entity.Person
import com.mrkirby153.foodandfriends.entity.PersonRepository
import jakarta.transaction.Transactional
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

interface OrderService {

    fun create(name: String): Order

    fun getOrder(id: String): Order?

    fun addPerson(person: Person, order: Order): Order

    fun removePerson(person: Person, order: Order): Order

    fun setName(order: Order, name: String): Order

    fun getPeople(order: Order): List<Person>

    fun getAllOrders(): List<Order>

    fun delete(order: Order)
}


@Service
class OrderManager(
    private val orderRepository: OrderRepository,
    private val personRepository: PersonRepository
) : OrderService {
    override fun create(name: String): Order {
        val order = Order(name)
        return orderRepository.save(order)
    }

    override fun getOrder(id: String): Order? {
        return orderRepository.findByIdOrNull(id)
    }

    override fun addPerson(person: Person, order: Order): Order {
        order.addPerson(person)
        return orderRepository.save(order)
    }

    override fun removePerson(person: Person, order: Order): Order {
        order.removePerson(person)
        return orderRepository.save(order)
    }

    override fun setName(order: Order, name: String): Order {
        order.name = name
        return orderRepository.save(order)
    }

    override fun getPeople(order: Order): List<Person> {
        return personRepository.findAllById(order.getPeopleIds())
    }

    override fun getAllOrders(): List<Order> {
        return orderRepository.findAll()
    }

    @Transactional
    override fun delete(order: Order) {
        orderRepository.delete(order)
    }

}