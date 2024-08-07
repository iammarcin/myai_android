// TopMenuHandler.kt

package biz.atamai.myai

import android.app.Dialog
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import biz.atamai.myai.databinding.ActivityMainBinding
import biz.atamai.myai.databinding.TopRightPopupMenuLayoutBinding
import biz.atamai.myai.databinding.TopFavoritePopupMenuBinding
import biz.atamai.myai.databinding.TopFavoritePopupFavItemBinding
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

// class to store favorite chat data
data class FavoriteChat(val id: String, var chatName: String, var chatCharacter: String)

class TopMenuHandler(
    private val mainHandler: MainHandler,
    private val chatHelperHandler: ChatHelperHandler,
    private val onFetchChatSessions: () -> Unit, // function to fetch chat sessions (when menu appears)
    private val onSearchMessages: (String) -> Unit // function to search messages (when user submits search query)
) {

    private var chatAdapterHandler: ChatAdapterHandler? = null
    private var utilityTools: UtilityTools
    private var textModelName: String = mainHandler.getConfigurationManager().getTextModelName()
    // in menu options dialog there are buttons like AUDIO, GENERAL, etc - so this is about this button (later it will be bold)
    private var currentSelectedButton: TextView? = null

    init {
        utilityTools = UtilityTools(
            mainHandler = mainHandler
        )
    }

    fun setChatAdapterHandler(chatAdapterHandler: ChatAdapterHandler) {
        this.chatAdapterHandler = chatAdapterHandler
    }

    fun setupTopMenus(binding: ActivityMainBinding) {
        // search text edit on top of menu
        val topLeftMenuSearchEditText = binding.topLeftMenuNavigationView.findViewById<EditText>(R.id.topLeftMenuSearchEditText)
        binding.menuLeft.setOnClickListener {
            onFetchChatSessions()
            topLeftMenuSearchEditText.setText("")
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.menuRight.setOnClickListener { view ->
            showTopRightPopupWindow(view)
        }

        binding.menuFavorite.setOnClickListener { view ->
            showFavoriteChatsPopup(view)
        }

        binding.topLeftMenuNavigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                // Define your menu item actions here
            }
            true
        }

        // search messages when user submits query
        topLeftMenuSearchEditText.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (actionId == EditorInfo.IME_NULL && event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                val query = topLeftMenuSearchEditText.text.toString()
                onSearchMessages(query)
                true
            } else {
                false
            }
        }

        topLeftMenuSearchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                // this has to stay here - even empty
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // this has to stay here - even empty
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                // search when at least 3 characters
                if (query.isNotEmpty()) {
                    onSearchMessages(query)
                }
            }
        })



    }

    private fun showFavoriteChatsPopup(view: View) {
        val popupBindingFavMenu = TopFavoritePopupMenuBinding.inflate(mainHandler.mainLayoutInflaterInstance)
        val currentChatId = chatHelperHandler.getCurrentDBSessionID()
        val currentCharacter = mainHandler.getFullCharacterData(mainHandler.getCurrentAICharacter())

        // Define the width for the popup window
        val popupWidth = (mainHandler.context.resources.displayMetrics.density * 250).toInt()
        val popupWindow = PopupWindow(popupBindingFavMenu.root, popupWidth, LinearLayout.LayoutParams.WRAP_CONTENT, true)
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // get the list of favorite chats
        var favoriteChats = getFavoriteChats()
        val isCurrentChatFavorited = favoriteChats.any { it.id == currentChatId }

        // depending if chat is already favorited, show different icon and text and set different click listener
        popupBindingFavMenu.btnAddFavorite.apply {
            setImageResource(if (isCurrentChatFavorited) R.drawable.ic_favorite_enabled else R.drawable.ic_favorite_disabled)
        }
        popupBindingFavMenu.addFavoriteTextView.text = if (isCurrentChatFavorited) "Remove from Favorites" else "Add to Favorites"

        val addFavoriteClickListener = View.OnClickListener {
            if (isCurrentChatFavorited) {
                removeFavoriteChat(currentChatId!!)
            } else {
                if (currentChatId != "")
                    addChatToFavorites(FavoriteChat(currentChatId, mainHandler.getConfigurationManager().getTextCurrentSessionName(), currentCharacter.nameForAPI))
            }
            popupWindow.dismiss()
        }

        popupBindingFavMenu.btnAddFavorite.setOnClickListener(addFavoriteClickListener)
        popupBindingFavMenu.addFavoriteTextView.setOnClickListener(addFavoriteClickListener)

        // populate the list of favorite chats
        popupBindingFavMenu.favoritesContainer.removeAllViews()
        favoriteChats.forEach { chat ->
            val chatItemBinding = TopFavoritePopupFavItemBinding.inflate(mainHandler.mainLayoutInflaterInstance)
            chatItemBinding.chatNameTextView.text = chat.chatName
            val character = mainHandler.getFullCharacterData(chat.chatCharacter)
            chatItemBinding.chatCharacterImageView.setImageResource(character.imageResId)

            chatItemBinding.removeButton.setOnClickListener {
                removeFavoriteChat(chat.id)
                popupWindow.dismiss()
            }

            chatItemBinding.chatNameTextView.setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    mainHandler.getDatabaseHelper().sendDBRequest("db_get_user_session", mapOf("session_id" to chat.id))
                }
                popupWindow.dismiss()
            }

            popupBindingFavMenu.favoritesContainer.addView(chatItemBinding.root)
        }

        popupWindow.showAsDropDown(view)

    }

    private fun addChatToFavorites(chat: FavoriteChat) {
        val favoriteChats = mainHandler.getConfigurationManager().getFavoriteChats().toMutableList()
        if (!favoriteChats.contains(chat)) {
            favoriteChats.add(chat)
            mainHandler.getConfigurationManager().setFavoriteChats(favoriteChats)
        }
    }

    private fun getFavoriteChats(): List<FavoriteChat> {
        return mainHandler.getConfigurationManager().getFavoriteChats()
    }

    private fun removeFavoriteChat(chatId: String) {
        val favoriteChats = mainHandler.getConfigurationManager().getFavoriteChats().toMutableList()
        // remove by provided id
        favoriteChats.remove(favoriteChats.find { it.id == chatId })
        mainHandler.getConfigurationManager().setFavoriteChats(favoriteChats)
    }

    private fun showTopRightPopupWindow(view: View) {
        val popupBinding = TopRightPopupMenuLayoutBinding.inflate(mainHandler.mainLayoutInflaterInstance)

        // set static width
        val popupWidth = (mainHandler.context.resources.displayMetrics.density * 200).toInt()

        val popupWindow = PopupWindow(popupBinding.root, popupWidth, LinearLayout.LayoutParams.WRAP_CONTENT, true)
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val items = listOf(popupBinding.gpt4o, popupBinding.gpt4oMini, popupBinding.claude, popupBinding.llama3170b)

        // Update items to reflect the selected model
        items.forEach { item ->
            val selected = item.text == textModelName
            item.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            item.setCompoundDrawablesWithIntrinsicBounds(0, 0, if (selected) R.drawable.ic_electric_bolt else 0, 0)
        }

        popupBinding.gpt4o.setOnClickListener {
            handleModelSelection(popupBinding.gpt4o.text.toString())
            popupWindow.dismiss()
        }
        popupBinding.gpt4oMini.setOnClickListener {
            handleModelSelection(popupBinding.gpt4oMini.text.toString())
            popupWindow.dismiss()
        }

        popupBinding.claude.setOnClickListener {
            handleModelSelection(popupBinding.claude.text.toString())
            popupWindow.dismiss()
        }

        popupBinding.llama3170b.setOnClickListener {
            handleModelSelection(popupBinding.llama3170b.text.toString())
            popupWindow.dismiss()
        }

        popupBinding.options.setOnClickListener {
            showOptionsDialog()
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(view)
    }

    private fun handleModelSelection(model: String) {
        mainHandler.getConfigurationManager().setTextModelName(model)
        textModelName = model
        mainHandler.createToastMessage("$model selected")
    }

    private fun showOptionsDialog() {
        val dialog = Dialog(mainHandler.context)
        dialog.setContentView(createDialogView(dialog))

        dialog.window?.setLayout(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (mainHandler.context.resources.displayMetrics.heightPixels * 0.7).toInt()
        )

        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val params = dialog.window?.attributes
        params?.dimAmount = 0.9f
        dialog.window?.attributes = params

        dialog.show()
    }

    private fun createDialogView(dialog: Dialog): View {
        val container = FrameLayout(mainHandler.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            id = View.generateViewId()
        }

        fun loadFragment(layoutId: Int) {
            container.removeAllViews()
            val fragmentView = createFragmentView(layoutId)
            container.addView(fragmentView)
            container.visibility = View.VISIBLE
            fragmentView.visibility = View.VISIBLE
        }

        val tabLayout = LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(16, 16, 16, 16)
        }

        val categories = listOf(
            "GENERAL" to 1,
            "TEXT" to 2,
            "IMAGE" to 3,
            "TTS" to 4,
            "SPEECH" to 5,
        )

        val buttons = categories.map { (name, layoutId) ->
            createCategoryButton(name, layoutId) {
                loadFragment(layoutId)
                updateButtonStyles(it)
            }
        }

        buttons.forEach { tabLayout.addView(it) }

        val dialogLayout = LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabLayout)
            addView(container)
        }

        loadFragment(1)
        updateButtonStyles(buttons.first())

        return dialogLayout
    }

    private fun createFragmentView(layoutId: Int): View {
        return when (layoutId) {
            1 -> createGeneralFragmentView()
            2 -> createTextFragmentView()
            3 -> createImageFragmentView()
            4 -> createTTSFragmentView()
            5 -> createSpeechFragmentView()
            else -> View(mainHandler.context)
        }
    }

    private fun createGeneralFragmentView(): View {
        val linearLayout = LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createSwitchRow("Use Bluetooth", mainHandler.getConfigurationManager().getUseBluetooth()) { isChecked ->
                mainHandler.getConfigurationManager().setUseBluetooth(isChecked)
            })
            addView(createSwitchRow("Test Data", mainHandler.getConfigurationManager().getUseTestData()) { isChecked ->
                mainHandler.getConfigurationManager().setUseTestData(isChecked)
            })
            // add production mode setting
            addView(createSwitchRow("Production Mode", mainHandler.getConfigurationManager().getIsProdMode()) { isChecked ->
                mainHandler.getConfigurationManager().setIsProdMode(isChecked)
                mainHandler.getConfigurationManager().setURLForAPICalls()
            })
            addView(createSwitchRow("Use Watson for nonprod", mainHandler.getConfigurationManager().getAppModeUseWatson()) { isChecked ->
                mainHandler.getConfigurationManager().setAppModeUseWatson(isChecked)
                mainHandler.getConfigurationManager().setURLForAPICalls()
            })
            addView(createSwitchRow("Download audio files before playing", mainHandler.getConfigurationManager().getDownloadAudioFilesBeforePlaying()) { isChecked ->
                mainHandler.getConfigurationManager().setDownloadAudioFilesBeforePlaying(isChecked)
            })
            // token for connecting to backend API
            addView(createTextEditRow("API auth Token", mainHandler.getConfigurationManager().getAuthTokenForBackend(), isPassword = true) { value ->
                mainHandler.getConfigurationManager().setAuthTokenForBackend(value)
            })
        }

        return ScrollView(mainHandler.context).apply {
            addView(linearLayout)
        }
    }

    private fun createTextFragmentView(): View {
        val linearLayout = LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createSeekBarRow("Temperature", 1, 0.05f, mainHandler.getConfigurationManager().getTextTemperature()) { value ->
                mainHandler.getConfigurationManager().setTextTemperature(value)
            })
            addView(createSeekBarRow("Memory Size", 2000, 1f, mainHandler.getConfigurationManager().getTextMemorySize().toFloat()) { value ->
                mainHandler.getConfigurationManager().setTextMemorySize(value.toInt())
            })
            addView(createSeekBarRow("Attachments message count limit", 10, 1f, mainHandler.getConfigurationManager().getTextFileAttachedMessageLimit().toFloat()) { value ->
                mainHandler.getConfigurationManager().setTextFileAttachedMessageLimit(value.toInt())
            })
            addView(createSeekBarRow("Text size in UI", 30, 1f, mainHandler.getConfigurationManager().getTextSizeInUI().toFloat()) { value ->
                mainHandler.getConfigurationManager().setTextSizeInUI(value.toInt())
                chatAdapterHandler?.onTextSizeChanged(value.toInt())
            })
            addView(createSwitchRow("Streaming", mainHandler.getConfigurationManager().getIsStreamingEnabled()) { isChecked ->
                mainHandler.getConfigurationManager().setIsStreamingEnabled(isChecked)
            })
        }

        return ScrollView(mainHandler.context).apply {
            addView(linearLayout)
        }
    }

    private fun createImageFragmentView(): View {
        val linearLayout = LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createTextEditRow("Model", mainHandler.getConfigurationManager().getImageModelName(), isPassword = false, additionalText = "Possible values: dall-e-3", ) { value ->
                mainHandler.getConfigurationManager().setImageModelName(value)
            })

            addView(createSwitchRow("HD Quality", mainHandler.getConfigurationManager().getImageQualityHD()) { isChecked ->
                mainHandler.getConfigurationManager().setImageQualityHD(isChecked)
            })

            addView(createSwitchRow("Disable Openai revised prompt", mainHandler.getConfigurationManager().getImageDisableSafePrompt()) { isChecked ->
                mainHandler.getConfigurationManager().setImageDisableSafePrompt(isChecked)
            })

            addView(createTextLabelRow(""))
            addView(createTextLabelRow("Artgen mode"))

            addView(createSwitchRow("Show image prompt", mainHandler.getConfigurationManager().getImageArtgenShowPrompt()) { isChecked ->
                mainHandler.getConfigurationManager().setImageArtgenShowPrompt(isChecked)
            })

            addView(createSwitchRow("Auto generate image", mainHandler.getConfigurationManager().getImageAutoGenerateImage()) { isChecked ->
                mainHandler.getConfigurationManager().setImageAutoGenerateImage(isChecked)
            })
        }

        return ScrollView(mainHandler.context).apply {
            addView(linearLayout)
        }
    }

    private fun createTTSFragmentView(): View {
        val linearLayout = LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createTextEditRow("Model", mainHandler.getConfigurationManager().getTTSModelName(), isPassword = false, additionalText = "ElevenLabs: english, multi, turbo\nOpenAI: tts-1, tts-1-hd ", ) { value ->
                mainHandler.getConfigurationManager().setTTSModelName(value)
            })

            addView(createTextEditRow("Voice", mainHandler.getConfigurationManager().getTTSVoice(), isPassword = false, additionalText = "ElevenLabs: alice, bill, brian, eric, jessica, sarah, will\nOpenAI: alloy, echo, fable, onyx, nova, shimmer", ) { value ->
                mainHandler.getConfigurationManager().setTTSVoice(value)
            })

            addView(createSwitchRow("Streaming", mainHandler.getConfigurationManager().getTTSStreaming()) { isChecked ->
                mainHandler.getConfigurationManager().setTTSStreaming(isChecked)
            })

            addView(createSwitchRow("Auto trigger TTS upon AI response", mainHandler.getConfigurationManager().getTTSAutoExecute()) { isChecked ->
                mainHandler.getConfigurationManager().setTTSAutoExecute(isChecked)
            })

            addView(createTextLabelRow(""))
            addView(createTextLabelRow("Elevenlabs"))

            addView(createRefreshDataRow("Get billing data") { textView ->
                CoroutineScope(Dispatchers.IO).launch {
                    textView.post {
                        textView.text = "Fetching..."
                    }
                    utilityTools.getElevenLabsBilling(
                        { response ->
                            textView.post {
                                textView.text = response
                            }
                        },
                        { error ->
                            println("Error getting billing status: $error")
                            textView.post {
                                textView.text = "Problem getting billing data"
                            }
                        }
                    )
                }
            })

            addView(createSeekBarRow("Stability", 1, 0.05f, mainHandler.getConfigurationManager().getTTSStability()) { value ->
                mainHandler.getConfigurationManager().setTTSStability(value)
            })
            addView(createSeekBarRow("Similarity", 1, 0.05f, mainHandler.getConfigurationManager().getTTSSimilarity()) { value ->
                mainHandler.getConfigurationManager().setTTSSimilarity(value)
            })

            addView(createSeekBarRow("Style exaggeration", 1, 0.05f, mainHandler.getConfigurationManager().getTTSStyleExaggeration()) { value ->
                mainHandler.getConfigurationManager().setTTSStyleExaggeration(value)
            })

            addView(createTextLabelRow(""))
            addView(createTextLabelRow("OpenAI"))

            // speed
            addView(createSeekBarRow("Speed", 4, 0.05f, mainHandler.getConfigurationManager().getTTSSpeed()) { value ->
                mainHandler.getConfigurationManager().setTTSSpeed(value)
            })
        }

        return ScrollView(mainHandler.context).apply {
            addView(linearLayout)
        }
    }

    private fun createSpeechFragmentView(): View {
        val linearLayout = LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createSwitchRow("Use Groq", mainHandler.getConfigurationManager().getSpeechUseGroq()) { isChecked ->
                mainHandler.getConfigurationManager().setSpeechUseGroq(isChecked)
            })

            addView(createTextEditRow("Language", mainHandler.getConfigurationManager().getSpeechLanguage()) { value ->
                // make sure we use only lowercase
                mainHandler.getConfigurationManager().setSpeechLanguage(value.lowercase(Locale.getDefault()))
            })
            addView(createSeekBarRow("Temperature", 1, 0.05f, mainHandler.getConfigurationManager().getSpeechTemperature()) { value ->
                mainHandler.getConfigurationManager().setSpeechTemperature(value)
            })
        }

        return ScrollView(mainHandler.context).apply {
            addView(linearLayout)
        }
    }

    private fun createSwitchRow(label: String, initialValue: Boolean, onValueChanged: (Boolean) -> Unit): RelativeLayout {
        return RelativeLayout(mainHandler.context).apply {
            val switch = SwitchCompat(context).apply {
                id = View.generateViewId()
                setPadding(0, 0, 16, 0)
                thumbDrawable.setTint(ContextCompat.getColor(context, R.color.white))
                trackDrawable.setTint(ContextCompat.getColor(context, R.color.colorPrimary))
                isChecked = initialValue
                setOnCheckedChangeListener { _, isChecked ->
                    onValueChanged(isChecked)
                }
            }

            val textView = TextView(context).apply {
                text = label
                setTextColor(ContextCompat.getColor(context, R.color.white))
                textSize = 16f
            }

            addView(switch, RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.CENTER_VERTICAL)
            })

            addView(textView, RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.END_OF, switch.id)
                addRule(RelativeLayout.CENTER_VERTICAL)
            })
        }
    }

    // for normal text or password / token
    private fun createTextEditRow(label: String, initialValue: String, isPassword: Boolean = false, additionalText: String? = null, onValueChanged: (String) -> Unit, ): LinearLayout {
        return LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)

            val textView = TextView(context).apply {
                text = label
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            val editText = EditText(context).apply {
                setText(initialValue)
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.white))
                setPadding(8, 0, 0, 0)
                inputType = if (isPassword) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT
                }
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        onValueChanged(s.toString())
                    }
                })
            }

            addView(textView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            addView(editText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            additionalText?.let {
                val additionalTextView = TextView(context).apply {
                    text = it
                    textSize = 12f // smaller font size
                    setTextColor(ContextCompat.getColor(context, R.color.top_left_menu_date_text_color)) // light gray color
                    setPadding(8, 4, 0, 0)
                }
                addView(additionalTextView, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
        }
    }

    private fun createTextLabelRow(label: String): LinearLayout {
        return LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)

            val textView = TextView(context).apply {
                text = label
                textSize = 16f // larger font size for subsection labels
                setTypeface(null, Typeface.BOLD) // make it bold
                setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            addView(textView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun createSeekBarRow(label: String, max: Int, step: Float, initialValue: Float, onValueChanged: (Float) -> Unit): LinearLayout {
        return LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL

            val textView = TextView(context).apply {
                text = label
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            val valueTextView = TextView(context).apply {
                text = initialValue.toString()
                setPadding(8, 0, 0, 0)
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            val seekBar = SeekBar(context).apply {
                this.max = (max / step).toInt()
                progress = (initialValue / step).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        val value = progress * step
                        valueTextView.text = value.toString()
                        onValueChanged(value)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                })
            }

            addView(textView)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(seekBar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(valueTextView)
            })
        }
    }

    private fun createRefreshDataRow(label: String, onRefresh: (TextView) -> Unit): LinearLayout {
        return LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)

            val textView = TextView(context).apply {
                text = label
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.white))
                setPadding(24, 32, 0, 0)
            }

            val imageButton = ImageButton(context).apply {
                setImageResource(R.drawable.ic_refresh)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    onRefresh(textView)
                }
            }

            addView(imageButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            addView(textView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }


    // category button on top of the dialog
    private fun createCategoryButton(name: String, layoutId: Int, onClick: (TextView) -> Unit): TextView {
        return TextView(mainHandler.context).apply {
            text = name
            setTextColor(ContextCompat.getColor(context, R.color.white))
            textSize = 15f
            setPadding(8, 8, 25, 8)
            setOnClickListener {
                onClick(this)
            }
        }
    }

    // make active button bold
    private fun updateButtonStyles(selected: TextView) {
        // Reset the style of the previously selected button
        currentSelectedButton?.setTypeface(null, Typeface.NORMAL)

        // Set the style of the currently selected button
        selected.setTypeface(null, Typeface.BOLD)

        // Update the reference to the currently selected button
        currentSelectedButton = selected
    }
}
