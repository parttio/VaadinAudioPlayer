package org.vaadin.addon.audio.server;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.server.StreamRegistration;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.StreamResourceRegistry;
import com.vaadin.flow.server.VaadinSession;
import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.vaadin.addon.audio.server.state.PlaybackState;
import org.vaadin.addon.audio.server.state.StateChangeCallback;
import org.vaadin.addon.audio.server.state.VolumeChangeCallback;
import org.vaadin.addon.audio.server.util.OnEndOfRange;
import org.vaadin.addon.audio.server.util.StringFormatter;
import org.vaadin.addon.audio.shared.ChunkDescriptor;
import org.vaadin.addon.audio.shared.SharedEffect;
import org.vaadin.addon.audio.shared.util.Log;

// This is the server-side UI component that provides public API for AudioPlayer
@SuppressWarnings("serial")
@Tag("audio-player")
@JsModule("./audio-player.js")
public class AudioPlayer extends Component {

    private Stream stream = null;
    private PlaybackState playbackState = PlaybackState.STOPPED;
    private int currentPosition = 0;
    private double volume = 1;
    private double[] channelVolumes = new double[0];
    private List<StreamRegistration> chunkRegistrations = new ArrayList<>();

    // TODO: use a proper event system
    private List<StateChangeCallback> stateCallbacks = new ArrayList<>();
    private List<VolumeChangeCallback> volumeCallbacks = new ArrayList<>();

    public int chunkTimeMillis;

    public int numChunksPreload;

    public int duration;

    public int reportPositionRepeatTime = 500;

    public final List<SharedEffect> effects = new ArrayList<SharedEffect>();

    public int startRange;

    public int endRange;
        
    public OnEndOfRange onEndOfRange;    

    /**
     * Create new AudioPlayer
     *
     * @param stream
     *            Stream to use
     */
    public AudioPlayer(Stream stream) {
        setupAudioPlayer(stream);
    }

    /**
     * Create new AudioPlayer
     *
     * @param stream
     *            Stream to use
     * @param reportPositionRepeatTime
     *            Define the interval for position reporting, default 500ms
     */
    public AudioPlayer(Stream stream, int reportPositionRepeatTime) {
        this.reportPositionRepeatTime = reportPositionRepeatTime;
        setupAudioPlayer(stream);
    }

    private void setupAudioPlayer(Stream stream) {
        // Register stream, set up chunk table in state
        setStream(stream);

        getElement().setProperty("reportPositionRepeatTime", reportPositionRepeatTime);
        
        // Set default values for range
        setStartRange(0);
        setEndRange(this.duration);
        setOnEndOfRange(OnEndOfRange.STOP_POSITION_END);       
    }

    @ClientCallable
    public void reportPlaybackPosition(int position_millis) {
        Log.message(this,"received position report: " + position_millis);
        if (position_millis != currentPosition) {
            currentPosition = position_millis;
            for (StateChangeCallback cb : stateCallbacks) {
                cb.playbackPositionChanged(position_millis);
            }
        }
    }

    @ClientCallable
    public void reportPlaybackStarted() {
        Log.message(this, "received playback state change to PLAYING");
        playbackState = PlaybackState.PLAYING;
        for (StateChangeCallback cb : stateCallbacks) {
            cb.playbackStateChanged(playbackState);
        }
    }

    @ClientCallable
    public void reportPlaybackPaused() {
        Log.message(this, "received playback state change to PAUSED");
        playbackState = PlaybackState.PAUSED;
        for (StateChangeCallback cb : stateCallbacks) {
            cb.playbackStateChanged(playbackState);
        }
    }

    @ClientCallable
    public void reportPlaybackStopped() {
        Log.message(this, "received playback state change to STOPPED");
        playbackState = PlaybackState.STOPPED;
        for (StateChangeCallback cb : stateCallbacks) {
            cb.playbackStateChanged(playbackState);
        }
    }

