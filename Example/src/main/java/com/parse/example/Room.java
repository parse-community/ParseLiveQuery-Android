package com.parse.example;

import com.parse.ParseClassName;
import com.parse.ParseObject;

@ParseClassName("Room")
public class Room extends ParseObject {

    private final String NAME_KEY = "name";

    public Room() {

    }

    String name;

    public String getName() {
        return getString(NAME_KEY);
    }

    public void setName(String name) {
        put(NAME_KEY, name);
    }

    @Override
    public String toString() {
        return String.format("objectId=%s: name=%s", getObjectId(), getName());
    }
}
