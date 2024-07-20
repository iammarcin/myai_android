// ConfigurationManager.kt

package biz.atamai.myai

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ConfigurationManager {
    private const val PREFS_NAME = "AIAppSettings"
    private const val APP_MODE_PRODUCTION = "app_mode_production" // production or test - different URL will be in use
    private const val APP_MODE_API_URL = "app_mode_api_url" // URL to be used depending on the mode
    private const val APP_MODE_USE_WATSON = "app_mode_use_watson" // user my internal watson server
    private const val TEXT_MODEL_NAME = "text_model_name"
    private const val TEXT_TEMPERATURE = "text_temperature"
    private const val TEXT_MEMORY_SIZE = "text_memory_size"
    private const val TEXT_STREAMING = "text_streaming"
    private const val TEXT_AI_CHARACTER = "text_ai_character"
    private const val TEXT_SESSION_NAME = "text_session_name"
    private const val TEXT_FILE_ATTACHED_MESSAGE_LIMIT = "text_file_attached_message_limit" // how many messages after which we don't send attachments any more
    private const val TEXT_SIZE_IN_UI = "text_size_in_ui"
    private const val GENERAL_USE_BLUETOOTH = "general_use_bluetooth"
    private const val GENERAL_TEST_DATA = "general_test_data"
    private const val GENERAL_DOWNLOAD_AUDIO_FILES_BEFORE_PLAYING = "general_download_audio_files_before_playing" // its in use in chat adapter / audio player - if we download - seekbar works fine (we can move audio playing to whatever point we want)
    private const val SPEECH_LANGUAGE = "speech_language"
    private const val SPEECH_TEMPERATURE = "speech_temperature"
    private const val TTS_STABILITY = "tts_stability"
    private const val TTS_SIMILARITY = "tts_similarity"
    private const val TTS_STYLE_EXAGGERATION = "tts_style_exaggeration"
    private const val TTS_VOICE = "tts_voice"
    private const val TTS_STREAMING = "tts_streaming"
    private const val TTS_SPEED = "tts_speed"
    private const val TTS_MODEL_NAME = "tts_model_name"
    private const val TTS_AUTO_EXECUTE = "tts_auto_execute"
    private const val IMAGE_MODEL_NAME = "image_model_name"
    private const val IMAGE_NUMBER_IMAGES = "image_number_images"
    private const val IMAGE_SIZE = "image_model_size"
    private const val IMAGE_QUALITY_HD = "image_quality_id"
    private const val IMAGE_DISABLE_SAFE_PROMPT = "image_disable_safe_prompt"
    private const val IMAGE_AUTO_GENERATE_IMAGE = "image_auto_generate_image"
    private const val IMAGE_ARTGEN_SHOW_PROMPT = "image_artgen_show_prompt"
    private const val AUTH_TOKEN_FOR_BACKEND = "auth_token_for_backend"
    private const val FAVORITE_CHATS = "favorite_chats"
    private const val FAVORITE_CHATS_NON_PROD = "favorite_chats_non_prod"

    private lateinit var sharedPreferences: SharedPreferences

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Default values
    private val defaultSettings = mapOf(
        APP_MODE_PRODUCTION to true, // "production" - true or "test" - false
        APP_MODE_API_URL to "https://www.goodtogreat.life/api/",
        APP_MODE_USE_WATSON to false,
        TEXT_MODEL_NAME to "GPT-4o",
        TEXT_AI_CHARACTER to "assistant",
        TEXT_SESSION_NAME to "New chat",
        TEXT_TEMPERATURE to 0.0f,
        TEXT_MEMORY_SIZE to 2000,
        TEXT_STREAMING to false,
        TEXT_FILE_ATTACHED_MESSAGE_LIMIT to 3,
        TEXT_SIZE_IN_UI to 16,
        GENERAL_USE_BLUETOOTH to false,
        GENERAL_TEST_DATA to false,
        GENERAL_DOWNLOAD_AUDIO_FILES_BEFORE_PLAYING to true,
        SPEECH_LANGUAGE to "en",
        SPEECH_TEMPERATURE to 0.0f,
        TTS_STABILITY to 0.0f,
        TTS_SIMILARITY to 0.0f,
        TTS_STYLE_EXAGGERATION to 0.0f,
        TTS_VOICE to "alloy",
        TTS_STREAMING to false,
        TTS_SPEED to 1.0f,
        TTS_MODEL_NAME to "tts-1",
        TTS_AUTO_EXECUTE to false,
        IMAGE_MODEL_NAME to "dall-e-3",
        IMAGE_NUMBER_IMAGES to 1,
        IMAGE_SIZE to 1024,
        IMAGE_QUALITY_HD to false, //hd or standard (true = hd)
        IMAGE_DISABLE_SAFE_PROMPT to false,
        IMAGE_AUTO_GENERATE_IMAGE to false,
        IMAGE_ARTGEN_SHOW_PROMPT to false,
        AUTH_TOKEN_FOR_BACKEND to "",

        // Add other default values
    )

    private fun getString(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    private fun getFloat(key: String, defaultValue: Float): Float {
        return sharedPreferences.getFloat(key, defaultValue)
    }

    private fun getInt(key: String, defaultValue: Int): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    private fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    private fun getJson(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    private fun setString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    private fun setFloat(key: String, value: Float) {
        sharedPreferences.edit().putFloat(key, value).apply()
    }

    private fun setInt(key: String, value: Int) {
        sharedPreferences.edit().putInt(key, value).apply()
    }

    private fun setBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    // getter methods
    fun getIsProdMode() = getBoolean(APP_MODE_PRODUCTION, defaultSettings[APP_MODE_PRODUCTION] as Boolean)
    fun getAppModeApiUrl() = getString(APP_MODE_API_URL, defaultSettings[APP_MODE_API_URL] as String)
    fun getAppModeUseWatson() = getBoolean(APP_MODE_USE_WATSON, defaultSettings[APP_MODE_USE_WATSON] as Boolean)
    fun getDownloadAudioFilesBeforePlaying() = getBoolean(GENERAL_DOWNLOAD_AUDIO_FILES_BEFORE_PLAYING, defaultSettings[GENERAL_DOWNLOAD_AUDIO_FILES_BEFORE_PLAYING] as Boolean)
    fun getTextModelName() = getString(TEXT_MODEL_NAME, defaultSettings[TEXT_MODEL_NAME] as String)
    fun getTextAICharacter() = getString(TEXT_AI_CHARACTER, defaultSettings[TEXT_AI_CHARACTER] as String)
    fun getTextCurrentSessionName() = getString(TEXT_SESSION_NAME, defaultSettings[TEXT_SESSION_NAME] as String)
    fun getTextTemperature() = getFloat(TEXT_TEMPERATURE, defaultSettings[TEXT_TEMPERATURE] as Float)
    fun getTextMemorySize() = getInt(TEXT_MEMORY_SIZE, defaultSettings[TEXT_MEMORY_SIZE] as Int)
    fun getTextFileAttachedMessageLimit() = getInt(TEXT_FILE_ATTACHED_MESSAGE_LIMIT, defaultSettings[TEXT_FILE_ATTACHED_MESSAGE_LIMIT] as Int)
    fun getTextSizeInUI() = getInt(TEXT_SIZE_IN_UI, defaultSettings[TEXT_SIZE_IN_UI] as Int)
    fun getIsStreamingEnabled() = getBoolean(TEXT_STREAMING, defaultSettings[TEXT_STREAMING] as Boolean)
    fun getUseBluetooth() = getBoolean(GENERAL_USE_BLUETOOTH, defaultSettings[GENERAL_USE_BLUETOOTH] as Boolean)
    fun getUseTestData() = getBoolean(GENERAL_TEST_DATA, defaultSettings[GENERAL_TEST_DATA] as Boolean)
    fun getSpeechLanguage() = getString(SPEECH_LANGUAGE, defaultSettings[SPEECH_LANGUAGE] as String)
    fun getSpeechTemperature() = getFloat(SPEECH_TEMPERATURE, defaultSettings[SPEECH_TEMPERATURE] as Float)
    fun getTTSStability() = getFloat(TTS_STABILITY, defaultSettings[TTS_STABILITY] as Float)
    fun getTTSSimilarity() = getFloat(TTS_SIMILARITY, defaultSettings[TTS_SIMILARITY] as Float)
    fun getTTSStyleExaggeration() = getFloat(TTS_STYLE_EXAGGERATION, defaultSettings[TTS_STYLE_EXAGGERATION] as Float)
    fun getTTSVoice() = getString(TTS_VOICE, defaultSettings[TTS_VOICE] as String)
    fun getTTSStreaming() = getBoolean(TTS_STREAMING, defaultSettings[TTS_STREAMING] as Boolean)
    fun getTTSSpeed() = getFloat(TTS_SPEED, defaultSettings[TTS_SPEED] as Float)
    fun getTTSModelName() = getString(TTS_MODEL_NAME, defaultSettings[TTS_MODEL_NAME] as String)
    fun getTTSAutoExecute() = getBoolean(TTS_AUTO_EXECUTE, defaultSettings[TTS_AUTO_EXECUTE] as Boolean)
    fun getImageModelName() = getString(IMAGE_MODEL_NAME, defaultSettings[IMAGE_MODEL_NAME] as String)
    fun getImageNumberImages() = getInt(IMAGE_NUMBER_IMAGES, defaultSettings[IMAGE_NUMBER_IMAGES] as Int)
    fun getImageSize() = getInt(IMAGE_SIZE, defaultSettings[IMAGE_SIZE] as Int)
    fun getImageQualityHD() = getBoolean(IMAGE_QUALITY_HD, defaultSettings[IMAGE_QUALITY_HD] as Boolean)
    fun getImageDisableSafePrompt() = getBoolean(IMAGE_DISABLE_SAFE_PROMPT, defaultSettings[IMAGE_DISABLE_SAFE_PROMPT] as Boolean)
    fun getImageAutoGenerateImage() = getBoolean(IMAGE_AUTO_GENERATE_IMAGE, defaultSettings[IMAGE_AUTO_GENERATE_IMAGE] as Boolean)
    fun getImageArtgenShowPrompt() = getBoolean(IMAGE_ARTGEN_SHOW_PROMPT, defaultSettings[IMAGE_ARTGEN_SHOW_PROMPT] as Boolean)
    fun getAuthTokenForBackend() = getString(AUTH_TOKEN_FOR_BACKEND, defaultSettings[AUTH_TOKEN_FOR_BACKEND] as String)

    // setter methods
    fun setIsProdMode(value: Boolean) = setBoolean(APP_MODE_PRODUCTION, value)
    fun setAppModeApiUrl(value: String) = setString(APP_MODE_API_URL, value)
    fun setAppModeUseWatson(value: Boolean) = setBoolean(APP_MODE_USE_WATSON, value)
    fun setDownloadAudioFilesBeforePlaying(value: Boolean) = setBoolean(GENERAL_DOWNLOAD_AUDIO_FILES_BEFORE_PLAYING, value)
    fun setTextModelName(value: String) = setString(TEXT_MODEL_NAME, value)
    fun setTextAICharacter(value: String) = setString(TEXT_AI_CHARACTER, value)
    fun setTextCurrentSessionName(value: String) = setString(TEXT_SESSION_NAME, value)
    fun setTextTemperature(value: Float) = setFloat(TEXT_TEMPERATURE, value)
    fun setTextMemorySize(value: Int) = setInt(TEXT_MEMORY_SIZE, value)
    fun setTextFileAttachedMessageLimit(value: Int) = setInt(TEXT_FILE_ATTACHED_MESSAGE_LIMIT, value)
    fun setTextSizeInUI(value: Int) = setInt(TEXT_SIZE_IN_UI, value)
    fun setIsStreamingEnabled(value: Boolean) = setBoolean(TEXT_STREAMING, value)
    fun setUseBluetooth(value: Boolean) = setBoolean(GENERAL_USE_BLUETOOTH, value)
    fun setUseTestData(value: Boolean) = setBoolean(GENERAL_TEST_DATA, value)
    fun setSpeechLanguage(value: String) = setString(SPEECH_LANGUAGE, value.lowercase())
    fun setSpeechTemperature(value: Float) = setFloat(SPEECH_TEMPERATURE, value)
    fun setTTSStability(value: Float) = setFloat(TTS_STABILITY, value)
    fun setTTSSimilarity(value: Float) = setFloat(TTS_SIMILARITY, value)
    fun setTTSStyleExaggeration(value: Float) = setFloat(TTS_STYLE_EXAGGERATION, value)
    fun setTTSVoice(value: String) = setString(TTS_VOICE, value)
    fun setTTSStreaming(value: Boolean) = setBoolean(TTS_STREAMING, value)
    fun setTTSSpeed(value: Float) = setFloat(TTS_SPEED, value)
    fun setTTSModelName(value: String) = setString(TTS_MODEL_NAME, value)
    fun setTTSAutoExecute(value: Boolean) = setBoolean(TTS_AUTO_EXECUTE, value)
    fun setImageModelName(value: String) = setString(IMAGE_MODEL_NAME, value)
    fun setImageNumberImages(value: Int) = setInt(IMAGE_NUMBER_IMAGES, value)
    fun setImageSize(value: Int) = setInt(IMAGE_SIZE, value)
    fun setImageQualityHD(value: Boolean) = setBoolean(IMAGE_QUALITY_HD, value)
    fun setImageDisableSafePrompt(value: Boolean) = setBoolean(IMAGE_DISABLE_SAFE_PROMPT, value)
    fun setImageAutoGenerateImage(value: Boolean) = setBoolean(IMAGE_AUTO_GENERATE_IMAGE, value)
    fun setImageArtgenShowPrompt(value: Boolean) = setBoolean(IMAGE_ARTGEN_SHOW_PROMPT, value)
    fun setAuthTokenForBackend(value: String) = setString(AUTH_TOKEN_FOR_BACKEND, value)

    // depending if it's production mode and also depending which internal API server is in use (sherlock, local, etc)
    fun setURLForAPICalls() {
        val url = if (getIsProdMode()) {
            "https://www.goodtogreat.life/api/"
        } else {
            if (getAppModeUseWatson()) {
                "http://192.168.1.123:8000/" 
            } else {
                // local
                //"http://192.168.23.66:8000/" // PT
                "http://192.168.1.150:8000/" // ES
            }
        }
        setAppModeApiUrl(url)
    }

    fun getFavoriteChats(): List<FavoriteChat> {
        val whichItem = if (getIsProdMode()) {
            FAVORITE_CHATS
        } else {
            FAVORITE_CHATS_NON_PROD
        }
        val json = getString(whichItem, "[]")
        return Gson().fromJson(json, object : TypeToken<List<FavoriteChat>>() {}.type)
    }

    fun setFavoriteChats(chats: List<FavoriteChat>) {
        val whichItem = if (getIsProdMode()) {
            FAVORITE_CHATS
        } else {
            FAVORITE_CHATS_NON_PROD
        }
        val json = Gson().toJson(chats)
        setString(whichItem, json)
    }

    // Update the name of a favorite chat (used in DatabaseHandler - when session is renamed)
    fun updateFavoriteChatName(sessionId: String, newName: String) {
        val favoriteChats = getFavoriteChats().toMutableList()
        var chat = favoriteChats.find { it.id == sessionId }
        if (chat != null) {
            chat.name = newName
            setFavoriteChats(favoriteChats)
        }
    }

    // used for API calls - to prepare dict with all settings
    fun getSettingsDict(): Map<String, Map<String, Any>> {
        return mapOf(
            "text" to mapOf(
                "temperature" to getTextTemperature(),
                "model" to getTextModelName(),
                "memory_limit" to getTextMemorySize(),
                "ai_character" to getTextAICharacter(),
                "streaming" to getIsStreamingEnabled(),
                "file_attached_message_limit" to getTextFileAttachedMessageLimit(),
            ),
            "tts" to mapOf(
                "stability" to getTTSStability(),
                "similarity_boost" to getTTSSimilarity(),
                "style_exaggeration" to getTTSStyleExaggeration(),
                "voice" to getTTSVoice(),
                "streaming" to getTTSStreaming(),
                "speed" to getTTSSpeed(),
                "model" to getTTSModelName(),
            ),
            "speech" to mapOf(
                "language" to getSpeechLanguage(),
                "temperature" to getSpeechTemperature()
            ),
            "image" to mapOf(
                "model" to getImageModelName(),
                "number_of_images" to getImageNumberImages(),
                "size_of_image" to getImageSize(),
                "quality_hd" to getImageQualityHD(),
                "disable_safe_prompt_adjust" to getImageDisableSafePrompt(),
            ),
            "general" to mapOf(
                "returnTestData" to getUseTestData(),
            ),
        )
    }
}
