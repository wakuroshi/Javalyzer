package com.example.javalyzer.tui;

public class WaveformProcessor {

    private final int width;
    private final float smoothing;

    private final float[] smoothL;
    private final float[] smoothR;

    private float peakHoldL = 0f;
    private float peakHoldR = 0f;

    private static final float PEAK_DECAY = 0.01f;
    private static final float FFT_SMOOTH = 0.65f;

    private float[] fftL;
    private float[] fftR;

    private final float[] fftSmoothL;
    private final float[] fftSmoothR;

    private float maxFreq = 22050.0f;
    private float sampleRate;

    public WaveformProcessor(int width, float smoothing, float sampleRate) {
        this.width = width;
        this.smoothing = smoothing;
        this.sampleRate = sampleRate;

        smoothL = new float[width];
        smoothR = new float[width];

        int fftSize = 512;
        fftSmoothL = new float[fftSize];
        fftSmoothR = new float[fftSize];

        fftL = fftSmoothL;
        fftR = fftSmoothR;
    }

    public void process(float[] inL, float[] inR,
                        int samples, float peakL, float peakR) {

        if (samples <= 0) return;

        int blockSize = Math.max(1, samples / width);

        for (int x = 0; x < width; x++) {
            float accL = 0f, accR = 0f;
            for (int i = 0; i < blockSize; i++) {
                int idx = x * blockSize + i;
                if (idx >= samples) break;
                accL += inL[idx];
                accR += inR[idx];
            }
            float l = accL / blockSize;
            float r = accR / blockSize;

            smoothL[x] = smoothL[x] * smoothing + l * (1f - smoothing);
            smoothR[x] = smoothR[x] * smoothing + r * (1f - smoothing);
        }

        peakHoldL = Math.max(peakHoldL - PEAK_DECAY, peakL);
        peakHoldR = Math.max(peakHoldR - PEAK_DECAY, peakR);

        computeFFT(inL, inR);
    }

    private void computeFFT(float[] inL, float[] inR) {
        float[] rawL = safeFFT(inL);
        float[] rawR = safeFFT(inR);

        // Suavizado de la FFT
        for (int i = 0; i < fftSmoothL.length; i++) {
            if (i < rawL.length) {
                fftSmoothL[i] = fftSmoothL[i] * FFT_SMOOTH + rawL[i] * (1f - FFT_SMOOTH);
                fftSmoothR[i] = fftSmoothR[i] * FFT_SMOOTH + rawR[i] * (1f - FFT_SMOOTH);
            } else {
                fftSmoothL[i] = fftSmoothL[i] * FFT_SMOOTH;
                fftSmoothR[i] = fftSmoothR[i] * FFT_SMOOTH;
            }
        }

        fftL = fftSmoothL;
        fftR = fftSmoothR;
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

    private float[] safeFFT(float[] input) {
        int n = input.length;
        int N = 1;
        while (N < n) N <<= 1;

        if (N < 512) N = 512;
        if (N > 4096) N = 4096; 

        float[] real = new float[N];
        float[] imag = new float[N];
        System.arraycopy(input, 0, real, 0, Math.min(n, N));

        int m = (int) (Math.log(N) / Math.log(2));

        for (int i = 0; i < N; i++) {
            int j = Integer.reverse(i) >>> (32 - m);
            if (j > i) {
                float tr = real[i]; real[i] = real[j]; real[j] = tr;
                float ti = imag[i]; imag[i] = imag[j]; imag[j] = ti;
            }
        }

        for (int s = 1; s <= m; s++) {
            int ms = 1 << s;
            int ms2 = ms >> 1;
            double theta = -2 * Math.PI / ms;
            float wR = 1f, wI = 0f;
            float cos = (float) Math.cos(theta);
            float sin = (float) Math.sin(theta);

            for (int j = 0; j < ms2; j++) {
                for (int k = j; k < N; k += ms) {
                    int t = k + ms2;
                    float tr = wR * real[t] - wI * imag[t];
                    float ti = wR * imag[t] + wI * real[t];

                    real[t] = real[k] - tr;
                    imag[t] = imag[k] - ti;
                    real[k] += tr;
                    imag[k] += ti;
                }
                float tmp = wR;
                wR = wR * cos - wI * sin;
                wI = tmp * sin + wI * cos;
            }
        }

        int maxBin = (int) (N * maxFreq / (sampleRate / 2f));
        maxBin = Math.min(maxBin, N / 2);
        
        if (maxBin < 64) maxBin = 64;

        float[] mag = new float[maxBin];
        float max = 0f;
        
        for (int i = 0; i < maxBin; i++) {
            double window = 0.5 * (1 - Math.cos(2 * Math.PI * i / (maxBin - 1)));
            
            mag[i] = (float) (Math.sqrt(
                    real[i] * real[i] +
                    imag[i] * imag[i]
            ) * window);
            
            if (mag[i] > max) max = mag[i];
        }

        if (max > 0f) {
            for (int i = 0; i < maxBin; i++) {
                mag[i] = (float) (Math.log1p(mag[i]) / Math.log1p(max));
            }
        }
        
        return mag;
    }

    public float[] left() { return smoothL; }
    public float[] right() { return smoothR; }
    public float peakHoldL() { return peakHoldL; }
    public float peakHoldR() { return peakHoldR; }
    public float[] fftLeft() { return fftL; }
    public float[] fftRight() { return fftR; }
    
    public void setMaxFreq(float maxFreq) {
        this.maxFreq = Math.min(maxFreq, sampleRate / 2);
    }
}
