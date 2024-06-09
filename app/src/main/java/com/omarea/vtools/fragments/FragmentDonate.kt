package com.omarea.vtools.fragments

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.omarea.permissions.CheckRootStatus
import com.omarea.utils.AlipayDonate
import com.omarea.vtools.R
import kotlinx.android.synthetic.main.fragment_donate.*


class FragmentDonate : androidx.fragment.app.Fragment(), View.OnClickListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_donate, container, false)

    override fun onResume() {
        super.onResume()
        // activity!!.title = getString(R.string.menu_paypal)
        activity!!.title = getString(R.string.app_name)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        

        bindClickEvent(nav_share)
        bindClickEvent(nav_qq)
    }

    private fun bindClickEvent(view: View) {
        view.setOnClickListener(this)
        if (!CheckRootStatus.lastCheckResult && "root".equals(view.getTag())) {
            view.isEnabled = false
        }
    }

    override fun onClick(v: View?) {
        v?.run {
            if (!CheckRootStatus.lastCheckResult && "root".equals(getTag())) {
                Toast.makeText(context, "没有获得ROOT权限，不能使用本功能", Toast.LENGTH_SHORT).show()
                return
               }
        }
    }

    private fun tryOpenApp(packageName: String) {
        val pm = context!!.packageManager
        if (packageName.equals("com.omarea.gesture")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.setComponent(ComponentName("com.omarea.gesture", "com.omarea.gesture.SettingsActivity"))
                startActivity(intent)
                return
            } catch (ex: java.lang.Exception) {
            }
        } else if (packageName.equals("com.omarea.filter")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.setComponent(ComponentName("com.omarea.filter", "com.omarea.filter.SettingsActivity"))
                startActivity(intent)
                return
            } catch (ex: java.lang.Exception) {
            }
        }

        try {
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            }
        } catch (ex: java.lang.Exception) {
        }

        openUrl("http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=DnuSkhJ_x1lHKBxfi9GtbUijy43awAPW&authKey=RUtBy2exLLjSAM0ceIQQQP5yDusp91dAY%2BxTJDxxgvClkXBFDuFrL7XgzrFijIZi&noverify=0&group_code=813807778" )
    }

    private fun openUrl(link: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (ex: Exception) {
        }
    }
}
