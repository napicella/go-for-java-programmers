package com.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.example.Google.*;
import static java.util.concurrent.CompletableFuture.*;
import static java.util.concurrent.CompletableFuture.anyOf;
import static java.util.concurrent.CompletableFuture.supplyAsync;

/**
 *  From the 'go for java programmers' talk
 *  Implementing the examples described in the slides: https://talks.golang.org/2015/go-for-java-programmers.slide#1 in
 *  java.
 */
public class Search {
    private static ExecutorService executorService = Executors.newFixedThreadPool(16);
    private static final int TIMEOUT_MILLIS = 150;

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        Search search = new Search();
        for (int i = 0; i < 20; i++) {
            search.google("some query");
        }

        executorService.shutdown();
    }

// Google search parallel: parallel calls to web, image and video services
// Go code from the example
//        func Google(query string) (results []Result) {
//            c := make(chan Result)
//            go func() { c <- Web(query) }()
//            go func() { c <- Image(query) }()
//            go func() { c <- Video(query) }()
//
//            for i := 0; i < 3; i++ {
//                result := <-c
//                results = append(results, result)
//            }
//            return
//        }
    public void google(String query) throws ExecutionException, InterruptedException {
        CompletableFuture<String>[] futures = new CompletableFuture[] {
            supplyAsync(() -> web(query)),
            supplyAsync(() -> image(query)),
            supplyAsync(() -> video(query))
        };

        List<String> result = new ArrayList<>();

        allOf(futures)
            .thenAccept((ignore) -> Arrays.stream(futures)
                                          .map(this::safeGet)
                                          .forEach(result::add)).get();
        // Note: calling get is necessary only because I want to print the result before returning from the function
        System.out.println(result);
    }

// What if we don't want to wait for slow servers. Adding a timeout to the picture.
// Go code from the example
// func Google(query string) (results []Result) {
//    c := make(chan Result, 3)
//    go func() { c <- Web(query) }()
//    go func() { c <- Image(query) }()
//    go func() { c <- Video(query) }()
//
//    timeout := time.After(80 * time.Millisecond)
//        for i := 0; i < 3; i++ {
//        select {
//            case result := <-c:
//                results = append(results, result)
//        case <-timeout:
//                fmt.Println("timed out")
//                return
//        }
//    }
//    return
    public void googleWithTimeout(String query) throws ExecutionException, InterruptedException {
        // This is the first difference with the go example, the result array must be a synchronized list.
        // Go channel are completely thread safe, so it's totally okay to funnel data from multiple go routines to an
        // array.
        List<String> result = Collections.synchronizedList(new ArrayList<>());

        // this is not safe since it's possible that all the thread in the thread pool (default to ForkJoin) are busy,
        // so the timer won't start
        CompletableFuture<Void> timeout = runAsync(() -> timeout(TIMEOUT_MILLIS));

        anyOf(
            allOf(runAsync(() -> result.add(web(query))),
                  runAsync(() -> result.add(image(query))),
                  runAsync(() -> result.add(video(query)))),
            timeout
        ).get();


        System.out.println(result);
    }

    // timeout 2 - fixing the problem mentioned earlier by using a dedicated ScheduledExecutorService for timeouts
    public void googleWithTimeout2(String query) throws ExecutionException, InterruptedException {
        List<String> result = Collections.synchronizedList(new ArrayList<>());

        anyOf(
            allOf(runAsync(() -> result.add(web(query))),
                  runAsync(() -> result.add(image(query))),
                  runAsync(() -> result.add(video(query)))
            ),
            time(TIMEOUT_MILLIS)
        ).get();


        System.out.println(result);
    }

// Let' add to the picture that we have replicate servers and use the first response
// Go code from the example
//    func Google(query string) (results []Result) {
//        c := make(chan Result, 3)
//        go func() { c <- First(query, Web1, Web2) }()
//        go func() { c <- First(query, Image1, Image2) }()
//        go func() { c <- First(query, Video1, Video2) }()
//        timeout := time.After(80 * time.Millisecond)
//        for i := 0; i < 3; i++ {
//            select {
//                case result := <-c:
//                    results = append(results, result)
//        case <-timeout:
//                    fmt.Println("timed out")
//                    return
//            }
//        }
//        return
//
//
//        func First(query string, replicas ...Search) Result {
//            c := make(chan Result, len(replicas))
//            searchReplica := func(i int) { c <- replicas[i](query) }
//            for i := range replicas {
//                go searchReplica(i)
//            }
//            return <-c
//        }
    public void googleWithReplicatedServers(String query) throws ExecutionException, InterruptedException {
        List<String> result = Collections.synchronizedList(new ArrayList<>());

        // Unfortunately this does not work as expected because the inner anyOf won't stop the other calls,
        // so the result might end up having duplicates, i.e [some-image, some-image, some-video]
        anyOf(
            allOf(
                anyOf(runAsync(() -> result.add(web(query))), runAsync(() -> result.add(webReplica(query)))),
                anyOf(runAsync(() -> result.add(image(query))), runAsync(() -> result.add(imageReplica(query)))),
                anyOf(runAsync(() -> result.add(video(query))), runAsync(() -> result.add(videoReplica(query))))
            ),
            time(TIMEOUT_MILLIS)
        ).get();

        System.out.println(result);
    }

    // replicate servers and use the first response - fixing the problem mentioned earlier by using supplyAsync +
    // thenAccept instead of runAsync
    public void googleWithReplicatedServers2(String query) throws ExecutionException, InterruptedException {
        List<String> result = Collections.synchronizedList(new ArrayList<>());

        anyOf(
            allOf(
                anyOf(supplyAsync(() -> web(query)),
                      supplyAsync(() -> webReplica(query))).thenAccept((s) -> result.add((String) s)),
                anyOf(supplyAsync(() -> image(query)),
                      supplyAsync(() -> imageReplica(query))).thenAccept((s) -> result.add((String) s)),
                anyOf(supplyAsync(() -> video(query)),
                      supplyAsync(() -> videoReplica(query))).thenAccept((s) -> result.add((String) s))
            ),
            time(TIMEOUT_MILLIS)
        ).get();

        System.out.println(result);
    }

    // same as above, but this time we use the function 'first', which is really just a wrapper around
    // CompletableFuture.anyOf
    public void googleWithReplicatedServers3(String query) throws ExecutionException, InterruptedException {
        List<String> result = Collections.synchronizedList(new ArrayList<>());

        anyOf(
            allOf(
                first(query, Google::web, Google::webReplica).thenAccept((s) ->  result.add((String) s)),
                first(query, Google::image, Google::imageReplica).thenAccept((s) ->  result.add((String) s)),
                first(query, Google::video, Google::videoReplica).thenAccept((s) ->  result.add((String) s))
            ),
            time(150)
        ).get();

        System.out.println(result);
    }

    protected CompletableFuture<Object> first(String param, Function<String, String>... replicas) {
        return anyOf(
            Arrays.stream(replicas)
                  .map(replica -> CompletableFuture.supplyAsync(() -> replica.apply(param), executorService))
                  .toArray(CompletableFuture[]::new)
        );
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
