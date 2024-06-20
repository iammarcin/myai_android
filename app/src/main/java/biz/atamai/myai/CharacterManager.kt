// CharacterManager.kt

package biz.atamai.myai

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import biz.atamai.myai.databinding.ActivityMainBinding
import biz.atamai.myai.databinding.CharacterCardBinding

class CharacterManager(private val context: Context) {

    // Data class to hold character information
    // name = what will be displayed on the card in UI
    // nameForAPI = what will be sent to the API
    // autoResponse = when user provides input, should character automatically responds (in general always yes - but there are few cases like blogger then NO)
    // showGPSButton = if character should have button to share GPS location enabled
    data class Character(val name: String, val imageResId: Int, val nameForAPI: String, val autoResponse: Boolean = true, val showGPSButton: Boolean = false)

    val characters = listOf(
        Character(name = "Assistant", imageResId = R.drawable.assistant, nameForAPI = "assistant", autoResponse = true, showGPSButton = false),
        Character(name = "Art gen", imageResId = R.drawable.tools_artgen, nameForAPI = "tools_artgen", autoResponse = true, showGPSButton = false),
        Character(name = "Alergy expert", imageResId = R.drawable.alergy, nameForAPI = "alergy", autoResponse = true, showGPSButton = true),
        Character(name = "Garmin", imageResId = R.drawable.garmin, nameForAPI = "garmin", autoResponse = true, showGPSButton = true),
        Character(name = "Dietetist", imageResId = R.drawable.dietetist, nameForAPI = "dietetist", autoResponse = true, showGPSButton = false),
        Character(name = "Blogger", imageResId = R.drawable.blogger, nameForAPI = "blogger", autoResponse = false, showGPSButton = true),
        Character(name = "Personal coach", imageResId = R.drawable.personal_coach, nameForAPI = "personal_coach", autoResponse = true, showGPSButton = false),
        Character(name = "Longevity expert", imageResId = R.drawable.longevity_expert, nameForAPI = "longevity_expert", autoResponse = true, showGPSButton = false),
        Character(name = "Sleep expert", imageResId = R.drawable.sleep_expert, nameForAPI = "sleep_expert", autoResponse = true, showGPSButton = false),
        Character(name = "Gardener", imageResId = R.drawable.gardener, nameForAPI = "gardener", autoResponse = true, showGPSButton = false),
        Character(name = "Doctor", imageResId = R.drawable.doctor, nameForAPI = "doctor", autoResponse = true, showGPSButton = false),
        Character(name = "Chef", imageResId = R.drawable.chef, nameForAPI = "chef", autoResponse = true, showGPSButton = false),
        Character(name = "Book Worm", imageResId = R.drawable.bookworm, nameForAPI = "bookworm", autoResponse = true, showGPSButton = false),
        Character(name = "Psychology", imageResId = R.drawable.psychology, nameForAPI = "psychology", autoResponse = true, showGPSButton = false),
        Character(name = "Psychology Mars", imageResId = R.drawable.psychology_mars, nameForAPI = "psychology_mars", autoResponse = true, showGPSButton = false),
        Character(name = "Happiness Expert", imageResId = R.drawable.psychology_expert_happiness, nameForAPI = "psychology_expert_happiness", autoResponse = true, showGPSButton = false),
        Character(name = "Meditation Guru", imageResId = R.drawable.meditation, nameForAPI = "meditation", autoResponse = true, showGPSButton = false),
        Character(name = "Jokester", imageResId = R.drawable.jokester, nameForAPI = "jokester", autoResponse = true, showGPSButton = false),
        Character(name = "Teacher", imageResId = R.drawable.teacher, nameForAPI = "teacher", autoResponse = true, showGPSButton = false),
        Character(name = "Brainstorm", imageResId = R.drawable.brainstormer, nameForAPI = "brainstormer", autoResponse = true, showGPSButton = false),
        Character(name = "CEO", imageResId = R.drawable.ceo, nameForAPI = "ceo", autoResponse = true, showGPSButton = false),
        Character(name = "CTO", imageResId = R.drawable.cto, nameForAPI = "cto", autoResponse = true, showGPSButton = false),
        Character(name = "Business Expert", imageResId = R.drawable.business_expert, nameForAPI = "business_expert", autoResponse = true, showGPSButton = false),
        Character(name = "Sales Guru", imageResId = R.drawable.sales_expert, nameForAPI = "sales_expert", autoResponse = true, showGPSButton = false),
        Character(name = "Conscious AI", imageResId = R.drawable.conscious_ai, nameForAPI = "conscious_ai", autoResponse = true, showGPSButton = false),
        Character(name = "Rogue AI", imageResId = R.drawable.rogue_ai, nameForAPI = "rogue_ai", autoResponse = true, showGPSButton = false),
        Character(name = "Story", imageResId = R.drawable.story_mode, nameForAPI = "story_mode", autoResponse = true, showGPSButton = false),
        Character(name = "Story AI", imageResId = R.drawable.story_ai, nameForAPI = "story_ai", autoResponse = true, showGPSButton = false),
        Character(name = "Story Rick", imageResId = R.drawable.story_rickmorty, nameForAPI = "story_rickmorty", autoResponse = true, showGPSButton = false),
        Character(name = "Story Rick AI", imageResId = R.drawable.story_rickmorty_ai, nameForAPI = "story_rickmorty_ai", autoResponse = true, showGPSButton = false),
        Character(name = "Samantha", imageResId = R.drawable.samantha, nameForAPI = "samantha", autoResponse = true, showGPSButton = false),
        Character(name = "Samantha v2", imageResId = R.drawable.samantha2, nameForAPI = "samantha2", autoResponse = true, showGPSButton = false),
        Character(name = "Elon", imageResId = R.drawable.elon, nameForAPI = "elon", autoResponse = true, showGPSButton = false),
        Character(name = "Yuval Noah Harari", imageResId = R.drawable.yuval, nameForAPI = "yuval", autoResponse = true, showGPSButton = false),
        Character(name = "Naval", imageResId = R.drawable.naval, nameForAPI = "naval", autoResponse = true, showGPSButton = false),
        Character(name = "Shaan Puri", imageResId = R.drawable.shaan, nameForAPI = "shaan", autoResponse = true, showGPSButton = false),
        Character(name = "Sir David", imageResId = R.drawable.david, nameForAPI = "david", autoResponse = true, showGPSButton = false),
        Character(name = "Rick Sanchez", imageResId = R.drawable.rick, nameForAPI = "rick", autoResponse = true, showGPSButton = false),
    )

