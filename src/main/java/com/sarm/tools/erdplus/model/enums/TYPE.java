package com.sarm.tools.erdplus.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by spencer on 16/07/2016.
 */
public enum TYPE {
    ENTITY("Entity"),
    RELATIONSHIP("Relationship"),
    RELATIONSHIP_CONNECTOR("RelationshipConnector");

    private final String textValue;
    private static Map<String, TYPE> namesMap = new HashMap<>();

    static {
        for( TYPE val : TYPE.class.getEnumConstants() )
        {
            namesMap.put(val.getTextValue(), val);
        }
    }
    TYPE(String s) {
        textValue = s;
    }

    @JsonCreator
    public static TYPE forValue(String value) {
        return namesMap.get(value);
    }

    @JsonValue
    public String getTextValue(){
        return textValue;
    }

}
