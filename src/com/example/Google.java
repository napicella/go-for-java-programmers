package com.example;

import java.util.concurrent.ThreadLocalRandom;

public class Google {

    public static String web(String query) {
        randomSleep();
        return "some-web";
    }

    public static String webReplica(String query) {
        randomSleep();
        return "replica: some-web";
    }

    public static String image(String query) {
        randomSleep();
        return "some-image";
    }

    public static String imageReplica(String query) {
        randomSleep();
        return "replica: some-image";
    }

    public static String video(String query) {
        randomSleep();
        return "some-video";
    }

    public static String videoReplica(String query) {
        randomSleep();
        return "replica: some-video";
    }

    private static void randomSleep() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 200 + 1));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
