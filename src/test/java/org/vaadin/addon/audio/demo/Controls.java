package org.vaadin.addon.audio.demo;

import org.vaadin.addon.audio.server.AudioPlayer;
import org.vaadin.addon.audio.server.state.PlaybackState;
import org.vaadin.addon.audio.server.state.StateChangeCallback;

import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.HtmlImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.templatemodel.TemplateModel;

/**
 * A Designer generated component for the player-controls template.
 * <p>
 * Designer will add and remove fields with @Id mappings but
 * does not overwrite or otherwise change this file.
 */
@Tag("player-controls")
@HtmlImport("player-controls.html")
@JsModule("./player-controls.js")
@Uses(SliderWithCaption.class)
public class Controls extends PolymerTemplate<Controls.PlayerControlsModel> implements HasSize, HasComponents {

    private static final long serialVersionUID = 908526599880999031L;

    /**
     * This model binds properties between PlayerControls and player-controls
     */
    public interface PlayerControlsModel extends TemplateModel {
        String getPlayerStatus();

        void setPlayerStatus(String playerStatus);

        String getStreamStatus();

        void setStreamStatus(String streamStatus);

        String getStreamName();

        void setStreamName(String streamName);

        String getTime();

        void setTime(String time);
    }

    private AudioPlayer player;

    @Id("back5Button")
    private Button back5Button;
    @Id("stopButton")
    private Button stopButton;
    @Id("pauseButton")
    private Button pauseButton;
    @Id("playButton")
    private Button playButton;
    @Id("forward5Button")
    private Button forward5Button;
    @Id("positionSlider")
    private SliderWithCaption positionSlider;
    @Id("volumeSlider")
    private SliderWithCaption volumeSlider;
    @Id("leftGainSlider")
    private SliderWithCaption leftGainSlider;
    @Id("rightGainSlider")
    private SliderWithCaption rightGainSlider;
    @Id("balanceSlider")
    private SliderWithCaption balanceSlider;
    @Id("speedSlider")
    private SliderWithCaption speedSlider;
    @Id("deleteButton")
    private Button deleteButton;

    /**
     * Creates a new PlayerControls with lazy setting of the AudioPlayer suitable for use in the AfterNavigationObserver callback.
     */
    public Controls() {
        setWidthFull();

        initButtonsAndSliders(player);
    }

    /**
     * Lazy setting of the AudioPlayer suitable for use in the AfterNavigationObserver callback, may need to now call @{link initPositionSlider}.
     * @param player The AudioPlayer instance
     * @param streamName The stream name
     */
    public void setPlayer(AudioPlayer player, String streamName) {
        this.player = player;

        getElement().appendChild(player.getElement());

        initListeners(player);

        getModel().setStreamName("Stream " + streamName);
    }

    /**
     * Initialise the position slider.
     */
    public void initPositionSlider() {
        int duration = player.getDuration();

        positionSlider.getSlider().setMaxValue(duration);
        positionSlider.getSlider().setMinValue(0.0);
        positionSlider.getSlider().setValue(0.0);
    }

    /**
     * Creates a new PlayerControls all in  one hit, no need to call @{link initPositionSlider}.
     * @param player The AudioPlayer instance
     * @param streamName The stream name
     */
    public Controls(AudioPlayer player, String streamName) {
        getModel().setStreamName("Stream " + streamName);
        setWidthFull();
        this.player = player;
        getElement().appendChild(player.getElement());

        initButtonsAndSliders(player);
        initListeners(player);
    }

