package com.disgusty.retroffice.compat;

import android.annotation.TargetApi;
import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;

/**
 * Edge-to-edge insets. Apps targeting Android 15 are always drawn behind the system bars there,
 * so the root view pads itself by the bar (and keyboard) sizes.
 */
@TargetApi(30)
final class Api30 {
    private Api30() {
    }

    interface InsetsSink {
        void onInsets(int left, int top, int right, int bottom);
    }

    static void listen(View root, final InsetsSink sink) {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime()
                        | WindowInsets.Type.displayCutout());
                sink.onInsets(i.left, i.top, i.right, i.bottom);
                return WindowInsets.CONSUMED;
            }
        });
        root.requestApplyInsets();
    }
}
