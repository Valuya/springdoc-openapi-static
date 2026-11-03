package io.github.valuya.springdoc.staticgen.sample;

public class GreetingService {

    private final GreetingRepository greetingRepository;

    public GreetingService(GreetingRepository greetingRepository) {
        this.greetingRepository = greetingRepository;
    }

    public GreetingDto greet(String name) {
        String salutation = greetingRepository.findSalutation();
        return new GreetingDto(salutation + " " + name, null);
    }
}
