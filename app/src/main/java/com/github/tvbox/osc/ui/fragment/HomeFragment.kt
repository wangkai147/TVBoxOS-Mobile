package com.github.tvbox.osc.ui.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.blankj.utilcode.util.ToastUtils
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.api.ApiConfig.LoadConfigCallback
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseVbFragment
import com.github.tvbox.osc.bean.AbsSortXml
import com.github.tvbox.osc.bean.MovieSort.SortData
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.databinding.FragmentHomeBinding
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.ui.activity.CollectActivity
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.activity.HistoryActivity
import com.github.tvbox.osc.ui.activity.MainActivity
import com.github.tvbox.osc.ui.activity.SubscriptionActivity
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter.SelectDialogInterface
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.ui.dialog.TipDialog
import com.github.tvbox.osc.util.DefaultConfig
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.V7GridLayoutManager

class HomeFragment : BaseVbFragment<FragmentHomeBinding>() {
    private lateinit var sourceViewModel: SourceViewModel
    private val fragments = ArrayList<Fragment>()
    private val mHandler = Handler(Looper.getMainLooper())

    /**
     * 顶部tabs分类集合,用于渲染tab页,每个tab对应fragment内的数据
     */
    private var mSortDataList: List<SortData> = ArrayList()
    private var dataInitOk = false
    private var jarInitOk = false


    override fun init() {
        ControlManager.get().startServer()

        //点击选择数据源
        mBinding.nameContainer.setOnClickListener {
            //TODO:拦截快速点击
            if (dataInitOk && jarInitOk) {
                showSiteSwitch()
            } else {
                ToastUtils.showShort("数据源未加载，长按刷新或切换订阅")
            }
        }

        //长按刷新
        mBinding.nameContainer.setOnLongClickListener {
            refreshHomeSources()
            true
        }

        mBinding.search.setOnClickListener {
            jumpActivity(
                FastSearchActivity::class.java
            )
        }
        mBinding.ivHistory.setOnClickListener {
            jumpActivity(
                HistoryActivity::class.java
            )
        }
        mBinding.ivCollect.setOnClickListener {
            jumpActivity(
                CollectActivity::class.java
            )
        }
        setLoadSir(mBinding.contentLayout)

        initViewModel()

        initData()
    }


    private fun initViewModel() {
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        sourceViewModel.sortResult.observe(this) { absXml: AbsSortXml? ->
            showSuccess()
            mSortDataList = if (absXml?.classes != null && absXml.classes.sortList != null) {
                DefaultConfig.adjustSort(
                    ApiConfig.get().homeSourceBean.key,
                    absXml.classes.sortList,
                    true
                )
            } else {
                DefaultConfig.adjustSort(ApiConfig.get().homeSourceBean.key, ArrayList(), true)
            }
            initViewPager(absXml)
        }
    }

    private fun initData() {
        val mainActivity = mActivity as MainActivity

        val home = ApiConfig.get().homeSourceBean
        if (home != null && home.name != null && home.name.isNotEmpty()) {
            mBinding.tvName.text = home.name
        }
        if (dataInitOk && jarInitOk) {
            showLoading()
            sourceViewModel.getSort(ApiConfig.get().homeSourceBean.key)
            return
        }
        showLoading()
        if (dataInitOk && !jarInitOk) {
            if (ApiConfig.get().spider.isNotEmpty()) {
                ApiConfig.get().loadJar(
                    mainActivity.useCacheConfig,
                    ApiConfig.get().spider,
                    object : LoadConfigCallback {
                        override fun success() {
                            jarInitOk = true
                            mHandler.postDelayed({
                                if (!mainActivity.useCacheConfig) ToastUtils.showShort("更新订阅成功")
                                initData()
                            }, 50)
                        }

                        override fun retry() {
                        }

                        override fun error(msg: String) {
                            jarInitOk = true
                            mHandler.post {
                                ToastUtils.showShort("更新订阅失败")
                                initData()
                            }
                        }
                    })
            }
            return
        }
        ApiConfig.get().loadConfig(mainActivity.useCacheConfig, object : LoadConfigCallback {
            var dialog: TipDialog? = null

            override fun retry() {
                mHandler.post { initData() }
            }

            override fun success() {
                dataInitOk = true
                if (ApiConfig.get().spider.isEmpty()) {
                    jarInitOk = true
                }
                mHandler.postDelayed({ initData() }, 50)
            }

            override fun error(msg: String) {
                if (msg.equals("-1", ignoreCase = true)) {
                    mHandler.post {
                        dataInitOk = true
                        jarInitOk = true
                        initData()
                    }
                    return
                }
                mHandler.post {
                    if (dialog == null) dialog = TipDialog(
                        requireActivity(),
                        msg,
                        "重试",
                        "取消",
                        object : TipDialog.OnListener {
                            override fun left() {
                                mHandler.post {
                                    initData()
                                    dialog!!.hide()
                                }
                            }

                            override fun right() {
                                dataInitOk = true
                                jarInitOk = true
                                mHandler.post {
                                    initData()
                                    dialog!!.hide()
                                }
                            }

                            override fun cancel() {
                                dataInitOk = true
                                jarInitOk = true
                                mHandler.post {
                                    initData()
                                    dialog!!.hide()
                                }
                            }

                            override fun onTitleClick() {
                                dialog!!.hide()
                                jumpActivity(SubscriptionActivity::class.java)
                            }
                        })
                    if (!dialog!!.isShowing) dialog!!.show()
                }
            }
        }, activity)
    }

