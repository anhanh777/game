package ru.playsoftware.j2meloader;

import android.content.Context;

/**
 * Clean context holder for j2me-core (ACRA-free).
 * Initialized by MultiBoxApp on process startup.
 */
public class EmulatorApplication {
    private static Context appContext;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static Context getInstance() {
        return appContext;
    }
}
