package com.example.javalyzer.tui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

public class WaveformView {
    private final int x, y;
    private final int width, height;

    public WaveformView(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    public void draw(TextGraphics g, float[] data, TextColor color, float rms, float peakHold) {
        int mid = y + height / 2;
        g.setForegroundColor(color);
        for (int i = 0; i < width && i < data.length; i++) {
            int yy = mid - (int) (data[i] * (height / 2f));
            yy = Math.max(y, Math.min(y + height - 1, yy));
            g.setCharacter(x + i, yy, '█');
        }

        // RMS bar
        int rmsLen = (int) (rms * width);
        g.setForegroundColor(TextColor.ANSI.GREEN);
        for (int i = 0; i < rmsLen && i < width; i++)
            g.setCharacter(x + i, y + height - 1, '▄');

        // Peak hold marker
        int px = (int) (peakHold * width);
        if(px >=0 && px < width) {
            g.setForegroundColor(TextColor.ANSI.YELLOW);
            g.setCharacter(x+px, mid, '│');
        }
    }
}

