package com.lagradost

sealed class ValidationResult {
    data class Valid(val path: String, val label: String) : ValidationResult()
    data object InvalidDomain : ValidationResult()
    data object InvalidPath : ValidationResult()
}
