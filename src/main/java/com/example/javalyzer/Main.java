package com.example.javalyzer;
import com.example.javalyzer.audio.AudioCapture;
import com.example.javalyzer.audio.LiveAudioCapture;
import com.example.javalyzer.tui.RenderLoop;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import java.io.File;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final Path SAMPLES_DIR = Paths.get("samples");

    public static void main(String[] args) {
        try {
            if (!Files.exists(SAMPLES_DIR)) {
                Files.createDirectories(SAMPLES_DIR);
                System.out.println("'Samples' directory created.");
            }
            DefaultTerminalFactory terminalFactory = new DefaultTerminalFactory();
            TerminalSize size = new TerminalSize(80, 40);
            terminalFactory.setInitialTerminalSize(size);
            Screen screen = new TerminalScreen(terminalFactory.createTerminal());
            screen.startScreen();
            screen.setCursorPosition(null);

            MultiWindowTextGUI gui =
                    new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.BLACK));

            boolean[] restart = {true};

            while (restart[0]) {
                restart[0] = false;
                
                boolean useLiveMode = selectMode(gui);
                if (useLiveMode) {
                    Mixer.Info inputDevice = selectInputDevice(gui);
                    if (inputDevice == null) continue;
                    
                    Mixer.Info outputMixer = selectMixer(gui, "Select output");
                    
                    try {
                        LiveAudioCapture audio = new LiveAudioCapture(inputDevice, 44100.0f, 12);
                        
                        screen.clear();
                        RenderLoop loop = new RenderLoop(screen, audio, true);
                        loop.setOnRestart(() -> restart[0] = true);
                        loop.run();
                        
                    } catch (LineUnavailableException e) {
                        showMessage(gui, "Error", "Cannot access the audio device: " + e.getMessage());
                        continue;
                    } catch (Exception e) {
                        e.printStackTrace();
                        showMessage(gui, "Error", "Live capture failure: " + e.getMessage());
                        continue;
                    }
                    
                } else {
                    Path wav = selectWav(gui);
                    if (wav == null) continue;

                    Mixer.Info mixerInfo = selectMixer(gui, "Select Mixer output");

                    try {
                        AudioCapture audio = new AudioCapture(wav.toAbsolutePath().toString(), mixerInfo);

                        screen.clear();
                        RenderLoop loop = new RenderLoop(screen, audio, false);
                        loop.setOnRestart(() -> restart[0] = true);
                        loop.run();
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                        showMessage(gui, "Error", "Something went wrong loading the WAV file: " + e.getMessage());
                        continue;
                    }
                }
            }

            screen.stopScreen();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error: " + e.getMessage());
            System.err.println("Actual directory: " + new File(".").getAbsolutePath());
        }
    }
    
    private static boolean selectMode(MultiWindowTextGUI gui) throws Exception {
        final boolean[] useLiveMode = {false};

        BasicWindow window = new BasicWindow("Select Mode");
        window.setHints(List.of(Window.Hint.CENTERED));

        Panel innerPanel = new Panel();
        innerPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

        innerPanel.addComponent(new Label("Select operation mode:")
                .setForegroundColor(TextColor.ANSI.BLACK));

        RadioBoxList<String> modeList = new RadioBoxList<>();
        modeList.addItem("WAV file");
        modeList.addItem("Live Mode (input)");
        modeList.setCheckedItemIndex(0);
        innerPanel.addComponent(modeList);
        innerPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        innerPanel.addComponent(new Button("Continue", () -> {
            useLiveMode[0] = (modeList.getCheckedItemIndex() == 1);
            window.close();
        }));

        window.setComponent(innerPanel);
        gui.addWindowAndWait(window);

        return useLiveMode[0];
    }
    
    private static Mixer.Info selectInputDevice(MultiWindowTextGUI gui) throws Exception {
        Mixer.Info[] inputs = LiveAudioCapture.getAvailableInputs();
        
        if (inputs.length == 0) {
            System.out.println("No audio input was found.");
            showMessage(gui, "Error", "No audio input was found.");
            return null;
        }

        final Mixer.Info[] selected = new Mixer.Info[1];

        BasicWindow window = new BasicWindow("Select Input");
        window.setHints(List.of(Window.Hint.CENTERED));

        Panel innerPanel = new Panel();
        innerPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

        innerPanel.addComponent(new Label("Select input device (" + inputs.length + " available):")
                .setForegroundColor(TextColor.ANSI.BLACK));

        RadioBoxList<String> list = new RadioBoxList<>();
        for (int i = 0; i < inputs.length; i++) {
            list.addItem("[" + i + "] " + inputs[i].getName() + " - " + inputs[i].getDescription());
        }
        list.setCheckedItemIndex(0);
        innerPanel.addComponent(list);
        innerPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        innerPanel.addComponent(new Button("Accept", () -> {
            selected[0] = inputs[list.getCheckedItemIndex()];
            window.close();
        }));

        Panel borderPanel = new Panel();
        borderPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
        borderPanel.addComponent(innerPanel.withBorder(Borders.singleLine(" ENTRADA ")));

        window.setComponent(borderPanel);
        gui.addWindowAndWait(window);

        return selected[0];
    }

    private static Path selectWav(MultiWindowTextGUI gui) throws Exception {
        List<Path> wavs;
        try (Stream<Path> s = Files.list(SAMPLES_DIR)) {
            wavs = s.filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".wav");
                    })
                    .sorted()
                    .collect(Collectors.toList());
        }

        if (wavs.isEmpty()) {
            System.out.println("No WAV files in directory: " + SAMPLES_DIR.toAbsolutePath());
            showMessage(gui, "Error", "No WAV files (.wav) in 'samples' directory.\nPut WAV files in the 'samples' directory.");
            return null;
        }

        final Path[] selected = new Path[1];

        BasicWindow window = new BasicWindow("Select WAV");
        window.setHints(List.of(Window.Hint.CENTERED));

        Panel innerPanel = new Panel();
        innerPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

        innerPanel.addComponent(new Label("Audio files (" + wavs.size() + " found):")
                .setForegroundColor(TextColor.ANSI.BLACK));

        ComboBox<Path> combo = new ComboBox<>(wavs);
        innerPanel.addComponent(combo);
        innerPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        innerPanel.addComponent(new Button("Aceptar", () -> {
            selected[0] = combo.getSelectedItem();
            window.close();
        }));

        Panel borderPanel = new Panel();
        borderPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
        borderPanel.addComponent(innerPanel.withBorder(Borders.singleLine(" WAV ")));

        window.setComponent(borderPanel);
        gui.addWindowAndWait(window);

        return selected[0];
    }
    private static Mixer.Info selectMixer(MultiWindowTextGUI gui, String title) throws Exception {
        Mixer.Info[] mixers = javax.sound.sampled.AudioSystem.getMixerInfo();
        final Mixer.Info[] selected = new Mixer.Info[1];

        BasicWindow window = new BasicWindow(title);
        window.setHints(List.of(Window.Hint.CENTERED));

        Panel innerPanel = new Panel();
        innerPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

        innerPanel.addComponent(new Label("Select mixer:")
                .setForegroundColor(TextColor.ANSI.BLACK));

        RadioBoxList<String> list = new RadioBoxList<>();
        for (int i = 0; i < mixers.length; i++) {
            list.addItem("[" + i + "] " + mixers[i].getName() + " - " + mixers[i].getDescription());
        }
        list.setCheckedItemIndex(0);
        innerPanel.addComponent(list);
        innerPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        innerPanel.addComponent(new Button("Accept", () -> {
            selected[0] = mixers[list.getCheckedItemIndex()];
            window.close();
        }));

        Panel borderPanel = new Panel();
        borderPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
        borderPanel.addComponent(innerPanel.withBorder(Borders.singleLine(" MIXER ")));

        window.setComponent(borderPanel);
        gui.addWindowAndWait(window);

        return selected[0];
    }
    
    private static Mixer.Info selectMixer(MultiWindowTextGUI gui) throws Exception {
        return selectMixer(gui, "Select Mixer");
    }
    
    private static void showMessage(MultiWindowTextGUI gui, String title, String message) throws Exception {
        BasicWindow window = new BasicWindow(title);
        window.setHints(List.of(Window.Hint.CENTERED));

        Panel innerPanel = new Panel();
        innerPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

        String[] lines = message.split("\n");
        for (String line : lines) {
            innerPanel.addComponent(new Label(line).setForegroundColor(TextColor.ANSI.BLACK));
        }
        
        innerPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        innerPanel.addComponent(new Button("OK", window::close));

        window.setComponent(innerPanel);
        gui.addWindowAndWait(window);
    }
}
