package Fuzzcode.Client.ui.styles;

import javax.swing.*;
import java.awt.*;

public class Styles {

    public static final Color BG_DARK = new Color(40, 40, 40);
    public static final Color BG_DARKER = new Color(30, 30, 30);
    public static final Color BG_MID = new Color(60, 60, 60);
    public static final Color FG_TEXT = Color.WHITE;

    public static void apply() {

        UIManager.put("Panel.background", BG_DARK);
        UIManager.put("Panel.foreground", FG_TEXT);

        UIManager.put("Label.background", BG_DARK);
        UIManager.put("Label.foreground", FG_TEXT);

        UIManager.put("Button.background", BG_MID);
        UIManager.put("Button.foreground", FG_TEXT);

        UIManager.put("TextField.background", BG_MID);
        UIManager.put("TextField.foreground", FG_TEXT);
        UIManager.put("TextField.caretForeground", FG_TEXT);

        UIManager.put("PasswordField.background", BG_MID);
        UIManager.put("PasswordField.foreground", FG_TEXT);
        UIManager.put("PasswordField.caretForeground", FG_TEXT);

        UIManager.put("Table.background", BG_DARK);
        UIManager.put("Table.foreground", FG_TEXT);
        UIManager.put("Table.gridColor", BG_MID);

        UIManager.put("TableHeader.background", BG_MID);
        UIManager.put("TableHeader.foreground", FG_TEXT);

        UIManager.put("ScrollPane.background", BG_DARK);
        UIManager.put("Viewport.background", BG_DARK);

        UIManager.put("List.background", BG_DARK);
        UIManager.put("List.foreground", FG_TEXT);

        UIManager.put("TitledBorder.titleColor", FG_TEXT);

        UIManager.put("ScrollBar.thumb", BG_MID);
        UIManager.put("ScrollBar.track", BG_DARKER);
    }
}
