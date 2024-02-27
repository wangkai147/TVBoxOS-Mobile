package com.github.tvbox.osc.ui.fragment

import android.content.Intent
import android.net.Uri
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseVbFragment
import com.github.tvbox.osc.databinding.FragmentMyBinding
import com.github.tvbox.osc.ui.activity.CollectActivity
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.HistoryActivity
import com.github.tvbox.osc.ui.activity.LiveActivity
import com.github.tvbox.osc.ui.activity.MovieFoldersActivity
import com.github.tvbox.osc.ui.activity.SettingActivity
import com.github.tvbox.osc.ui.activity.SubscriptionActivity
import com.github.tvbox.osc.ui.dialog.AboutDialog
import com.github.tvbox.osc.util.Utils
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.lxj.xpopup.XPopup

/**
 * @author pj567
 * @date :2021/3/9
 */
class MyFragment : BaseVbFragment<FragmentMyBinding>() {
    override fun init() {
        val version = "v${AppUtils.getAppVersionName()}"
        mBinding.tvVersion.text = version

        mBinding.addrPlay.setOnClickListener {
            XPopup.Builder(context).asInputConfirm(
                "播放", "", if (isPush(
                        ClipboardUtils.getText().toString()
                    )
                ) ClipboardUtils.getText() else "", "地址", { text: String ->
                    if (text.isNotEmpty()) {
                        val newIntent = Intent(mContext, DetailActivity::class.java)
                        newIntent.putExtra("id", text)
                        newIntent.putExtra("sourceKey", "push_agent")
                        newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(newIntent)
                    }
                }, null, R.layout.dialog_input
            ).show()
        }
        mBinding.tvLive.setOnClickListener {
            jumpActivity(
                LiveActivity::class.java
            )
        }

        mBinding.tvSetting.setOnClickListener {
            jumpActivity(
                SettingActivity::class.java
            )
        }

        mBinding.tvHistory.setOnClickListener {
            jumpActivity(
                HistoryActivity::class.java
            )
        }

        mBinding.tvFavorite.setOnClickListener {
            jumpActivity(
                CollectActivity::class.java
            )
        }

        mBinding.tvLocal.setOnClickListener {
            if (!XXPermissions.isGranted(mContext, Permission.MANAGE_EXTERNAL_STORAGE)) {
                showPermissionTipPopup()
            } else {
                jumpActivity(MovieFoldersActivity::class.java)
            }
        }

        mBinding.llSubscription.setOnClickListener {
            jumpActivity(
                SubscriptionActivity::class.java
            )
        }

        mBinding.llAbout.setOnClickListener {
            XPopup.Builder(mActivity).asCustom(AboutDialog(mActivity)).show()
        }
    }

    private fun showPermissionTipPopup() {
        XPopup.Builder(mActivity).isDarkTheme(Utils.isDarkTheme())
            .asConfirm("提示", "为了播放视频、音频等,我们需要访问您设备文件的读写权限") {
                permission
            }.show()
    }

    private val permission: Unit
        get() {
            XXPermissions.with(this).permission(Permission.MANAGE_EXTERNAL_STORAGE)
                .request(object : OnPermissionCallback {
                    override fun onGranted(permissions: List<String>, all: Boolean) {
                        if (all) {
                            jumpActivity(MovieFoldersActivity::class.java)
                        } else {
                            ToastUtils.showLong("部分权限未正常授予,请授权")
                        }
                    }

                    override fun onDenied(permissions: List<String>, never: Boolean) {
                        if (never) {
                            ToastUtils.showLong("读写文件权限被永久拒绝，请手动授权")
                            // 如果是被永久拒绝就跳转到应用权限系统设置页面
                            XXPermissions.startPermissionActivity(mActivity, permissions)
                        } else {
                            ToastUtils.showShort("获取权限失败")
                            showPermissionTipPopup()
                        }
                    }
                })
        }

    private fun isPush(text: String): Boolean {
        return text.isNotEmpty() && mutableListOf(
            "smb", "http", "https", "thunder", "magnet", "ed2k", "mitv", "jianpian"
        ).contains(
            Uri.parse(text).scheme
        )
    }
}