package com.disgusty.retroffice.compat;

import android.annotation.TargetApi;
import android.content.Context;

/** Material You dynamic colors (Android 12+). */
@TargetApi(31)
final class Api31 {
    private Api31() {
    }

    /** {accent1, accent2, neutral1, neutral2} tonal palettes at tones 0,10,50,100..1000 (13 each). */
    static int[][] palettes(Context ctx) {
        int[][] ids = {
                {android.R.color.system_accent1_0, android.R.color.system_accent1_10, android.R.color.system_accent1_50,
                        android.R.color.system_accent1_100, android.R.color.system_accent1_200, android.R.color.system_accent1_300,
                        android.R.color.system_accent1_400, android.R.color.system_accent1_500, android.R.color.system_accent1_600,
                        android.R.color.system_accent1_700, android.R.color.system_accent1_800, android.R.color.system_accent1_900,
                        android.R.color.system_accent1_1000},
                {android.R.color.system_accent2_0, android.R.color.system_accent2_10, android.R.color.system_accent2_50,
                        android.R.color.system_accent2_100, android.R.color.system_accent2_200, android.R.color.system_accent2_300,
                        android.R.color.system_accent2_400, android.R.color.system_accent2_500, android.R.color.system_accent2_600,
                        android.R.color.system_accent2_700, android.R.color.system_accent2_800, android.R.color.system_accent2_900,
                        android.R.color.system_accent2_1000},
                {android.R.color.system_neutral1_0, android.R.color.system_neutral1_10, android.R.color.system_neutral1_50,
                        android.R.color.system_neutral1_100, android.R.color.system_neutral1_200, android.R.color.system_neutral1_300,
                        android.R.color.system_neutral1_400, android.R.color.system_neutral1_500, android.R.color.system_neutral1_600,
                        android.R.color.system_neutral1_700, android.R.color.system_neutral1_800, android.R.color.system_neutral1_900,
                        android.R.color.system_neutral1_1000},
                {android.R.color.system_neutral2_0, android.R.color.system_neutral2_10, android.R.color.system_neutral2_50,
                        android.R.color.system_neutral2_100, android.R.color.system_neutral2_200, android.R.color.system_neutral2_300,
                        android.R.color.system_neutral2_400, android.R.color.system_neutral2_500, android.R.color.system_neutral2_600,
                        android.R.color.system_neutral2_700, android.R.color.system_neutral2_800, android.R.color.system_neutral2_900,
                        android.R.color.system_neutral2_1000},
        };
        int[][] out = new int[4][13];
        for (int p = 0; p < 4; p++) for (int t = 0; t < 13; t++) out[p][t] = ctx.getColor(ids[p][t]);
        return out;
    }
}
