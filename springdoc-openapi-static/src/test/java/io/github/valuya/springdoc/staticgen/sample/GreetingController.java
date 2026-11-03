package io.github.valuya.springdoc.staticgen.sample;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/greetings")
public class GreetingController {

    private final GreetingService greetingService;
    private final GreetingRepository greetingRepository;

    public GreetingController(GreetingService greetingService, GreetingRepository greetingRepository) {
        this.greetingService = greetingService;
        this.greetingRepository = greetingRepository;
    }

    @GetMapping("/{name}")
    public GreetingDto getGreeting(@PathVariable String name) {
        return greetingService.greet(name);
    }

    @GetMapping("/salutation")
    public String getSalutation() {
        return greetingRepository.findSalutation();
    }
}
