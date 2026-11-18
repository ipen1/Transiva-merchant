package com.transiva.app;

import android.content.Context;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

/** Reflows content against the visible window, including system bars and the keyboard. */
final class MerchantResponsiveUi {
    interface SizeListener {
        void onSizeChanged(int width, int height);
    }

    private MerchantResponsiveUi() {}

    static void onSafeSizeChanged(View view, SizeListener listener) {
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private int previousWidth = -1;
            private int previousHeight = -1;

            @Override public void onGlobalLayout() {
                int width = Math.max(0, view.getWidth() - view.getPaddingLeft() - view.getPaddingRight());
                int height = Math.max(0, view.getHeight() - view.getPaddingTop() - view.getPaddingBottom());
                if (width == previousWidth && height == previousHeight) return;
                previousWidth = width;
                previousHeight = height;
                listener.onSizeChanged(width, height);
            }
        });
    }

    static ScrollView boundedPanel(Context context, View content, float maxHeightRatio) {
        ScrollView panel = new ScrollView(context) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
                    int limit = Math.round(MeasureSpec.getSize(heightMeasureSpec) * maxHeightRatio);
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        panel.setClipToPadding(false);
        panel.addView(content, new ScrollView.LayoutParams(-1, -2));
        return panel;
    }
}
