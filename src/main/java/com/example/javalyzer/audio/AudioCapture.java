package com.example.javalyzer.audio;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class AudioCapture {

    private final AudioInputStream stream;
    private final AudioFormat fmt;
    private final byte[] buffer;
    private final int bufferSize;
    private SourceDataLine line;

    public float[] left;
    public float[] right;
    public float rmsL, rmsR;
    public float peakL, peakR;
    private long totalFramesRead = 0;

    public AudioCapture(String path, Mixer.Info mixerInfo) throws Exception {
        File file = new File(path);
        if (!file.exists()) throw new IOException("File not found: " + path);

        AudioInputStream original = AudioSystem.getAudioInputStream(file);
        AudioFormat base = original.getFormat();
        AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                base.getSampleRate(),
                16,
                base.getChannels(),
                base.getChannels() * 2,
                base.getSampleRate(),
                false
        );
        stream = AudioSystem.getAudioInputStream(target, original);
        fmt = target;

        int bufferMs = 12; 
        int computed = (int) (fmt.getFrameRate() * fmt.getFrameSize() * (bufferMs / 1000.0));
        bufferSize = Math.max(computed, fmt.getFrameSize() * 5);
        buffer = new byte[bufferSize];

        int frames = bufferSize / fmt.getFrameSize();
        left = new float[frames];
        right = new float[frames];

        Mixer mixer = null;
        if (mixerInfo != null) {
            try {
                mixer = AudioSystem.getMixer(mixerInfo);
            } catch (Exception e) {
                System.err.println("[ WARNING ]: Mixer not found, using default...");
            }
        }

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);

        if (mixer != null && mixer.isLineSupported(info)) {
            line = (SourceDataLine) mixer.getLine(info);
        } else {
            line = (SourceDataLine) AudioSystem.getLine(info);
        }

        try {
            line.open(fmt, bufferSize * 8);
        } catch (LineUnavailableException e) {
            line.open(fmt);
        }
        line.start();

        System.out.println("AudioCapture ready. Internal buffer: " + line.getBufferSize() + " bytes");
    }

public AudioFormat getAudioFormat() {
    return fmt;
}

    public boolean update() throws IOException {
        int read = stream.read(buffer, 0, buffer.length);
        if (read <= 0) return false;

        int frameSize = fmt.getFrameSize();
        int channels = fmt.getChannels();

        rmsL = rmsR = 0f;
        peakL = peakR = 0f;

        int framesRead = read / frameSize;

        for (int i = 0; i < read; i += frameSize) {
            int lo = buffer[i] & 0xFF;
            int hi = buffer[i + 1];
            int sampleL = (hi << 8) | lo;

            int sampleR = sampleL;
            if (channels > 1) {
                int loR = buffer[i + 2] & 0xFF;
                int hiR = buffer[i + 3];
                sampleR = (hiR << 8) | loR;
            }

            int idx = i / frameSize;
            if (idx < left.length) left[idx] = sampleL / 32768f;
            if (idx < right.length) right[idx] = sampleR / 32768f;

            rmsL += left[idx] * left[idx];
            rmsR += right[idx] * right[idx];

            peakL = Math.max(peakL, Math.abs(left[idx]));
            peakR = Math.max(peakR, Math.abs(right[idx]));
        }

        rmsL = (float) Math.sqrt(rmsL / framesRead);
        rmsR = (float) Math.sqrt(rmsR / framesRead);

        totalFramesRead += framesRead;

        line.write(buffer, 0, read);
        return true;
    }

    public void close() {
        if (line != null) {
            line.drain();
            line.stop();
            line.close();
        }
    }

    public long getFramesRead() {
        return totalFramesRead;
    }

    public float getFrameRate() {
        return fmt.getSampleRate();
    }

    public static Mixer.Info[] mixersWithPlaybackSupport(AudioFormat fmt) {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
        return Arrays.stream(AudioSystem.getMixerInfo())
                .filter(mi -> AudioSystem.getMixer(mi).isLineSupported(info))
                .toArray(Mixer.Info[]::new);
    }
}

