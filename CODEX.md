# VaadinAudioPlayer - Codex Notes

## Purpose
VaadinAudioPlayer is a Vaadin Flow add-on that streams server-side PCM audio data to the browser using Web Audio. The server slices PCM data into time-based chunks, exposes each chunk via a Vaadin StreamResource, and the client fetches and schedules those chunks for smooth playback. Playback controls, range handling, and basic effects are managed through a custom web component backed by a Java server-side component.

## High-level architecture
- Server-side (Java, Vaadin Flow):
  - Builds a `Stream` from PCM data + format + `Encoder`.
  - Splits audio into `ChunkDescriptor` entries with time/sample offsets.
  - `AudioPlayer` registers each chunk as a `StreamResource` and passes chunk metadata to the client as element properties.
  - Server receives playback/volume updates from the client via `@ClientCallable`.
- Client-side (Web Components + Web Audio):
- `audio-player.ts` defines the `<audio-player>` element (Lit) and creates `AudioStreamPlayer` + `ClientStream`.
  - `ClientStream` fetches chunks via XHR (arraybuffer) and wraps them in `ClientStreamBuffer`, which decodes to an `AudioBuffer`.
  - `AudioStreamPlayer` schedules chunk playback, handles speed/pitch correction, per-channel gain, balance, and range behavior.

## Key server-side types
- `AudioPlayer` (Vaadin component) in `src/main/java/org/vaadin/addon/audio/server/AudioPlayer.java`
  - Main UI component; registers chunk resources and exposes JS calls for playback controls.
  - Properties set on the element: `chunks`, `duration`, `chunkTimeMillis`, `reportPositionRepeatTime`, `startRange`, `endRange`, `onEndOfRange`.
  - Receives client callbacks: `reportPlaybackPosition`, `reportPlaybackStarted`, `reportPlaybackPaused`, `reportPlaybackStopped`, `reportVolumeChange`.
  - Effects list is tracked but only partially wired.
  - Known gaps: `skip(int)`, `play(int)`, and `getChunkDescriptor` are TODO; `setNumberChunksToPreload` is a no-op due to variable shadowing.

- `Stream` in `src/main/java/org/vaadin/addon/audio/server/Stream.java`
  - Encodes PCM data into chunks (default 5s) and exposes `getChunks()` and `getChunkData()`.
  - `getChunkData` uses the configured `Encoder` and returns bytes via callback.
  - Tracks `StreamState` via callbacks.
  - Compression toggles exist but no client decompression usage in current code.

- Encoders in `src/main/java/org/vaadin/addon/audio/server/encoders`
  - `WaveEncoder`: implemented; wraps PCM data in a WAV header.
  - `MP3Encoder`/`OGGEncoder`: placeholders returning `null`.

- Effects in `src/main/java/org/vaadin/addon/audio/server/effects`
  - `BalanceEffect`, `FilterEffect`, `VolumeEffect` build `SharedEffect` metadata.
  - `PitchEffect` is unimplemented (returns `null`).

- Shared DTOs in `src/main/java/org/vaadin/addon/audio/shared`
  - `ChunkDescriptor`: time/sample offsets and resource URL.
  - `PCMFormat`: PCM metadata (channels, sample rate, bits, etc.).
  - `SharedEffect` + `SharedEffectProperty`: effect metadata transported to client.

- Utilities in `src/main/java/org/vaadin/addon/audio/server/util`
  - `WaveUtil` parses/creates WAV headers, `Endian` helpers, `StringFormatter` for timestamps.
  - `ULawUtil` converts u-law to PCM for demo use.
  - `FeatureSupport` performs coarse browser support checks.
  - `OnEndOfRange` enum defines range-end behavior.

## Key client-side modules
- `audio-player.ts` in `src/main/resources/META-INF/resources/frontend/audio-player.ts`
  - Defines `<audio-player>` custom element.
  - Creates `AudioContext`, resumes on Safari user gestures, and forwards control calls to `AudioStreamPlayer`.
  - Reports playback state and position to the server on a timer (`reportPositionRepeatTime`).

- `AudioStreamPlayer` in `src/main/resources/META-INF/resources/frontend/src/audio-stream-player.js`
  - Owns playback scheduling and chunk transitions.
  - Uses `BufferPlayerManager` to swap between audio buffers (two players by default).
  - Handles `startRange`, `endRange`, and `onEndOfRange` behaviors (stop at end, stop at start, or loop).
  - Applies playback speed changes with pitch correction via `PitchShiftNode`.

- `ClientStream` in `src/main/resources/META-INF/resources/frontend/src/client-stream.js`
  - Fetches chunk URLs and caches buffers (simple LRU via size cap).

- `ClientStreamBuffer` in `src/main/resources/META-INF/resources/frontend/src/client-stream-buffer.js`
  - Decodes ArrayBuffer to `AudioBuffer` via `AudioContext.decodeAudioData`.

- `BufferPlayer` + `BufferPlayerManager`
  - Wraps `AudioBufferSourceNode` scheduling and handles seamless chunk swaps.

- `MultiChannelGainNode` and `PitchShiftNode`
  - Per-channel volume control via splitter/merger.
  - Pitch correction uses `Jungle` (see `third-party/jungle.js`).

## Data flow summary
1. Server constructs `Stream` from PCM data and an `Encoder`.
2. `AudioPlayer` registers each chunk as a Vaadin `StreamResource` and sends chunk metadata to the client.
3. Client requests chunks by timestamp; server streams bytes for that chunk.
4. Client decodes bytes into `AudioBuffer` and schedules playback.
5. Client reports playback position/state to server at intervals.

## Demo usage (test UI)
- The demo in `src/test/java/org/vaadin/addon/audio/demo/DemoUIFlow.java`:
  - Loads WAV (or u-law) samples from `src/test/resources/org/vaadin/addon/audio/wav`.
  - Builds a `Stream` using `WaveEncoder`.
  - Instantiates `AudioPlayer` and wraps controls via `Controls`.
- Run the demo:
  ```bash
  mvn jetty:run
  ```
  Demo UI at `http://localhost:8080`.

## API sketch (server-side)
```java
// Build a stream from PCM data
PCMFormat format = new PCMFormat(channels, sampleRate, bitsPerSample);
Stream stream = new Stream(pcmBuffer, format, new WaveEncoder(), 5000);

// Create player
AudioPlayer player = new AudioPlayer(stream);

// Control playback
player.play();
player.pause();
player.resume();
player.stop();
player.setPosition(10000);
player.setVolume(0.8);
player.setBalance(-0.3);
player.setPlaybackSpeed(1.25);

// Range behavior
player.setStartRange(2000);
player.setEndRange(12000);
player.setOnEndOfRange(OnEndOfRange.LOOP_POSITION_START);
```

## Known limitations / TODOs
- `MP3Encoder` and `OGGEncoder` are placeholders; only `WaveEncoder` currently works.
- `PitchEffect` is unimplemented; effect propagation is partially wired.
- `AudioPlayer.skip(int)` and `AudioPlayer.play(int)` are stubs.
- `AudioPlayer.setNumberChunksToPreload` does not update the instance field (shadowed variable).
- Client-side compression support (`pako_inflate.min.js`) is present but unused in the current flow.

## File map
- Server core: `src/main/java/org/vaadin/addon/audio/server/`
- Shared DTOs: `src/main/java/org/vaadin/addon/audio/shared/`
- Client web component: `src/main/resources/META-INF/resources/frontend/audio-player.ts`
- Client modules: `src/main/resources/META-INF/resources/frontend/src/`
- Demo UI: `src/test/java/org/vaadin/addon/audio/demo/`
