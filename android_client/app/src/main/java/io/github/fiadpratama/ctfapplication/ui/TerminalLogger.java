package io.github.fiadpratama.ctfapplication.ui;

import android.os.Handler;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

public class TerminalLogger {

    private final TextView terminalLog;
    private final ScrollView scrollView;
    private final Handler handler;
    private final Queue<Character> textQueue = new LinkedList<>();
    private final Random random = new Random();
    private boolean isTyping = false;

    public TerminalLogger(TextView terminalLog, ScrollView scrollView, Handler handler) {
        this.terminalLog = terminalLog;
        this.scrollView = scrollView;
        this.handler = handler;
    }

    public void log(String message) {
        String fullMessage = message + "\n";
        for (char c : fullMessage.toCharArray()) {
            textQueue.add(c);
        }
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

        int randomDelay;
        if (random.nextFloat() > 0.95f) {
            randomDelay = random.nextInt(50) + 20;
        } else {
            randomDelay = random.nextInt(10) + 2;
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                typeNextCharacter();
            }
        }, randomDelay);
    }
}
