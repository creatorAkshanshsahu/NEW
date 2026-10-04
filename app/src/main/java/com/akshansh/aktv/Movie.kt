package com.akshansh.aktv

data class Movie(
    val id: String,
    val title: String,
    val year: String?,
    val description: String?,
    val rights: String?,
    val licenseUrl: String?,
    val poster: String,
    var videoUrl: String? = null
)
