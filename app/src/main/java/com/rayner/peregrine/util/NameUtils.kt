package com.rayner.peregrine.util

fun formatCameraName(name: String): String {
    return name.replace("_", " ")
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}
