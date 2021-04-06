package me.tongfei.progressbar;

import me.tongfei.progressbar.DefaultProgressBarRenderer;
import me.tongfei.progressbar.ProgressBarRenderer;
import me.tongfei.progressbar.ProgressState;

import java.time.temporal.ChronoUnit;

import static com.diogonunes.jcolor.Ansi.colorize;
import static com.diogonunes.jcolor.Attribute.TEXT_COLOR;

public class ColorPBR extends DefaultProgressBarRenderer {

    public ColorPBR() {
        super(ProgressBarStyle.COLORFUL_UNICODE_BLOCK,
            "",
            1,
            false,
            null,
            ChronoUnit.SECONDS
            );
    }


}
