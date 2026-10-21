package io.github.fiadpratama.ctfapplication.ui;

import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/**
 * Terminal UI Emulator
 *
 * Implements typewriter-style character output and automatic downward scrolling
 * to simulate a retro security assessment terminal console.
 */
public class TerminalLogger {

    /**
     * Callback invoked once a queued text output animation finishes.
     */
    public interface OnCompleteListener {
        void onComplete();
    }

    private final TextView terminalLog;
    private final NestedScrollView scrollView;
    private final Handler handler;
    private final Queue<Character> textQueue = new LinkedList<>();
    private final Random random = new Random();
    private boolean isTyping = false;
    private OnCompleteListener pendingListener;

    public TerminalLogger(TextView terminalLog, NestedScrollView scrollView, Handler handler) {
        this.terminalLog = terminalLog;
        this.scrollView = scrollView;
        this.handler = handler;
    }

    /**
     * Queues a message to be rendered character-by-character on the terminal.
     *
     * @param message Text to display
     */
    public void log(String message) {
        log(message, null);
    }

    /**
     * Queues a message with a completion callback triggered when typing finishes.
     *
     * @param message Text to display
     * @param onComplete Optional callback invoked after all characters are typed
     */
    public void log(String message, OnCompleteListener onComplete) {
        if (message == null) {
            message = "";
        }
        String fullMessage = message + "\n";
        for (char c : fullMessage.toCharArray()) {
            textQueue.add(c);
        }
        pendingListener = onComplete;
        if (!isTyping) {
            typeNextCharacter();
        }
    }

    /**
     * Clears terminal text and cancels ongoing typing animations.
     */
    public void clear() {
        handler.removeCallbacksAndMessages(null);
        textQueue.clear();
        isTyping = false;
        pendingListener = null;
        terminalLog.setText("");
    }

    /**
     * Sequentially pops and appends characters to the TextView with randomized latency.
     */
    private void typeNextCharacter() {
        if (textQueue.isEmpty()) {
            isTyping = false;
            if (pendingListener != null) {
                OnCompleteListener listener = pendingListener;
                pendingListener = null;
                listener.onComplete();
            }
            return;
        }
        isTyping = true;
        char c = textQueue.poll();
        terminalLog.append(String.valueOf(c));

        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });

        int delay = random.nextInt(15) + 5;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                typeNextCharacter();
            }
        }, delay);
    }
}
