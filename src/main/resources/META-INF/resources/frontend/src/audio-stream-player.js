import { Schedule } from './schedule.js';
import { MultiChannelGainNode } from './multi-channel-gain-node.js';
import { PitchShiftNode } from './pitch-shift-node.js';
import { BufferPlayerManager } from './buffer-player-manager.js';
import './client-stream-buffer.js';
import './client-stream.js';

/**
 * Implements chunk streaming and playback sequencing.
 *
 * @memberOf VaadinAudioPlayer
 */
export const AudioStreamPlayer = (() => {
    const MAX_BUFFER_PLAYERS = 2;

    return class AudioStreamPlayer {
        /**
         * @param {AudioContext} context
         * @param {ClientStream} stream
         * @param {number} timePerChunk
         */
        constructor(context, stream, timePerChunk) {
            this._context = context;
            this._stream = stream;
            this._timePerChunk = timePerChunk;

            this._chunkOverlapTime = 0;
            this._numChunksPreload = 0;

            this._position = 0;
            this._chunkPosition = 0;

            this._volume = 1;
            this._balance = 0;
            this._playbackSpeed = 1;

            this._playerManager = new BufferPlayerManager(this._context, MAX_BUFFER_PLAYERS);

            this._gainNode = this._context.createGain();
            this._playerManager.connectAll(this._gainNode);

            this._multiChannelGainNode = new MultiChannelGainNode(this._gainNode);

            // Set channel interpretation to speakers so that a mono audio source is output on two channels
            console.log('setting channel interpretation to speakers');

            const oscillator = this._context.createOscillator();
            const gainNode = this._gainNode;

            oscillator.connect(gainNode);
            gainNode.connect(this._context.destination);

            oscillator.channelInterpretation = "speakers";

            // Could use StereoPannerNode instead, but it is missing in Safari :-(
            this._pannerNode = this._context.createPanner();
            this._pannerNode.panningModel = 'equalpower';
            this._pannerNode.rolloffFactor = 1;
            if (this._pannerNode.positionZ) {
                this._pannerNode.positionZ.value = 1;
            } else {
                this._pannerNode.setPosition(0, 0, -1);
            }

            this._multiChannelGainNode.connect(this._pannerNode);

            this._pitchShiftNode = new PitchShiftNode(this._pannerNode);

            this._chunkStartTime = undefined;

            this._tryResume = undefined;

            // request first audio chunk
            this._initFirstAudioChunk();
        }

        /**
         * @param {AudioNode} destination
         */
        connect(destination) {
            this._pitchShiftNode.connect(destination);
        }

        /**
         */
        disconnect() {
            this._pitchShiftNode.disconnect();
        }

        _getScheduleTime() {
            return 1000 * this._context.currentTime
                    + Schedule.AUDIO_SCHEDULE_DELAY_MS;
        }

        /**
         * @param {number?} timeOffset
         * @param {boolean} isChunkTransition
         */
        play(timeOffset, isChunkTransition = false) {
            if (timeOffset === undefined) {
                if (this._playerManager.currentPlayer.isScheduled) {
                    // Do nothing, the playback is going
                    return;
                }

                timeOffset = this.position % this._timePerChunk;
            } else {
                if (this._playerManager.currentPlayer.isScheduled) {
                    this._chunkStartTime = undefined;
                }
            }

            if (timeOffset < 0 || this._position + timeOffset >= this.duration) {
                // Start offset is outside the range
                return;
            }

            if (isChunkTransition && this._chunkStartTime !== undefined) {
                // Chunk transition, start playback precisely when
                // the current chunk’s playback ends
                this._chunkStartTime +=
                    ((this._prevChunkDuration - this._chunkPosition) + timeOffset)
                    / this._playbackSpeed;
            } else {
                // Not a transition, start playback right about now
                this._chunkStartTime = this._getScheduleTime();
            }

            // Make sure to stop the previous player’s playback
            this._playerManager.prevPlayer.stop(this._chunkStartTime / 1000);

            if (!this._playerManager.currentPlayer.buffer) {
                // Current chunk is not ready, pause and resume later
                const scheduleTime = this._chunkStartTime;
                this.pause();
                this._chunkPosition = timeOffset;
                this._resumeWhenNextChunkReadyFrom(timeOffset);
            } else {
                this._chunkPosition = timeOffset;
                this.resume();
            }

        }

        /**
         * @param {number} timeOffset
         * @param {boolean?} moveToNextPlayer
         * @private
         */
        _resumeWhenNextChunkReadyFrom(timeOffset, moveToNextPlayer = false) {
            this._tryResume = () => {
                if (this._chunkStartTime === undefined) {
                    if (moveToNextPlayer) {
                        this._playerManager.moveToNextPlayer();
                    }
                    this.resume();
                }
                this._tryResume = undefined;
            };
        }

        pause() {
            const scheduleTime = this._getScheduleTime();
            if (this._chunkStartTime !== undefined) {
                const elapsedTime = scheduleTime - this._chunkStartTime;
                this._chunkPosition += elapsedTime * this._playbackSpeed;
                this._chunkStartTime = undefined;
            }
            this._playerManager.prevPlayer.stop(scheduleTime / 1000);
            this._playerManager.currentPlayer.stop(scheduleTime / 1000);
            this._stopNextChunkScheduling();
            this._chunkStartTime = undefined;
            this._tryResume = undefined;
        }

        resume() {
            this._setPersistingPlayerOptions(this._playerManager.currentPlayer);
            if (this._chunkStartTime === undefined) {
                this._chunkStartTime = this._getScheduleTime();
            }
            this._playerManager.currentPlayer.play(this._chunkPosition, this._chunkStartTime / 1000);
            this._startNextChunkScheduling();

            const nextChunkTime = Math.min(
                this.duration,
                this._position + this._timePerChunk + this._chunkOverlapTime
            );
            if (nextChunkTime < this.duration) {
                this._fetchChunksForNextPlayer(nextChunkTime);
            }
        }

        stop() {
            this._playerManager.prevPlayer.stop();
            this._playerManager.currentPlayer.stop();
            this._stopNextChunkScheduling();
            this._position = 0;
            this._chunkPosition = 0;
            this._chunkStartTime = undefined;
            this._tryResume = undefined;
            if (this.onStop) {
                this.onStop();
            }
            this._initFirstAudioChunk();
        }

        _initFirstAudioChunk() {
            this._playerManager.moveToPrevPlayer();
            this._fetchChunksForNextPlayer(0)
                .then(
                    /**
                     * @param {VaadinAudioPlayer.ClientStreamBuffer} buffer
                     */
                    buffer => {
                        this._chunkOverlapTime = buffer.chunk.overlapTime;
                        return buffer;
                    }
                );
            this._playerManager.moveToNextPlayer();
        }

        /**
         * @param {number} timestamp
         * @returns {Promise<VaadinAudioPlayer.ClientStreamBuffer>}
         */
        _fetchChunksForNextPlayer(timestamp) {
            const player = this._playerManager.nextPlayer;
            player.buffer = null;
            const request = this._stream.requestChunkByTimestamp(timestamp)
                .then(
                    /**
                     * @param {VaadinAudioPlayer.ClientStreamBuffer} buffer
                     */
                    buffer => {
                        buffer.ready.then(
                            /**
                             * @param {AudioBuffer} audio
                             * @returns {AudioBuffer}
                             */
                            audio => {
                                const currentChunkTimestamp = Math.max(0, this._position - this._chunkOverlapTime);
                                player.buffer = audio;

								//console.log('switching to 2-channel mono: ', audio.length);
                                //audio.output.L = input.M;
                                //audio.output.R = input.M;

                                if (this._tryResume && timestamp === currentChunkTimestamp) {
                                    this._tryResume();
                                }
                                // console.warn(
                                //     'decoded audio buffer for',
                                //     'chunk', buffer.chunk.id,
                                //     'timestamp', timestamp,
                                //     'buffer duration', audio.duration,
                                //     'length', audio.length,
                                //     'samplerate', audio.sampleRate
                                // );
                                return audio;
                            }
                        );

                        return buffer;
                    }
                );

            if (this._numChunksPreload > 1) {
                for (let i = 1; i < this._numChunksPreload; i++) {
                    const time = i * this._timePerChunk;
                    this._stream.requestChunkByTimestamp(time);
                }
            }

            return request;
        }

        _startNextChunkScheduling() {
            if (this._playNextChunkInterval !== undefined) {
                return;
            }

            this._playNextChunkInterval = window.setInterval(
                () => this._scheduleNextChunk(),
                Schedule.CHUNK_SCHEDULE_INTERVAL_MS
            );
        }

        _stopNextChunkScheduling() {
            if (this._playNextChunkInterval === undefined) {
                return;
            }

            window.clearInterval(this._playNextChunkInterval);
            this._playNextChunkInterval = undefined;
        }

        _scheduleNextChunk() {
            const chunkDuration = this._currentChunkDuration / this._playbackSpeed;
            const chunkOffset = this._currentChunkPosition / this._playbackSpeed;
            const timeLeft = chunkDuration - chunkOffset
                - Schedule.AUDIO_SCHEDULE_DELAY_MS;
            // start next chunk if time left is small enough
            if (timeLeft < 2 * Schedule.CHUNK_SCHEDULE_INTERVAL_MS) {
                this._playNextChunk();
            }
        }

        /**
         * @returns {number}
         */
        get _currentChunkPosition() {
            let position = this._chunkPosition;
            if (this._chunkStartTime !== undefined) {
                position +=
                    (this._getScheduleTime() - this._chunkStartTime)
                    * this._playbackSpeed;
            }
            return position;
        }

        /**
         * @returns {number}
         */
        get _currentChunkDuration() {
            if (this._playerManager.currentPlayer.buffer) {
                return this._playerManager.currentPlayer.buffer.duration * 1000;
            } else {
                // No chunk yet, assume infinite duration
                return Infinity;
            }
        }

        /**
         * @returns {number}
         */
        get _prevChunkDuration() {
            if (this._playerManager.prevPlayer.buffer) {
                return this._playerManager.prevPlayer.buffer.duration * 1000;
            } else {
                // No chunk yet, assume infinite duration
                return Infinity;
            }
        }

        _playNextChunk() {
            const nextChunkOffset = this._position + this._currentChunkDuration;
            if (nextChunkOffset >= this.duration) {
                // console.warn("to stop");
                this.stop();
            } else {
                // console.warn("to play next chunk");
                this._position += this._timePerChunk;
                this._playerManager.moveToNextPlayer();
                this.play(0, true);
            }
        }

        /**
         * @param {VaadinAudioPlayer.BufferPlayer} player
         */
        _setPersistingPlayerOptions(player) {
            player.playbackRate.value = this._playbackSpeed;
        }

        set numChunksPreload(numChunksPreload) {
            this._numChunksPreload = numChunksPreload;
        }

        get duration() {
            return this._stream.duration;
        }

        /**
         * @param {number} millis
         */
        set position(millis) {
            const isPlaying = this._chunkStartTime !== undefined;

            const offset = millis % this._timePerChunk;
            const newPosition = millis - offset;
            if (newPosition === this._position) {
                if (isPlaying) {
                    this.play(offset, false);
                } else {
                    this._chunkPosition = offset;
                }
            } else {
                if (isPlaying) {
                    this.pause();
                }
                this._position = newPosition;
                this._chunkPosition = offset;
                const shouldResume = isPlaying || this._tryResume !== undefined;
                if (shouldResume) {
                    this._resumeWhenNextChunkReadyFrom(offset, true);
                }
                this._fetchChunksForNextPlayer(this._position + this._chunkOverlapTime)
                    .then(
                        /**
                         * @param {VaadinAudioPlayer.ClientStreamBuffer} buffer
                         */
                        buffer => {
                            buffer.ready.then(
                                /**
                                 * @param {AudioBuffer} audio
                                 */
                                audio => {
                                    if (!shouldResume) {
                                        this._playerManager.moveToNextPlayer();
                                    }
                                }
                            );
                        }
                    );
            }
        }

        get position() {
            const position = this._position + this._currentChunkPosition;
            return Math.min(position, this.duration);
        }

        /**
         * @return {number}
         */
        get volume() {
            return this._gainNode.gain.value;
        }

        /**
         * @param {number} volume
         */
        set volume(volume) {
            this._gainNode.gain.value = volume;
        }

        get playbackSpeed() {
            return this._playbackSpeed;
        }

        set playbackSpeed(playbackSpeed) {
            if (playbackSpeed <= 0) {
                throw new Error('playback speed must be greater than 0');
            }

            const isPlaying = this._chunkStartTime !== undefined;
            const scheduleTime = this._getScheduleTime();
            // calculate the position in the chunk based on elapsed time and current playback speed
            if (isPlaying) {
                const elapsedTime = scheduleTime - this._chunkStartTime;
                this._chunkPosition += elapsedTime * this._playbackSpeed;
                this._chunkStartTime = scheduleTime;
            }

            const when = scheduleTime / 1000;

            // Apply pitch correction
            this._pitchShiftNode.setPitchFactor(1 / playbackSpeed, when);

            // update playback speeds
            this._playbackSpeed = playbackSpeed;
            this._playerManager.players.forEach(
                player => player.playbackRate.setValueAtTime(this._playbackSpeed, when)
            );

            // if (isPlaying) {
            // reset next chunk schedule
            // this._stopNextChunkScheduling();
            // this._startNextChunkScheduling();
            // }
        }

        get balance() {
            return this._balance;
        }

        set balance(balance) {
            this._balance = balance;
            const a = 0.5 * balance * Math.PI;
            const x = Math.sin(a);
            const z = -Math.cos(a);
            if (this._pannerNode.positionX) {
                this._pannerNode.positionX.value = x;
                this._pannerNode.positionZ.value = z;
            } else {
                this._pannerNode.setPosition(x, 0, z);
            }
        }

        /**
         * @param {number} channel
         * @returns number
         */
        getVolumeOnChannel(channel) {
            return this._multiChannelGainNode.getGain(channel).value;
        }

        /**
         * @param {number} volume
         * @param {number} channel
         */
        setVolumeOnChannel(volume, channel) {
            this._multiChannelGainNode.getGain(channel).value = volume;
        }
    };
})();

