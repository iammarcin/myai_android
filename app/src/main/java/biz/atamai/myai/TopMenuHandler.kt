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
import android.text.TextWatcher

class TopMenuHandler(private val context: Context, private val inflater: LayoutInflater) {

    private var textModelName: String = ConfigurationManager.getTextModelName()

    fun setupTopMenus(binding: ActivityMainBinding) {
        binding.menuLeft.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.menuRight.setOnClickListener { view ->
            showTopRightPopupWindow(view)
        }

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                // Define your menu item actions here
            }
            true
        }


    }

    private fun showTopRightPopupWindow(view: View) {
        val popupBinding = TopRightPopupMenuLayoutBinding.inflate(inflater)

        // set static width
        val popupWidth = (context.resources.displayMetrics.density * 200).toInt()

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
        ConfigurationManager.setString("text_model_name", model)
        textModelName = model
        Toast.makeText(context, "$model selected", Toast.LENGTH_SHORT).show()
    }

    private fun showOptionsDialog() {
        val dialog = Dialog(context)
        dialog.setContentView(createDialogView(dialog))

        dialog.window?.setLayout(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.heightPixels * 0.4).toInt()
        )

        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        val params = dialog.window?.attributes
        params?.dimAmount = 0.9f
        dialog.window?.attributes = params

        dialog.show()
    }

    private fun createDialogView(dialog: Dialog): View {
        val container = FrameLayout(context).apply {
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

        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(16, 16, 16, 16)
        }

        val categories = listOf(
            "GENERAL" to 1,
            "TEXT" to 2,
            "AUDIO" to 3,
            "SPEECH" to 4,
        )

        val buttons = categories.map { (name, layoutId) ->
            createCategoryButton(name, layoutId) {
                loadFragment(layoutId)
                updateButtonStyles(it)
            }
        }

        buttons.forEach { tabLayout.addView(it) }

        val dialogLayout = LinearLayout(context).apply {
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
            3 -> createAudioFragmentView()
            4 -> createSpeechFragmentView()
            else -> View(context)
        }
    }

    private fun createGeneralFragmentView(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createSwitchRow("Use Bluetooth", ConfigurationManager.getUseBluetooth()) { isChecked ->
                ConfigurationManager.setBoolean("general_use_bluetooth", isChecked)
            })
            addView(createSwitchRow("Test Data", ConfigurationManager.getUseTestData()) { isChecked ->
                ConfigurationManager.setBoolean("general_test_data", isChecked)
            })
        }
    }

    private fun createTextFragmentView(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createSeekBarRow("Temperature", 1, 0.05f, ConfigurationManager.getTextTemperature()) { value ->
                ConfigurationManager.setFloat("text_temperature", value)
            })
            addView(createSeekBarRow("Memory Size", 2000, 1f, ConfigurationManager.getTextMemorySize().toFloat()) { value ->
                ConfigurationManager.setInt("text_memory_size", value.toInt())
            })
            addView(createSwitchRow("Streaming", ConfigurationManager.getIsStreamingEnabled()) { isChecked ->
                ConfigurationManager.setBoolean("text_streaming", isChecked)
            })
        }
    }

    private fun createAudioFragmentView(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createSeekBarRow("Stability", 1, 0.05f, ConfigurationManager.getAudioStability()) { value ->
                ConfigurationManager.setFloat("audio_stability", value)
            })
            addView(createSeekBarRow("Similarity", 1, 0.05f, ConfigurationManager.getAudioSimilarity()) { value ->
                ConfigurationManager.setFloat("audio_similarity", value)
            })
        }
    }

    private fun createSpeechFragmentView(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createTextEditRow("Language", ConfigurationManager.getSpeechLanguage()) { newValue ->
                ConfigurationManager.setString("speech_language", newValue)
            })
            addView(createSeekBarRow("Temperature", 1, 0.05f, ConfigurationManager.getSpeechTemperature()) { value ->
                ConfigurationManager.setFloat("speech_temperature", value)
            })
        }
    }

    private fun createSwitchRow(label: String, initialValue: Boolean, onValueChanged: (Boolean) -> Unit): RelativeLayout {
        return RelativeLayout(context).apply {
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


    private fun createTextEditRow(label: String, initialValue: String, onValueChanged: (String) -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
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
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        onValueChanged(s.toString())
                    }
                })
            }

            addView(textView, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f
            ))

            addView(editText, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f
            ))
        }
    }


    private fun createSeekBarRow(label: String, max: Int, step: Float, initialValue: Float, onValueChanged: (Float) -> Unit): LinearLayout {
        return LinearLayout(context).apply {
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

    private fun createCategoryButton(name: String, layoutId: Int, onClick: (TextView) -> Unit): TextView {
        return TextView(context).apply {
            text = name
            setTextColor(ContextCompat.getColor(context, R.color.white))
            textSize = 18f
            setPadding(8, 8, 25, 8)
            setOnClickListener {
                onClick(this)
            }
        }
    }

    private fun updateButtonStyles(selected: TextView) {
        selected.setTypeface(null, Typeface.BOLD)
    }
}