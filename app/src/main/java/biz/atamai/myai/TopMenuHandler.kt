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

import android.graphics.Color
import android.graphics.drawable.ColorDrawable

class TopMenuHandler(private val context: Context, private val inflater: LayoutInflater) {

    private var selectedModel: String = "GPT-4o" // default selection

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
        val popupView = inflater.inflate(R.layout.top_right_popup_menu_layout, null)
        val popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val gpt4oItem = popupView.findViewById<TextView>(R.id.gpt4o)
        val gpt4Item = popupView.findViewById<TextView>(R.id.gpt4)
        val gpt35Item = popupView.findViewById<TextView>(R.id.gpt35)
        val optionsItem = popupView.findViewById<TextView>(R.id.options)

        val items = listOf(gpt4oItem, gpt4Item, gpt35Item)

        // Update items to reflect the selected model
        items.forEach { item ->
            if (item.text == selectedModel) {
                item.setTypeface(null, Typeface.BOLD)
                item.setBackgroundColor(ContextCompat.getColor(context, R.color.white))
            } else {
                item.setTypeface(null, Typeface.NORMAL)
                item.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        gpt4oItem.setOnClickListener {
            handleModelSelection(gpt4oItem.text.toString())
            popupWindow.dismiss()
        }
        gpt4Item.setOnClickListener {
            handleModelSelection(gpt4Item.text.toString())
            popupWindow.dismiss()
        }
        gpt35Item.setOnClickListener {
            handleModelSelection(gpt35Item.text.toString())
            popupWindow.dismiss()
        }
        optionsItem.setOnClickListener {
            showOptionsDialog()
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(view)
    }

    private fun handleModelSelection(model: String) {
        selectedModel = model
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

            addView(createSwitchRow("Use Bluetooth"))
            addView(createSwitchRow("Test Data"))
        }
    }

    private fun createTextFragmentView(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createSeekBarRow("Temperature", 1, 0.05f))
            addView(createSeekBarRow("Memory Size", 2000, 1f))
            addView(createSwitchRow("Streaming"))
        }
    }

    private fun createAudioFragmentView(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createSeekBarRow("Stability", 1, 0.1f))
            addView(createSeekBarRow("Similarity", 1, 0.1f))
        }
    }

    private fun createSpeechFragmentView(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            addView(createTextEditRow("Language", "en"))
            addView(createSeekBarRow("Temperature", 1, 0.05f))
        }
    }

    private fun createSwitchRow(label: String): RelativeLayout {
        return RelativeLayout(context).apply {
            val switch = SwitchCompat(context).apply {
                id = View.generateViewId()
                setPadding(0, 0, 16, 0)
                thumbDrawable.setTint(ContextCompat.getColor(context, R.color.white))
                trackDrawable.setTint(ContextCompat.getColor(context, R.color.colorPrimary))
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

    private fun createTextEditRow(label: String, defaultText: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)

            val textView = TextView(context).apply {
                text = label
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            val editText = EditText(context).apply {
                setText(defaultText)
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.white))
                setPadding(8, 0, 0, 0)
            }

            addView(textView, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.3f
            ))

            addView(editText, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f
            ))
        }
    }

    private fun createSeekBarRow(label: String, max: Int, step: Float): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            val textView = TextView(context).apply {
                text = label
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            val valueTextView = TextView(context).apply {
                text = "0.0"
                setPadding(8, 0, 0, 0)
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            val seekBar = SeekBar(context).apply {
                this.max = (max / step).toInt()
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        val value = progress * step
                        valueTextView.text = value.toString()
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
