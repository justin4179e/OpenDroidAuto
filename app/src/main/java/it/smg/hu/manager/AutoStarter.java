package it.smg.hu.manager;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import it.smg.hu.config.Settings;
import it.smg.hu.service.ODAService;
import it.smg.hu.ui.PlayerActivity;
import it.smg.libs.common.Log;

/**
 * Auto-connect: starts the USB projection by itself as soon as the phone is
 * ready in AOAP mode (e.g. right after the car powers on), so nobody has to
 * tap "START ANDROID AUTO".
 *
 * It only ever starts from a freshly prepared transport (state IDLE with USB
 * mode). A session the user stopped (EXITED), a failed one (ERROR) or one that
 * is still being set up or torn down is never restarted automatically, so this
 * cannot cause a reconnect loop or overlapping sessions.
 */
public final class AutoStarter {
    private static final String TAG = "AutoStarter";

    /** Give USB permission / AOAP handling a moment to settle before starting. */
    private static final long START_DELAY_MS = 1500L;

    private static final Handler handler_ = new Handler(Looper.getMainLooper());
    private static Runnable pending_;

    private AutoStarter() {}

    /** @return true if an automatic start was scheduled (the caller then skips the start popup). */
    public static synchronized boolean schedule(Context context) {
        if (!Settings.instance().connectivity.autoStartUsb()) {
            return false;
        }
        if (pending_ != null) {
            return true;
        }
        final Context appContext = context.getApplicationContext();
        pending_ = () -> {
            synchronized (AutoStarter.class) {
                pending_ = null;
            }
            startIfReady(appContext);
        };
        handler_.postDelayed(pending_, START_DELAY_MS);
        if (Log.isInfo()) Log.i(TAG, "AOAP ready -> auto-start scheduled");
        return true;
    }

    private static void startIfReady(Context context) {
        ConnectionManager connection = ConnectionManager.instance();
        USBManager usb = USBManager.instance();
        if (connection == null || usb == null) {
            return;
        }
        if (connection.state() != ConnectionState.IDLE || !ODAService.MODE_USB.equals(connection.mode())) {
            if (Log.isInfo()) Log.i(TAG, "auto-start skipped, state=" + connection.state() + " mode=" + connection.mode());
            return;
        }
        if (usb.aoapDevice() == null || PlayerActivity.isActive() || ODAService.isSessionActive()) {
            if (Log.isInfo()) Log.i(TAG, "auto-start skipped, no device or session already running");
            return;
        }

        if (Log.isInfo()) Log.i(TAG, "auto-start USB projection");
        Intent player = new Intent(context, PlayerActivity.class);
        player.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        player.putExtra("mode", ODAService.MODE_USB);
        context.startActivity(player);
    }
}
