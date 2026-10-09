package com.undershards.godarmour.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Small helper for translating '&amp;' colour codes into Adventure
 * components and sending prefixed messages.
 */
public final class Text {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private Text() {
    }

    /**
     * Translates '&amp;' colour codes into a {@link Component}.
     *
     * @param input raw text with optional '&amp;' codes
     * @return the translated component
     */
    public static @NotNull Component colorize(@NotNull String input) {
        return LEGACY.deserialize(input);
    }

    /**
     * Sends a prefixed, colourised message to any sender.
     *
     * @param sender  receiver
     * @param prefix  message prefix (already colour coded)
     * @param message message body (already colour coded)
     */
    public static void send(@NotNull CommandSender sender, @NotNull String prefix, @NotNull String message) {
        sender.sendMessage(colorize(prefix + message));
    }
}