    // Function to programmatically set up character cards
    fun setupCharacterCards(binding: ActivityMainBinding, onCharacterSelected: (String) -> Unit) {
        // default setting before user selection
        ConfigurationManager.setTextAICharacter("assistant")
        displayCharacterCards(binding, characters, onCharacterSelected)
    }

    // when restoring data from DB - using just resId will not be enough (because for example if you reinstall app - IDs will be different)
    // so we will use this method to lookup directly images based on name of character
    fun getCharacterImageResId(characterNameForAPI: String): Int {
        return characters.find { it.nameForAPI == characterNameForAPI }?.imageResId ?: R.drawable.ai_avatar_placeholder
    }

    // return whole character (used in chatHelper)
    fun getCharacterByNameForAPI(characterName: String): Character? {
        return characters.find { it.nameForAPI == characterName }
    }

    private fun displayCharacterCards(binding: ActivityMainBinding, characters: List<Character>, onCharacterSelected: (String) -> Unit) {
        binding.characterScrollView.removeAllViews()
        for (character in characters) {
            val cardBinding = CharacterCardBinding.inflate(LayoutInflater.from(context))
            cardBinding.characterName.text = character.name
            cardBinding.characterImage.setImageResource(character.imageResId)
            cardBinding.root.setOnClickListener {
                Toast.makeText(context, "${character.name} selected", Toast.LENGTH_SHORT).show()
                binding.characterHorizontalMainScrollView.visibility = View.GONE
                ConfigurationManager.setTextAICharacter(character.nameForAPI)
                // show GPS button, but only for specific characters
                // first reset in case other character is chosen
                binding.btnShareLocation.visibility = View.GONE
                if (character.showGPSButton) {
                    binding.btnShareLocation.visibility = View.VISIBLE
                }
                onCharacterSelected(character.name)
            }
            val layoutParams = LinearLayout.LayoutParams(
                context.resources.getDimensionPixelSize(R.dimen.character_card_width),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = context.resources.getDimensionPixelSize(R.dimen.character_card_margin)
            }
            cardBinding.root.layoutParams = layoutParams
            binding.characterScrollView.addView(cardBinding.root)
        }
    }

    fun filterCharacters(binding: ActivityMainBinding, query: String, onCharacterSelected: (String) -> Unit) {
        val filteredCharacters = characters.filter { it.name.contains(query, ignoreCase = true) }
        displayCharacterCards(binding, filteredCharacters, onCharacterSelected)
    }

}
