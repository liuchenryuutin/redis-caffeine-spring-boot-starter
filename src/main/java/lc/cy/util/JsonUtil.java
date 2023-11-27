package lc.cy.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonParseException;

import java.io.IOException;
import java.text.SimpleDateFormat;

/**
 * 类名称： <br>
 * 类描述： <br>
 *
 * @Date: 2023/04/21 17:25 <br>
 * @author: liuchen11
 */
public class JsonUtil {
    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private JsonUtil() {
    }

    private static void init() {
        mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, true);
        mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        mapper.configure(com.fasterxml.jackson.core.JsonGenerator.Feature.QUOTE_FIELD_NAMES, false);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.configure(com.fasterxml.jackson.core.JsonGenerator.Feature.QUOTE_FIELD_NAMES, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        mapper.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        mapper.setDateFormat(df);
        mapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
        mapper.configure(SerializationFeature.WRITE_ENUMS_USING_INDEX, false);
        mapper.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        mapper.configure(SerializationFeature.WRITE_CHAR_ARRAYS_AS_JSON_ARRAYS, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    public static String toJson(Object obj) {

        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException var3) {
            throw new JsonParseException(var3);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        T t = null;

        try {
            t = mapper.readValue(json, clazz);
            if (log.isDebugEnabled()) {
                log.debug(" trasform [{}] into class:{}", json, clazz);
            }

            return t;
        } catch (IOException var4) {
            throw new JsonParseException(var4);
        }
    }

    public static <T> T fromJson(String json, TypeGetter<T> type) {
        try {
            T t = mapper.readValue(json, type);
            if (log.isDebugEnabled()) {
                log.debug(" trasform [{}]into class:{}", json, type.getType());
            }

            return t;
        } catch (IOException var4) {
            throw new JsonParseException(var4);
        }
    }

    public abstract class TypeGetter<T> extends TypeReference<T> {
        public TypeGetter() {
        }
    }

    static {
        init();
    }
}
