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
import com.cleanphone.freespace.utility.base.command.log
import com.cleanphone.freespace.utility.base.command.safeClick
import com.cleanphone.freespace.utility.base.config.AppConfig

import kotlin.to


class NotificationDialog(context: Context) : Dialog(context) {
    private var v: View? = null

    init {
        var inflater = LayoutInflater.from(context)
        v = inflater.inflate(R.layout.dialog_notlification, null)
        setContentView(v!!)
        setCancelable(true)
        setCanceledOnTouchOutside(false)
        val params = this.window!!.attributes
        params.width = dp2px(context, 320)
        params.height =  WindowManager.LayoutParams.WRAP_CONTENT
        window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        this.window!!.attributes = params
        this.window!!.setGravity(Gravity.CENTER)
        v!!.findViewById<Button>(R.id.bt_setting).safeClick {
            dismiss()
            if(mOnClickListener!=null){
                mOnClickListener!!.onAllow()
            }
        }
        v!!.findViewById<TextView>(R.id.tv_not_allow).safeClick {
            dismiss()
        }
    }
    fun toShow() {
        log("notification_popup")
        if (v == null) {
            return
        }
        if (!isShowing) {
            show()
        }
    }

    fun release() {
        if (isShowing) {
            dismiss()
        }
        mOnClickListener=null
        v = null
    }

    override fun dismiss() {
        super.dismiss()
    }


    private var mOnClickListener: onClickListener? = null

    interface onClickListener {
        fun onAllow() //void
    }
    fun setmOnClickListener(mOnClickListener: onClickListener) {
        this.mOnClickListener = mOnClickListener
    }
}