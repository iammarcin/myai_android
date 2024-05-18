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
    data class Character(val name: String, val imageResId: Int)

    // Function to programmatically set up character cards
    fun setupCharacterCards(binding: ActivityMainBinding) {

        // default setting before user selection
        ConfigurationManager.setTextAICharacter("Assistant")

        val characters = listOf(
            Character("Elon", R.drawable.brainstorm_elon),
            Character("Conscious AI", R.drawable.brainstorm_conscious_ai),
            Character("Doctor", R.drawable.brainstorm_doctor),
            Character("Chef", R.drawable.brainstorm_chef),
        )

        for (character in characters) {
            val cardBinding = CharacterCardBinding.inflate(LayoutInflater.from(context))
            cardBinding.characterName.text = character.name
            cardBinding.characterImage.setImageResource(character.imageResId)
            cardBinding.root.setOnClickListener {
                Toast.makeText(context, "${character.name} selected", Toast.LENGTH_SHORT).show()
                binding.characterHorizontalMainScrollView.visibility = View.GONE

                ConfigurationManager.setTextAICharacter(character.name)
            }

            // Set fixed width for the card
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
}
