package com.akshansh.aktv

data class Channel(
    val id: String,
    val name: String,
    val logo: String?,
    val streamUrl: String?,
    val category: String?
)
