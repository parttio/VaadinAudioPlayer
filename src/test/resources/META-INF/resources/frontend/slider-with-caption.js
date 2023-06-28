import '@polymer/polymer/polymer-legacy.js';
import { html } from '@polymer/polymer/lib/utils/html-tag.js';
import { PolymerElement } from '@polymer/polymer/polymer-element.js';

class SliderWithCaption extends PolymerElement {
  static get template() {
    return html`
   <style>
            :host {
                display: block;
            }
        </style> [[caption]] 
   <slot name="slider"></slot> 
`;
  }

  static get is() {
      return 'slider-with-caption';
  }

  static get properties() {
      return {
          caption: {
              type: String,
              value: 'caption'
          },         
      };
  }
}

customElements.define(SliderWithCaption.is, SliderWithCaption);

