package com.z7dream.manager.mvp.ui.holder;


import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.z7dream.manager.R2;
import com.z7dream.manager.mvp.ui.listener.FileManagerListListener;
import com.z7dream.manager.tool.recycler.BaseHolder;
import com.z7dream.manager.widget.TCheckBox;

import butterknife.BindView;

public class FileManagerOtherListHolder extends BaseHolder {
    @BindView(R2.id.iv_ifopl_collect)
    public ImageView iv_ifopl_collect;

    @BindView(R2.id.iv_ifopl_ico)
    public ImageView iv_ifopl_ico;

    @BindView(R2.id.tv_ifopl_title)
    public TextView tv_ifopl_title;

    @BindView(R2.id.tv_ifopl_content)
    public TextView tv_ifopl_content;

    @BindView(R2.id.tv_ifopl_date)
    public TextView tv_ifopl_date;

    @BindView(R2.id.fl_ifopl_right)
    FrameLayout fl_ifopl_right;

    @BindView(R2.id.rl_ifopl_check)
    public RelativeLayout rl_ifopl_check;

    @BindView(R2.id.cb_ifopl_check)
    public TCheckBox cb_ifopl_check;

    public FileManagerOtherListHolder(View itemView, FileManagerListListener listener) {
        super(itemView);
        fl_ifopl_right.setOnClickListener(v -> listener.onCheckClickListener(cb_ifopl_check, getAdapterPosition(), cb_ifopl_check.isChecked()));
        itemView.setOnClickListener(v -> listener.onCheckClickListener(cb_ifopl_check, getAdapterPosition(), cb_ifopl_check.isChecked()));
        rl_ifopl_check.setOnClickListener(v -> listener.onCheckClickListener(cb_ifopl_check, getAdapterPosition(), cb_ifopl_check.isChecked()));
    }
}