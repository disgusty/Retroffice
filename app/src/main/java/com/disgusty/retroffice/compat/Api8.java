package com.disgusty.retroffice.compat;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;

@TargetApi(8)
final class Api8 {
    private Api8() {
    }

    static boolean night(Context ctx) {
        int m = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }
}
