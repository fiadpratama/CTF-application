package io.github.fiadpratama.ctfapplication.ui;

import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/**
 * Terminal UI presentation controller.
 *
 * Simulates asynchronous typewriter console output with character-by-character
 * streaming, randomized latency pacing, and auto-scrolling synchronization.
 */
public class TerminalLogger {

    /**
     * Callback invoked once a queued text output animation finishes.
     */
    public interface OnCompleteListener {
        void onComplete();
    }

    private static class LogEntry {
        final String text;
        final OnCompleteListener listener;
        int charIndex = 0;

        LogEntry(String text, OnCompleteListener listener) {
            this.text = text;
            this.listener = listener;
        }
    }

    private final TextView terminalLog;
    private final NestedScrollView scrollView;
    private final Handler handler;
    private final Queue<LogEntry> entryQueue = new LinkedList<>();
    private final Random random = new Random();
    private boolean isTyping = false;

    private final Runnable typeRunnable = new Runnable() {
        @Override
        public void run() {
            typeNextCharacter();
        }
    };

    public TerminalLogger(TextView terminalLog, NestedScrollView scrollView, Handler handler) {
        this.terminalLog = terminalLog;
        this.scrollView = scrollView;
        this.handler = handler;
    }

    /**
     * Appends a log line to the typing queue.
     *
     * @param message Line of text to output
     */
    public void log(String message) {
        log(message, null);
    }

    /**
     * Appends a log line to the typing queue with a completion callback.
     *
     * @param message Line of text to output
     * @param onComplete Callback invoked after the line has completed rendering
     */
    public void log(String message, OnCompleteListener onComplete) {
        if (message == null) {
            message = "";
        }
        entryQueue.add(new LogEntry(message + "\n", onComplete));
        if (!isTyping) {
            typeNextCharacter();
        }
    }

    /**
     * Cancels active typing tasks, clears queued messages, and empties the log view.
     */
    public void clear() {
        handler.removeCallbacks(typeRunnable);
        entryQueue.clear();
        isTyping = false;
        terminalLog.setText("");
    }

    /**
     * Processes and renders the next character from the active queue item with randomized delay.
     */
    private void typeNextCharacter() {
        if (entryQueue.isEmpty()) {
            isTyping = false;
            return;
        }

        isTyping = true;
        LogEntry currentEntry = entryQueue.peek();
        if (currentEntry == null) {
            isTyping = false;
            return;
        }

        char c = currentEntry.text.charAt(currentEntry.charIndex++);
        terminalLog.append(String.valueOf(c));

        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });

        if (currentEntry.charIndex >= currentEntry.text.length()) {
            entryQueue.poll();
            if (currentEntry.listener != null) {
                currentEntry.listener.onComplete();
            }
        }

        if (entryQueue.isEmpty()) {
            isTyping = false;
            return;
        }

        int delay = random.nextInt(15) + 5;
        handler.postDelayed(typeRunnable, delay);
    }
}
