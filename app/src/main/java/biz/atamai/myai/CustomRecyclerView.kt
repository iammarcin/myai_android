package biz.atamai.myai

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView


// i don't like it but well.. what can i do
// this was created because i wanted to add onclick listener to recyclerview
// and it was getting warning
// Custom view `RecyclerView` has setOnTouchListener called on it but does not override performClick
// is used in main activity
//binding.editTextMessage.clearFocus()
// so if we are in focus with main edit text and we click on recycler view it will clear focus
// and set bottom view to original state
class CustomRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    override fun onTouchEvent(e: MotionEvent?): Boolean {
        performClick()
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
