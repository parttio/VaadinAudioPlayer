package org.vaadin.addon.audio.demo;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.component.notification.Notification;
import org.vaadin.addon.audio.server.AudioPlayer;
import org.vaadin.addon.audio.server.state.PlaybackState;
import org.vaadin.addon.audio.server.state.StateChangeCallback;
import org.vaadin.addon.audio.server.util.OnEndOfRange;

/**
 * Component for the player-controls template.
 */
@Tag("player-controls")
@JsModule("./player-controls.js")
@Uses(SliderWithCaption.class)
public class Controls extends Component implements HasSize, HasComponents {

	private AudioPlayer player;
	private Button back5Button;
	private Button stopButton;
	private Button pauseButton;
	private Button playButton;
	private Button forward5Button;
	private Button range1Button;
	private Button range2Button;
	private Button range3Button;
	private ComboBox<OnEndOfRange> onEndRangeOptions;
	private SliderWithCaption positionSlider;
	private SliderWithCaption volumeSlider;
	private SliderWithCaption leftGainSlider;
	private SliderWithCaption rightGainSlider;
	private SliderWithCaption balanceSlider;
	private SliderWithCaption speedSlider;
	private Button deleteButton;

	/**
	 * Creates a new PlayerControls.
	 */
	public Controls(AudioPlayer player, String streamName) {
		this.initControlButtons();
		this.initSliders();
		this.setStreamName("Stream " + streamName);
		setWidthFull();
		this.player = player;
		getElement().appendChild(this.player.getElement());

		onEndRangeOptions.setLabel("On End Range");
		onEndRangeOptions.setWidth("250px");
		onEndRangeOptions.setItems(OnEndOfRange.values());
		onEndRangeOptions.setItemLabelGenerator(OnEndOfRange::name);
		onEndRangeOptions.setValue(player.getOnEndOfRange());
		onEndRangeOptions.addValueChangeListener(e -> {
			player.setOnEndOfRange(e.getValue());
		});

		positionSlider.getSlider().addValueChangeListener(e -> {
			if (e.isFromClient()) {
				player.setPosition(e.getValue().intValue());
			}
		});

		back5Button.addClickListener(e -> player.skip(-5000));
		stopButton.addClickListener(e -> player.stop());
		pauseButton.addClickListener(e -> {
			if (player.isPaused()) {
				player.resume();
			} else {
				player.pause();
			}
		});
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
		forward5Button.addClickListener(e -> player.skip(5000));

		range1Button.addClickListener(e -> {
			player.setStartRange(2000);
			player.setEndRange(10000);
		});

		range2Button.addClickListener(e -> {
			player.setStartRange(3000);
			player.setEndRange(11000);
		});

		range3Button.addClickListener(e -> {
			player.setStartRange(0);
			player.setEndRange(player.getDuration());
		});

		volumeSlider.getSlider().addValueChangeListener(e -> {
			Notification.show("Volume: " + e.getValue());
			player.setVolume(e.getValue());
			leftGainSlider.getSlider().setValue(e.getValue());
			rightGainSlider.getSlider().setValue(e.getValue());
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
				this.setStreamStatus(text);
			});
		});

		player.addStateChangeListener(new StateChangeCallback() {

			@Override
			public void playbackStateChanged(final PlaybackState new_state) {
				ui.access(() -> {
					String text = "Player status: ";
					switch (new_state) {
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
					setPlayerStatus(text);
				});
			}

			@Override
			public void playbackPositionChanged(final int new_position_millis) {
				ui.access(() -> {
					// TODO: for proper slider setting, we need to know the position
					// in millis and total duration of audio
					int duration = player.getDuration();
					int pos = player.getPosition();
					positionSlider.getSlider().setMaxValue(duration);
					positionSlider.getSlider().setMinValue(0.0);
					// set value without trigger value change event
					positionSlider.getSlider().setValue((double) new_position_millis);
					setTime(player.getPositionString() + " / " + player.getDurationString());
				});
			}
		});

	}

	private void initControlButtons() {
		back5Button = createPrimaryButton("Back 5 sec", "back5Button");
		stopButton = createPrimaryButton("Stop", "stopButton");
		pauseButton = createPrimaryButton("Pause", "pauseButton");
		playButton = createPrimaryButton("Play", "playButton");
		forward5Button = createPrimaryButton("Forward 5 sec", "forward5Button");
		range1Button = createPrimaryButton("Set Range 2000-10000", "range1");
		range2Button = createPrimaryButton("Set Range 3000-11000", "range2");
		range3Button = createPrimaryButton("Clear Ranges", "range3");
		onEndRangeOptions = new ComboBox<>();
		onEndRangeOptions.getElement().setAttribute("slot", "onEndRangeOptions");
		this.getElement().appendChild(back5Button.getElement(), stopButton.getElement(), pauseButton.getElement(),
				playButton.getElement(), forward5Button.getElement(), range1Button.getElement(),
				range2Button.getElement(), range3Button.getElement(), onEndRangeOptions.getElement());

		deleteButton = new Button("Delete stream");
		deleteButton.setId("deleteButton");
		deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
		deleteButton.getElement().getStyle().set("margin", "var(--lumo-space-m)");
		deleteButton.getElement().setAttribute("slot", "deleteButton");
		this.getElement().appendChild(deleteButton.getElement());
	}

	private Button createPrimaryButton(String buttonName, String slotName) {
		Button button = new Button(buttonName);
		button.setId(slotName);
		button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		button.getElement().setAttribute("slot", slotName);
		return button;
	}

	private void initSliders() {
		positionSlider = new SliderWithCaption();
		positionSlider.setId("positionSlider");
		positionSlider.setCaption("Position");
		positionSlider.getSlider().setValue(0.0);
		positionSlider.getSlider().setMaxValue(100.0);
		positionSlider.getSlider().getElement().getStyle().set("width", "100%");
		positionSlider.getElement().setAttribute("slot", "positionSlider");
		this.getElement().appendChild(positionSlider.getElement());

		volumeSlider = createBaseSlider("Volume", "volumeSlider");
		volumeSlider.getSlider().setValue(1.0);
		volumeSlider.getSlider().setMaxValue(10.0);

		leftGainSlider = createBaseSlider("L", "leftGainSlider");
		leftGainSlider.getSlider().setValue(1.0);
		leftGainSlider.getSlider().setMaxValue(10.0);

		rightGainSlider = createBaseSlider("R", "rightGainSlider");
		rightGainSlider.getSlider().setValue(1.0);
		rightGainSlider.getSlider().setMaxValue(10.0);

		balanceSlider = createBaseSlider("Balance", "balanceSlider");
		balanceSlider.getSlider().setValue(0.0);
		balanceSlider.getSlider().setMaxValue(1.0);
		balanceSlider.getSlider().setMinValue(-1.0);

		speedSlider = createBaseSlider("Speed", "speedSlider");
		speedSlider.getSlider().setValue(1.0);
		speedSlider.getSlider().setMaxValue(3.0);
		speedSlider.getSlider().setMinValue(0.5);

		this.getElement().appendChild(volumeSlider.getElement(), leftGainSlider.getElement(),
				rightGainSlider.getElement(), balanceSlider.getElement(), speedSlider.getElement());
	}

	private SliderWithCaption createBaseSlider(String caption, String slotName) {
		SliderWithCaption slider = new SliderWithCaption();
		slider.setId(slotName);
		slider.setCaption(caption);
		slider.getSlider().setStep(0.1);
		slider.getSlider().getElement().getStyle().setWidth("250px");
		slider.getElement().setAttribute("slot", slotName);
		return slider;
	}

	public String getPlayerStatus() {
		return this.getElement().getProperty("playerStatus");
	}

	public void setPlayerStatus(String playerStatus) {
		this.getElement().setProperty("playerStatus", playerStatus);
	}

	public String getStreamStatus() {
		return this.getElement().getProperty("streamStatus");
	}

	public void setStreamStatus(String streamStatus) {
		this.getElement().setProperty("streamStatus", streamStatus);
	}

	public String getStreamName() {
		return this.getElement().getProperty("streamName");
	}

	public void setStreamName(String streamName) {
		this.getElement().setProperty("streamName", streamName);
	}

	public String getTime() {
		return this.getElement().getProperty("time");
	}

	public void setTime(String time) {
		this.getElement().setProperty("time", time);
	}

}