    @ClientCallable
    public void reportVolumeChange(double volume, double[] channelVolumes) {
        Log.message(this, "volume change reported from client");
        this.volume = volume;
        this.channelVolumes = channelVolumes;
        for (VolumeChangeCallback cb : volumeCallbacks) {
            cb.onVolumeChange(volume, channelVolumes);
        }
    }

    public void destroy() {
        // ui.removeExtension(this);
    }

    /**
     * Gets Stream object that supplies audio data to this AudioPlayer.
     * 
     * @return Stream
     */
    public Stream getStream() {
        return stream;
    }

    public Stream setStream(Stream stream) {
        unregisterStreamChunks();

        this.stream = stream;

        registerStreamChunks();

        // TODO: prettify this verbose JSON serialization
        JsonArray chunksJson = Json.createArray();
        List<ChunkDescriptor> chunks = stream.getChunks();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkDescriptor chunk = chunks.get(i);
            JsonObject chunkDescriptor = Json.createObject();
            chunkDescriptor.put("id", chunk.getId());
            chunkDescriptor.put("startTimeOffset", chunk.getStartTimeOffset());
            chunkDescriptor.put("endTimeOffset", chunk.getEndTimeOffset());
            chunkDescriptor.put("leadInDuration", chunk.getLeadInDuration());
            chunkDescriptor.put("leadOutDuration", chunk.getLeadOutDuration());
            chunkDescriptor.put("overlapTime", chunk.getOverlapTime());
            chunkDescriptor.put("startSampleOffset", chunk.getStartSampleOffset());
            chunkDescriptor.put("endSampleOffset", chunk.getEndSampleOffset());
            chunkDescriptor.put("url", chunk.getUrl().toASCIIString());
            chunksJson.set(i, chunkDescriptor);
        }
        getElement().setPropertyJson("chunks", chunksJson);
        duration = stream.getDuration();
        getElement().setProperty("duration", duration);
        chunkTimeMillis = stream.getChunkLength();
        getElement().setProperty("chunkTimeMillis", chunkTimeMillis);
        return stream;
    }

    private void unregisterStreamChunks() {
        for (int i = 0; i < chunkRegistrations.size(); i++) {
            chunkRegistrations.get(i).unregister();
        }

        chunkRegistrations.clear();
    }

    private void registerStreamChunks() {
        if (this.stream == null) {
            return;
        }

        List<ChunkDescriptor> chunks = this.stream.getChunks();
        for (int i = 0; i < chunks.size(); i++) {
            registerChunkResource(chunks.get(i));
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        if (this.stream != null && this.chunkRegistrations.isEmpty()) {
            registerStreamChunks();
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        unregisterStreamChunks();
    }

    private void registerChunkResource(ChunkDescriptor chunk) {
        StreamResource resource = new StreamResource("audio",
                (OutputStream outputStream, VaadinSession session) -> stream.getChunkData(chunk, bytes -> {
                    try {
                        outputStream.write(bytes);
                        outputStream.flush();
                        outputStream.close();
                    } catch (IOException e) {
                        Log.message(this, "could not register audio chunk with id: " + chunk.getId());
                    }
                }));
        StreamResourceRegistry registry = UI.getCurrent().getSession().getResourceRegistry();
        StreamRegistration registration = registry.registerResource(resource);
        chunkRegistrations.add(registration);
        chunk.setUrl(registration.getResourceUri());
    }

    /**
     * Gets current audio files total duration in milliseconds.
     * 
     * @return int milliseconds
     */
    public int getDuration() {
        return stream.getDuration();
    }

    /**
     * Gets current audio players time position.
     * 
     * @return int milliseconds
     */
    public int getPosition() {
        return currentPosition;
    }

    public void setPosition(int millis) {
        if (millis == currentPosition) {
            // Avoid setting the same position again loopback
            return;
        }

        currentPosition = millis;
        getElement().callJsFunction("setPlaybackPosition", millis);
        Log.message(AudioPlayer.this, "set playback position: " + millis);
    }

    /**
     * Moves play position by milliseconds.
     * 
     * @param millis
     *            number of milliseconds to move
     */
    public void skip(int millis) {
        Log.message(AudioPlayer.this, "skip " + millis + " milliseconds");
    }

    /**
     * Starts playing audio from the beginning of the audio file.
     */
    public void play() {
        getElement().callJsFunction("startPlayback");
        Log.message(AudioPlayer.this, "start or restart playback");
    }

    /**
     * Starts playing audio from the specified position (milliseconds).
     * NOT IMPLEMENTED.
     * 
     * @param offset_millis
     *            start position in milliseconds
     */
    public void play(int offset_millis) {
        Log.message(AudioPlayer.this, "start playback at time offset");
    }

    /**
     * Pauses the current audio.
     */
    public void pause() {
        getElement().callJsFunction("pausePlayback");
        Log.message(AudioPlayer.this, "pause playback");
    }

    /**
     * Plays audio from last known position (usually used to play while paused).
     */
    public void resume() {
        getElement().callJsFunction("resumePlayback");
        Log.message(AudioPlayer.this, "resume playback");
    }

    /**
     * Stops playing the audio and resets the position to 0 (beginning of audio file).
     */
    public void stop() {
        getElement().callJsFunction("stopPlayback");
        Log.message(AudioPlayer.this, "stop playback");
    }

    public boolean isPlaying() {
        return playbackState == PlaybackState.PLAYING;
    }

    public boolean isPaused() {
        return playbackState == PlaybackState.PAUSED;
    }

    public boolean isStopped() {
        return playbackState == PlaybackState.STOPPED;
    }

    /**
     * Sets the volume of the audio player. 1 is 100% volume (default), 2 is 200% volume, etc.
     * 
     * @param volume
     *            volume level
     */
    public void setVolume(double volume) {
        getElement().callJsFunction("setVolume", volume);
        Log.message(AudioPlayer.this, "setting volume to " + volume);
    }

    public void setVolumeOnChannel(double volume, int channel) {
        getElement().callJsFunction("setVolumeOnChannel", volume, channel);
        Log.message(AudioPlayer.this, "setting volume to " + volume + " on channel " + channel);
    }

    public double getVolume() {
        return volume;
    }

    public double getVolumeOnChannel(int channel) {
        if (channelVolumes.length > channel) {
            return channelVolumes[channel];
        }
        return -1;
    }

    public double getNumberOfChannels() {
        return channelVolumes.length;
    }

    /**
     * Sets the speed at which the audio is played. Changing this will not change
     * the pitch of the audio. 1 is 100% speed (default), 2 is 200%, etc.
     * 
     * @param playbackSpeed
     *            speed ratio
     */
    public void setPlaybackSpeed(double playbackSpeed) {
        getElement().callJsFunction("setPlaybackSpeed", playbackSpeed);
        Log.message(AudioPlayer.this, "setting playback speed to " + playbackSpeed);
    }

    /**
     * Sets the spread of total gain (volume) between the left and right channels.
     * -1 is to only play left channel.
     * 0 is to play equally left and right channels (default).
     * 1 is to only play right channel.
     * 
     * @param balance
     */
    public void setBalance(double balance) {
        getElement().callJsFunction("setBalance", balance);
    }

    /**
     * Sets the number of audio chunks that are loaded ahead of the current playing audio chunk.
     * 
     * @param numChunksPreload
     */
    public void setNumberChunksToPreload(int numChunksPreload) {
        numChunksPreload = numChunksPreload;
    }

    /**
     * Gets number of chunks to load each time audio chunks are requested.
     * 
     * @return number of chunks
     */
    public int getNumberChunksToPreload() {
        return numChunksPreload;
    }

    protected ChunkDescriptor getChunkDescriptor(int chunkId) {
        // TODO: return chunk descriptor
        return null;
    }

    /**
     * Gets String representing current player time position.
     * 
     * @return String
     */
    public String getPositionString() {
        return StringFormatter.msToPlayerTimeStamp(getPosition());
    }

    /**
     * Gets String representing current player's total time duration.
     * 
     * @return String
     */
    public String getDurationString() {
        return StringFormatter.msToPlayerTimeStamp(getDuration());
    }
    
    /**
     * Gets the start of the audio play range.
     */
    public int getStartRange() {
      return startRange;
    }

    /**
     * Sets the start of the audio play range.
     * 
     * @param startRange
     */
    public void setStartRange(int startRange) {
      if(startRange >= 0 && startRange < this.getDuration()) {
        this.startRange = startRange;
        this.getElement().setProperty("startRange", startRange);
        Log.message(AudioPlayer.this, "setting start range to " + startRange);
      }     
    }

    /**
     * Gets the end of the audio player range.
     */
    public int getEndRange() {
      return endRange;
    }

    /**
     * Sets the end of the audio play range.
     * 
     * @param endRange
     */
    public void setEndRange(int endRange) {
      if(endRange > 0 && endRange <= this.getDuration()) {
        this.endRange = endRange;
        this.getElement().setProperty("endRange", endRange);
        Log.message(AudioPlayer.this, "setting end range to " + endRange);
      } 
    }    
    
    /**
     * Gets the enum representing the action that should happen when playback
     * reaches the end of the range.
     */
    public OnEndOfRange getOnEndOfRange() {
      return onEndOfRange;
    }

    /**
     * Sets the enum value that represents the action to be applied when playback 
     * reaches the end of the range.
     * 
     * @param onEndOfRange 
     */
    public void setOnEndOfRange(OnEndOfRange onEndOfRange) {
      this.onEndOfRange = onEndOfRange;
      this.getElement().setProperty("onEndOfRange", onEndOfRange.getAction());
      Log.message(AudioPlayer.this, "setting action to do when reaching end of range to " + onEndOfRange);
    }

    // =========================================================================
    // === Effects =============================================================
    // =========================================================================

    /**
     * Add effect immediately to the audio player.
     * 
     * @param effect
     *            Effect to add
     */
    public void addEffect(Effect effect) {
        // TODO: update effect if it already exists
        effects.add(effect.getSharedEffectObject());
    }

    /**
     * Removes effect immediately from audio player.
     * 
     * @param effect
     *            Effect to remove
     */
    public void removeEffect(Effect effect) {
        // TODO: optimize removing effects so we don't have to loop
        for (SharedEffect e : effects) {
            if (effect.getID().equals(e.getID())) {
                Log.message(AudioPlayer.this, "removing effect: " + e.getName().name());
                effects.remove(e);
            }
        }
    }

    /**
     * Updates properties of the effect and passes the changes to the client side.
     * 
     * @param effect
     *            Effect to update
     */
    public void updateEffect(Effect effect) {
        for (SharedEffect e : effects) {
            if (effect.getID().equals(e.getID())) {
                Log.message(AudioPlayer.this, "updating effect: " + e.getName().name());
                e.setProperties(effect.getSharedEffectObject().getProperties());
            }
        }
    }

    // =========================================================================
    // === Listeners ===========================================================
    // =========================================================================

    public void addStateChangeListener(StateChangeCallback cb) {
        stateCallbacks.add(cb);
    }

    public void removeStateChangeListener(StateChangeCallback cb) {
        stateCallbacks.remove(cb);
    }

    public void addValueChangeListener(VolumeChangeCallback cb) {
        volumeCallbacks.add(cb);
    }

    public void removeValueChangeListener(VolumeChangeCallback cb) {
        volumeCallbacks.remove(cb);
    }

    // =========================================================================
    // =========================================================================
    // =========================================================================

}