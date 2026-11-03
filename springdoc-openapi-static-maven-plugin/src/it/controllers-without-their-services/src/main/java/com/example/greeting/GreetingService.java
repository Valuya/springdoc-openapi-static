package com.example.greeting;

public class GreetingService {

    public GreetingDto greet(String name) {
        return new GreetingDto("Hello " + name);
    }
}
