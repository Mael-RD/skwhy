package skwhy.modules.FakeDisplayElements.expressions;

import ch.njol.skript.lang.Expression;
import org.bukkit.event.Event;
import skwhy.data.DisplayData;
import skwhy.data.DisplayGroupData;

import java.util.ArrayList;
import java.util.List;

final class DisplayExpressionTargets {

    private DisplayExpressionTargets() {
    }

    static List<DisplayData> resolve(Expression<?> targets, Event event) {
        Object[] values = targets.getAll(event);
        if (values == null || values.length == 0) {
            return List.of();
        }

        List<DisplayData> displays = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof DisplayData display) {
                displays.add(display);
            } else if (value instanceof DisplayGroupData group) {
                displays.addAll(group.getDisplays());
            }
        }
        return displays;
    }

    static List<DisplayData> resolveForChange(Expression<?> targets, Event event, boolean pluralPattern) {
        if (pluralPattern) {
            return resolve(targets, event);
        }

        Object value = targets.getSingle(event);
        if (value instanceof DisplayData display) {
            return List.of(display);
        }
        if (value instanceof DisplayGroupData group) {
            return group.getDisplays();
        }
        return List.of();
    }
}
