// TopMenuHandler.kt

package biz.atamai.myai

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import biz.atamai.myai.databinding.ActivityMainBinding
import biz.atamai.myai.databinding.TopRightPopupMenuLayoutBinding
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo

class TopMenuHandler(
    private val mainHandler: MainHandler,
    private val onFetchChatSessions: () -> Unit, // function to fetch chat sessions (when menu appears)
    private val onSearchMessages: (String) -> Unit // function to search messages (when user submits search query)
) {

    private var textModelName: String = ConfigurationManager.getTextModelName()
    // in menu options dialog there are buttons like AUDIO, GENERAL, etc - so this is about this button (later it will be bold)
    private var currentSelectedButton: TextView? = null

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

        binding.topLeftMenuNavigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                // Define your menu item actions here
            }
            true
        }


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

    private fun showTopRightPopupWindow(view: View) {
        val popupBinding = TopRightPopupMenuLayoutBinding.inflate(mainHandler.mainLayoutInflaterInstance)

        // set static width
        val popupWidth = (mainHandler.context.resources.displayMetrics.density * 200).toInt()

        val popupWindow = PopupWindow(popupBinding.root, popupWidth, LinearLayout.LayoutParams.WRAP_CONTENT, true)
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val items = listOf(popupBinding.gpt4o, popupBinding.gpt4, popupBinding.gpt35, popupBinding.llama370b)

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
        popupBinding.gpt4.setOnClickListener {
            handleModelSelection(popupBinding.gpt4.text.toString())
            popupWindow.dismiss()
        }
        popupBinding.gpt35.setOnClickListener {
            handleModelSelection(popupBinding.gpt35.text.toString())
            popupWindow.dismiss()
        }

        popupBinding.llama370b.setOnClickListener {
            handleModelSelection(popupBinding.llama370b.text.toString())
            popupWindow.dismiss()
        }

        popupBinding.options.setOnClickListener {
            showOptionsDialog()
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(view)
    }

    private fun handleModelSelection(model: String) {
        ConfigurationManager.setTextModelName(model)
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
        return LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createSwitchRow("Use Bluetooth", ConfigurationManager.getUseBluetooth()) { isChecked ->
                ConfigurationManager.setUseBluetooth(isChecked)
            })
            addView(createSwitchRow("Test Data", ConfigurationManager.getUseTestData()) { isChecked ->
                ConfigurationManager.setUseTestData(isChecked)
            })
            // add production mode setting
            addView(createSwitchRow("Production Mode", ConfigurationManager.getIsProdMode()) { isChecked ->
                ConfigurationManager.setIsProdMode(isChecked)
                ConfigurationManager.setURLForAPICalls()
            })
            addView(createSwitchRow("Use Watson for nonprod", ConfigurationManager.getAppModeUseWatson()) { isChecked ->
                ConfigurationManager.setAppModeUseWatson(isChecked)
                ConfigurationManager.setURLForAPICalls()
            })
            addView(createSwitchRow("Download audio files before playing", ConfigurationManager.getDownloadAudioFilesBeforePlaying()) { isChecked ->
                ConfigurationManager.setDownloadAudioFilesBeforePlaying(isChecked)
            })
            // token for connecting to backend API
            addView(createTextEditRow("API auth Token", ConfigurationManager.getAuthTokenForBackend(), isPassword = true) { value ->
                ConfigurationManager.setAuthTokenForBackend(value)
            })
        }
    }

    private fun createTextFragmentView(): View {
        return LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createSeekBarRow("Temperature", 1, 0.05f, ConfigurationManager.getTextTemperature()) { value ->
                ConfigurationManager.setTextTemperature(value)
            })
            addView(createSeekBarRow("Memory Size", 2000, 1f, ConfigurationManager.getTextMemorySize().toFloat()) { value ->
                ConfigurationManager.setTextMemorySize(value.toInt())
            })
            addView(createSwitchRow("Streaming", ConfigurationManager.getIsStreamingEnabled()) { isChecked ->
                ConfigurationManager.setIsStreamingEnabled(isChecked)
            })
        }
    }

    private fun createImageFragmentView(): View {
        return LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createTextEditRow("Model", ConfigurationManager.getImageModelName(), isPassword = false, additionalText = "Possible values: dall-e-3", ) { value ->
                ConfigurationManager.setImageModelName(value)
            })

            addView(createSwitchRow("HD Quality", ConfigurationManager.getImageQualityHD()) { isChecked ->
                ConfigurationManager.setImageQualityHD(isChecked)
            })

            addView(createSwitchRow("Disable Openai revised prompt", ConfigurationManager.getImageDisableSafePrompt()) { isChecked ->
                ConfigurationManager.setImageDisableSafePrompt(isChecked)
            })

            addView(createTextLabelRow(""))
            addView(createTextLabelRow("Artgen mode"))

            addView(createSwitchRow("Show image prompt", ConfigurationManager.getImageArtgenShowPrompt()) { isChecked ->
                ConfigurationManager.setImageArtgenShowPrompt(isChecked)
            })

            addView(createSwitchRow("Auto generate image", ConfigurationManager.getImageAutoGenerateImage()) { isChecked ->
                ConfigurationManager.setImageAutoGenerateImage(isChecked)
            })
        }
    }

    private fun createTTSFragmentView(): View {
        return LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createTextEditRow("Model", ConfigurationManager.getTTSModelName(), isPassword = false, additionalText = "Possible values: tts-1, tts-1-hd", ) { value ->
                ConfigurationManager.setTTSModelName(value)
            })

            addView(createSwitchRow("Streaming", ConfigurationManager.getTTSStreaming()) { isChecked ->
                ConfigurationManager.setTTSStreaming(isChecked)
            })

            addView(createSwitchRow("Auto trigger TTS upon AI response", ConfigurationManager.getTTSAutoExecute()) { isChecked ->
                ConfigurationManager.setTTSAutoExecute(isChecked)
            })

            addView(createTextLabelRow(""))
            addView(createTextLabelRow("OpenAI"))

            addView(createTextEditRow("Voice", ConfigurationManager.getTTSVoice(), isPassword = false, additionalText = "Possible values: alloy, echo, fable, onyx, nova, and shimmer", ) { value ->
                ConfigurationManager.setTTSVoice(value)
            })

            // speed
            addView(createSeekBarRow("Speed", 4, 0.05f, ConfigurationManager.getTTSSpeed()) { value ->
                ConfigurationManager.setTTSSpeed(value)
            })

            addView(createTextLabelRow(""))
            addView(createTextLabelRow("Elevenlabs"))

            addView(createSeekBarRow("Stability", 1, 0.05f, ConfigurationManager.getTTSStability()) { value ->
                ConfigurationManager.setTTSStability(value)
            })
            addView(createSeekBarRow("Similarity", 1, 0.05f, ConfigurationManager.getTTSSimilarity()) { value ->
                ConfigurationManager.setTTSSimilarity(value)
            })
        }
    }

    private fun createSpeechFragmentView(): View {
        return LinearLayout(mainHandler.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createTextEditRow("Language", ConfigurationManager.getSpeechLanguage()) { value ->
                ConfigurationManager.setSpeechLanguage(value)
            })
            addView(createSeekBarRow("Temperature", 1, 0.05f, ConfigurationManager.getSpeechTemperature()) { value ->
                ConfigurationManager.setSpeechTemperature(value)
            })
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
