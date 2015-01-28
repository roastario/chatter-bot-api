package com.google.code.chatterbotapi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.jsoup.Jsoup;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Author stefanofranz
 */
public class Main {

    private final AtomicReferenceArray<ChatterBotSession> sessions = new AtomicReferenceArray<ChatterBotSession>(1);
    private final AtomicReference<ChatterBotSession> activeSession = new AtomicReference<ChatterBotSession>();
    private long counter = 0;


    private volatile long lastTimeUsed = -1;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    private void setupBots() {
        scheduledExecutorService.scheduleWithFixedDelay(BOT_CREATE(), 0, 30, TimeUnit.MINUTES);
        scheduledExecutorService.scheduleWithFixedDelay(BOT_SWAP(), 0, 1, TimeUnit.MINUTES);
    }

    private Runnable BOT_SWAP() {
        return new Runnable() {
            public void run() {
                long timeElapsedSinceLastBotRequest = System.currentTimeMillis() - lastTimeUsed;
                if (timeElapsedSinceLastBotRequest > TimeUnit.MINUTES.toMillis(5)) {
                    ChatterBotSession newBot = sessions.get((int) (counter % sessions.length()));
                    System.out.println("SWAPPED BOTS from: " + activeSession + " -> " + newBot);
                    lastTimeUsed = System.currentTimeMillis();
                    activeSession.set(newBot);
                } else {
                    System.out.println("Did not swap bots because only: " + TimeUnit.MILLISECONDS.toMinutes(timeElapsedSinceLastBotRequest) + " minutes have elapsed since last bot invocation");
                }
            }
        };
    }

    private Runnable BOT_CREATE() {
        return new Runnable() {
            public void run() {
                try {
                    System.out.println("Refreshing BOTS");
                    ChatterBotFactory factory = new ChatterBotFactory();
                    ChatterBot bot1 = factory.create(ChatterBotType.CLEVERBOT);
                    sessions.set(0, bot1.createSession());
//                    ChatterBot bot2 = factory.create(ChatterBotType.JABBERWACKY);
//                    sessions.set(1, bot2.createSession());
//                    ChatterBot bot3 = factory.create(ChatterBotType.PANDORABOTS, "b0dafd24ee35a477");
//                    sessions.set(2, bot3.createSession());
                    System.out.println("Finished Setting Bots");
                } catch (Exception ex) {
                    System.err.println("Failed to Create bots");
                    ex.printStackTrace();
                }
            }
        };
    }

    private void setupHTTPServer() throws IOException {
        System.out.println("Starting HTTP EndPoint at: localhost:8976/think");
        System.out.println("Usage: GET http://localhost:8976/think?text=<TEXT>");
        HttpServer server = HttpServer.create();

        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/think", new HttpHandler() {
            public void handle(HttpExchange exchange) throws IOException {
                URI requestURI = exchange.getRequestURI();
                String processedText = getText(requestURI.toString());
                byte[] bytes = processedText.getBytes();
                int length = bytes.length;
                exchange.sendResponseHeaders(200, length);
                exchange.getResponseBody().write(bytes);

                exchange.close();
            }
        });
        server.bind(new InetSocketAddress("localhost", 8976), 0);
        server.start();
    }

    public static void main(String[] args) throws IOException {

        Main main = new Main();
        main.setupBots();
        main.setupHTTPServer();

    }

    private static String processText(String sContainingHref) {
        return Jsoup.parse(sContainingHref).text();
    }

    private String getText(String queryString) {
        String[] split = queryString.split("\\?text=");
        if (split.length > 1) {
            String decoded = URLDecoder.decode(split[1]);
            try {
                String returned = activeSession.get().think(decoded);
                lastTimeUsed = System.currentTimeMillis();
                String processed = processText(returned);
                return processed;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "You're such a bad englisher";
    }

}
