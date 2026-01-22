package org.vaadin.addon.audio.demo;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.Route;
import org.vaadin.addon.audio.server.AudioPlayer;
import org.vaadin.addon.audio.server.Encoder;
import org.vaadin.addon.audio.server.Stream;
import org.vaadin.addon.audio.server.encoders.MP3Encoder;
import org.vaadin.addon.audio.server.encoders.OGGEncoder;
import org.vaadin.addon.audio.server.encoders.WaveEncoder;
import org.vaadin.addon.audio.server.util.FeatureSupport;
import org.vaadin.addon.audio.server.util.ULawUtil;
import org.vaadin.addon.audio.server.util.WaveUtil;
import org.vaadin.addon.audio.shared.ChunkDescriptor;
import org.vaadin.addon.audio.shared.PCMFormat;
import org.vaadin.addon.audio.shared.util.Log;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The main view contains a button and a click listener.
 */
@Route("")
public class DemoUIFlow extends VerticalLayout {

    public static final String TEST_FILE_PATH = "src/test/resources/org/vaadin/addon/audio/wav";

    public DemoUIFlow() {
        Select<String> fileList = new Select<>("File", listFileNames(TEST_FILE_PATH));
        fileList.addValueChangeListener(this::fileSelected);
        fileList.setWidth("400px");
        add(fileList);
    }

    /**
     * Returns a ByteBuffer filled with PCM data. If the original audio file is using
     * a different encoding, this method attempts to decode it into PCM signed data.
     * 
     * @param fname
     *            filename
     * @param dir
     *            directory in which the file exists
     * @return ByteBuffer containing byte[] of PCM data
     */
    private static ByteBuffer decodeToPcm(String fname, String dir) {
        // TODO: add other supported encodings for decoding to PCM
        ByteBuffer buffer = null;
        try {
            // load audio file
            Path path = Paths.get(TEST_FILE_PATH + "/" + fname);
            byte[] bytes = Files.readAllBytes(path);
            // create input stream with audio file bytes
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));
            AudioFormat.Encoding encoding = audioInputStream.getFormat().getEncoding();
            // handle current encoding
            if (encoding.equals(AudioFormat.Encoding.ULAW)) {
                buffer = ULawUtil.decodeULawToPcm(audioInputStream);
            } else {
                // for now assume it is PCM data and load it straight into byte buffer
                buffer = ByteBuffer.wrap(bytes);
            }

        } catch (UnsupportedAudioFileException e) {
            Notification.show("Audio file is not of supported type");
        } catch (Exception e) {
            Log.error(DemoUIFlow.class, "File read failed");
            e.printStackTrace();
        }
        return buffer;
    }

    private static Stream createWaveStream(ByteBuffer waveFile, Encoder outputEncoder) {
        int startOffset = WaveUtil.getDataStartOffset(waveFile);
        int dataLength = WaveUtil.getDataLength(waveFile);
        int chunkLength = 5000;
        PCMFormat dataFormat = WaveUtil.getDataFormat(waveFile);
        System.out.println(dataFormat.toString());
        System.out.println("arrayLength: " + waveFile.array().length + "\n\rstartOffset: " + startOffset
                + "\n\rdataLength: " + dataLength + "\r\nsampleRate: " + dataFormat.getSampleRate());
        ByteBuffer dataBuffer = ByteBuffer.wrap(waveFile.array(), startOffset, dataLength);
        Stream stream = new Stream(dataBuffer.slice(), dataFormat, outputEncoder, chunkLength);
        return stream;
    }

    public static final List<String> listFileNames(String dir) {
        List<String> fnames = new ArrayList<String>();

        File d = new File(dir);
        File[] files = d.listFiles();

        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f.isFile()) {
                fnames.add(f.getName());
            } else if (f.isDirectory()) {
                fnames.addAll(listFileNames(f.getPath()));
            }
        }
        return fnames;
    }

    private void fileSelected(AbstractField.ComponentValueChangeEvent<Select<String>, String> e) {
        String itemName = e.getValue();

        // Choose encoder based on support
        Encoder encoder = null;

        // Prefer OGG support
        if (FeatureSupport.isOggSupported()) {
            encoder = new OGGEncoder();
        } else if (FeatureSupport.isMp3Supported() && MP3Encoder.isSupported()) {
            // Try MP3 support (it's patent-encumbered)
            encoder = new MP3Encoder();
        } else {
            // WaveEncoder should always work
            encoder = new WaveEncoder();
        }

        // we decode any other formats to PCM Signed data to make encoding to
        // other formats easier
        ByteBuffer fileBytes = decodeToPcm(itemName, TEST_FILE_PATH);

        if (fileBytes != null) {

            // TODO: use the following line when OGG and/or MP3 encoders have been implemented
            // Stream stream = createWaveStream(fileBytes, encoder);
            Stream stream = createWaveStream(fileBytes, new WaveEncoder());

            // debugging
            for (ChunkDescriptor d : stream.getChunks()) {
                Log.message(this, d.toString());
            }

            Log.message(this, "Stream duration: " + stream.getDurationString());

            if (encoder instanceof WaveEncoder) {
                // TODO: enable the following line when client decompression library can be loaded
                // stream.setCompression(true);
            }
            AudioPlayer audio = new AudioPlayer(stream);
            Controls controls = new Controls(audio, itemName);
            add(controls);
        }
    }
}
