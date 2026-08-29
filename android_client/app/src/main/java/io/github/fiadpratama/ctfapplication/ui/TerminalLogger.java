package io.github.fiadpratama.ctfapplication.ui;

import android.os.Handler;
import android.view.View;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

public class TerminalLogger {

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

    public void log(String message) {
        log(message, null);
    }

    public void log(String message, OnCompleteListener onComplete) {
        String fullMessage = message + "\n";
        for (char c : fullMessage.toCharArray()) {
            textQueue.add(c);
        }
        pendingListener = onComplete;
        if (!isTyping) {
            typeNextCharacter();
        }
    }

    public void clear() {
        textQueue.clear();
        isTyping = false;
        terminalLog.setText("");
    }

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
