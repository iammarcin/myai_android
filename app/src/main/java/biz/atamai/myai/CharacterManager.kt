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
    data class Character(val name: String, val imageResId: Int, val nameForAPI: String)

    private val characters = listOf(
        Character("Assistant", R.drawable.brainstorm_assistant, "Assistant"),
        Character("Personal coach", R.drawable.brainstorm_gym_instructor, "GymInstructor"),
        Character("Gardener", R.drawable.brainstorm_gardener, "Gardener"),
        Character("Doctor", R.drawable.brainstorm_doctor, "Doctor"),
        Character("Chef", R.drawable.brainstorm_chef, "Chef"),
        Character("Book Worm", R.drawable.brainstorm_book_worm, "BookWorm"),
        Character("Psychology ", R.drawable.brainstorm_psychology, "Psychology"),
        Character("Psychology Mars", R.drawable.brainstorm_psychology_mars, "PsychologyMars"),
        Character("Happiness Expert", R.drawable.brainstorm_psychology_expert_happiness, "PsychologyExpertHappiness"),
        Character("Meditation Guru", R.drawable.brainstorm_meditation, "Meditation"),
        Character("Jokester", R.drawable.brainstorm_jokester, "Jokester"),
        Character("Teacher", R.drawable.brainstorm_teacher, "Teacher"),
        Character("Brainstorm", R.drawable.brainstorm_brainstormer, "Brainstormer"),
        Character("CEO", R.drawable.brainstorm_ceo, "CEO"),
        Character("CTO", R.drawable.brainstorm_cto, "CTO"),
        Character("Business Expert", R.drawable.brainstorm_business_expert, "BusinessExpert"),
        Character("Sales Guru", R.drawable.brainstorm_sales, "Sales"),
        Character("Conscious AI", R.drawable.brainstorm_conscious_ai, "ConsciousAI"),
        Character("Rogue AI", R.drawable.brainstorm_rogue_ai, "RogueAI"),
        Character("Elon", R.drawable.brainstorm_elon, "Elon"),
        Character("Yuval Noah Harari", R.drawable.brainstorm_yuval, "Yuval"),
        Character("Naval", R.drawable.brainstorm_naval, "Naval"),
        Character("Shaan Puri", R.drawable.brainstorm_shaan, "Shaan"),
        Character("Sir David", R.drawable.brainstorm_david, "David"),
        Character("Rick Sanchez", R.drawable.brainstorm_rick, "Rick"),
    )

    // Function to programmatically set up character cards
    fun setupCharacterCards(binding: ActivityMainBinding, onCharacterSelected: (String) -> Unit) {
        // default setting before user selection
        ConfigurationManager.setTextAICharacter("Assistant")
        displayCharacterCards(binding, characters, onCharacterSelected)
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
