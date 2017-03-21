package com.parse.example;

import com.parse.ParseClassName;
import com.parse.ParseObject;

@ParseClassName("Room")
public class Room extends ParseObject {

    public Room() {

    }

    String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("objectId=%s: name=%s", getObjectId(), getName());
    }
}
