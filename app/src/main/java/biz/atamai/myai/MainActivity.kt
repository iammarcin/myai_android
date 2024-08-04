// MainActivity.kt

package biz.atamai.myai

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import biz.atamai.myai.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
        // load standard UI for dynamic fragment
        loadFragment(MainActivityChatStandard())

        // set status bar color (above app -where clock is)
        window.statusBarColor = ContextCompat.getColor(this, R.color.popupmenu_background)
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.dynamicFragmentContainer, fragment)
            .commit()
    }

}
