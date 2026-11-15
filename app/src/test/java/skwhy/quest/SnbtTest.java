package skwhy.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SnbtTest {

    @Test
    void scalarsKeepTheirType() {
        assertEquals("5", Snbt.write(5));
        assertEquals("5L", Snbt.write(5L));
        assertEquals("1.5d", Snbt.write(1.5));
        assertEquals("1.5f", Snbt.write(1.5f));
        assertEquals("1b", Snbt.write(true));
        assertEquals("0b", Snbt.write(false));
        assertEquals("\"a\\\"b\\\\c\"", Snbt.write("a\"b\\c"));
    }

    @Test
    void compoundsQuoteTheirKeys() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Chasseur");
        map.put("script;reward", Map.of("count", 2));
        map.put("list", List.of(1, 2));
        assertEquals("{\"name\":\"Chasseur\",\"script;reward\":{\"count\":2},\"list\":[1,2]}", Snbt.write(map));
    }

    @Test
    void aListWithDecimalsIsWrittenAsDoubles() {
        assertEquals("[1.0d,2.5d]", Snbt.write(List.of(1, 2.5)));
    }
}
