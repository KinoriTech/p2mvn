package tech.kinori.eclipse.p2mvn.cli;

import org.jetbrains.annotations.NotNull;

import static com.diogonunes.jcolor.Ansi.colorize;
import static com.diogonunes.jcolor.Attribute.*;

public class Message {

    private static final String prompt = colorize("?", GREEN_TEXT());
    private static final String warn = colorize("!", YELLOW_TEXT());
    private static final String err = colorize("X", RED_TEXT());

    public void  askInput(@NotNull String text) {
        this.askInput(text, "");
    }

    public void  askInput(@NotNull String text, @NotNull String options) {
        String entry = colorize(text, MAGENTA_TEXT());
        String choices = colorize(options, YELLOW_TEXT());
        System.out.print(String.format("%s %s %s ", prompt, entry, choices));
    }

    public void  showProgress(@NotNull String text) {
        System.out.println(colorize(text, GREEN_TEXT()));
    }

    public void showResult(@NotNull String text, @NotNull String result) {
        String info = colorize(result, TEXT_COLOR(208));
        System.out.println(String.format("%s: %s", text, info));
    }

    public void showInfo(@NotNull String text) {
        System.out.println(colorize(text, CYAN_TEXT()));
    }

    public void  showWarn(@NotNull String text) {
        String entry = colorize(text, YELLOW_TEXT(), BOLD());
        System.out.println(String.format("%s %s", warn, entry));
    }

    public void  showError(@NotNull String text) {
        String entry = colorize(text, RED_TEXT(), BOLD());
        System.out.println(String.format("%s %s", err, entry));
    }


}
