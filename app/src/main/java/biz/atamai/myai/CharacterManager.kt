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
        Character(name = "Assistant", imageResId = R.drawable.brainstorm_assistant, nameForAPI = "Assistant", autoResponse = true, showGPSButton = false),
        Character(name = "Art gen", imageResId = R.drawable.tools_artgen, nameForAPI = "Artgen", autoResponse = true, showGPSButton = false),
        Character(name = "Alergy", imageResId = R.drawable.brainstorm_alergy, nameForAPI = "Alergy", autoResponse = true, showGPSButton = true),
        Character(name = "Garmin", imageResId = R.drawable.brainstorm_garmin_health, nameForAPI = "Garmin", autoResponse = true, showGPSButton = true),
        Character(name = "Researcher", imageResId = R.drawable.brainstorm_research_assistant, nameForAPI = "Researcher", autoResponse = true, showGPSButton = true),
        Character(name = "Calories", imageResId = R.drawable.brainstorm_calories, nameForAPI = "Calories", autoResponse = true, showGPSButton = false),
        Character(name = "Blogger", imageResId = R.drawable.brainstorm_blogger, nameForAPI = "Blogger", autoResponse = false, showGPSButton = true),
        Character(name = "Personal coach", imageResId = R.drawable.brainstorm_gym_instructor, nameForAPI = "PersonalCoach", autoResponse = true, showGPSButton = false),
        Character(name = "Gardener", imageResId = R.drawable.brainstorm_gardener, nameForAPI = "Gardener", autoResponse = true, showGPSButton = false),
        Character(name = "Doctor", imageResId = R.drawable.brainstorm_doctor, nameForAPI = "Doctor", autoResponse = true, showGPSButton = false),
        Character(name = "Chef", imageResId = R.drawable.brainstorm_chef, nameForAPI = "Chef", autoResponse = true, showGPSButton = false),
        Character(name = "Book Worm", imageResId = R.drawable.brainstorm_book_worm, nameForAPI = "BookWorm", autoResponse = true, showGPSButton = false),
        Character(name = "Psychology", imageResId = R.drawable.brainstorm_psychology, nameForAPI = "Psychology", autoResponse = true, showGPSButton = false),
        Character(name = "Psychology Mars", imageResId = R.drawable.brainstorm_psychology_mars, nameForAPI = "PsychologyMars", autoResponse = true, showGPSButton = false),
        Character(name = "Happiness Expert", imageResId = R.drawable.brainstorm_psychology_expert_happiness, nameForAPI = "PsychologyExpertHappiness", autoResponse = true, showGPSButton = false),
        Character(name = "Meditation Guru", imageResId = R.drawable.brainstorm_meditation, nameForAPI = "Meditation", autoResponse = true, showGPSButton = false),
        Character(name = "Jokester", imageResId = R.drawable.brainstorm_jokester, nameForAPI = "Jokester", autoResponse = true, showGPSButton = false),
        Character(name = "Teacher", imageResId = R.drawable.brainstorm_teacher, nameForAPI = "Teacher", autoResponse = true, showGPSButton = false),
        Character(name = "Brainstorm", imageResId = R.drawable.brainstorm_brainstormer, nameForAPI = "Brainstormer", autoResponse = true, showGPSButton = false),
        Character(name = "CEO", imageResId = R.drawable.brainstorm_ceo, nameForAPI = "CEO", autoResponse = true, showGPSButton = false),
        Character(name = "CTO", imageResId = R.drawable.brainstorm_cto, nameForAPI = "CTO", autoResponse = true, showGPSButton = false),
        Character(name = "Business Expert", imageResId = R.drawable.brainstorm_business_expert, nameForAPI = "BusinessExpert", autoResponse = true, showGPSButton = false),
        Character(name = "Sales Guru", imageResId = R.drawable.brainstorm_sales, nameForAPI = "Sales", autoResponse = true, showGPSButton = false),
        Character(name = "Conscious AI", imageResId = R.drawable.brainstorm_conscious_ai, nameForAPI = "ConsciousAI", autoResponse = true, showGPSButton = false),
        Character(name = "Rogue AI", imageResId = R.drawable.brainstorm_rogue_ai, nameForAPI = "RogueAI", autoResponse = true, showGPSButton = false),
        Character(name = "Story", imageResId = R.drawable.story_random, nameForAPI = "StoryUser", autoResponse = true, showGPSButton = false),
        Character(name = "Story AI", imageResId = R.drawable.story_mode, nameForAPI = "StoryAIConscious", autoResponse = true, showGPSButton = false),
        Character(name = "Story Rick", imageResId = R.drawable.story_rickmorty, nameForAPI = "RickMorty", autoResponse = true, showGPSButton = false),
        Character(name = "Story Rick AI", imageResId = R.drawable.story_rickmorty_ai, nameForAPI = "RickMortyAI", autoResponse = true, showGPSButton = false),
        Character(name = "Samantha", imageResId = R.drawable.brainstorm_samantha, nameForAPI = "Samantha", autoResponse = true, showGPSButton = false),
        Character(name = "Samantha v2", imageResId = R.drawable.brainstorm_samantha3, nameForAPI = "Samantha2", autoResponse = true, showGPSButton = false),
        Character(name = "Elon", imageResId = R.drawable.brainstorm_elon, nameForAPI = "Elon", autoResponse = true, showGPSButton = false),
        Character(name = "Yuval Noah Harari", imageResId = R.drawable.brainstorm_yuval, nameForAPI = "Yuval", autoResponse = true, showGPSButton = false),
        Character(name = "Naval", imageResId = R.drawable.brainstorm_naval, nameForAPI = "Naval", autoResponse = true, showGPSButton = false),
        Character(name = "Shaan Puri", imageResId = R.drawable.brainstorm_shaan, nameForAPI = "Shaan", autoResponse = true, showGPSButton = false),
        Character(name = "Sir David", imageResId = R.drawable.brainstorm_david, nameForAPI = "David", autoResponse = true, showGPSButton = false),
        Character(name = "Rick Sanchez", imageResId = R.drawable.brainstorm_rick, nameForAPI = "Rick", autoResponse = true, showGPSButton = false),
        Character(name = "TLDR", imageResId = R.drawable.tools_tldr, nameForAPI = "Tldr", autoResponse = true, showGPSButton = false)
    )

    // Function to programmatically set up character cards
    fun setupCharacterCards(binding: ActivityMainBinding, onCharacterSelected: (String) -> Unit) {
        // default setting before user selection
        ConfigurationManager.setTextAICharacter("Assistant")
        displayCharacterCards(binding, characters, onCharacterSelected)
    }

    // when restoring data from DB - using just resId will not be enough (because for example if you reinstall app - IDs will be different)
    // so we will use this method to lookup directly images based on name of character
    fun getCharacterImageResId(characterNameForAPI: String): Int {
        return characters.find { it.nameForAPI == characterNameForAPI }?.imageResId ?: R.drawable.ai_avatar_placeholder
    }

    // return whole character (used in chatHelper)
    fun getCharacterByName(characterName: String): Character? {
        return characters.find { it.name == characterName }
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
