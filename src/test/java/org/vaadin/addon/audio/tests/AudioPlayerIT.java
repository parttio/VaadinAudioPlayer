package org.vaadin.addon.audio.tests;

import com.microsoft.playwright.assertions.PlaywrightAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.vaadin.addons.dramafinder.element.SelectElement;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AudioPlayerIT extends SpringPlaywrightIT {

    @Override
    public String getView() {
        return "";
    }


    @Test
    void demoLoadsAudioPlayer() {
        SelectElement fileSelector = SelectElement.getByLabel(page, "File");
        fileSelector.selectItem("ABCSample.wav");
        PlaywrightAssertions.assertThat(page.locator("player-controls")).isVisible();
    }
}
