package com.pandaq.pandaeye.ui.video;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.pandaq.pandaeye.R;
import com.pandaq.pandaeye.adapters.VideoListAdapter;
import com.pandaq.pandaeye.adapters.VideoTopPagerAdapter;
import com.pandaq.pandaeye.config.Constants;
import com.pandaq.pandaeye.model.video.RetDataBean;
import com.pandaq.pandaeye.presenter.video.VideoFragPresenter;
import com.pandaq.pandaeye.rxbus.RxBus;
import com.pandaq.pandaeye.rxbus.RxConstants;
import com.pandaq.pandaeye.ui.ImplView.IVideoListFrag;
import com.pandaq.pandaeye.ui.base.BaseFragment;
import com.pandaq.pandaeye.utils.DensityUtil;
import com.pandaq.pandaqlib.loopbander.AutoScrollViewPager;
import com.pandaq.pandaqlib.loopbander.ViewGroupIndicator;
import com.pandaq.pandaqlib.magicrecyclerView.BaseItem;
import com.pandaq.pandaqlib.magicrecyclerView.BaseRecyclerAdapter;
import com.pandaq.pandaqlib.magicrecyclerView.MagicRecyclerView;
import com.pandaq.pandaqlib.magicrecyclerView.SpaceDecoration;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Subscription;
import rx.functions.Action1;

/**
 * Created by PandaQ on 2016/9/9.
 * email : 767807368@qq.com
 * 冒泡圈Fragment
 */
public class VideoCircleFragment extends BaseFragment implements IVideoListFrag, SwipeRefreshLayout.OnRefreshListener, BaseRecyclerAdapter.OnItemClickListener {

    @BindView(R.id.mrv_video)
    MagicRecyclerView mMrvVideo;
    @BindView(R.id.srl_refresh)
    SwipeRefreshLayout mSrlRefresh;
    private AutoScrollViewPager scrollViewPager;
    private ViewGroupIndicator viewGroupIndicator;
    private VideoTopPagerAdapter mPagerAdapter;
    private VideoListAdapter mAdapter;
    private VideoFragPresenter mPresenter = new VideoFragPresenter(this);
    private ArrayList<BaseItem> mBaseItems;
    private Subscription mSubscription;
    private StaggeredGridLayoutManager mStaggeredGridLayoutManager;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.video_fragment, container, false);
        ButterKnife.bind(this, view);
        initView();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        onHiddenChanged(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSrlRefresh.setRefreshing(false);
        mPresenter.unSubscribe();
        onHiddenChanged(true);
    }

    private void initView() {
        mBaseItems = new ArrayList<>();
        mStaggeredGridLayoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        mMrvVideo.setLayoutManager(mStaggeredGridLayoutManager);
        mMrvVideo.setItemAnimator(new DefaultItemAnimator());
        mMrvVideo.getItemAnimator().setChangeDuration(0);
        SpaceDecoration itemDecoration = new SpaceDecoration(DensityUtil.dip2px(getContext(), 8));
        itemDecoration.setPaddingEdgeSide(true);
        itemDecoration.setPaddingStart(true);
        itemDecoration.setPaddingHeaderFooter(false);
        mMrvVideo.addItemDecoration(itemDecoration);
        FrameLayout headerView = (FrameLayout) mMrvVideo.getHeaderView();
        scrollViewPager = (AutoScrollViewPager) headerView.findViewById(R.id.scroll_pager);
        viewGroupIndicator = (ViewGroupIndicator) headerView.findViewById(R.id.scroll_pager_indicator);
        mSrlRefresh.setProgressBackgroundColorSchemeColor(getResources().getColor(R.color.white_FFFFFF));
        mSrlRefresh.setOnRefreshListener(this);
        mSrlRefresh.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
        refreshData();
        mPresenter.loadCache();
        mMrvVideo.addOnItemClickListener(this);
    }

    @Override
    public void refreshData() {
        mPresenter.loadData();
    }

    @Override
    public void refreshSuccess(ArrayList<RetDataBean.ListBean> listBeen) {
        for (RetDataBean.ListBean listBean : listBeen) { //事实上只会执行一次，Banner 为第一个 item
            if (Constants.SHOW_TYPE_BANNER.equals(listBean.getShowType())) { //判断是否为 banner
                //配置顶部故事
                if (mPagerAdapter == null) {
                    mPagerAdapter = new VideoTopPagerAdapter(this, listBean.getChildList());
                    scrollViewPager.setAdapter(mPagerAdapter);
                } else {
                    mPagerAdapter.resetData(listBean.getChildList());
                    mPagerAdapter.notifyDataSetChanged();
                }
                viewGroupIndicator.setParent(scrollViewPager);
                listBeen.remove(listBean);
                break;
            }
        }
        //配置底部列表故事
        mBaseItems.clear();
        for (RetDataBean.ListBean listBean : listBeen) {
            if (!TextUtils.isEmpty(listBean.getMoreURL())) {
                BaseItem<RetDataBean.ListBean> baseItem = new BaseItem<>();
                baseItem.setData(listBean);
                mBaseItems.add(baseItem);
            }
        }
        if (mAdapter == null) {
            mAdapter = new VideoListAdapter(this);
            mAdapter.setBaseDatas(mBaseItems);
            mMrvVideo.setAdapter(mAdapter);
        } else {
            if (listBeen.size() != 0) {
                mAdapter.setBaseDatas(mBaseItems);
            }
        }
    }

    @Override
    public void refreshFail(String errCode, String errMsg) {

    }

    @Override
    public void showProgressBar() {
        if (!mSrlRefresh.isRefreshing())
            mSrlRefresh.setRefreshing(true);
    }

    @Override
    public void hideProgressBar() {
        mSrlRefresh.setRefreshing(false);
    }

    @Override
    public void onRefresh() {
        refreshData();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden && mSrlRefresh.isRefreshing()) { // 隐藏的时候停止 SwipeRefreshLayout 转动
            mSrlRefresh.setRefreshing(false);
        }
        if (!hidden) {
            mSubscription = RxBus
                    .getDefault()
                    .toObservableWithCode(RxConstants.BACK_PRESSED_CODE, String.class)
                    .subscribe(new Action1<String>() {
                        @Override
                        public void call(String s) {
                            if (s.endsWith(RxConstants.BACK_PRESSED_DATA) && mMrvVideo != null) {
                                //滚动到顶部
                                mStaggeredGridLayoutManager.smoothScrollToPosition(mMrvVideo, null, 0);
                            }
                        }
                    });
        } else {
            if (mSubscription != null && !mSubscription.isUnsubscribed()) {
                mSubscription.unsubscribe();
            }
        }
    }

    @Override
    public void onItemClick(int position, BaseItem data, View view) {
        RetDataBean.ListBean dataBean = (RetDataBean.ListBean) data.getData();
        Intent intent = new Intent(this.getActivity(), TypedVideosActivity.class);
        intent.putExtra(Constants.TYPED_MORE_TITLE, dataBean.getTitle());
        startActivity(intent);
    }

}
