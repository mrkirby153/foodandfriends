package com.mrkirby153.foodandfriends.service

import com.google.api.client.util.store.DataStoreFactory
import com.mrkirby153.foodandfriends.entity.DataStoreEntityRepository
import com.mrkirby153.foodandfriends.google.PostgresDataStoreFactory
import org.springframework.stereotype.Service

interface DataStoreService {
    fun getDataStoreFactory(id: String): DataStoreFactory
}


@Service
class PostgresDataStoreManager(
    private val dataStoreEntityRepository: DataStoreEntityRepository
) : DataStoreService {

    override fun getDataStoreFactory(id: String): DataStoreFactory {
        return PostgresDataStoreFactory(dataStoreEntityRepository, id)
    }

}