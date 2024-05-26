package com.mrkirby153.foodandfriends.service

import com.google.api.client.util.store.DataStoreFactory
import com.mrkirby153.foodandfriends.entity.DataStoreEntityRepository
import com.mrkirby153.foodandfriends.google.PostgresDataStoreFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

interface DataStoreService {
    fun getDataStoreFactory(): DataStoreFactory
}


@Service
class PostgresDataStoreManager(
    private val dataStoreEntityRepository: DataStoreEntityRepository,
    private val template: TransactionTemplate
) : DataStoreService {

    override fun getDataStoreFactory(): DataStoreFactory {
        return PostgresDataStoreFactory(dataStoreEntityRepository, template)
    }

}