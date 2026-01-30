package com.example.javalyzer.audio;

import javax.sound.sampled.*;
import java.io.IOException;
import java.util.Arrays;

public class LiveAudioCapture {
    
    private TargetDataLine line;
    private final AudioFormat fmt;
    private final byte[] buffer;
    private final int bufferSize;
    
    public float[] left;
    public float[] right;
    public float rmsL, rmsR;
    public float peakL, peakR;
    
    public LiveAudioCapture(Mixer.Info mixerInfo, float sampleRate, int bufferMs) throws LineUnavailableException {
        fmt = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate,
            16,           
            2,            
            4,            
            sampleRate,
            false         
        );
        
        bufferSize = (int) (fmt.getFrameRate() * fmt.getFrameSize() * (bufferMs / 1000.0));
        buffer = new byte[bufferSize];
        
        int frames = bufferSize / fmt.getFrameSize();
        left = new float[frames];
        right = new float[frames];
        
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
        
        Mixer mixer = null;
        if (mixerInfo != null) {
            try {
                mixer = AudioSystem.getMixer(mixerInfo);
            } catch (Exception e) {
                System.err.println("Mixer not found, using default...");
            }
        }
        
        if (mixer != null && mixer.isLineSupported(info)) {
            line = (TargetDataLine) mixer.getLine(info);
        } else {
            line = (TargetDataLine) AudioSystem.getLine(info);
        }
        
        line.open(fmt, bufferSize * 2);
        line.start();
        
        System.out.println("Captura en vivo iniciada. Buffer: " + bufferSize + " bytes (" + bufferMs + " ms)");
    }
    
    public boolean update() throws IOException {
        int read = line.read(buffer, 0, buffer.length);
        if (read <= 0) return false;
        
        int frameSize = fmt.getFrameSize();
        int channels = fmt.getChannels();
        
        rmsL = rmsR = 0f;
        peakL = peakR = 0f;
        
        int framesRead = read / frameSize;
        
        for (int i = 0; i < read; i += frameSize) {
            int loL = buffer[i] & 0xFF;
            int hiL = buffer[i + 1];
            int sampleL = (hiL << 8) | loL;
            
            int loR = buffer[i + 2] & 0xFF;
            int hiR = buffer[i + 3];
            int sampleR = (hiR << 8) | loR;
            
            int idx = i / frameSize;
            if (idx < left.length) left[idx] = sampleL / 32768f;
            if (idx < right.length) right[idx] = sampleR / 32768f;
            
            rmsL += left[idx] * left[idx];
            rmsR += right[idx] * right[idx];
            
            peakL = Math.max(peakL, Math.abs(left[idx]));
            peakR = Math.max(peakR, Math.abs(right[idx]));
        }
        
        if (framesRead > 0) {
            rmsL = (float) Math.sqrt(rmsL / framesRead);
            rmsR = (float) Math.sqrt(rmsR / framesRead);
        }
        
        return true;
    }
    
    public void close() {
        if (line != null) {
            line.stop();
            line.close();
        }
    }
    
    public float getFrameRate() {
        return fmt.getSampleRate();
    }
    
    public static Mixer.Info[] getAvailableInputs() {
        return Arrays.stream(AudioSystem.getMixerInfo())
                .filter(mi -> {
                    try {
                        Mixer mixer = AudioSystem.getMixer(mi);
                        Line.Info[] lines = mixer.getTargetLineInfo();
                        return lines.length > 0;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toArray(Mixer.Info[]::new);
    }
}
