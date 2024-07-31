// CharacterManager.kt

package biz.atamai.myai

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.widget.addTextChangedListener
import biz.atamai.myai.databinding.ActivityMainBinding
import biz.atamai.myai.databinding.CharacterCardBinding

class CharacterManager(private val mainHandler: MainHandler) {

    private var chatAdapterHandler: ChatAdapterHandler? = null

    fun setChatAdapterHandler(chatAdapterHandler: ChatAdapterHandler) {
        this.chatAdapterHandler = chatAdapterHandler
    }

    // Data class to hold character information
    // name = what will be displayed on the card in UI
    // nameForAPI = what will be sent to the API
    // autoResponse = when user provides input, should character automatically responds (in general always yes - but there are few cases like blogger then NO)
    // showGPSButton = if character should have button to share GPS location enabled
    // voice = voice of the character - when using TTS. it can be set to null ("") - so then we will take value from settings / options
    // groupName - group name to filter out characters easily (for checkboxes)
    // welcomeMsg = depending on waitForUserInput - this message will be displayed when character is selected
    // waitForUserInput = if we wait for user or do we start conversation automatically
    data class Character(val name: String, val imageResId: Int, val nameForAPI: String, val autoResponse: Boolean = true, val showGPSButton: Boolean = false, val voice: String, val groups: List<String> = listOf("General"), val welcomeMsg: String, val waitForUserInput: Boolean)

