package com.example.m3uplayer

import android.content.Context

/**
 * Manages favorited media entries using SharedPreferences.
 * Stores a set of unique IDs for quick lookup.
 */
class FavoritesManager(context: Context) {
    private val prefs = context.getSharedPreferences("m3uplayer_favorites", Context.MODE_PRIVATE)
    private val KEY_FAVORITES = "favorites_set"

    fun isFavorite(id: String): Boolean {
        val favorites = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        return favorites.contains(id)
    }

    fun toggleFavorite(id: String) {
        val favorites = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (favorites.contains(id)) {
            favorites.remove(id)
        } else {
            favorites.add(id)
        }
        persist(favorites)
    }

    fun getFavoriteIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    private fun persist(favorites: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
    }
}
