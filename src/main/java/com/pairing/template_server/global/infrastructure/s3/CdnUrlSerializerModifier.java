package com.pairing.template_server.global.infrastructure.s3;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;

import java.util.List;

/**
 * {@link CdnMappable}을 구현한 응답 DTO의 프로퍼티 중,
 * 이름이 {@code ~Url}/{@code ~Urls}로 끝나는 String / List&lt;String&gt; 필드에
 * {@link CdnUrlSerializer}를 자동으로 할당한다. ({@link NoCdnUrl} 필드는 제외)
 */
public class CdnUrlSerializerModifier extends BeanSerializerModifier {

    private final CdnUrlSerializer serializer = new CdnUrlSerializer();

    @Override
    public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                                                     BeanDescription beanDesc,
                                                     List<BeanPropertyWriter> beanProperties) {
        if (!CdnMappable.class.isAssignableFrom(beanDesc.getBeanClass())) {
            return beanProperties;
        }

        for (BeanPropertyWriter writer : beanProperties) {
            if (writer.getAnnotation(NoCdnUrl.class) != null) {
                continue;
            }
            if (isUrlName(writer.getName()) && isStringOrStringCollection(writer.getType())) {
                writer.assignSerializer(serializer);
            }
        }
        return beanProperties;
    }

    private boolean isUrlName(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith("url") || lower.endsWith("urls");
    }

    private boolean isStringOrStringCollection(JavaType type) {
        if (type.getRawClass() == String.class) {
            return true;
        }
        return type.isCollectionLikeType()
                && type.getContentType() != null
                && type.getContentType().getRawClass() == String.class;
    }
}
