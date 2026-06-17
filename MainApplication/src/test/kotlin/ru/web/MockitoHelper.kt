package ru.web

import org.mockito.ArgumentMatchers
import org.mockito.Mockito

/**
 * Kotlin-safe Mockito helper.
 *
 * Mockito's ArgumentMatchers.any() / eq() return null in Java, which violates
 * Kotlin's non-null contract at the call site and causes NullPointerExceptions
 * before the method body executes. These helpers add an unchecked cast so Kotlin
 * treats the return value as non-null while Mockito still records the matcher.
 */
@Suppress("UNCHECKED_CAST")
fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() as T

@Suppress("UNCHECKED_CAST")
fun <T> eqNonNull(value: T): T = ArgumentMatchers.eq(value) as T
