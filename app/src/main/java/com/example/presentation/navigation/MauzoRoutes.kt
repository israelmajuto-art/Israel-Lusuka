package com.example.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface MauzoRoute {
    @Serializable
    data object Nyumbani : MauzoRoute

    @Serializable
    data object Miamala : MauzoRoute

    @Serializable
    data object Sms : MauzoRoute

    @Serializable
    data object Ripoti : MauzoRoute

    @Serializable
    data object Mipangilio : MauzoRoute

    @Serializable
    data class TransactionDetail(val transactionLocalId: Long) : MauzoRoute
}
