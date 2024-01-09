package com.kos.views

import com.kos.characters.Character
import com.kos.characters.CharacterRequest
import kotlinx.serialization.Serializable

@Serializable
data class SimpleView(val id: String, val name: String, val owner: String, val characterIds: List<Long>)

@Serializable
data class View(val id: String,val name: String, val owner: String, val characters: List<Character>)

@Serializable
data class ViewRequest(val name: String, val characters: List<CharacterRequest>)

@Serializable
sealed interface ViewResult {
    val isSuccess: Boolean
}

@Serializable
data class ViewDeleted(val viewId: String) : ViewResult {
    override val isSuccess: Boolean = true
}

@Serializable
data class ViewModified(val viewId: String, val characters: List<Long>) : ViewResult {
    override val isSuccess: Boolean = true
}

@Serializable
data class ViewNotFound(val viewId: String) : ViewResult {
    override val isSuccess: Boolean = false
}

@Serializable
class TooMuchViews : ViewResult {
    override val isSuccess: Boolean = false
}

@Serializable
data class InsertCharacterError(val message: String) : ViewResult {
    override val isSuccess: Boolean = false
}