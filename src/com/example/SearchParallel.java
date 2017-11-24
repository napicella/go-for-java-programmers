package com.example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static java.util.concurrent.CompletableFuture.*;
import static java.util.concurrent.CompletableFuture.anyOf;
import static java.util.concurrent.CompletableFuture.supplyAsync;

// implementing the example described in https://talks.golang.org/2015/go-for-java-programmers.slide#39 in java
public class SearchParallel {

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        new SearchParallel().run5();
        Thread.currentThread().join();
    }

    // search parallel
    public void run1() throws ExecutionException, InterruptedException {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        futures.add(supplyAsync(this::service1));
        futures.add(supplyAsync(this::service2));

        allOf(supplyAsync(this::service1), supplyAsync(this::service2)).get();

        // The result array must be a synchronized list
        List<String> result = Collections.synchronizedList(new ArrayList<>());

        futures.stream().map(this::safeGet).forEach(result::add);

        System.out.println(result);
    }

    // timeout
    public void run2() throws ExecutionException, InterruptedException {
        List<String> result = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture<Void> c1 = runAsync(() -> result.add(service1()));
        CompletableFuture<Void> c2 = runAsync(() -> result.add(service2()));

        // this is not safe since it's possible
        // that there are no threads available in the
        // thread pool, so the timer won't start
        CompletableFuture<Void> timeout = runAsync(() -> timeout(80));


        anyOf(
            allOf(c1, c2),
            timeout
        ).get();


        System.out.println(result);
    }

    // timeout 2 - fixing the problem mentioned earlier by using a dedicated ScheduledExecutorService for timeouts
    public void run3() throws ExecutionException, InterruptedException {
        List<String> result = Collections.synchronizedList(new ArrayList<>());

        anyOf(
            allOf(runAsync(() -> result.add(service1())), runAsync(() -> result.add(service1()))),
            time(100)
        ).get();


        System.out.println(result);
    }

    // replicate servers and use the first response
    public void run4() throws ExecutionException, InterruptedException {
        List<String> result = Collections.synchronizedList(new ArrayList<>());


        // Unfortunately this does not work as expected because the inner anyOf won't top the other call,
        // so the result might end up having duplicates, i.e [java, java, golang]
        anyOf(
            allOf(
                anyOf(runAsync(() -> result.add(service1())), runAsync(() -> result.add(service1()))),
                anyOf(runAsync(() -> result.add(service2())), runAsync(() -> result.add(service2())))
            ),
            time(100)
        ).get();

        System.out.println(result);
    }

    // replicate servers and use the first response - fixing the problem mentioned earlier by using supplyAsync +
    // thenAccept instead of runAsync
    public void run5() throws ExecutionException, InterruptedException {
        List<String> result = Collections.synchronizedList(new ArrayList<>());

        anyOf(
            allOf(
                anyOf(supplyAsync(this::service1), supplyAsync(this::service1)).thenAccept((s) ->  result.add((String) s)),
                anyOf(supplyAsync(this::service2), supplyAsync(this::service2)).thenAccept((s) ->  result.add((String) s))
            ),
            time(150)
        ).get();

        System.out.println(result);
    }

    protected String safeGet(Future<String> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        return "";
    }

    protected String service1() {
        randomSleep();
        return "java";
    }

    protected String service2() {
        randomSleep();
        return "golang";
    }

    private void randomSleep() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 200 + 1));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected Void timeout(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    protected CompletableFuture<Void> time(int millis) {
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

        final CompletableFuture<Void> timeout = new CompletableFuture<>();
        executorService.schedule(() -> {
            timeout.complete(null);
        }, millis, TimeUnit.MILLISECONDS);

        return timeout;
    }
}
