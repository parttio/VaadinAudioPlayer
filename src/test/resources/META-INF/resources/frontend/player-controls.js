import '@polymer/polymer/polymer-legacy.js';
import '@vaadin/button/src/vaadin-button.js';
import '@vaadin/horizontal-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/vertical-layout/src/vaadin-vertical-layout.js';
import { html } from '@polymer/polymer/lib/utils/html-tag.js';
import { PolymerElement } from '@polymer/polymer/polymer-element.js';

class PlayerControls extends PolymerElement {
  static get template() {
    return html`
   <style>
            :host {
                display: block;
            }

            .margin {
                margin: var(--lumo-space-m);
            }
        </style> 
   <vaadin-vertical-layout theme="" style="width: 100%; height: 100%;"> 
    <div style="width: 100%; height: 100%;">
      [[streamName]] 
    </div> 
    <div style="width: 100%; height: 100%;">
      [[time]] 
    </div> 
   </vaadin-vertical-layout> 
   <slot name="positionSlider"></slot> 
   <vaadin-horizontal-layout theme="spacing margin" style="width: 100%; height: 100%; justify-content: center;"> 
    <slot name="back5Button"></slot> 
    <slot name="stopButton"></slot> 
    <slot name="pauseButton"></slot> 
    <slot name="playButton"></slot> 
    <slot name="forward5Button"></slot> 
    <slot name="range1"></slot> 
    <slot name="range2"></slot> 
    <slot name="range3"></slot> 
    <slot name="onEndRangeOptions"></slot>
   </vaadin-horizontal-layout> 
   <vaadin-horizontal-layout theme="margin" style="width: 100%; height: 100%;"> 
   	<slot name="volumeSlider"></slot>
   	<slot name="leftGainSlider"></slot>
   	<slot name="rightGainSlider"></slot>
   	<slot name="balanceSlider"></slot>
   	<slot name="speedSlider"></slot>
   </vaadin-horizontal-layout> 
   <div style="display: flex; align-items: center; justify-content: flex-end;"> 
    <div class="margin">
      [[playerStatus]] 
    </div> 
    <div class="margin">
      [[streamStatus]] 
    </div> 
    <slot name="deleteButton"></slot> 
   </div> 
`;
  }

  static get is() {
      return 'player-controls';
  }

  static get properties() {
      return {
          playerStatus: {
              type: String,
              value: ''
          },
          streamStatus: {
              type: String,
              value: ''
          },
          streamName: {
              type: String,
              value: ''
          },
          time: {
              type: String,
              value: '00:00 / 00:00'
          }
      };
  }
}

customElements.define(PlayerControls.is, PlayerControls);

