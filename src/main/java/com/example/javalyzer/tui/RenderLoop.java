package com.example.javalyzer.tui;

import com.example.javalyzer.audio.AudioCapture;
import com.example.javalyzer.audio.LiveAudioCapture;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;

import java.io.IOException;

public class RenderLoop {

    private enum ViewMode {
        WAVEFORM,
        FREQUENCY,
        HEATMAP
    }

    private ViewMode view = ViewMode.WAVEFORM;
    private float maxFreq = 22050.0f;

    private final Screen screen;
    private final boolean liveMode;
    private final WaveformView leftView;
    private final WaveformView rightView;
    private final WaveformProcessor processor;
    private FrequencyView frequencyView;
    private HeatmapView heatmapView;

    private boolean running = true;
    private boolean paused = false;
    private final float windowMs;

    private Runnable onRestart;
    
    private AudioCapture audioFile;
    private LiveAudioCapture audioLive;

    public RenderLoop(Screen screen, Object audioSource, boolean liveMode) {
        this.screen = screen;
        this.liveMode = liveMode;
        
        if (liveMode) {
            this.audioLive = (LiveAudioCapture) audioSource;
            this.audioFile = null;
        } else {
            this.audioFile = (AudioCapture) audioSource;
            this.audioLive = null;
        }

        TerminalSize size = screen.getTerminalSize();
        int w = size.getColumns();
        int h = size.getRows();

        leftView  = new WaveformView(0, 2, w, h / 2 - 2);
        rightView = new WaveformView(0, h / 2 + 2, w, h / 2 - 2);

        float sampleRate = getFrameRate();
        processor = new WaveformProcessor(w, 0.35f, sampleRate);
        
        float nyquist = sampleRate / 2;
        maxFreq = Math.min(maxFreq, nyquist);
        processor.setMaxFreq(maxFreq);

        createFrequencyViews(w, h);

        int bufferSize = liveMode ? audioLive.left.length : audioFile.left.length;
        windowMs = (bufferSize / sampleRate) * 1000f;
    }
    
    private float getFrameRate() {
        return liveMode ? audioLive.getFrameRate() : audioFile.getFrameRate();
    }
    
    private float[] getLeftChannel() {
        return liveMode ? audioLive.left : audioFile.left;
    }
    
    private float[] getRightChannel() {
        return liveMode ? audioLive.right : audioFile.right;
    }
    
    private float getRMSL() {
        return liveMode ? audioLive.rmsL : audioFile.rmsL;
    }
    
    private float getRMSR() {
        return liveMode ? audioLive.rmsR : audioFile.rmsR;
    }
    
    private float getPeakL() {
        return liveMode ? audioLive.peakL : audioFile.peakL;
    }
    
    private float getPeakR() {
        return liveMode ? audioLive.peakR : audioFile.peakR;
    }
    
    private boolean updateAudio() throws IOException {
        return liveMode ? audioLive.update() : audioFile.update();
    }
    
    private void closeAudio() {
        if (liveMode) {
            audioLive.close();
        } else {
            audioFile.close();
        }
    }

    private void createFrequencyViews(int w, int h) {
        float sampleRate = getFrameRate();
        frequencyView = new FrequencyView(0, 1, w, h - 3, sampleRate, maxFreq);
        heatmapView = new HeatmapView(0, 1, w, h - 3, sampleRate, maxFreq);
    }

    public void setOnRestart(Runnable r) {
        this.onRestart = r;
    }

