package org.vaadin.addon.audio.demo;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.Uses;

/**
 * Component for the slider-with-caption template.
 */
@Tag("slider-with-caption")
@JsModule("./slider-with-caption.js")
@Uses(PaperSlider.class)
public class SliderWithCaption extends Component {

    private PaperSlider slider;

    public SliderWithCaption() {
    	slider = new PaperSlider();
    	slider.setId("slider");
    	slider.setValue(80.0);
    	slider.setMinValue(0.0);
    	slider.setMaxValue(100.0);
    	slider.setStep(1.0);
    	slider.getElement().setAttribute("slot", "slider");
    	this.getElement().appendChild(slider.getElement());
    }

    public String getCaption() {
    	return this.getElement().getProperty("caption");
    }

    public void setCaption(String caption) {
    	this.getElement().setProperty("caption", caption);
    }
    
    public PaperSlider getSlider() {
        return slider;
    }

    public void setSlider(PaperSlider slider) {
        this.slider = slider;
    }

}
