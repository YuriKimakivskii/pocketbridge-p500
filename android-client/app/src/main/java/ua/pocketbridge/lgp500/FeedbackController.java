package ua.pocketbridge.lgp500;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Vibrator;

final class FeedbackController {
    private FeedbackController() { }

    static void success(Context context) {
        if (AppPreferences.hapticFeedback(context)) {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(35L);
            }
        }
        if (AppPreferences.soundFeedback(context)) {
            playTone(ToneGenerator.TONE_PROP_ACK, 70);
        }
    }

    static void failure(Context context) {
        if (AppPreferences.hapticFeedback(context)) {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(new long[] {0L, 70L, 80L, 110L}, -1);
            }
        }
        if (AppPreferences.soundFeedback(context)) {
            playTone(ToneGenerator.TONE_PROP_NACK, 120);
        }
    }

    private static void playTone(final int tone, final int durationMs) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ToneGenerator generator = null;
                try {
                    generator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 45);
                    generator.startTone(tone, durationMs);
                    Thread.sleep(durationMs + 25L);
                } catch (Exception ignored) {
                } finally {
                    if (generator != null) {
                        generator.release();
                    }
                }
            }
        }, "PocketBridgeFeedback").start();
    }
}
