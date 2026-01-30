package com.example.javalyzer.tui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

public class HeatmapView {

    private final int x, y, width, height;
    private final float[][] heat;
    private final float sampleRate;
    private final float maxFreq;

    public HeatmapView(int x, int y, int width, int height,
                       float sampleRate, float maxFreq) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.sampleRate = sampleRate;
        this.maxFreq = maxFreq;
        this.heat = new float[height][width];
    }

    public void pushFFT(float[] fft) {
        for (int i = 0; i < height - 1; i++) {
            System.arraycopy(heat[i + 1], 0, heat[i], 0, width);
        }

        if (fft.length == width) {
            System.arraycopy(fft, 0, heat[height - 1], 0, width);
        } else {
            float[] interpolated = interpolate(fft, width);
            System.arraycopy(interpolated, 0, heat[height - 1], 0, width);
        }
    }
    
    private float[] interpolate(float[] in, int size) {
        float[] out = new float[size];
        
        if (in.length == 1) {
            for (int i = 0; i < size; i++) out[i] = in[0];
            return out;
        }
        
        for (int i = 0; i < size; i++) {
            float p = i * (in.length - 1f) / (size - 1);
            int a = (int)p;
            int b = Math.min(a + 1, in.length - 1);
            float t = p - a;
            out[i] = in[a] + t * (in[b] - in[a]);
        }
        return out;
    }

    public void draw(TextGraphics g) {
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                float v = heat[row][col];
                g.setForegroundColor(colorFor(v));
                g.setCharacter(x + col, y + row, '█');
            }
        }
        
        drawFrequencyScale(g);
    }
    
    private void drawFrequencyScale(TextGraphics g) {
        g.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        
        for (int freq = 1000; freq <= (int)maxFreq; freq += 2000) {
            int xPos = (int)(freq * width / maxFreq);
            if (xPos >= 0 && xPos < width) {
                String label;
                if (freq >= 1000) {
                    label = String.format("%dk", freq / 1000);
                } else {
                    label = String.format("%d", freq);
                }
                
                int labelX = x + xPos - label.length() / 2;
                if (labelX >= x && labelX + label.length() <= x + width) {
                    g.putString(labelX, y + height, label);
                }
            }
        }
    }

    private TextColor colorFor(float v) {
        if (v < 0.15f) return TextColor.ANSI.BLACK;
        if (v < 0.30f) return TextColor.ANSI.BLUE;
        if (v < 0.45f) return TextColor.ANSI.CYAN;
        if (v < 0.60f) return TextColor.ANSI.GREEN;
        if (v < 0.75f) return TextColor.ANSI.YELLOW;
        return TextColor.ANSI.RED;
    }
}
