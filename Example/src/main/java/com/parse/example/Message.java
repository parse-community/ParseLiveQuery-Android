package com.parse.example;


import com.parse.ParseClassName;
import com.parse.ParseObject;
import com.parse.ParseUser;

@ParseClassName("Message")
public class Message extends ParseObject {

    private final String PARSE_USER_KEY = "parseUser";
    private final String AUTHOR_KEY = "author";
    private final String MESSAGE_KEY = "message";
    private final String ROOM_KEY = "room";

    public Message() {

    }

    public ParseUser getParseUser() {
        return getParseUser(PARSE_USER_KEY);
    }

    public void setParseUser(ParseUser parseUser) {
        put(PARSE_USER_KEY, parseUser);
    }

    public String getAuthorName() {
        return getString(AUTHOR_KEY);
    }

    public void setAuthorName(String authorName) {
        put(AUTHOR_KEY, authorName);
    }

    public String getMessage() {
        return getString(MESSAGE_KEY);
    }

    public void setMessage(String message) {
        put(MESSAGE_KEY, message);
    }

    public Room getRoom() {
        return (Room) get(ROOM_KEY);
    }

    public void setRoom(Room room) {
        put(ROOM_KEY, room);
    }

    @Override
    public String toString() {
        return String.format("objectId=%s: message=%s room=%s", getObjectId(), getMessage(), getRoom());
    }
}
