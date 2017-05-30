package dk.nota.lyt.libvlc;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

/**
 * Created by dfg on 16/11/2016.
 */

public abstract class CountDownTimer {

    /**
     * Millis since epoch when alarm should stop.
     */
    private final long mMillisInFuture;

    /**
     * The interval in millis that the user receives callbacks
     */
    private final long mCountdownInterval;

    private long mStopTimeInFuture;

    private long mPauseMillisRemaining = 0;

    private boolean mCancelled = false;

    private boolean mFinished = false;

    private boolean mPaused = false;

    /**
     * @param millisInFuture The number of millis in the future from the call
     *   to {@link #start(boolean)} until the countdown is done and {@link #onFinish()}
     *   is called.
     * @param countDownInterval The interval along the way to receive
     *   {@link #onTick(long)} callbacks.
     */
    public CountDownTimer(long millisInFuture, long countDownInterval) {
        mMillisInFuture = millisInFuture;
        mCountdownInterval = countDownInterval;
    }

    /**
     * Cancel the countdown.
     *
     * Do not call it from inside CountDownTimer threads
     */
    public final void cancel() {
        mHandler.removeMessages(MSG);
        mCancelled = true;
    }

    /**
     * Start the countdown.
     * @param paused If the countdown should start in a paused state
     */
    public synchronized final CountDownTimer start(boolean paused) {
        if (mMillisInFuture <= 0) {
            onFinish();
            mFinished = true;
            return this;
        }
        long startTime = SystemClock.elapsedRealtime();
        mStopTimeInFuture = startTime + mMillisInFuture;
        mCancelled = false;
        mFinished = false;
        mPaused = paused;
        mPauseMillisRemaining = mMillisInFuture;
        if (!paused) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG));
        }
        return this;
    }

    public boolean isPaused() {
        return mPaused;
    }

    /**
     * Pause the countdown.
     */
    public long pause() {
        mHandler.removeMessages(MSG);
        mPauseMillisRemaining = mStopTimeInFuture - SystemClock.elapsedRealtime();
        mPaused = true;
        return mPauseMillisRemaining;
    }

    /**
     * Resume the countdown.
     */
    public long resume() {
        mStopTimeInFuture = mPauseMillisRemaining + SystemClock.elapsedRealtime();
        mPaused = false;
        mHandler.sendMessage(mHandler.obtainMessage(MSG));
        return mPauseMillisRemaining;
    }

    public CountDownTimer reset() {
        return start(false);
    }

    public boolean isFinishedOrCancelled() {
        return mCancelled || mFinished;
    }

    public long getMillisLeftUntilFinished() {
//        Log.d("CDT", "mMillisInFuture: "+ mMillisInFuture);
//        Log.d("CDT", "mPauseMillisRemaining: "+ mPauseMillisRemaining);
        return mPaused ? mPauseMillisRemaining :
            Math.max(0, mStopTimeInFuture - SystemClock.elapsedRealtime());
    }

    /**
     * Callback fired on regular interval.
     * @param millisUntilFinished The amount of time until finished.
     */
    public abstract void onTick(long millisUntilFinished);

    /**
     * Callback fired when the time is up.
     */
    public abstract void onFinish();


    private static final int MSG = 1;


    // handles counting down
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            synchronized (CountDownTimer.this) {
                if (!mPaused) {
                    final long millisLeft = mStopTimeInFuture - SystemClock.elapsedRealtime();
//                    Log.d("CDT", "tick, millisLeft: "+ millisLeft);

                    if (millisLeft <= 0) {
                        onFinish();
                        mFinished = true;
                    } else if (millisLeft < mCountdownInterval) {
                        // no tick, just delay until done
                        sendMessageDelayed(obtainMessage(MSG), millisLeft);
                    } else {
                        long lastTickStart = SystemClock.elapsedRealtime();
                        onTick(millisLeft);

                        // take into account user's onTick taking time to execute
                        long delay = lastTickStart + mCountdownInterval - SystemClock.elapsedRealtime();

                        // special case: user's onTick took more than interval to
                        // complete, skip to next interval
                        while (delay < 0) delay += mCountdownInterval;

                        if (!mCancelled) {
                            sendMessageDelayed(obtainMessage(MSG), delay);
                        }
                    }
                }
            }
        }
    };
}