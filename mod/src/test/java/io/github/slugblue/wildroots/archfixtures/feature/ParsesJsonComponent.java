package io.github.slugblue.wildroots.archfixtures.feature;
/** SEC-07 violation: parses a string as a JSON text component. */
public class ParsesJsonComponent {
    Object f(String s) { return net.minecraft.network.chat.Component.Serializer.fromJson(s); }
}
