package com.mrkirby153.foodandfriends.kutils

import java.util.concurrent.CompletableFuture

fun <T> T.asCompletedFuture(): CompletableFuture<T> = CompletableFuture.completedFuture(this)

fun List<CompletableFuture<*>>.allOf(): CompletableFuture<Void> =
    CompletableFuture.allOf(*this.toTypedArray())

fun <T> List<CompletableFuture<T>>.getAll(): CompletableFuture<List<T>> {
    return this.allOf().thenApply {
        this.map { it.get() }
    }
}