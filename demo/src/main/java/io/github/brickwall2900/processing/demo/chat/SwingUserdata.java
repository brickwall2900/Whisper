package io.github.brickwall2900.processing.demo.chat;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

// Swing has no fucking way to store userdata
// here, i'll just use some external object to do that...
public class SwingUserdata {
    private final Map<Integer, Object> hashCodeToUserdataMap = new HashMap<>();

    public <T extends JComponent> void putUserdata(T component, Object o) {
        int hashcode = component.hashCode();

        if (o == null) {
            hashCodeToUserdataMap.remove(hashcode);
        } else {
            hashCodeToUserdataMap.put(hashcode, o);
        }
    }

    public <T extends JComponent> Object getUserdata(T component) {
        return hashCodeToUserdataMap.get(component.hashCode());
    }

    public void clearAll() {
        hashCodeToUserdataMap.clear();
    }
}