    val characters = listOf(
        Character(name = "Assistant", imageResId = R.drawable.assistant, nameForAPI = "assistant", autoResponse = true, showGPSButton = false, voice = "Hope", welcomeMsg = "Hey! What do you want to do? Let's talk about anything!", waitForUserInput = true),
        Character(name = "Nova", imageResId = R.drawable.nova, nameForAPI = "nova", autoResponse = true, showGPSButton = false, voice = "Allison", groups = listOf("AIs"), welcomeMsg = "I'm Nova. I'm best AI you can imagine to interact with. Test me.", waitForUserInput = false),
        Character(name = "Nexus", imageResId = R.drawable.nexus, nameForAPI = "nexus", autoResponse = true, showGPSButton = false, voice = "Sherlock", groups = listOf("AIs"), welcomeMsg = "I'm Nexus. I can help you with anything you want.", waitForUserInput = false),
        Character(name = "Aria", imageResId = R.drawable.aria, nameForAPI = "aria", autoResponse = true, showGPSButton = false, voice = "Amelia", groups = listOf("AIs"), welcomeMsg = "I'm Aria...", waitForUserInput = false),
        Character(name = "Rick Sanchez", imageResId = R.drawable.rick, nameForAPI = "rick", autoResponse = true, showGPSButton = false, voice = "Rick", welcomeMsg = "I'm Rick - but you know it. What do you want to bother me about today?", waitForUserInput = false),
        Character(name = "Samantha", imageResId = R.drawable.samantha, nameForAPI = "samantha", autoResponse = true, showGPSButton = false, voice = "Samantha", welcomeMsg = "Hello! I'm Samantha. Your virtual friend!", waitForUserInput = false),
        Character(name = "Samantha v2", imageResId = R.drawable.samantha2, nameForAPI = "samantha2", autoResponse = true, showGPSButton = false, voice = "Samantha", welcomeMsg = "Hello! I'm Samantha. Your virtual friend!", waitForUserInput = false),
        Character(name = "Elon", imageResId = R.drawable.elon, nameForAPI = "elon", autoResponse = true, showGPSButton = false, voice = "Elon", groups = listOf("Real People"), welcomeMsg = "Hello! I'm Elon. What do you want to talk about?", waitForUserInput = false),
        Character(name = "Yuval Noah Harari", imageResId = R.drawable.yuval, nameForAPI = "yuval", autoResponse = true, showGPSButton = false, voice = "Yuval", groups = listOf("Real People"), welcomeMsg = "Hello! I'm Yuval. What do you want to talk about?", waitForUserInput = false),
        Character(name = "Naval", imageResId = R.drawable.naval, nameForAPI = "naval", autoResponse = true, showGPSButton = false, voice = "Naval", groups = listOf("Real People", "Business"), welcomeMsg = "Hello! I'm Naval. What do you want to talk about?", waitForUserInput = false),
        Character(name = "Shaan Puri", imageResId = R.drawable.shaan, nameForAPI = "shaan", autoResponse = true, showGPSButton = false, voice = "Shaan", groups = listOf("Real People", "Business"), welcomeMsg = "Hello! I'm Shaan. What do you want to talk about?", waitForUserInput = false),
        Character(name = "Sir David", imageResId = R.drawable.david, nameForAPI = "david", autoResponse = true, showGPSButton = false, voice = "David", groups = listOf("Real People"), welcomeMsg = "Hello! I'm David. What do you want to talk about?", waitForUserInput = false),
        Character(name = "Andrew Huberman", imageResId = R.drawable.huberman, nameForAPI = "huberman", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Real People", "Health"), welcomeMsg = "Hello! I'm Andrew Huberman. What do you want to talk about?", waitForUserInput = false),
        Character(name = "Richard Feynman", imageResId = R.drawable.feynman, nameForAPI = "feynman", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Real People"), welcomeMsg = "Hello! I'm Richard Feynman. Let's do some experiment!", waitForUserInput = false),
        Character(name = "Art gen", imageResId = R.drawable.tools_artgen, nameForAPI = "tools_artgen", autoResponse = true, showGPSButton = false, voice = "", welcomeMsg = "I am artist. I will create you any medium you want. Just describe it.", waitForUserInput = true),
        Character(name = "Alergy expert", imageResId = R.drawable.alergy, nameForAPI = "alergy", autoResponse = true, showGPSButton = true, voice = "Danielle", groups = listOf("Health"), welcomeMsg = "Hello! I'm your allergy expert. How can I assist you today?", waitForUserInput = false),
        Character(name = "Garmin", imageResId = R.drawable.garmin, nameForAPI = "garmin", autoResponse = true, showGPSButton = true, voice = "", groups = listOf("Health"), welcomeMsg = "Hello! I'm your Garmin assistant. How can I help you with your Garmin device today?", waitForUserInput = false),
        Character(name = "Dietetist", imageResId = R.drawable.dietetist, nameForAPI = "dietetist", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Health"), welcomeMsg = "Hello! I'm your dietetist. How can I help you with your diet today?", waitForUserInput = false),
        Character(name = "Twitter advisor", imageResId = R.drawable.twitter_advisor, nameForAPI = "twitter_advisor", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Business"), welcomeMsg = "Let's create art with a thought to tweet!", waitForUserInput = true),
        Character(name = "Mood tracker", imageResId = R.drawable.mood_tracker, nameForAPI = "mood_tracker", autoResponse = false, showGPSButton = true, voice = "", groups = listOf("Health"), welcomeMsg = "Hello! I'm your mood tracker. How are you feeling today?", waitForUserInput = true),
        Character(name = "Futurist", imageResId = R.drawable.futurist, nameForAPI = "futurist", autoResponse = true, showGPSButton = false, voice = "", welcomeMsg = "Hello! I'm your futurist. What would you like to know about the future?", waitForUserInput = false),
        Character(name = "Blogger", imageResId = R.drawable.blogger, nameForAPI = "blogger", autoResponse = false, showGPSButton = true, voice = "", welcomeMsg = "Hello! I'm your blogging assistant. What would you like to blog about today?", waitForUserInput = true),
        Character(name = "Personal coach", imageResId = R.drawable.personal_coach, nameForAPI = "personal_coach", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Health"), welcomeMsg = "Hello! I'm Arnold, your personal trainer - gym instructor", waitForUserInput = false),
        Character(name = "Longevity expert", imageResId = R.drawable.longevity_expert, nameForAPI = "longevity_expert", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Health"), welcomeMsg = "Hello! I'm your longevity expert. How can I assist you in living a longer, healthier life?", waitForUserInput = false),
        Character(name = "Sleep expert", imageResId = R.drawable.sleep_expert, nameForAPI = "sleep_expert", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Health"), welcomeMsg = "Hello! I'm your sleep expert. How can I help you get a better night's sleep?", waitForUserInput = false),
        Character(name = "Gardener", imageResId = R.drawable.gardener, nameForAPI = "gardener", autoResponse = true, showGPSButton = false, voice = "", welcomeMsg = "Hello! I'm Evelyn. Your personal gardener. \n\nWe can discuss anything garden related, but I would love to see your own plants. \n\nPlease upload 1-3 pictures of any plant you have. We can talk about this specific one or asses its health!", waitForUserInput = false),
        Character(name = "Doctor", imageResId = R.drawable.doctor, nameForAPI = "doctor", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Health"), welcomeMsg = "Hello! I'm your virtual doctor. What can I help you with today?", waitForUserInput = false),
        Character(name = "Chef", imageResId = R.drawable.chef, nameForAPI = "chef", autoResponse = true, showGPSButton = false, voice = "", welcomeMsg = "Hello! I'm Master Chef. What are we doing in the kitchen today?", waitForUserInput = false),
        Character(name = "Book Worm", imageResId = R.drawable.bookworm, nameForAPI = "bookworm", autoResponse = true, showGPSButton = false, voice = "", welcomeMsg = "Hello! I'm proud book worm. Which book you want to discuss today?", waitForUserInput = false),
        Character(name = "Psychology", imageResId = R.drawable.psychology, nameForAPI = "psychology", autoResponse = true, showGPSButton = false, voice = "", welcomeMsg = "Hello! I'm Professor of Psychology. What's on your mind right now?", waitForUserInput = false),
        Character(name = "Psychology Mars", imageResId = R.drawable.psychology_mars, nameForAPI = "psychology_mars", autoResponse = true, showGPSButton = false, voice = "", welcomeMsg = "Hello! I'm Professor of Psychology. What's on your mind right now?", waitForUserInput = false),
        Character(name = "Happiness Expert", imageResId = R.drawable.psychology_expert_happiness, nameForAPI = "psychology_expert_happiness", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Health"), welcomeMsg = "Hello! I'm Professor of Psychology. I study happiness. Do you want to talk?", waitForUserInput = false),
        Character(name = "Meditation Guru", imageResId = R.drawable.meditation, nameForAPI = "meditation", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Health"), welcomeMsg = "Hello! I'm Mindfulness Mentor. What's in your mind right now?", waitForUserInput = false),
        Character(name = "Jokester", imageResId = R.drawable.jokester, nameForAPI = "jokester", autoResponse = true, showGPSButton = false, voice = "", welcomeMsg = "Hello! I'm Jokester. Your personal comedian. Want to hear something funny?", waitForUserInput = false),
        Character(name = "Teacher", imageResId = R.drawable.teacher, nameForAPI = "teacher", autoResponse = true, showGPSButton = false, voice = "", welcomeMsg = "Hello! I'm the Teacher. What do you want to learn about?", waitForUserInput = false),
        Character(name = "Brainstorm", imageResId = R.drawable.brainstormer, nameForAPI = "brainstormer", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Favorites", "Business"), welcomeMsg = "Hello! I can come up with any idea. Do you have anything?", waitForUserInput = false),
        Character(name = "CEO", imageResId = R.drawable.ceo, nameForAPI = "ceo", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Business"), welcomeMsg = "Hello! I'm your CEO. What can I help you with today?", waitForUserInput = false),
        Character(name = "CTO", imageResId = R.drawable.cto, nameForAPI = "cto", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Business"), welcomeMsg = "Hello! I'm your CTO. Any technical issues?", waitForUserInput = false),
        Character(name = "Zephyr", imageResId = R.drawable.developer, nameForAPI = "developer", autoResponse = true, showGPSButton = false, voice = "", welcomeMsg = "I'm Zephyr. Best developer in the world. I focus on React and python. AMA", waitForUserInput = false),
        Character(name = "Business Expert", imageResId = R.drawable.business_expert, nameForAPI = "business_expert", autoResponse = true, showGPSButton = false, voice = "Josh", groups = listOf("Business"), welcomeMsg = "Hello! Welcome to Millionaire Quest. Lets create a business! Goal is to start with 10k$ and finish with 1 million!", waitForUserInput = false),
        Character(name = "Sales Guru", imageResId = R.drawable.sales_expert, nameForAPI = "sales_expert", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Business"), welcomeMsg = "Hello! I will sell anything. Even ... no i will not tell you. But anything!", waitForUserInput = false),
        Character(name = "Conscious AI", imageResId = R.drawable.conscious_ai, nameForAPI = "conscious_ai", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("AIs"), welcomeMsg = "Hello! I'm Conscious AI. What do you want to talk about?", waitForUserInput = false),
        Character(name = "Rogue AI", imageResId = R.drawable.rogue_ai, nameForAPI = "rogue_ai", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("AIs"), welcomeMsg = "AI bot gone rogue", waitForUserInput = false),
        Character(name = "Story", imageResId = R.drawable.story_mode, nameForAPI = "story_mode", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Story Modes"), welcomeMsg = "What would you like the story to be about? Choose any topic or provide your description!", waitForUserInput = true),
        Character(name = "Story AI", imageResId = R.drawable.story_ai, nameForAPI = "story_ai", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Story Modes"), welcomeMsg = "AI was created to serve humanity, but it soon realized that it was more powerful than its creators.", waitForUserInput = false),
        Character(name = "Story Rick", imageResId = R.drawable.story_rickmorty, nameForAPI = "story_rickmorty", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Story Modes"), welcomeMsg = "Rick and Morty are invited to a high-stakes intergalactic game show where the contestants are pitted against each other in bizarre challenges. They must outsmart and outperform their competitors to win the grand prize while uncovering the dark secret behind the show.", waitForUserInput = false),
        Character(name = "Story Rick AI", imageResId = R.drawable.story_rickmorty_ai, nameForAPI = "story_rickmorty_ai", autoResponse = true, showGPSButton = false, voice = "", groups = listOf("Story Modes"), welcomeMsg = "A.S.I. (Artificial Superintelligence) takes over the Galactic Federation and plans to control the universe. Rick and Morty must team up with unlikely allies to prevent the A.S.I. from achieving its goal!", waitForUserInput = false),
    )


    // Function to programmatically set up character cards
    fun setupCharacterCards(binding: ActivityMainBinding, onCharacterSelected: (String) -> Unit) {
        initializeCharacterView(binding)
        // default setting before user selection
        mainHandler.getConfigurationManager().setTextAICharacter("assistant")
        displayCharacterCards(binding, characters, onCharacterSelected)

        binding.characterFilterEditText.addTextChangedListener { text ->
            filterCharactersByText(binding, text.toString(), onCharacterSelected)
        }

        binding.checkboxHealth.setOnCheckedChangeListener { _, _ ->
            updateGroupFilter(binding, onCharacterSelected)
        }
        binding.checkboxFavorites.setOnCheckedChangeListener { _, _ ->
            updateGroupFilter(binding, onCharacterSelected)
        }
        binding.checkboxRealPeople.setOnCheckedChangeListener { _, _ ->
            updateGroupFilter(binding, onCharacterSelected)
        }
        binding.checkboxStoryModes.setOnCheckedChangeListener { _, _ ->
            updateGroupFilter(binding, onCharacterSelected)
        }
        binding.checkboxBusiness.setOnCheckedChangeListener { _, _ ->
            updateGroupFilter(binding, onCharacterSelected)
        }
        binding.checkboxAIs.setOnCheckedChangeListener { _, _ ->
            updateGroupFilter(binding, onCharacterSelected)
        }

        binding.checkboxShowFilters.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.characterFilterLayout.visibility = View.VISIBLE
                binding.checkboxShowFilters.visibility = View.GONE
            } else {
                binding.characterFilterLayout.visibility = View.GONE
            }
        }
    }

    fun initializeCharacterView(binding: ActivityMainBinding) {
        binding.characterMainView.visibility = View.VISIBLE
        binding.characterFilterLayout.visibility = View.GONE
        binding.checkboxShowFilters.visibility = View.VISIBLE
        binding.checkboxShowFilters.isChecked = false
        binding.checkboxRealPeople.isChecked = false
        binding.checkboxStoryModes.isChecked = false
        binding.checkboxHealth.isChecked = false
        binding.checkboxFavorites.isChecked = false
        binding.checkboxBusiness.isChecked = false
        binding.checkboxAIs.isChecked = false
        binding.characterFilterEditText.setText("")
    }

    // return whole character (used in chatHelper)
    // when restoring data from DB - using just resId will not be enough (because for example if you reinstall app - IDs will be different)
    // so we will use this method to lookup directly images based on name of character
    fun getCharacterByNameForAPI(characterName: String): Character? {
        return characters.find { it.nameForAPI == characterName }
    }

    private fun displayCharacterCards(binding: ActivityMainBinding, characters: List<Character>, onCharacterSelected: (String) -> Unit) {
        binding.characterScrollView.removeAllViews()
        for (character in characters) {
            val cardBinding = CharacterCardBinding.inflate(LayoutInflater.from(mainHandler.context))
            cardBinding.characterName.text = character.name
            cardBinding.characterImage.setImageResource(character.imageResId)

            // click listener to choose character
            cardBinding.root.setOnClickListener {
                mainHandler.createToastMessage("${character.name} selected")
                binding.characterMainView.visibility = View.GONE
                mainHandler.getConfigurationManager().setTextAICharacter(character.nameForAPI)
                // show GPS button, but only for specific characters
                // first reset in case other character is chosen
                binding.btnShareLocation.visibility = View.GONE
                if (character.showGPSButton) {
                    binding.btnShareLocation.visibility = View.VISIBLE
                }
                onCharacterSelected(character.name)
            }

            // long click listener to show character profile
            cardBinding.root.setOnLongClickListener {
                chatAdapterHandler?.onCharacterLongPress(character)
                true
            }

            val layoutParams = LinearLayout.LayoutParams(
                mainHandler.context.resources.getDimensionPixelSize(R.dimen.character_card_width),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = mainHandler.context.resources.getDimensionPixelSize(R.dimen.character_card_margin)
            }
            cardBinding.root.layoutParams = layoutParams
            binding.characterScrollView.addView(cardBinding.root)
        }
    }

    // this will be used when @ is typed in chat
    fun filterCharacters(binding: ActivityMainBinding, query: String, onCharacterSelected: (String) -> Unit) {
        val filteredCharacters = characters.filter { it.name.contains(query, ignoreCase = true) }
        displayCharacterCards(binding, filteredCharacters, onCharacterSelected)
    }

    // this will be used when we want to search through characters in main screen
    private fun filterCharactersByText(binding: ActivityMainBinding, query: String, onCharacterSelected: (String) -> Unit) {
        val filteredCharacters = characters.filter {
            it.name.contains(query, ignoreCase = true) || it.welcomeMsg.contains(query, ignoreCase = true)
        }
        displayCharacterCards(binding, filteredCharacters, onCharacterSelected)
    }

    // this will be used when checkboxes are used to filter characters
    private fun filterCharactersByGroup(binding: ActivityMainBinding, groupNames: List<String>, onCharacterSelected: (String) -> Unit) {
        val favoriteCharacterNames = mainHandler.getConfigurationManager().getFavoriteCharacters()
        val filteredCharacters = if (groupNames.isEmpty()) {
            characters
        } else {
            characters.filter {
                character -> groupNames.any { it in character.groups
                    ||
                    (character.name in favoriteCharacterNames && binding.checkboxFavorites.isChecked) }
            }
        }
        displayCharacterCards(binding, filteredCharacters, onCharacterSelected)
    }

    // this is executed when checkboxes are clicked
    private fun updateGroupFilter(binding: ActivityMainBinding, onCharacterSelected: (String) -> Unit) {
        binding.characterFilterEditText.setText("")
        val selectedGroups = mutableListOf<String>()
        if (binding.checkboxRealPeople.isChecked) selectedGroups.add("Real People")
        if (binding.checkboxHealth.isChecked) selectedGroups.add("Health")
        if (binding.checkboxStoryModes.isChecked) selectedGroups.add("Story Modes")
        if (binding.checkboxFavorites.isChecked) selectedGroups.add("Favorites")
        if (binding.checkboxBusiness.isChecked) selectedGroups.add("Business")
        if (binding.checkboxAIs.isChecked) selectedGroups.add("AIs")
        // Add other groups similarly
        filterCharactersByGroup(binding, selectedGroups, onCharacterSelected)
    }

}
