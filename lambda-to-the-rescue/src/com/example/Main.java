package com.example;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class Main {
    private static final String RESOURCE = "resources/resource";

    public static void main(String[] args) throws IOException, URISyntaxException {
        Matcher startsWith = (line, toMatch) -> startsWith(line, toMatch);

        ProfileFilter profileFilter =
            new ProfileFilter(
                startsWith.curry("#some-profile"),
                startsWith.curry("key2"),
                startsWith.curry("key3")
            );

        Files.lines(resource())
             .filter(profileFilter)
             .forEach(System.out::println);
    }

    static class ProfileFilter implements Predicate<String> {
        private LinkedList<Predicate<String>> predicates;

        ProfileFilter(Predicate<String>...predicates) {
            this.predicates = new LinkedList<>(Arrays.asList(predicates));
        }

        @Override
        public boolean test(String s) {
            if (predicates.size() == 0) {
                return false;
            }

            boolean shouldFilter = predicates.getFirst().test(s);

            if (shouldFilter) {
                predicates.removeFirst();
            }

            return shouldFilter;
        }
    }

    interface Matcher extends BiPredicate<String, String> {

        default Predicate<String> curry(String applied) {
            return s -> this.test(s, applied);
        }
    }

    static Boolean startsWith(String line, String toMatch) {
        return line.toLowerCase().trim().startsWith(toMatch.toLowerCase());
    }

    static Path resource() throws URISyntaxException {
        return Paths.get(Main.class.getClassLoader().getResource(RESOURCE).toURI());
    }
}
