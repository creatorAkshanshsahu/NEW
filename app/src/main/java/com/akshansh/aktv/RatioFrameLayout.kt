package com.akshansh.aktv

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** FrameLayout whose height = width * heightRatio, so grid cards scale to any screen. */
class RatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var heightRatio = 1f

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.RatioFrameLayout)
        heightRatio = a.getFloat(R.styleable.RatioFrameLayout_heightRatio, 1f)
        a.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.makeMeasureSpec((w * heightRatio).toInt(), MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, h)
    }
}
