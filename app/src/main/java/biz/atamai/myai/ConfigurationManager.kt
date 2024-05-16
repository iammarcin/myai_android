package biz.atamai.myai

import android.content.Context
import android.content.SharedPreferences

object ConfigurationManager {
    private const val PREFS_NAME = "AIAppSettings"
    private const val TEXT_MODEL_NAME = "text_model_name"
    private const val TEXT_TEMPERATURE = "text_temperature"
    private const val TEXT_MEMORY_SIZE = "text_memory_size"
    private const val TEXT_STREAMING = "text_streaming"
    private const val GENERAL_USE_BLUETOOTH = "general_use_bluetooth"
    private const val GENERAL_TEST_DATA = "general_test_data"
    private const val SPEECH_LANGUAGE = "speech_language"
    private const val SPEECH_TEMPERATURE = "speech_temperature"
    private const val AUDIO_STABILITY = "audio_stability"
    private const val AUDIO_SIMILARITY = "audio_similarity"

    private lateinit var sharedPreferences: SharedPreferences

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Default values
    private val defaultSettings = mapOf(
        TEXT_MODEL_NAME to "GPT-4o",
        TEXT_TEMPERATURE to 0.0f,
        TEXT_MEMORY_SIZE to 2000,
        TEXT_STREAMING to false,
        GENERAL_USE_BLUETOOTH to false,
        GENERAL_TEST_DATA to false,
        SPEECH_LANGUAGE to "en",
        SPEECH_TEMPERATURE to 0.0f,
        AUDIO_STABILITY to 0.0f,
        AUDIO_SIMILARITY to 0.0f

        // Add other default values
    )

    fun getString(key: String, defaultValue: String): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    fun getFloat(key: String, defaultValue: Float): Float {
        return sharedPreferences.getFloat(key, defaultValue)
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return sharedPreferences.getInt(key, defaultValue)
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    fun setString(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun setFloat(key: String, value: Float) {
        sharedPreferences.edit().putFloat(key, value).apply()
    }

    fun setInt(key: String, value: Int) {
        sharedPreferences.edit().putInt(key, value).apply()
    }

    fun setBoolean(key: String, value: Boolean) {
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    fun getTextModelName() = getString(TEXT_MODEL_NAME, defaultSettings[TEXT_MODEL_NAME] as String)
    fun getTextTemperature() = getFloat(TEXT_TEMPERATURE, defaultSettings[TEXT_TEMPERATURE] as Float)
    fun getTextMemorySize() = getInt(TEXT_MEMORY_SIZE, defaultSettings[TEXT_MEMORY_SIZE] as Int)
    fun getIsStreamingEnabled() = sharedPreferences.getBoolean(TEXT_STREAMING, defaultSettings[TEXT_STREAMING] as Boolean)
    fun getUseBluetooth() = sharedPreferences.getBoolean(GENERAL_USE_BLUETOOTH, defaultSettings[GENERAL_USE_BLUETOOTH] as Boolean)
    fun getUseTestData() = sharedPreferences.getBoolean(GENERAL_TEST_DATA, defaultSettings[GENERAL_TEST_DATA] as Boolean)
    fun getSpeechLanguage() = getString(SPEECH_LANGUAGE, defaultSettings[SPEECH_LANGUAGE] as String)
    fun getSpeechTemperature() = getFloat(SPEECH_TEMPERATURE, defaultSettings[SPEECH_TEMPERATURE] as Float)
    fun getAudioStability() = getFloat(AUDIO_STABILITY, defaultSettings[AUDIO_STABILITY] as Float)
    fun getAudioSimilarity() = getFloat(AUDIO_SIMILARITY, defaultSettings[AUDIO_SIMILARITY] as Float)

    // Add other getter and setter methods
}
