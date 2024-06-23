package biz.atamai.myai

import com.google.gson.*
import java.lang.reflect.Type

// Data class for a folder storing chats
data class FavoriteFolder(val id: String, var name: String, val items: MutableList<FavoriteItem> = mutableListOf())
// favorite chat
data class FavoriteChat(val id: String, var name: String, var imageResId: Int)

// Sealed class to represent either a chat or a folder
sealed class FavoriteItem {
    data class Chat(val chat: FavoriteChat) : FavoriteItem()
    data class Folder(val folder: FavoriteFolder) : FavoriteItem()
}

// this is to handle favorite chats or folders in top menu handler
class FavoriteItemTypeAdapter : JsonSerializer<FavoriteItem>, JsonDeserializer<FavoriteItem> {

    override fun serialize(src: FavoriteItem?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        val jsonObject = JsonObject()
        when (src) {
            is FavoriteItem.Chat -> {
                jsonObject.addProperty("type", "chat")
                jsonObject.add("chat", context?.serialize(src.chat))
            }
            is FavoriteItem.Folder -> {
                jsonObject.addProperty("type", "folder")
                jsonObject.add("folder", context?.serialize(src.folder))
            }
            else -> {
                jsonObject.addProperty("type", "unknown")
            }
        }
        return jsonObject
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): FavoriteItem {
        val jsonObject = json?.asJsonObject ?: throw JsonParseException("Invalid JSON for FavoriteItem")
        val type = jsonObject.get("type")?.asString ?: run {
            // Attempt to infer type from existing JSON structure
            when {
                jsonObject.has("chat") -> "chat"
                jsonObject.has("folder") -> "folder"
                else -> throw JsonParseException("Unknown element type and unable to infer: $jsonObject")
            }
        }

        println("Deserializing FavoriteItem with type: $type")

        return when (type) {
            "chat" -> {
                val chat = context?.deserialize<FavoriteChat>(jsonObject.get("chat"), FavoriteChat::class.java)
                    ?: throw JsonParseException("Chat data is missing")
                FavoriteItem.Chat(chat)
            }
            "folder" -> {
                val folder = context?.deserialize<FavoriteFolder>(jsonObject.get("folder"), FavoriteFolder::class.java)
                    ?: throw JsonParseException("Folder data is missing")
                FavoriteItem.Folder(folder)
            }
            else -> throw JsonParseException("Unknown element type: $type")
        }
    }
}

