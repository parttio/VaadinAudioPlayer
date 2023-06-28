import './src/client-stream-buffer.js';
import { ClientStream } from './src/client-stream.js';
import { AudioStreamPlayer } from './src/audio-stream-player.js';

import { PolymerElement } from '@polymer/polymer/polymer-element.js';

class AudioPlayer extends PolymerElement {
    constructor() {
        super();

        // Safari has webkitAudioContext
        const AudioContext = window.AudioContext || window.webkitAudioContext;
        this._context = new AudioContext();

        // Safari requires resuming the context from an user-originated
        // event listener, e. g., on click
        const safariResumeAudioOnClick = () => {
            if (this._context.state !== 'running') {
                const resumeListener = () => {
                    window.removeEventListener('click', resumeListener);
                    this._context.resume();
                };
                window.addEventListener('click', resumeListener);
            }
        };
        safariResumeAudioOnClick();

        // Safari can suspend the audio, detect and apply the resume trick
        this._context.addEventListener('statechange', safariResumeAudioOnClick);
    }

    static get is() {
        return 'audio-player';
    }

    static get properties() {
        return {
            chunks: {
                type: Array,
                value: function() {
                    return [];
                }
            },
            chunkTimeMillis: Number,
            duration: Number,
            numChunksPreload: Number,
            effects: Array,
            reportPositionRepeatTime: {
                type: Number,
                value: 500
            },
            _reportPositionRepeatInterval: Number,
            _lastPlaybackPosition: Number,
            startRange: Number,
            endRange: Number,
            onEndOfRange: Number
        };
    }

    static get observers() {
        return [
            '_updateStream(chunks, chunkTimeMillis)',
            '_updateRangeValues(startRange, endRange, onEndOfRange)'
        ];
    }

    connectedCallback() {
        super.connectedCallback();
        if (this._player) {
            this._player.connect(this._context.destination);
        }
    }

    disconnectedCallback() {
        super.disconnectedCallback();
        if (this._player) {
            this.stopPlayback();
            this._player.disconnect();
        }
    }

    _updateRangeValues(startRange, endRange, onEndOfRange) {
        if (this._player) {
            this._player._startRange = startRange;
            this._player._endRange = endRange;
            this._player._onEndOfRange = onEndOfRange;
        }
    }

    _updateStream(chunks, chunkTimeMillis) {
        if (chunks === undefined || chunkTimeMillis === undefined) {
            return;
        }

        if (this._player) {
            this._player.disconnect();
            this._player.onStop = undefined;
        }

        // console.table(chunks);

        this._stream = new ClientStream(this._context, this);
        this._player = new AudioStreamPlayer(this._context, this._stream, chunkTimeMillis, this.startRange, this.endRange, this.onEndOfRange);
        this._player.connect(this._context.destination);
        this._player.onStop = () => {
            this.$server.reportPlaybackStopped();
            this.$server.reportPlaybackPosition(this._player.position);
            this._cancelPositionReportSchedule();
        };
    }

    /**
     * @param {number} startTime
     * @param {number} endTime
     */
    requestAndCacheAudioChunks(startTime, endTime) {
        for (let i = startTime; i < endTime; i += this._chunkTimeMillis) {
            this._stream.requestChunkByTimestamp(i);
        }
    }

    /**
     * @param {number} position_millis
     */
    setPlaybackPosition(position_millis) {
        // console.warn("set position", position_millis);
        // this.$server.reportPlaybackPosition(position_millis);
        this._player.position = Math.max(0, position_millis);
    }

    /**
     * @param {number} delta_millis
     */
    skipPosition(delta_millis) {
        this.setPlaybackPosition(delta_millis);
    }

    startPlayback() {
        // console.warn('NAP element startPlayback');
        this.$server.reportPlaybackStarted();
        this._player.play();
        this._schedulePositionReport();
    }

    pausePlayback() {
        // console.warn('NAP element pausePlayback');
        this._player.pause();
        this.$server.reportPlaybackPosition(this._player.position);
        this.$server.reportPlaybackPaused();
        this._cancelPositionReportSchedule();
    }

    resumePlayback() {
        // console.warn('NAP element resumePlayback');
        this._player.resume();
        this.$server.reportPlaybackStarted();
        this._schedulePositionReport();
    }

    stopPlayback() {
        // console.warn('NAP element stopPlayback');
        this._player.stop();
        this._cancelPositionReportSchedule();
    }

    _schedulePositionReport() {
        this._cancelPositionReportSchedule();
        this._reportPositionRepeatInterval = window.setInterval(() => {
            const position = this._player && this._player.position;
            if (position !== this._lastPlaybackPosition) {
                this.$server.reportPlaybackPosition(position);
                this._lastPlaybackPosition = position;
            }
        }, this.reportPositionRepeatTime);
    }

    _cancelPositionReportSchedule() {
        if (this._reportPositionRepeatInterval) {
            window.clearInterval(this._reportPositionRepeatInterval);
            this._reportPositionRepeatInterval = undefined;
        }
    }

    /**
     * @param {number} speed_multiplier
     */
    setPlaybackSpeed(speed_multiplier) {
        this._player.playbackSpeed = speed_multiplier;
    }

    /**
     * @param {number} volume
     */
    setVolume(volume) {
        this.$server.reportVolumeChange(volume, [0,1]);
        this._player.volume = volume;
    }

    /**
     * @param {number} volume
     * @param {number} channel
     */
    setVolumeOnChannel(volume, channel) {
		this.$server.reportVolumeChange(volume, [channel]);
        this._player.setVolumeOnChannel(volume, channel);
    }

    /**
     * @param {number} balance
     */
    setBalance(balance) {
        this._player.balance = balance;
    }
}
customElements.define(AudioPlayer.is, AudioPlayer);