    private fun initViewPager(absXml: AbsSortXml?) {
        if (mSortDataList.isNotEmpty()) {
            for (data in mSortDataList) {
                if (data.id == "my0") {
                    //tab是主页,添加主页fragment 根据设置项显示豆瓣热门/站点推荐(每个源不一样)/历史记录
                    if (Hawk.get(
                            HawkConfig.HOME_REC,
                            0
                        ) == 1 && absXml != null && absXml.videoList != null && absXml.videoList.isNotEmpty()
                    ) { //站点推荐
                        fragments.add(UserFragment.newInstance(absXml.videoList))
                    } else {
                        //豆瓣热门/历史记录
                        fragments.add(UserFragment.newInstance(null))
                    }
                } else {
                    //来自源的分类
                    fragments.add(GridFragment.newInstance(data))
                }
            }
            //禁用预加载
//            mBinding.mViewPager.setOffscreenPageLimit(ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT);
            mBinding.mViewPager.adapter = object : FragmentStateAdapter(this) {
                override fun createFragment(position: Int): Fragment {
                    return fragments[position]
                }

                override fun getItemCount(): Int {
                    return fragments.size
                }
            }
            val tabLayoutMediator = TabLayoutMediator(
                mBinding.tabLayout,
                mBinding.mViewPager
            ) { tab: TabLayout.Tab, position: Int ->
                //这里可以自定义TabView
                tab.setText(mSortDataList[position].name)
            }
            tabLayoutMediator.attach()
        }
    }

    /**
     * 提供给主页返回操作
     */
    fun scrollToFirstTab(): Boolean {
        if (mBinding.tabLayout.selectedTabPosition != 0) {
            mBinding.mViewPager.setCurrentItem(0, false)
            return true
        } else {
            return false
        }
    }

    val tabIndex: Int
        /**
         * 提供给主页返回操作
         */
        get() = mBinding.tabLayout.selectedTabPosition

    val allFragments: List<Fragment>
        /**
         * 提供给主页返回操作
         */
        get() = fragments


    override fun onPause() {
        super.onPause()
        mHandler.removeCallbacksAndMessages(null)
    }

    private fun showSiteSwitch() {
        val sites = ApiConfig.get().sourceBeanList
        if (sites.isNotEmpty()) {
            val dialog = SelectDialog<SourceBean>(requireActivity())
            val tvRecyclerView = dialog.findViewById<TvRecyclerView>(R.id.list)

            tvRecyclerView.layoutManager = V7GridLayoutManager(dialog.context, 2)

            dialog.setTip("请选择首页数据源")
            dialog.setAdapter(object : SelectDialogInterface<SourceBean> {
                override fun click(value: SourceBean, pos: Int) {
                    ApiConfig.get().setSourceBean(value)
                    refreshHomeSources()
                }

                override fun getDisplay(value: SourceBean): String {
                    return value.name
                }
            }, object : DiffUtil.ItemCallback<SourceBean>() {
                override fun areItemsTheSame(oldItem: SourceBean, newItem: SourceBean): Boolean {
                    return oldItem === newItem
                }

                @SuppressLint("DiffUtilEquals")
                override fun areContentsTheSame(oldItem: SourceBean, newItem: SourceBean): Boolean {
                    return oldItem.key == newItem.key
                }
            }, sites, sites.indexOf(ApiConfig.get().homeSourceBean))
            dialog.show()
        } else {
            ToastUtils.showLong("暂无可用数据源")
        }
    }

    private fun refreshHomeSources() {
        val intent = Intent(App.getInstance(), MainActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val bundle = Bundle()
        bundle.putBoolean("useCache", true)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        ControlManager.get().stopServer()
    }
}