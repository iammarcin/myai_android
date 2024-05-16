// TopMenuHandler.kt
package biz.atamai.myai

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import biz.atamai.myai.databinding.ActivityMainBinding
import biz.atamai.myai.databinding.TopRightMenuDialogOptionsBinding

class TopMenuHandler(private val context: Context, private val inflater: LayoutInflater) {

    fun setupTopMenus(binding: ActivityMainBinding) {
        binding.menuLeft.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.menuRight.setOnClickListener { view ->
            showTopRightPopupMenu(view)
        }

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                // Define your menu item actions here
            }
            true
        }
    }

    private fun showTopRightPopupMenu(view: View) {
        val popupMenu = PopupMenu(context, view)
        popupMenu.menuInflater.inflate(R.menu.top_right_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.model -> {
                    Toast.makeText(context, "Model selected", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.mode -> {
                    Toast.makeText(context, "Mode selected", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.options -> {
                    showOptionsDialog()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showOptionsDialog() {
        val container = FrameLayout(context)
        container.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            //FrameLayout.LayoutParams.WRAP_CONTENT
            // set height to 40% of screen height
            (context.resources.displayMetrics.heightPixels * 0.4).toInt()
        )
        container.id = View.generateViewId()

        val dialog = AlertDialog.Builder(context)
            .setView(container)
            .create()

        fun loadFragment(layoutId: Int) {
            container.removeAllViews()
            val fragmentView = inflater.inflate(layoutId, container, false)
            fragmentView.id = View.generateViewId()
            container.addView(fragmentView)

            container.visibility = View.VISIBLE
            fragmentView.visibility = View.VISIBLE
        }

        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(16, 16, 16, 16)
        }

        val btnGeneral = TextView(context).apply {
            text = "GENERAL"
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))

            // make text bold
            setTypeface(null, Typeface.BOLD)

            setPadding(8, 8, 8, 8)
            setOnClickListener {
                Log.d("OptionsDialog", "General button clicked")
                loadFragment(R.layout.top_right_menu_fragment_general)
            }
        }

        val btnText = TextView(context).apply {
            text = "TEXT"
            setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
            setPadding(8, 8, 8, 8)
            setOnClickListener {
                Log.d("OptionsDialog", "Text button clicked")
                loadFragment(R.layout.top_right_menu_fragment_text)
            }
        }

        tabLayout.addView(btnGeneral)
        tabLayout.addView(btnText)

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(tabLayout)
            addView(container)
        }

        dialog.setView(dialogLayout)

        // Load the initial fragment
        loadFragment(R.layout.top_right_menu_fragment_general)

        dialog.show()

        // Set fixed size for dialog
        dialog.window?.setLayout(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
    }
}