    private void initListeners(AudioPlayer player) {
        final UI ui = UI.getCurrent();

        player.getStream().addStateChangeListener(newState -> {
            ui.access(() -> {
                String text = "Stream status: ";
                switch (newState) {
                    case COMPRESSING:
                        text += "COMPRESSING";
                        break;
                    case ENCODING:
                        text += "ENCODING";
                        break;
                    case IDLE:
                        text += "IDLE";
                        break;
                    case READING:
                        text += "READING";
                        break;
                    case SERIALIZING:
                        text += "SERIALIZING";
                        break;
                    default:
                        text += "broken or something";
                        break;
                }
                getModel().setStreamStatus(text);
            });
        });

        player.addStateChangeListener(new StateChangeCallback() {

            {
                modeButtons(PlaybackState.STOPPED);
            }

            @Override
            public void playbackStateChanged(final PlaybackState newState) {
                ui.access(() -> {
                    modeButtons(newState);

                    String text = "Player status: ";
                    switch (newState) {
                        case PAUSED:
                            text += "PAUSED";
                            break;
                        case PLAYING:
                            text += "PLAYING";
                            break;
                        case STOPPED:
                            text += "STOPPED";
                            break;
                        default:
                            break;
                    }
                    getModel().setPlayerStatus(text);
                });
            }

            @Override
            public void playbackPositionChanged(final int newPositionMillis) {
                ui.access(() -> {
                    // TODO for proper slider setting, we need to know the position in millis and total duration of audio
                    updateSlider(newPositionMillis);
                });
            }

            private void modeButtons(PlaybackState newState) {
                switch (newState) {
                case PAUSED:
                    stopButton.setEnabled(true);
                    playButton.setEnabled(false);
                    pauseButton.setEnabled(true);
                    break;
                case PLAYING:
                    stopButton.setEnabled(true);
                    playButton.setEnabled(false);
                    pauseButton.setEnabled(true);
                    break;
                case STOPPED:
                    stopButton.setEnabled(false);
                    playButton.setEnabled(true);
                    pauseButton.setEnabled(false);
                    break;
                default:
                    break;
                }
            }
        });
    }

    private void initButtonsAndSliders(AudioPlayer player) {
        positionSlider.getSlider().addValueChangeListener(e -> {
            if (e.isFromClient()) {
                player.setPosition(e.getValue().intValue());
                getModel().setTime(player.getPositionString() + " / " + player.getDurationString());
            }
        });

        back5Button.addClickListener(e -> {
            //player.skip(-5000);
            int pos = Math.max(0, player.getPosition() - 5_000);
            player.setPosition(pos);
            updateSlider(pos);
        });
        back5Button.setIcon(VaadinIcon.FAST_BACKWARD.create());

        stopButton.addClickListener(e -> {
            player.stop();
            // not sure why this is needed
            int pos = 0;
            player.setPosition(pos);
            updateSlider(pos);
        });
        stopButton.setIcon(VaadinIcon.STOP.create());

        pauseButton.addClickListener(e -> {
            if (player.isPaused()) {
                player.resume();
            } else {
                player.pause();
            }
        });
        pauseButton.setIcon(VaadinIcon.PAUSE.create());

        playButton.addClickListener(e -> {
            if (player.isStopped()) {
                player.play();
            } else if (player.isPaused()) {
                player.resume();
            } else {
                // player.play(0);
                player.play();
            }
        });
        playButton.setIcon(VaadinIcon.PLAY.create());

        forward5Button.addClickListener(e -> {
            //player.skip(5000);
            int pos = Math.min(player.getDuration(), player.getPosition() + 5_000);
            player.setPosition(pos);
            updateSlider(pos);
        });
        forward5Button.setIcon(VaadinIcon.FAST_FORWARD.create());

        volumeSlider.getSlider().addValueChangeListener(e -> {
            Notification.show("Volume: " + e.getValue());
            player.setVolume(e.getValue());
        });
        leftGainSlider.getSlider().addValueChangeListener(e -> {
            Notification.show("Left gain: " + e.getValue());
            player.setVolumeOnChannel(e.getValue(), 0);
        });
        rightGainSlider.getSlider().addValueChangeListener(e -> {
            Notification.show("Right gain: " + e.getValue());
            player.setVolumeOnChannel(e.getValue(), 1);
        });
        balanceSlider.getSlider().addValueChangeListener(e -> {
            Notification.show("Balance: " + e.getValue());
            player.setBalance(e.getValue());
        });
        speedSlider.getSlider().addValueChangeListener(e -> {
            Notification.show("Speed: " + e.getValue());
            player.setPlaybackSpeed(e.getValue());
        });

        deleteButton.addClickListener(e -> getElement().removeFromParent());
    }

    private void updateSlider(int newPositionMillis) {
        int duration = player.getDuration();

        positionSlider.getSlider().setMaxValue(duration);
        positionSlider.getSlider().setMinValue(0.0);
        // set value without trigger value change event
        positionSlider.getSlider().setValue((double) newPositionMillis);

        getModel().setTime(player.getPositionString() + " / " + player.getDurationString());
    }

}
