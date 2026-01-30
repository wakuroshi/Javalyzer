package com.example.javalyzer.tui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

public class FrequencyView {

    private final int x, y, width, height;
    private final float sampleRate;
    private final float maxFreq;

    public FrequencyView(int x, int y, int width, int height,
                         float sampleRate, float maxFreq) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.sampleRate = sampleRate;
        this.maxFreq = maxFreq;
    }

    public void draw(TextGraphics g, float[] fftL, float[] fftR) {
        int graphWidth = width - 8; 
        int graphHeight = height;
        
        drawVerticalScale(g, graphWidth, graphHeight);
        
        drawFrequencyScale(g, graphWidth, graphHeight);
        
        drawFrequencyBars(g, fftL, fftR, graphWidth, graphHeight);
    }
    
    private void drawVerticalScale(TextGraphics g, int graphWidth, int graphHeight) {
        g.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        
        int scaleX = x + graphWidth + 1;
        for (int row = 0; row < graphHeight; row++) {
            g.setCharacter(scaleX, y + row, '│');
        }
        
        for (int i = 0; i <= 5; i++) {
            int level = i * 20;
            int yPos = y + graphHeight - 1 - (i * (graphHeight - 1) / 5);
            
            // Etiqueta del nivel
            String label = String.format("%3d%%", level);
            g.putString(scaleX + 2, yPos, label);
            
            // Marca en el eje
            g.setCharacter(scaleX, yPos, '├');
        }
        
        g.putString(scaleX + 2, y - 1, "Ampl");
    }
    
    private void drawFrequencyScale(TextGraphics g, int graphWidth, int graphHeight) {
        g.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        
        int[] freqMarks = {50, 100, 200, 500, 1000, 2000, 5000, 10000, 15000, 20000};
        
        for (int freq : freqMarks) {
            if (freq <= maxFreq) {
                int xPos = (int)(freq * graphWidth / maxFreq);
                
                if (xPos >= 0 && xPos < graphWidth) {
                    
                    String label;
                    if (freq >= 1000) {
                        label = String.format("%dk", freq / 1000);
                    } else {
                        label = String.format("%d", freq);
                    }
                    
                    int labelX = x + xPos - label.length() / 2;
                    if (labelX >= x && labelX + label.length() < x + graphWidth) {
                        g.putString(labelX, y + graphHeight, label);
                    }
                }
            }
        }
    }
    
    private void drawFrequencyBars(TextGraphics g, float[] fftL, float[] fftR, int graphWidth, int graphHeight) {
        float[] displayL = new float[graphWidth];
        float[] displayR = new float[graphWidth];
        
        for (int i = 0; i < graphWidth; i++) {
            float pos = i * (fftL.length - 1f) / (graphWidth - 1);
            int idx1 = (int) pos;
            int idx2 = Math.min(idx1 + 1, fftL.length - 1);
            float t = pos - idx1;
            
            displayL[i] = fftL[idx1] + t * (fftL[idx2] - fftL[idx1]);
            displayR[i] = fftR[idx1] + t * (fftR[idx2] - fftR[idx1]);
        }
        
        for (int i = 0; i < graphWidth; i++) {
            float lv = displayL[i];
            float rv = displayR[i];
            
            int lh = (int) (lv * (graphHeight - 1));
            int rh = (int) (rv * (graphHeight - 1));
            
            lh = Math.min(lh, graphHeight - 1);
            rh = Math.min(rh, graphHeight - 1);
            
            for (int j = 0; j < lh; j++) {
                g.setForegroundColor(TextColor.ANSI.BLUE);
                g.setCharacter(x + i, y + graphHeight - 1 - j, '█');
            }
            
            for (int j = 0; j < rh; j++) {
                g.setForegroundColor(TextColor.ANSI.RED);
                g.setCharacter(x + i, y + graphHeight - 1 - j, '█');
            }
        }
    }
}
