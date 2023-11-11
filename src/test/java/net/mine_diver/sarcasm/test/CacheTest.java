package net.mine_diver.sarcasm.test;

import net.mine_diver.sarcasm.util.Namespace;

import java.util.Random;

public class CacheTest {
    public static void main(String[] args) throws InterruptedException {
        Random random = new Random();
        for (int i = 0; i < 1000000; i++) {
            Namespace.of(() -> "Something" + random.nextInt());
        }
        System.gc();
        Thread.sleep(1000);
        System.out.println("Done!");
    }
}
