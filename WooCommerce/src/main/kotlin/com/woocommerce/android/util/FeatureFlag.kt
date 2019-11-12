package com.woocommerce.android.util

import android.content.Context
import com.woocommerce.android.BuildConfig

/**
 * "Feature flags" are used to hide in-progress features from release versions
 */
enum class FeatureFlag {
    PRODUCT_LIST,
    PRODUCT_VARIANTS,
    DB_DOWNGRADE,
    PRODUCT_IMAGE_CHOOSER,
    REFUNDS;
    fun isEnabled(context: Context? = null): Boolean {
        return when (this) {
            PRODUCT_LIST -> BuildConfig.DEBUG
            PRODUCT_VARIANTS -> BuildConfig.DEBUG
            REFUNDS -> BuildConfig.DEBUG
            PRODUCT_IMAGE_CHOOSER -> BuildConfig.DEBUG
            DB_DOWNGRADE -> {
                BuildConfig.DEBUG || context != null && PackageUtils.isBetaBuild(context)
            }
        }
    }
}