    public void run() throws Exception {
        final int oversample = 3;
        TextGraphics g = screen.newTextGraphics();

        while (running) {
            handleInput();

            if (!paused) {
                for (int i = 0; i < oversample; i++) {
                    try {
                        if (!updateAudio()) {
                            if (!liveMode) {
                                running = false;
                                break;
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        running = false;
                        break;
                    }
                }

                processor.process(getLeftChannel(), getRightChannel(), 
                                 getLeftChannel().length, getPeakL(), getPeakR());

                if (view == ViewMode.HEATMAP) {
                    heatmapView.pushFFT(processor.fftLeft());
                }
            }

            switch (view) {
                case WAVEFORM -> drawWaveform(g);
                case FREQUENCY -> drawFrequency(g);
                case HEATMAP -> drawHeatmap(g);
            }

            Thread.sleep(16);
        }

        closeAudio();
    }

    private void handleInput() throws IOException {
        KeyStroke k;
        while ((k = screen.pollInput()) != null) {
            if (k.getKeyType() == KeyType.Character) {
                char c = k.getCharacter();
                if (c == 'q' || c == 'Q') running = false;
                if (c == 'p' || c == 'P') paused = !paused;
                if (c == 'r' || c == 'R') {
                    if (onRestart != null) onRestart.run();
                    running = false;
                }
                if (c == '+' || c == '=') {
                    increaseMaxFreq();
                }
                if (c == '-' || c == '_') {
                    decreaseMaxFreq();
                }
                if (c == 'm' || c == 'M') {
                    setMaxFreqToNyquist();
                }
            } else if (k.getKeyType() == KeyType.ArrowRight) {
                view = ViewMode.values()[(view.ordinal() + 1) % 3];
            } else if (k.getKeyType() == KeyType.ArrowLeft) {
                view = ViewMode.values()[(view.ordinal() + 2) % 3];
            }
        }
    }

    private void increaseMaxFreq() {
        float nyquist = getFrameRate() / 2;
        maxFreq = Math.min(maxFreq + 1000.0f, nyquist);
        processor.setMaxFreq(maxFreq);
        TerminalSize size = screen.getTerminalSize();
        createFrequencyViews(size.getColumns(), size.getRows());
    }

    private void decreaseMaxFreq() {
        maxFreq = Math.max(maxFreq - 1000.0f, 100.0f);
        processor.setMaxFreq(maxFreq);
        TerminalSize size = screen.getTerminalSize();
        createFrequencyViews(size.getColumns(), size.getRows());
    }
    
    private void setMaxFreqToNyquist() {
        maxFreq = getFrameRate() / 2;
        processor.setMaxFreq(maxFreq);
        TerminalSize size = screen.getTerminalSize();
        createFrequencyViews(size.getColumns(), size.getRows());
    }

    private void clear(TextGraphics g) {
        TerminalSize s = screen.getTerminalSize();
        g.setBackgroundColor(TextColor.ANSI.BLACK);
        g.fillRectangle(new TerminalPosition(0, 0), new TerminalSize(s.getColumns(), s.getRows()), ' ');
    }

    private void footer(TextGraphics g, String fmt, Object... args) {
        TerminalSize s = screen.getTerminalSize();
        String info = String.format(fmt, args);
        String modeIndicator = liveMode ? "[LIVE] " : "[WAV] ";
        String controls = modeIndicator + "[p] pause  [q] quit  [r] restart  [+/-] freq  [m] max";
        String combined = info + "  " + controls;
        g.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        g.putString(s.getColumns() / 2 - combined.length() / 2, s.getRows() - 1, combined);
    }

    private void drawWaveform(TextGraphics g) throws IOException {
        TerminalSize s = screen.getTerminalSize();
        int w = s.getColumns();
        int h = s.getRows();

        clear(g);

        g.setForegroundColor(TextColor.ANSI.WHITE);

        String l = String.format("LEFT RMS:%5.3f PEAK:%5.3f HOLD:%5.3f", getRMSL(), getPeakL(), processor.peakHoldL());
        String r = String.format("RIGHT RMS:%5.3f PEAK:%5.3f HOLD:%5.3f", getRMSR(), getPeakR(), processor.peakHoldR());

        g.putString((w - l.length()) / 2, 0, l);
        g.putString((w - r.length()) / 2, h / 2, r);

        leftView.draw(g, processor.left(), TextColor.ANSI.BLUE, getRMSL(), processor.peakHoldL());
        rightView.draw(g, processor.right(), TextColor.ANSI.RED, getRMSR(), processor.peakHoldR());

        String modeText = liveMode ? "LIVE" : "FILE";
        footer(g, "%s | Time → %.1f ms | Sample Rate: %.0f Hz", modeText, windowMs, getFrameRate());
        screen.refresh();
    }

    private void drawFrequency(TextGraphics g) throws IOException {
        clear(g);
        frequencyView.draw(g, processor.fftLeft(), processor.fftRight());
        g.setForegroundColor(TextColor.ANSI.WHITE);
        
        String modeText = liveMode ? "LIVE - " : "";
        String title = String.format("%sFREQUENCY VIEW (Max: %.0f Hz / Nyquist: %.0f Hz)", 
                                     modeText, maxFreq, getFrameRate() / 2);
        g.putString((screen.getTerminalSize().getColumns() - title.length()) / 2, 0, title);
        
        footer(g, "[←/→] switch view");
        screen.refresh();
    }

    private void drawHeatmap(TextGraphics g) throws IOException {
        clear(g);
        heatmapView.draw(g);
        TerminalSize s = screen.getTerminalSize();
        g.setForegroundColor(TextColor.ANSI.WHITE);
        
        String modeText = liveMode ? "LIVE - " : "";
        String title = String.format("%sHEATMAP VIEW (Max: %.0f Hz / Nyquist: %.0f Hz)", 
                                     modeText, maxFreq, getFrameRate() / 2);
        g.putString((s.getColumns() - title.length()) / 2, 0, title);
        
        footer(g, "[←/→] switch view");
        screen.refresh();
    }
}
