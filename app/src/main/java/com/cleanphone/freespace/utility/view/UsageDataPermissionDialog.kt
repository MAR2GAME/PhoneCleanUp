package com.cleanphone.freespace.utility.view

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.cleanphone.freespace.utility.R
import com.cleanphone.freespace.utility.base.command.dp2px
import com.cleanphone.freespace.utility.base.command.safeClick


class UsageDataPermissionDialog (context: Context) : Dialog(context) {

    private var rootView: View? = null
    private var onClickListener: OnClickListener? = null

    init {
        initializeDialog()
        setupClickListeners()
    }

    private fun initializeDialog() {
        val inflater = LayoutInflater.from(context)
        rootView = inflater.inflate(R.layout.dialog_request_usage, null)
        setContentView(rootView!!)
        setupDialogProperties()
        setupWindowProperties()
    }

    private fun setupDialogProperties() {
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    private fun setupWindowProperties() {
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setGravity(Gravity.CENTER)
            val params = attributes.apply {
                width = dp2px(context, 320)
                height = WindowManager.LayoutParams.WRAP_CONTENT
            }
            attributes = params
        }
    }


    private fun setupClickListeners() {
        rootView?.apply {
            findViewById<Button>(R.id.bt_allow).safeClick {
                handleAllowClick()
            }
        }
    }

    private fun handleAllowClick() {
        dismiss()
        onClickListener?.onAllow()
    }

    fun showDialog() {
        if (rootView != null && !isShowing) {
            show()
        }
    }
    fun release() {
        if (isShowing) {
            dismiss()
        }
        onClickListener = null
        rootView = null
    }
    fun setOnClickListener(listener: OnClickListener) {
        this.onClickListener = listener
    }
    interface OnClickListener {
        fun onAllow()
    }
}